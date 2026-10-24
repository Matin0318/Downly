**English** · [فارسی](README.fa.md)

# Downly

Downly is a free, open-source download manager for Android, in the spirit of IDM and ADM. It splits every file across several connections, picks up where it left off after any interruption, and keeps your downloads organised in queues that can run on a schedule, even when the app is closed.

It is written in Kotlin with Compose Multiplatform and comes in English and Persian, with proper right-to-left layout.

**Version 1.0.0** · No account · No ads · No analytics · MIT license

## Contents
- [Install](#install)
- [A quick tour](#a-quick-tour)
- [What it can do](#what-it-can-do)
- [Supported links](#supported-links)
- [Where files are saved](#where-files-are-saved)
- [Permissions](#permissions)
- [Known limits](#known-limits)
- [FAQ](#faq)
- [Build from source](#build-from-source)
- [Project structure](#project-structure)
- [Reporting a problem](#reporting-a-problem)
- [Legal and license](#legal-and-license)

The full feature guide, with every setting explained, is in [docs/FEATURES.md](docs/FEATURES.md).

## Install
1. Download the APK from the [Releases](../../releases) page.
2. Open it on your phone. When Android asks, allow "install unknown apps" for your browser or file manager.
3. Optional: compare the file's SHA-256 with the `.sha256` file published next to the APK.

Downly needs Android 8.0 (API 26) or newer. It is not on Google Play: two of the permissions it uses for the best experience ("All files access" and exact alarms) are restricted there. See [Known limits](#known-limits).

## A quick tour
- **Add a download.** Tap the add button, paste a link and wait a moment. Downly checks the link and fills in the file name and size. Choose a queue and a folder if you want, then tap Start. If you copied a link before opening the app, a banner offers to download it right away.
- **Share into Downly.** Share a link from any app and Downly asks whether to download it. Links that start with `magnet:` and `.torrent` files open in Downly directly.
- **Watch it work.** The Downloads tab shows every file with its speed, time left and progress. Filter by All, Active, Done or Failed, search by name or link, and sort by date, name or size.
- **Organise.** Queues are the second tab. Each queue has its own schedule, speed limit, parallel limit and Wi-Fi rule.
- **Browse.** The built-in browser catches downloads and video or audio streams on the pages you open and lets you send them to Downly with the right cookies.
- **Tune it.** The third tab holds settings: connections, speed limit, retries, proxy, storage, backup and more. Swipe left or right to move between the three tabs.

## What it can do

### Fast, resilient downloads
- Every file is split into several parts, each downloaded over its own HTTP/1.1 connection. The default is 16 connections per file and you can go up to 64.
- When a connection finishes early it does not sit idle: it takes half of the part that still has the most bytes left, so the fast connections keep working until the very end.
- Pause and resume work after the app is closed or the phone restarts, because progress is saved as you go.
- If a server answers 429, 403, 503 or 509 because you opened too many connections, Downly lowers the number by itself and carries on instead of failing.
- Failed downloads retry automatically, waiting a little longer each time (5, 10, 20 seconds, up to a minute). You choose how many tries: off, 1, 3, 5 or 10.
- You can give a download extra mirror links. If the main link keeps failing, Downly moves on to the next one.
- A link that expired can be edited or replaced and the download continues from where it stopped.
- Paste many links at once and Downly adds them all, skipping duplicates.
- After a download finishes you can check it against an MD5, SHA-1 or SHA-256 hash from the website.

### Many kinds of links
HTTP and HTTPS, FTP, HLS and DASH streams, BitTorrent, pages full of files, and cURL commands. Details are in [Supported links](#supported-links).

### Queues and schedules
- Create as many queues as you like. Every queue has its own name, schedule, speed limit, simultaneous-download limit, Wi-Fi-only rule and a "when finished" action.
- Schedules run on any minute of the day and on the weekdays you choose. A schedule can cross midnight.
- Schedules are set as Android alarms, so they work with the app closed and the screen off. They are set again after a reboot, an app update, or a change of clock or time zone.
- If the phone was off at the start time and is switched on while the queue's window is still open, the queue starts right then.
- Inside a schedule window Downly keeps retrying a failed download (at most once a minute) until the window closes, because the network is often not ready right after the phone wakes up.
- Move any download to the top or bottom of its queue, give one download its own speed limit, or select several downloads to start, pause, move or delete them together.
- A queue can be switched off without deleting it. Deleting a queue moves its downloads to the main queue, which cannot be deleted.
- When a queue finishes, Downly can show a notification that opens Android's internet panel, so you can turn off Wi-Fi or mobile data with one tap. Android does not let apps do that on their own.

### Saving files
- With "All files access", files are written straight into `Downloads/Downly` while they download. The app itself does not grow and nothing has to be copied at the end.
- Without it, unfinished files stay in the app's storage and finished ones are moved to the public Downloads folder, or to a folder you pick.
- Files can be sorted into sub-folders by type: Video, Audio, Images, Documents, Archives, Apps and Programs.
- A storage report shows what Downly keeps inside the app, with a button that removes leftovers.

### Built-in browser
Tabs that survive a restart, bookmarks, history, an ad blocker, a desktop-site switch, find in page, copy link and share. It catches downloads and media streams and keeps the cookies, referer and user agent. It uses the proxy from Settings.

### Tools
- A file manager for everything you downloaded: open with any app, rename, share, delete, and a built-in video and audio player.
- HTTP and SOCKS5 proxy, with optional user name and password.
- A chart of how much you downloaded each day, and a live speed card.
- A home-screen widget with a button that pauses or resumes everything.
- Backup and restore for settings, queues and the download list.
- Search that treats Persian and Arabic look-alike letters and digits as the same.

### Language and looks
- English and Persian, with right-to-left layout and Persian digits and units.
- Light, dark or system theme.
- Layouts for phones, tablets, foldables and landscape: a floating bottom bar on phones, a side rail on wide screens, and two or three columns where there is room.

### Privacy
No account, no ads, no analytics. See [PRIVACY.md](PRIVACY.md).

## Supported links
| Type | Notes |
|---|---|
| HTTP, HTTPS | Multi-connection when the server supports `Range`. Without it, one connection is used and a paused download restarts from the beginning. |
| FTP | Passive mode with resume. Anonymous login unless the link has a user name and password. |
| `user:pass@host` links | The login is sent only to that host, never to another host after a redirect. |
| HLS (`.m3u8`) | Quality picker for master playlists, AES-128 encryption, fragmented MP4. Segments download in parallel and are written in order into one file. |
| DASH (`.mpd`) | Quality picker. Audio and video are downloaded separately and joined on the phone without re-encoding. On-demand streams only, no DRM. |
| BitTorrent | Magnet links and `.torrent` files, single files and folders. |
| cURL command | Paste a "Copy as cURL" command from your browser's developer tools to reuse its cookies, referer and user agent. |
| Web page | Site Grabber scans a page for downloadable files and lets you pick which ones to add. |

## Where files are saved
| Situation | Where the file goes |
|---|---|
| "All files access" allowed | Written directly to `Downloads/Downly` |
| Not allowed, Android 10 or newer | Kept in app storage while downloading, then moved to `Downloads/Downly` |
| Not allowed, Android 8 or 9 | Stays in the app's storage and opens from there |
| You picked a folder in Settings | Moved to that folder when the download finishes |

## Permissions
| Permission | Why |
|---|---|
| Internet, network state | Downloading, and noticing mobile data for the Wi-Fi-only rule |
| Foreground service (data sync), wake lock | Keep downloads running with the screen off |
| Notifications | Progress, finished and failed messages |
| Exact alarms, boot completed | Scheduled queues that start with the app closed |
| All files access (optional) | Save straight into `Downloads/Downly` |
| Ignore battery optimizations (optional) | Reliable downloads on phones that stop background apps |
| Write external storage (Android 9 and older) | Saving to the public Downloads folder on old versions |

On Xiaomi, Huawei, Oppo, Vivo and Samsung phones there is usually a separate "Autostart" or "Background activity" switch in the phone settings. Turn it on for Downly if scheduled queues do not start.

## Known limits
- Torrents stop when they finish: Downly does not seed. The speed limit does not apply to torrents.
- DASH works for on-demand streams only, with no DRM.
- A server that does not report the file size cannot be downloaded: the size is needed to plan the parts.
- If a server ignores `Range`, the download uses one connection and cannot continue after a pause or a restart.
- Plain `http://` links are allowed, so they are not encrypted in transit.
- "All files access" and exact alarms give the best experience, but Google Play restricts both. Downly is meant for GitHub and F-Droid style distribution.

## FAQ
**Why is a download not faster with 64 connections?** Many servers limit each visitor. Downly lowers the connection count by itself when a server refuses, and a slow server stays slow whatever you do.

**A scheduled queue did not start. What do I check?** Open Settings and look at the scheduled-downloads card: it shows whether exact alarms and battery optimization are fine. On some phones you also need the brand's "Autostart" switch.

**Can I use Downly on Windows?** Not yet. The interface is shared Compose Multiplatform code and the engine has a desktop JVM target, but only the Android app exists today.

**Where is my data?** In the app's private storage on your phone, nowhere else.

## Build from source
You need JDK 17 and the Android SDK 35.

```
./gradlew :core:engine:jvmTest :core:data:jvmTest
./gradlew :androidApp:assembleDebug
```

The debug APK ends up in `androidApp/build/outputs/apk/debug/`. For a signed release build and the release workflow, read [docs/RELEASE.md](docs/RELEASE.md). The Gradle settings list Aliyun mirrors before Google and Maven Central, which helps where those are slow or blocked; remove them in `settings.gradle.kts` if you prefer the originals only.

## Project structure
| Module | Purpose |
|---|---|
| `core:model` | Shared data models |
| `core:data` | Settings and download list storage, backup |
| `core:engine` | Download engine for HTTP, HLS, DASH and FTP, with unit tests |
| `shared:ui` | The Compose Multiplatform interface and the download controller |
| `androidApp` | The Android app: service, alarms, notifications, browser, torrents, widget |

How the pieces fit together is described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Reporting a problem
Open an issue and include the app version (Settings > About), your Android version, your phone model and what you did. If the app crashed, attach `last_crash.txt` from `Android/data/app.downly.android/files/`. Security problems should be reported privately, see [SECURITY.md](SECURITY.md).

## Legal and license
Downly is a tool. You are responsible for what you download, so respect copyright and the terms of the sites you use. BitTorrent is public by nature: other peers can see your IP address.

Downly is released under the MIT license, see [LICENSE](LICENSE). The libraries it uses are listed in [THIRD_PARTY.md](THIRD_PARTY.md).
