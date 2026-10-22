# Orlon Code

Vibecode on your phone. Orlon Code runs [OpenCode](https://opencode.ai) (MIT) **on the phone itself** — no PC, no ports, no tunnels.

## Install
1. Install `OrlonCode.apk` (allow "install unknown apps" for your browser/file manager).
2. Open it and tap **Set up Orlon Code** once (about 100 MB download, about 600 MB installed; Wi-Fi recommended).
3. You land on the prompt box. Pick a model and start prompting. Free OpenCode models work out of the box; add your own providers/API keys from OpenCode's **Settings → Providers**.

## Using it
- Projects live in `/root/projects` inside the app's private Linux sandbox.
- **Settings (cog) → Preview what was built** opens your project as a website (e.g. `index.html`) or any local dev-server port (type `5173`). Review code changes in OpenCode's own **Changes** tab.
- **Themes:** type `/themes` in the prompt box and send (or **Settings → Themes**) to pick from OpenCode's 37 themes plus System/Light/Dark. The whole app follows it: OpenCode's UI, setup/error/About screens, menus and system bars.
- The agent keeps running in the background (a notification shows while it does; tap **Stop** to end it).
- **Settings (cog) → Connect to a PC instead** lets Orlon Code be a remote client for an OpenCode server on another machine.

## Build Android apps on the phone
Ask the agent, e.g. "make a tip calculator app and build the APK". It scaffolds a project (`orlon-new-android`), writes the code, runs Gradle on the phone (`orlon-build`), reads compiler errors, fixes them and rebuilds until it succeeds. The first build installs the build tools by itself (JDK 17 + Android SDK 36, about 750 MB). When an APK is ready you get an **Install** banner or notification, and any APK path in the chat is tappable.

You can also drive it yourself: **Settings (cog) > Build Android app** lets you set up the tools, pick or add a project (clone a public Git repo or import a ZIP), build debug or release APKs, see parsed errors, install/share the APK, or hand the errors to the agent. Projects that have a `gradlew` use their own Gradle wrapper. Release builds are signed with a key created on the phone (keep it: updates must use the same key).

Limits: arm64 phones only; the first build of a project downloads its libraries; very large projects may not fit in memory; no NDK/CMake yet; private Git repositories are not supported in the clone dialog.

## How it works
`libproot.so` (proot, shipped as a native library) launches an Alpine Linux arm64 root filesystem in app storage; OpenCode 1.19.3 (musl arm64) runs inside it as `opencode web` on `127.0.0.1` with a random per-install password. The app's WebView shows OpenCode's UI with Orlon Code branding and phone-fit fixes. Downloads (Alpine + OpenCode) are pinned and SHA-256 verified.

## Build
```
./gradlew testDebugUnitTest assembleRelease   # needs JDK 17 + Android SDK 36; release signing via keystore.properties
```
Keep `keystore/mobilecode.jks` and `keystore.properties` safe: updates must be signed with the same key. Installing a build signed with a different key requires uninstalling first (which deletes on-device projects).

## Limits
arm64 phones only (Android 8+). Models run in the cloud, so internet is required. Preview is for web projects.
