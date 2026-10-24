**English** · [فارسی](RELEASE.fa.md)

# Releasing Downly

Everything below is already set up in the repository. You only need to do the steps marked **You**.

## One-time setup
1. **You:** create the signing key and keep a copy somewhere safe. A lost key means you can never update the app for people who installed it:
   ```
   keytool -genkeypair -v -keystore downly.jks -alias downly -keyalg RSA -keysize 4096 -validity 10000
   ```
2. **You:** on GitHub open Settings > Secrets and variables > Actions and add four secrets: `DOWNLY_KEYSTORE_BASE64` (the output of `base64 -w0 downly.jks`), `DOWNLY_KEYSTORE_PASSWORD`, `DOWNLY_KEY_ALIAS` and `DOWNLY_KEY_PASSWORD`.
3. **You:** to build a signed APK on your own computer, create `keystore.properties` in the project root. It is ignored by Git:
   ```
   storeFile=downly.jks
   storePassword=...
   keyAlias=downly
   keyPassword=...
   ```
4. **You (recommended):** in the repository's Settings > Code security, enable private vulnerability reporting so [SECURITY.md](../SECURITY.md) works as written.

## First release (1.0.0)
`androidApp/build.gradle.kts` is already set to `versionCode = 1` and `versionName = "1.0.0"`.
1. **You:** run the checklist in [TESTING.md](TESTING.md) on a real phone with a **release** build.
2. **You:** tag and push:
   ```
   git tag v1.0.0
   git push --tags
   ```
3. The `Release` workflow runs the unit tests, builds the signed APK, writes its SHA-256 and publishes both on the Releases page with generated release notes.

## Later releases
1. Raise `versionCode` and `versionName` in `androidApp/build.gradle.kts`.
2. Add an entry to [CHANGELOG.md](../CHANGELOG.md) and [CHANGELOG.fa.md](../CHANGELOG.fa.md).
3. Tag it, for example `v1.1.0`.

A tag with a `-` in it, such as `v1.1.0-rc.1`, is published as a GitHub *pre-release*.

## Continuous integration
`ci.yml` runs on every push and pull request. It runs the unit tests, builds the unsigned release variant (so R8 problems show up early) and the debug APK, and uploads the debug APK as a build artifact. If something works in debug but not in release, add keep rules to `androidApp/proguard-rules.pro`.

## Notes for the release page
Mention these known limits so nobody is surprised:
- "All files access" and exact alarms give the best experience. Google Play restricts both, so this app is meant for GitHub and F-Droid style distribution.
- Torrents stop when they finish (no seeding) and the speed limit does not apply to them.
- DASH works for on-demand streams only, with no DRM. Audio and video are joined with the phone's own `MediaMuxer`.
- A server that does not report the file size cannot be downloaded.
