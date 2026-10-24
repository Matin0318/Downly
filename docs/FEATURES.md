**English** · [فارسی](FEATURES.fa.md)

# Feature guide

This guide walks through everything Downly does, screen by screen. For a shorter overview, see the [README](../README.md).

## Contents
1. [Adding downloads](#1-adding-downloads)
2. [The Downloads screen](#2-the-downloads-screen)
3. [How a download works](#3-how-a-download-works)
4. [Link types](#4-link-types)
5. [Queues and schedules](#5-queues-and-schedules)
6. [Saving files and storage](#6-saving-files-and-storage)
7. [The built-in browser](#7-the-built-in-browser)
8. [Files, player and widget](#8-files-player-and-widget)
9. [Settings](#9-settings)
10. [Notifications](#10-notifications)
11. [Language, theme and layouts](#11-language-theme-and-layouts)
12. [Backup and restore](#12-backup-and-restore)
13. [Logs and troubleshooting](#13-logs-and-troubleshooting)

## 1. Adding downloads
There are five ways to get a link into Downly.

- **The add window.** Paste or type a link. Downly asks the server for the file name and size and shows them while you decide. You can change the name, pick a queue, choose a folder, and press Start (download now) or Add (put it in the list without starting). You can paste several links, one per line, and they are all added.
- **The clipboard banner.** When you come back to the app and the clipboard holds a link that is not in your list, a banner offers to download it. Dismiss it and it will not come back for that same link.
- **Share from another app.** Sharing text that contains a link opens Downly with a "Download this file?" question.
- **Magnet links and torrent files.** `magnet:` links and links to `.torrent` files open in Downly from other apps.
- **The built-in browser.** Downloads and media found on a page are sent to Downly together with the page's cookies, referer and user agent.

Extras in the add window:
- **Quality.** For HLS and DASH links Downly lists the available qualities, best first. The default is the best one.
- **Mirrors.** Add other links to the same file. If the main link keeps failing, Downly tries the mirrors one by one.
- **Copy as cURL.** Paste a cURL command copied from a browser's developer tools. Downly takes the link and keeps the Cookie, Referer, User-Agent and login headers, which helps with sites that only serve files to a logged-in browser. Only options that matter for a plain download are read; request bodies and similar options are ignored.
- **Site Grabber.** Enter the address of a page that links to many files, tap Scan and pick the ones you want. Downly looks for links in `href`, `src` and `data-src` attributes and keeps the ones whose extension looks like a file (archives, documents, audio, video, images, apps and disk images).
- **Logins in the link.** Links such as `https://user:pass@host/file.zip` work. The login is turned into a standard HTTP Basic header and is sent only to that same host, even if the server redirects somewhere else.
- **Duplicates.** If a link is already in the list, it is not added twice.

## 2. The Downloads screen
- **Header cards.** Active, completed and total downloaded, plus a live speed card with a small chart.
- **Tabs.** All, Active, Done and Failed.
- **Search.** Every word you type must appear in the file name or the link. Persian and Arabic forms of the same letter (ي and ی, ك and ک), Persian, Arabic and Latin digits, and invisible characters such as the zero-width non-joiner are treated as equal, so you find what you meant however it was typed. Search covers every status, not just the open tab.
- **Sorting and type filter.** Newest, oldest, name or largest first. Filter by type: Video, Audio, Images, Documents, Archives, Apps, Programs and Other. Each type has its own colour and icon.
- **Each card** shows the file name, progress, speed, time left and size. The menu on a card offers:
  - pause, resume or retry
  - open, share, copy link and details (link, size, save location, connections, status, checksum)
  - edit link (the same window also edits the mirrors)
  - move to the top or bottom of the queue, or into another queue
  - a speed limit for just this file
  - delete, with an option to remove the file from storage too

  The checksum check lives in the details window of a finished file: paste the MD5, SHA-1 or SHA-256 from the website and tap Verify.
- **Selecting many.** Long-press to select several downloads, then start, pause, move them to a queue or delete them in one go.
- **Pause all and resume all** are always one tap away, and the notification has a Pause all button too.

## 3. How a download works
This is what happens behind the progress bar, in case you wonder why something behaves the way it does.

1. **Probe.** Downly sends a tiny ranged request to learn the file size, whether the server supports `Range`, the final address after redirects and the file name. The name comes from the `Content-Disposition` header, including UTF-8 names, or from the link. Unsafe characters are replaced and the name is cut at 200 characters. If a file with that name already exists, you get `name (1).ext`.
2. **Plan.** The file is cut into parts of at least 1 MB, up to the number of connections you set.
3. **Download.** Each part has its own connection and writes straight into a `.part` file at the right offset. Compression is switched off, so you get the file byte for byte. Sockets use a large receive buffer and no Nagle delay, which keeps fast and distant links full.
4. **Rebalance.** When a connection is done, it splits the part with the most bytes remaining and takes the second half. This only happens when at least 2 MB remain, so tiny pieces are not created.
5. **Back off.** If the server answers 429, 403, 503 or 509, one connection leaves and the others continue, so the download slows down instead of failing.
6. **Retry.** A part that fails is retried up to three times. If the whole download fails, the automatic retry waits 5, 10, 20, 40 and then 60 seconds between tries, up to the number you chose. Then the mirrors are tried.
7. **Finish.** The part file's size is checked against the expected size and then renamed to the final name in one step, so you never see a half-written file under the real name.

If the server does not support `Range`, the download uses one connection and starts over after a pause. If the file changes on the server between a pause and a resume, Downly notices and throws away the old partial data.

## 4. Link types

### HTTP and HTTPS
The main path described above. Speed limits, queues, mirrors and checksums all work here.

### FTP
A small built-in client in passive mode and binary transfer, using the `REST` command to resume. Without a user name and password in the link it logs in as anonymous. Speed limits apply.

### HLS streams (`.m3u8`)
- A master playlist shows its qualities. Pick one or let Downly use the best.
- Segments are fetched several at a time (as many as your connection setting) and written in order into one file.
- AES-128 encrypted segments are decrypted as they arrive. Fragmented MP4 streams are supported.
- Progress is counted in segments, and a paused HLS download resumes from the last finished segment.

### DASH streams (`.mpd`)
- Downly reads the manifest, lists the video qualities by bandwidth and resolution, and picks the best audio track that fits the video's container (WebM with WebM, MP4 with MP4).
- Video and audio are downloaded as separate files and joined with the phone's own media muxer. The compressed data is copied, nothing is re-encoded, so it is fast and loses no quality.
- On-demand streams only. Live streams and DRM-protected streams are not supported.

### BitTorrent
- Magnet links and `.torrent` files, from a link or from another app. For a magnet link Downly waits up to 45 seconds for peers to supply the torrent information.
- Single-file and multi-file torrents. A torrent with a folder is saved into a folder named after the torrent, and the list entry points to its biggest file.
- A torrent stops when it finishes: Downly does not seed. The speed limit does not apply.

### cURL commands and Site Grabber
Covered in [Adding downloads](#1-adding-downloads).

## 5. Queues and schedules
Every download belongs to a queue. A new install has one queue, called Queue 1 in English, which cannot be deleted. You can add more on the Queues screen.

Each queue has:
| Setting | What it does |
|---|---|
| Name | Free text |
| Schedule | A start time and a stop time, to the minute, on the weekdays you tick. The window may cross midnight. |
| Simultaneous downloads | How many files of this queue download at once. Auto uses the app-wide number; you can type any number. |
| Speed limit | A limit shared by the whole queue, in KB/s or MB/s. |
| Wi-Fi only | Pause on mobile data and continue on Wi-Fi. |
| When finished | Do nothing, or show a notification that opens the internet panel. |
| Switch | Turn the whole queue off. Its downloads pause and its schedule is ignored. |

How schedules are kept reliable:
- Each schedule is armed as a pair of Android alarms (start and stop), so it works with the app closed and the screen off.
- If Android refuses exact alarms, Downly falls back to an alarm-clock style alarm, which Android does not postpone. You may see the small alarm icon in the status bar.
- Alarms are armed again after a reboot, an app update, a change of time or time zone, or when you allow exact alarms. They are also armed whenever the app opens.
- Downly remembers which start moments it has already handled, so a queue is never started twice for the same moment.
- If a start time passed while the phone was off, the queue starts when the phone is on again, provided the window has not closed.
- Inside a window, failed downloads keep retrying at most once a minute until the window ends.
- The Settings screen has a card that tells you whether exact alarms and the battery setting are fine and gives you a button to fix each one.

Wi-Fi-only works at two levels: a switch in Settings for the whole app and a switch per queue. When the phone leaves Wi-Fi, running downloads pause; when Wi-Fi returns, they continue.

## 6. Saving files and storage
There are three ways files can be stored, and Downly tells you which one is active in Settings.

1. **Direct saving** (needs "All files access"). Files are written straight into `Downloads/Downly`. Nothing is kept inside the app, however many downloads run, and nothing is copied at the end. The first time you use the app, Downly asks if you want this.
2. **Move at the end.** Without that permission, unfinished downloads live in the app's storage. When one finishes, it is moved to the public Downloads folder (Android 10 and newer, through MediaStore, no permission needed). On Android 8 and 9 the file simply stays in the app's storage.
3. **A folder you pick.** Choose any folder, for example on an SD card. Finished files are copied there.

Related options:
- **Sort into folders.** New files go into sub-folders such as Video, Audio and Documents.
- **Custom folder name** per download, chosen in the add window. Folders are created inside Downly's download folder.
- **Storage report.** Shows unfinished downloads, finished files that have not been moved yet and leftovers (files nothing refers to). The Clean up button removes leftovers and tells you how much space it freed.
- **Cleanup at start.** Half-finished copies that a closed app left in the Downloads folder are removed when Downly starts.
- **Delete with file.** Removing a download can also delete its file from storage.

## 7. The built-in browser
The browser opens in its own window and is meant for finding downloads, not for everyday browsing.

- **Tabs** are remembered between launches, along with which one was in front.
- **Bookmarks and history**, with a button to clear history.
- **Ad blocker** with a built-in list of advertising and tracking hosts, including their sub-domains. It is a simple host list, not a full filter-list engine, so some ads will get through.
- **Desktop site** switch, which changes the user agent and the viewport.
- **Find in page, copy link, share page.**
- **Download catching.** Normal downloads and torrents are sent to Downly. The browser also notices media requests (`.m3u8`, `.mpd`, `.mp4`, `.mkv`, `.webm`, `.mp3`, `.m4a`, `.flac`, `.ogg`, `.mov`) and gathers them in a list for the current page. Start playing the video first, then open the list.
- **Cookies and referer** are kept, so sites that check them still serve the file.
- **Proxy.** The browser uses the proxy from Settings. This needs a recent Android System WebView.

## 8. Files, player and widget
- **Files screen.** A list of your finished downloads with a search box, type chips (only the types you actually have are shown) and a summary of how many files are listed and their total size. For each file you can open it with any app (Android's "Open with" for every type), play it, share it, rename it or delete it. Renaming works for files in the Downloads folder and in app storage.
- **Player.** Videos and music play in a built-in player, as long as Android can decode them.
- **Widget.** A home-screen widget shows how many downloads are active and the total speed. Tapping it opens Downly. A round button pauses everything that is running, or resumes what was paused.

## 9. Settings
| Section | What you find |
|---|---|
| Language, theme | English or Persian. System, light or dark. |
| Simultaneous downloads | How many files run at once. Any number, or unlimited. |
| Speed limit | A cap for the whole app. Type any value in KB/s or MB/s. |
| Connections per download | 1 to 64. More is usually faster, until a server says no. |
| Auto-retry | Off, 1, 3, 5 or 10 tries. |
| Switches | Auto-resume on launch, completion notifications, Wi-Fi only, sort into folders. |
| Proxy | Off, HTTP or SOCKS5, with host, port and an optional login. It applies to downloads and the browser, and running downloads use it when they reconnect. |
| Data usage | A chart of the last days and totals for today, the week and the month. Downly keeps 60 days. |
| Background downloads | A shortcut to the battery settings so Android does not stop long downloads. |
| Scheduled downloads | Whether exact alarms and battery settings are fine, with buttons to fix them, and a note about brand-specific switches. |
| Storage | The save folder, with a button to choose another one or go back to the default. |
| Save location | Whether direct saving is on, and a button to allow it. |
| Storage used by Downly | The report and the Clean up button. |
| Backup | Export and import. |
| About | The version. |

## 10. Notifications
- **Progress.** One low-priority notification while downloads run, updated about once a second, with a Pause all button. It is also what keeps Android from stopping the app.
- **Finished and failed.** One notification per download, which you can switch off in Settings.
- **Queue finished.** A notification that opens the internet panel, if you enabled it on the queue.

While downloads run, Downly holds a partial wake lock and a high-performance Wi-Fi lock, so downloads do not crawl when the screen is off. Both are released when nothing is active.

## 11. Language, theme and layouts
- The whole interface is available in English and Persian. Persian uses Persian digits and units (مگابایت، گیگابایت، ثانیه) and a right-to-left layout, including dialogs and menus.
- The last language and theme are cached, so the first frame already looks right.
- On phones there is a floating bottom bar. On tablets, foldables and landscape there is a side rail, and lists become two or three columns.
- You can swipe between Downloads, Queues and Settings.
- Rotating the phone or resizing the window keeps your place and does not restart anything.

## 12. Backup and restore
Backup writes one JSON file with your settings (queues, proxy host and so on) and the download list. The proxy password is left out on purpose. To restore, pick a backup file; Downly checks it really is a Downly backup and tells you how many downloads it brought back. Because the file lists your links, share it only with people you trust.

## 13. Logs and troubleshooting
- An unexpected crash is written to `last_crash.txt`, and an error in background work to `last_error.txt`. Both are in `Android/data/app.downly.android/files/`. Attach `last_crash.txt` to bug reports.
- **Download fails at once with a size error.** The server does not report a file size. Downly cannot plan the parts without it.
- **Speed is low.** Try fewer or more connections, check for a speed limit on the app, the queue or the file, and see whether the server limits each visitor.
- **A queue did not start on time.** Check the scheduled-downloads card in Settings and your phone brand's autostart switch.
- **The app grows while downloading.** Allow direct saving, or lower the number of simultaneous downloads.
- **Torrent says no peers answered.** A magnet link needs peers to share the torrent information. Try again later or use a `.torrent` file.
