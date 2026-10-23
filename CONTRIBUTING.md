# Contributing to Orlon Code

Thanks for helping. Orlon Code is a small Android app (Kotlin, no UI framework) that runs the OpenCode coding agent inside a
Linux sandbox on the phone and lets that agent build Android apps. Bug reports, fixes, and new ideas are welcome.

## Build and test

Requirements: JDK 17, Android SDK platform 36 + build-tools 36.0.0 (`ANDROID_HOME` set or `local.properties` with `sdk.dir`).

```bash
./gradlew testDebugUnitTest      # JVM unit tests (no device needed)
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
```

Release builds are signed with a key you provide: copy `keystore.properties.example` to `keystore.properties` (git-ignored) and
point it at your keystore, then `./gradlew assembleRelease`.

## Testing on a device

The app targets arm64 phones (Android 8+). The debug build includes `ExecReceiver`, a debug-only broadcast receiver that runs a
script inside the sandbox over `adb`, which is how the sandbox scripts (`app/src/main/assets/sandbox/`) are tested without
touching the UI. See `docs/ARCHITECTURE.md`. Release builds do not contain it.

Before opening a pull request:
- run the unit tests;
- if you changed anything in `Branding.kt` (the scripts injected into OpenCode's web UI), test it on a real device, because
  those scripts depend on OpenCode's page structure;
- if you changed a sandbox script, run it inside the sandbox and include the output;
- keep changes focused; add or update a test when the logic is testable on the JVM.

## Updating a pinned download

OpenCode, Alpine and the SDK pieces are pinned with SHA-256 checksums (`Sandbox.kt`, `assets/sandbox/orlon-toolchain`).
To bump one: update the version/URL, recompute the checksum from the official source, run the app on a device from a clean
install, and confirm setup, a prompt, and an app build all work. Do not add downloads without a checksum.

## Security

Please do not file public issues for vulnerabilities; see `SECURITY.md`.

By contributing you agree that your contribution is licensed under the Apache License 2.0 (see `LICENSE`).
