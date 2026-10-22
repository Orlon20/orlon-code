# Architecture

```
Android app (Kotlin, no UI framework)
├─ SetupActivity / Installer      first launch: download + verify + unpack the sandbox
├─ LocalServerService (foreground) runs `opencode web` in the sandbox; watches for new APKs
├─ MainActivity                    WebView on http://127.0.0.1:<port>, top bar, loading screen, APK banner
│   └─ Branding.kt                 scripts injected into OpenCode's web UI (branding, phone-fit CSS, themes, tap-to-install)
├─ PreviewActivity                 project files as a website (WebViewAssetLoader) or any local port
├─ Sandbox / SandboxScripts        proot command construction; installs the orlon-* scripts + agent notes
└─ ThemeDialog / Theming           theme picker; native screens follow the page's colours

Sandbox (Alpine arm64 root filesystem in files/rootfs, run by proot)
├─ /usr/local/bin/opencode         pinned OpenCode release (musl arm64)
├─ orlon-new-android               create an Android project from a template
├─ orlon-build                     Gradle build as a background job (auto-installs the toolchain)
├─ orlon-toolchain                 JDK 17 + Android SDK 36 + native arm64 aapt2/zipalign
├─ orlon-keystore / orlon.init.gradle   on-device release signing without touching project files
└─ /root/.config/opencode/AGENTS.md    instructions the in-app agent reads
```

## Why proot from the APK

Android apps may only execute files from their native-library directory. proot (and its loader) are therefore packaged as
`lib*.so` files and extracted by the installer; proot then loads the Alpine userland and OpenCode from private storage. This
works at `targetSdk` 35 without root.

## Pinned, verified downloads

`Sandbox.kt` pins the Alpine root filesystem and the OpenCode release by SHA-256. `orlon-toolchain` pins the Android
command-line tools and the static arm64 SDK tools. Nothing is downloaded without a checksum.

## Local server security

OpenCode listens on `127.0.0.1` only and requires HTTP Basic auth with a random per-install password. The WebView supplies it
automatically. The web page can call exactly three app functions (`openThemes`, `themesData`, `installApk`); `installApk` only
accepts a `.apk` inside some project's `.orlon/out` folder, resolved with a canonical-path check (`Projects.kt`), because a
project's build script can print any text. APK delivery to the installer goes through a `FileProvider` limited to the projects
folder.

## Android builds on a phone

Google ships no arm64 Linux build tools, so `orlon-toolchain` installs the platform-independent parts with `sdkmanager`
(platforms, `d8`/`apksigner` jars) and swaps in static arm64 builds of `aapt2`, `aapt` and `zipalign`. Gradle is told to use
that `aapt2` through `android.aapt2FromMavenOverride` in `~/.gradle/gradle.properties`, so projects need no changes.
`orlon-build` runs Gradle as a detached background job (a first build can take minutes, longer than a typical tool time-out),
streams or summarises the log depending on who is calling, copies APKs to `<project>/.orlon/out/`, and prints
machine-readable markers (`ORLON_APK`, `ORLON_RESULT`, `ORLON_RUNNING`). The app's watcher notices new APKs and offers to
install them.

## Fragile points (check these when upgrading OpenCode)

`Branding.kt` selects elements in OpenCode's web UI by attribute and class patterns (`data-slot=…`, `prompt-input`,
`dialog-body`, …). After bumping the pinned OpenCode version, check: the loading-screen readiness test, the model picker
clean-up, the long-command wrapping, theme switching (`localStorage` keys), and the `/themes` interception.

## Testing

- `./gradlew testDebugUnitTest`: pure logic (proot command building, theme/colour parsing, wordmark geometry, path safety, APK
  detection).
- Debug builds include `ExecReceiver`, which runs a script inside the sandbox over `adb` so the shell tools can be exercised
  on a device without the UI. Release builds do not contain it.
