**English** · [فارسی](CHANGELOG.fa.md)

# Changelog

All notable changes to Downly are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [semantic versioning](https://semver.org/).

## 1.0.0 – First release

The first public version of Downly. Everything below is included.

### Downloading
- Multi-connection engine: 16 connections per file by default and up to 64, HTTP/1.1 with a separate connection for every part, and a `.part` file that is renamed only when the size matches.
- Dynamic re-splitting: a connection that runs out of work takes the second half of the part with the most bytes left.
- Pause, resume and recovery after the app or the phone restarts.
- Fewer connections automatically when a server answers 429, 403, 503 or 509.
- Auto-retry with a growing delay (off, 1, 3, 5 or 10 tries), mirror links, duplicate-link detection, editing or refreshing the link of an unfinished download, and adding many links at once.
- Checksum verification with MD5, SHA-1 and SHA-256.
- Link types: HTTP(S); FTP with resume; `user:pass@host` links; HLS (`.m3u8`) with quality picker, AES-128 and fragmented MP4; DASH (`.mpd`) with quality picker and on-device audio and video joining; BitTorrent (magnet and `.torrent`); pasted "Copy as cURL" commands; Site Grabber.

### Queues and scheduling
- Multiple queues, each with its own schedule (any minute, chosen weekdays, windows that cross midnight), simultaneous-download limit (any number or unlimited), speed limit (KB/s or MB/s), Wi-Fi-only rule, action when finished, on/off switch and delete.
- Schedules run with the app closed and the screen off, and are set again after a reboot, an app update, or a change of clock or time zone. A queue whose start passed while the phone was off starts when the phone is on again, if its window is still open.
- Priority: move a download to the top or bottom of its queue. Per-download speed limit.
- Select many downloads to start, pause, move to a queue or delete.
- After a queue finishes, a notification opens the internet panel so you can switch Wi-Fi or mobile data off.

### Storage
- Direct saving into `Downloads/Downly` with "All files access". Otherwise finished files move to the public Downloads folder, or to a folder you pick.
- Optional category sub-folders, a storage report with a Clean up button, and removal of half-finished copies at start.

### Built-in browser
- Tabs that are remembered, bookmarks, history, ad blocker, desktop-site switch, find in page, copy link, share page, a media list for the current page and proxy support.
- Downloads and streams are caught with their cookies, referer and user agent.

### Tools
- File manager with "Open with", rename, share, delete and a built-in video and audio player.
- HTTP and SOCKS5 proxy with an optional login. The password is encrypted with a key in the Android Keystore and is never written to backups.
- Data-usage chart, live speed card, home-screen widget with a pause/resume button, backup and restore.
- Search across all downloads that treats Persian and Arabic letters and digits as equal; sorting and filtering by file type.

### Look and language
- English and Persian with right-to-left layout and Persian digits and units.
- Light and dark themes. Phone, tablet, foldable and landscape layouts: a floating bottom bar or a side rail, grid lists and swiping between pages. The app keeps its state when you rotate the phone or resize the window.

### Project
- MIT license, release signing from `keystore.properties` or CI secrets, R8 keep rules, a tag-triggered release workflow, a CI workflow that builds the release variant, issue and pull-request templates, privacy and security policies and a third-party notice.
- Crashes are written to `last_crash.txt` and background errors to `last_error.txt` in the app's files folder.
