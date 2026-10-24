**English** · [فارسی](SECURITY.fa.md)

# Security

## Reporting a vulnerability
Please report security problems privately instead of opening a public issue. On GitHub, go to the **Security** tab and choose **Report a vulnerability**. Include what you found, the app version, your Android version, and steps to reproduce it if you can.

You will get an answer as soon as the maintainers can look at it. Please give us a reasonable time to fix the problem before you share it publicly.

## Supported versions
Only the latest release receives security fixes.

## What Downly does to protect you
- **Proxy password.** It is encrypted with AES-256-GCM using a key stored in the Android Keystore. The key cannot be read out of the phone. If the key is lost (a new phone, restored app data), the password is simply asked for again. It is never written to backup files, and if encryption fails it is not stored in plain text.
- **Logins in links.** In `https://user:pass@host/file` links the login is turned into an HTTP Basic header and sent only to that host. If the server redirects to another host, the login and any custom headers (cookies, referer) are not forwarded.
- **Request headers.** Headers copied from a cURL command are cleaned: headers that could break the transfer (`Range`, `Host`, `Content-Length`, `Accept-Encoding`, `Connection`, `Transfer-Encoding`) and values with line breaks are dropped.
- **File names.** Names from the server or the link are stripped of path separators and characters that are not allowed, so a download cannot write outside its folder.
- **No telemetry.** No accounts, no ads, no analytics, no crash reporting. The app talks only to the addresses you download from, your proxy, and torrent peers.
- **Private storage.** Settings, the download list, bookmarks, history and tabs are kept in the app's private storage.
- **Signed releases.** Release APKs are built by the GitHub Actions workflow and published with a SHA-256 checksum.

## Things to be aware of
- **Plain HTTP.** Downly accepts `http://` links, and those are not encrypted in transit. Use HTTPS when you can.
- **Backups contain your links.** A Downly backup file lists the links you downloaded. Share it only with people you trust.
- **Android's own backup.** If the system backup of your phone is switched on, Android may copy app data. Check your phone's backup settings if that matters to you.
- **Torrents are public.** Other peers can see your IP address.
- **All files access.** This optional permission lets Downly write straight into your Downloads folder. It is used only for that.
