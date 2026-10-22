# Security policy

## Reporting a vulnerability

Please report security problems privately using GitHub's **"Report a vulnerability"** button (Security tab of this repository),
not in a public issue. Include what you found, how to reproduce it, and the device/Android version. You will get an
acknowledgement, and fixes are released as soon as practical.

## Security model (what to expect)

- Everything runs on the user's phone inside the app's private storage; there is no Orlon Code server.
- The OpenCode server listens on `127.0.0.1` only and requires a random per-install password (HTTP Basic auth), so other apps
  on the phone cannot use it.
- The only functions the web UI can call in the app are `openThemes`, `themesData` and `installApk`. `installApk` refuses
  everything except a real `.apk` inside a project's `.orlon/out` folder.
- Downloads are pinned to exact versions and verified against SHA-256 checksums.
- The agent can run commands in its sandbox with full access to the sandbox's files (that is its job). It cannot read other
  apps' data or the phone's shared storage. It can use the network.
- Release APKs built on the phone are signed with a key generated on the phone and stored in the app's private storage.
  If you uninstall the app, that key is lost.
