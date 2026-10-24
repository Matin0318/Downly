**English** · [فارسی](ARCHITECTURE.fa.md)

# Architecture

This page is for people who want to read or change the code. It explains how the modules fit together and where each feature lives.

## The big picture
Downly is split into five Gradle modules. Each one depends only on the ones below it:

```
androidApp      Android-only code: service, alarms, notifications, browser, torrents, widget
    |
shared:ui       Compose Multiplatform screens + DownloadsController
    |
core:data       Settings, download list, backup (JSON files)
    |
core:engine     HTTP, HLS, DASH, FTP, proxy, speed limits
    |
core:model      Small shared data classes
```

The interface never touches the network itself. It asks `DownloadsController` to do things, and the controller uses the engine. State flows the other way as Kotlin `Flow`s that the screens collect.

Anything that needs a feature only one platform has (a foreground service, alarms, the clipboard, file pickers, MediaStore) sits behind an interface and is implemented in `androidApp`. The interface and engine code is common Kotlin, and `core:engine`, `core:data` and `core:model` also have a desktop JVM target, so another platform could be added on the same modules. `shared:ui` already declares a `desktop` target, but there is no desktop application yet.

## Modules in detail

### core:model
`DownloadTask` and `DownloadStatus` (queued, downloading, paused, completed, failed). Nothing else.

### core:engine
The part that moves bytes. It knows nothing about screens.

| File | Job |
|---|---|
| `SegmentedDownloader` | The multi-connection HTTP downloader: probe, plan, parallel workers, dynamic re-splitting, back-off on 429/403/503/509, size check, atomic rename |
| `SegmentPlanner` | Cuts a file into ranges of at least 1 MB |
| `DownloadSnapshot` | The full progress state of a download. Persisting it is what makes resume work |
| `RateLimiter` | A shared limiter. One instance is global, others are per queue or per download |
| `HlsPlaylist`, `HlsDownloader` | Parse playlists and download segments in parallel, written in order, with AES-128 decryption |
| `Dash`, `MiniXml` | A small XML reader and the DASH manifest parser; DASH reuses the HLS downloader for its segments |
| `Ftp`, `FtpClient` | A passive-mode FTP client with resume |
| `CurlParser`, `UrlCredentials`, `RequestHeaders` | Reading cURL commands, `user:pass@` links and cleaning headers |
| `FileNameResolver` | File names from `Content-Disposition` or the URL, made safe for disk |
| `FileChecksum` | MD5, SHA-1, SHA-256 |
| `SiteGrabber` | Finds file links in a page |
| `ProxyConfig`, `ProxySupport`, `TunedSocketFactory` | The proxy, and sockets with big receive buffers |

Platform pieces use `expect`/`actual`: the HTTP client factory, AES and digests. FTP and proxy support live in `jvmShared`, a source folder shared by the Android and desktop targets.

### core:data
- `Models.kt`: `AppSettings`, `DownloadQueue`, `StoredDownload` and the small enums. The status of a download is stored as text, so adding a new status never breaks an old file.
- `Repositories.kt`: `JsonDownloadRepository` and `JsonSettingsRepository`. The settings repository locks the proxy password with a `SecretCodec`.
- `AtomicJsonFile`: every write goes to a temp file and is then moved into place, so a crash cannot leave a half-written file. A file that cannot be read is renamed to `<name>.corrupt` and defaults are used.
- `Backup`: one JSON file with the settings (password removed) and the download list.

### shared:ui
- `DownloadsController` is the brain of the app. It owns the list of downloads, the queues, the scheduler, the retry logic and the storage logic, and it picks the right path (HTTP, FTP, HLS, DASH or torrent) for each link.
- `downloads/`, `queues/`, `settings/`, `files/`: the screens.
- `components/`: shared pieces such as the navigation bar and rail, pickers, progress bars, the icon set and the logo.
- `Strings.kt`, `Strings2.kt`: all user-visible text, one object per language.
- `theme/`: colours, typography and the window size classes used for phone, tablet and landscape layouts.
- Interfaces implemented by the platform: `PlatformActions`, `PublicStorage`, `MediaMerger`, `TorrentEngine`.

