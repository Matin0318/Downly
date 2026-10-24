**English** · [فارسی](TESTING.fa.md)

# Release test checklist

Run this on at least one Android 10 phone, one Android 14 or 15 phone and one Xiaomi or Samsung phone (their battery managers are the strictest). Always use a **release** build, because R8 can break things that work in debug.

## Automated tests
```
./gradlew :core:engine:jvmTest :core:data:jvmTest
```
These cover the engine and the data layer. CI runs them on every push. The checklist below is for what only a real phone can show.

## Downloads
- [ ] A server with Range support and a file larger than 4 GB: all segments finish, the size matches and the checksum matches.
- [ ] A server without Range: one connection works; pause and resume restarts cleanly.
- [ ] A redirect chain, an expiring link (edit the link), a `user:pass@` link, and a cURL paste with cookies.
- [ ] HLS: a plain playlist, AES-128, fMP4 and a master playlist with several qualities.
- [ ] DASH (.mpd) with separate audio and video: the quality list appears and the joined file plays.
- [ ] An FTP link: download and resume.
- [ ] Torrents: a magnet link and a `.torrent` link both finish; pause and resume; a single-file torrent and a folder torrent.
- [ ] Airplane mode in the middle and back: auto-retry resumes without corruption.
- [ ] Kill the app while it downloads and reopen it: the download resumes.
- [ ] Reboot the phone: schedules still fire.
- [ ] HTTP and SOCKS5 proxies, with and without a login: downloads and the built-in browser.
- [ ] A server that answers 429 or 503 to many connections: the download slows down instead of failing.

## Storage
- [ ] Direct saving: allow "All files access" and start 10 downloads at once. Settings > Storage shows 0 unfinished bytes inside the app, the files appear in `Downloads/Downly`, and tapping a finished file offers "Open with".
- [ ] A completed file appears in Files, and it can be opened and shared from the app.
- [ ] Choose a custom folder (SD card or another folder), finish a download, then delete it from the app with "delete file".
- [ ] Android 8 and 9: the file stays in app storage (no public copy) and still opens.
- [ ] Press Clean up in the storage report: leftovers disappear and the freed space is shown.

## Queues and schedules
- [ ] Two queues with different schedules, one of them crossing midnight, and chosen weekdays.
- [ ] A speed limit, a parallel limit and Wi-Fi only on a single queue.
- [ ] A scheduled queue with the app closed and the screen off (Android 12, 13 and 14): it starts on time, and also with exact alarms denied.
- [ ] The phone is off at the start time and switched on inside the window: the queue starts.
- [ ] "Offer to turn off the internet": the notification appears and opens the panel.
- [ ] Select several downloads, move them between queues, delete several.
- [ ] Switch a queue off: its downloads pause and its schedule does not fire.

## Language and look
- [ ] Persian and English, light and dark, small and large font sizes.
- [ ] In both languages, the header, the download cards (speed, time left, size) and the queue cards show no clipped text.
- [ ] Tablet and landscape: the side rail, grid lists, and swiping between Downloads, Queues and Settings.
- [ ] Search finds finished files while another status tab is open, and finds Persian text typed with Arabic letters.
- [ ] Rotate the phone during a dialog: nothing is lost.

## Tools
- [ ] Backup and restore, the built-in browser, the file manager, the player and the widget all work in the release build.
- [ ] Browser: open a page with a video, play it, and download it from the media list; check the ad blocker and the desktop-site switch.
- [ ] Share a link from another app, and open a `magnet:` link from another app.
