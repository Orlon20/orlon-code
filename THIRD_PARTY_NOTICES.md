# Third-party notices

Orlon Code's own code is Apache-2.0-licensed (see `LICENSE`). It bundles, downloads, or builds on the components below.
Full license texts are in [`licenses/`](licenses/).

## Shipped inside the APK (and in this repository)

| Component | Version | License | Where | Source |
|---|---|---|---|---|
| proot (`libproot.so`) and its loader (`libproot-loader.so`) | 5.1.107.98 (Termux build, unmodified) | GPL-2.0-or-later | `app/src/main/jniLibs/arm64-v8a/` | https://github.com/proot-me/proot · build recipe/patches: https://github.com/termux/termux-packages/tree/master/packages/proot |
| libtalloc (`libtalloc.so`) | 2.5.0 (Termux build, unmodified) | LGPL-3.0-or-later | same folder | https://download.samba.org/pub/talloc/ |
| libandroid-shmem (`libandroid-shmem.so`) | 0.7 (Termux build, unmodified) | BSD-3-Clause | same folder | https://github.com/termux/libandroid-shmem |
| Gradle wrapper (`gradle-wrapper.jar`, `gradlew`) | 8.14.3 | Apache-2.0 | repository root and the app template | https://github.com/gradle/gradle |
| "Settings" icon path | Material Icons | Apache-2.0 | `app/src/main/res/drawable/ic_settings.xml` | https://github.com/google/material-design-icons |
| Kotlin standard library, AndroidX (core-ktx, appcompat, webkit) | see `app/build.gradle.kts` | Apache-2.0 | linked into the APK | https://developer.android.com |

**GPL / LGPL notice.** proot and talloc are separate, unmodified shared objects/executables that Orlon Code starts as
separate processes (proot) or loads as a separate shared library (talloc). You may replace any of them by rebuilding the
APK with your own copies in `app/src/main/jniLibs/arm64-v8a/`. The corresponding source for the exact versions above is
available at the URLs in the table; to obtain it from the maintainers instead, open an issue in this repository.

## Downloaded on the user's device at first use (not part of the APK)

Each download is pinned to an exact version and verified against a SHA-256 checksum recorded in the source
(`Sandbox.kt`, `orlon-toolchain`).

| Component | Version | License | Source |
|---|---|---|---|
| OpenCode (`opencode-linux-arm64-musl`) | 1.19.3 | MIT | https://github.com/sst/opencode (npm: `opencode-ai`) |
| Alpine Linux mini root filesystem and its packages (OpenJDK 17, git, ripgrep, bash, busybox, …) | 3.24.3 | various (GPL/MIT/…); see each package | https://alpinelinux.org |
| Android command-line tools, platform 36, build-tools 36 | r11076708 / 36 / 36.0.0 | Android SDK License (accepted by the user during setup) | https://developer.android.com/studio |
| Static arm64 Android build tools (`aapt2`, `zipalign`) | 35.0.2 | Apache-2.0 (AOSP builds) | https://github.com/lzhiyong/android-sdk-tools |
| Gradle distribution, Android Gradle Plugin, Kotlin Gradle plugin, project dependencies | per project | Apache-2.0 / others | downloaded by Gradle when building a project |

## Trademarks

OpenCode is a project of its own authors. Orlon Code is an independent app that runs the unmodified OpenCode server and
shows its web interface; it is **not affiliated with or endorsed by** the OpenCode project. The "orloncode" pixel wordmark
reuses OpenCode's letter-grid style for five of its letters out of respect for the look of the project it wraps; replace it
if you fork and want a distinct identity (see `Wordmark.kt`).

## Brand logos

`app/src/main/assets/logos/*.svg` are the logos of AI model makers and providers, taken from [models.dev](https://models.dev) (MIT),
and are shown only to identify each model. They are trademarks of their respective owners and are not covered by this project's
license; no endorsement is implied.
