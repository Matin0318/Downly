**English** · [فارسی](CONTRIBUTING.fa.md)

# Contributing to Downly

Thanks for wanting to help. Bug reports, ideas, translations and code are all welcome.

## Before you start
- Look through the existing issues first, in case someone already reported or suggested the same thing.
- For a bigger change, open an issue and describe it before writing the code. It saves you from building something that does not fit.
- Security problems should not go in a public issue. Follow [SECURITY.md](SECURITY.md) instead.

## Setting up
You need JDK 17 and the Android SDK 35 (Android Studio's latest stable version works well).

```
git clone <your fork>
cd Downly
./gradlew :core:engine:jvmTest :core:data:jvmTest
./gradlew :androidApp:assembleDebug
```

The first command runs the unit tests on the JVM, so it does not need a phone or an emulator. The second builds a debug APK into `androidApp/build/outputs/apk/debug/`.

`settings.gradle.kts` lists Aliyun mirrors before Google and Maven Central. If you do not need them, you can remove those lines locally; please do not commit that change unless it is the point of your pull request.

## Where things live
| Module | What goes there |
|---|---|
| `core:model` | Plain data models shared by everything |
| `core:data` | Settings, the download list, backup. Stored as JSON, written atomically |
| `core:engine` | Everything about moving bytes: HTTP, HLS, DASH, FTP, proxy, speed limits |
| `shared:ui` | Compose Multiplatform screens and `DownloadsController`, which ties the engine to the UI |
| `androidApp` | Anything that needs Android: service, alarms, notifications, browser, torrents, widget, file access |

Code that needs a platform feature should go behind an interface in `shared:ui` or `core:engine` and be implemented in `androidApp`. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains the idea in more detail.

## Guidelines
- Keep a pull request focused on one thing.
- Engine and data logic should come with unit tests. Look at the existing tests in `core/engine/src/commonTest` for the style: a mock HTTP client and a fake file system, no network.
- All text the user sees goes through `AppStrings` (`Strings.kt` and `Strings2.kt`) and needs both an English and a Persian version.
- Use the Kotlin official code style (`kotlin.code.style=official`). Comments explain why, not what.
- A change that works in debug but breaks in release usually needs a keep rule in `androidApp/proguard-rules.pro`. CI builds the release variant to catch this.
- If your change is visible to users, add a line to [CHANGELOG.md](CHANGELOG.md) and [CHANGELOG.fa.md](CHANGELOG.fa.md).

## Adding or fixing a translation
Every language is one implementation of the `AppStrings` interface (and its `MoreStrings` part). To fix a Persian or English text, edit it in `Strings.kt` or `Strings2.kt`. To add a language, add a value to `AppLanguage` in `core:data`, implement the two interfaces and register the language where `stringsFor` picks the strings. Right-to-left languages also need the layout direction handled the way Persian is.

## Opening a pull request
1. Fork the repository and create a branch for your change.
2. Make sure `./gradlew :core:engine:jvmTest :core:data:jvmTest :androidApp:assembleDebug` passes.
3. Fill in the pull request template: what changed, why, and how you tested it. A line saying you tried it on a real phone helps a lot.

By contributing you agree that your work is released under the project's [MIT license](LICENSE).
