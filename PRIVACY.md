**English** · [فارسی](PRIVACY.fa.md)

# Privacy

Downly keeps everything on your phone. This page says what that means in practice.

## Short version
- No account, no analytics, no ads.
- The app does not send usage data or crash reports to anyone.
- The developers have no server that your data goes to.

## What the app connects to
Only these:
- the addresses you download from, and the pages you open in the built-in browser;
- the proxy you set up, if any;
- other BitTorrent peers and trackers, when you download a torrent.

Torrents are public by nature: the other peers in a swarm can see your IP address.

## What it stores on your phone
- your download list, with links, file names and progress;
- settings, queues and schedules;
- browser bookmarks, history, open tabs and cookies;
- how much you downloaded each day (kept for 60 days), for the usage chart;
- downloaded files, in the folder you chose.

All of this is in the app's private storage, apart from the files you download, which go where you told Downly to put them.

## Proxy password
If you set a proxy with a password, it is encrypted with a key in the Android Keystore. It is never written to backup files.

## Backups
A backup file contains your settings (without the proxy password) and your download list. Because it lists the links you downloaded, share it only with people you trust.

If the system backup of your Android phone is switched on, Android itself may copy app data to your account. That is controlled by your phone's settings, not by Downly.

## The built-in browser
The browser keeps cookies, history and bookmarks like any other browser. To clear them, use the clear-history button in the browser, or clear the app's data in Android settings.

## Permissions
The permissions and the reason for each one are listed in the [README](README.md#permissions). None of them is used to collect information.

## Removing your data
Uninstalling Downly deletes its private storage. Files you downloaded to the public Downloads folder or to a folder you picked stay where they are until you delete them.

## Questions
Open an issue on the project's GitHub page.