### androidApp
| File | Job |
|---|---|
| `DownlyApplication` | Creates the controller, wires notifications, alarms and the service, and writes crash logs |
| `MainActivity` | Hosts the UI, handles shared links, `magnet:` links and `.torrent` files |
| `DownloadService` | A foreground service of type data sync that keeps the process alive, with a wake lock and a Wi-Fi lock |
| `DownloadNotifications` | The progress, finished and failed notifications |
| `ScheduleAlarms` | Arms start and stop alarms for every scheduled queue; the boot receiver arms them again |
| `AndroidPublicStorage`, `StorageAccess` | Direct saving, MediaStore export and the folder picker |
| `AndroidTorrentEngine` | BitTorrent through libtorrent4j |
| `AndroidMediaMerger` | Joins DASH audio and video with `MediaMuxer` without re-encoding |
| `BrowserActivity`, `BrowserStore`, `AdBlock` | The built-in browser, its saved data and the ad-host list |
| `PlayerActivity` | The built-in video and audio player |
| `DownlyWidget` | The home-screen widget |
| `KeystoreSecretCodec` | AES-256-GCM encryption for the proxy password |
| `LookCache` | Remembers language and theme so the first frame is already right |
| `AndroidPlatformActions` | Clipboard, sharing, opening files, battery and alarm settings, backup pickers |

## How a download travels
1. The user adds a link. `DownloadsController.add` validates it and creates an entry in a queue.
2. `start` chooses the path by link type: torrent, FTP, HLS, DASH, or plain HTTP.
3. For HTTP the controller probes the link, makes a `DownloadSnapshot`, waits for a free slot (`awaitSlot`) and calls `SegmentedDownloader.download`.
4. The downloader emits snapshots. The controller turns them into speed and progress, adds to the daily usage counter and saves the list at most every two seconds.
5. When it is done, `publish` moves the file to the public folder if needed, and the status becomes completed.
6. If something fails, the controller decides: try a mirror, retry later with a growing delay, keep retrying inside a schedule window, or mark the download failed.

## Scheduling
The controller checks the clock every few seconds while the app lives, and `ScheduleAlarms` arms system alarms so that schedules also fire when the process is gone. When an alarm fires, it calls `fireSchedule` on the controller. `ScheduleAlarms` stores which start moments it has handled so nothing starts twice. After a reboot, update or clock change, the boot receiver arms everything again and starts any queue whose window is open and whose start was missed.

## Where data lives
| What | Where |
|---|---|
| Settings | `settings.json` in the app's private folder, proxy password encrypted |
| Download list | `downloads.json` in the same folder |
| Bookmarks, history, tabs | Private preferences managed by `BrowserStore` |
| Language and theme cache | A tiny preferences file, `LookCache` |
| Unfinished files | `<name>.part` next to the final file (in `Downloads/Downly` with direct saving, otherwise in app storage) |
| Crash and error logs | `last_crash.txt`, `last_error.txt` in the app's files folder |

## Tests
There are unit tests in `core/engine/src/commonTest` and `core/data/src/commonTest`. They run on the JVM with a mock HTTP client and a fake file system, so they need no network and no phone:

```
./gradlew :core:engine:jvmTest :core:data:jvmTest
```

They cover the segment planner, dynamic segmentation, the segmented downloader, HLS and DASH parsing, the cURL parser, file name resolution, URL logins, checksums, the site grabber, backup and the repositories.

## Adding things
- **A new link type:** add the downloader in `core:engine`, then add a branch in `DownloadsController.start`.
- **A new language:** implement `AppStrings` and `MoreStrings`, add the value to `AppLanguage`, and register it in `stringsFor`.
- **A new platform:** implement `PlatformActions`, `PublicStorage` and, if wanted, `MediaMerger` and `TorrentEngine`, then host `App` from the new platform's entry point.
