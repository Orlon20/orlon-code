# Mobilecode

Vibecode on your phone. Mobilecode runs [OpenCode](https://opencode.ai) (MIT) **on the phone itself** — no PC, no ports, no tunnels.

## Install
1. Install `Mobilecode.apk` (allow "install unknown apps" for your browser/file manager).
2. Open it and tap **Set up Mobilecode** once (about 100 MB download, about 600 MB installed; Wi-Fi recommended).
3. You land on the prompt box. Pick a model and start prompting. Free OpenCode models work out of the box; add your own providers/API keys from OpenCode's **Settings → Providers**.

## Using it
- Projects live in `/root/projects` inside the app's private Linux sandbox.
- **⋮ → Preview what was built** opens your project as a website (e.g. `index.html`) or any local dev-server port (type `5173`). Review code changes in OpenCode's own **Changes** tab.
- The agent keeps running in the background (a notification shows while it does; tap **Stop** to end it).
- **⋮ → Connect to a PC instead** lets Mobilecode be a remote client for an OpenCode server on another machine.

## How it works
`libproot.so` (proot, shipped as a native library) launches an Alpine Linux arm64 root filesystem in app storage; OpenCode 1.19.3 (musl arm64) runs inside it as `opencode web` on `127.0.0.1` with a random per-install password. The app's WebView shows OpenCode's UI with Mobilecode branding and phone-fit fixes. Downloads (Alpine + OpenCode) are pinned and SHA-256 verified.

## Build
```
./gradlew testDebugUnitTest assembleRelease   # needs JDK 17 + Android SDK 36; release signing via keystore.properties
```
Keep `keystore/mobilecode.jks` and `keystore.properties` safe: updates must be signed with the same key. Installing a build signed with a different key requires uninstalling first (which deletes on-device projects).

## Limits
arm64 phones only (Android 8+). Models run in the cloud, so internet is required. Preview is for web projects.
