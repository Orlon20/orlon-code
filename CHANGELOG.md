# Changelog

## 1.1
- Renamed to **Orlon Code**; new pixel wordmark and launcher icon.
- Runs OpenCode 1.19.3 fully on the phone inside a proot/Alpine sandbox (first-launch setup downloads ~100 MB).
- Build Android apps on the phone: `orlon-new-android`, `orlon-build` (background job, auto-installs JDK 17 + Android SDK 36),
  signed debug/release APKs, tap-to-install links in the chat, install banner/notification.
- Themes: `/themes` and Settings > Themes (all OpenCode themes + light/dark); native screens follow the theme.
- Preview what was built (project files as a website, or any local dev-server port).
- AMOLED black default theme; white home wordmark; model logos (models.dev) in the prompt toolbar and model picker.
- Phone-fit fixes for OpenCode's UI (prompt toolbar, long commands), branded loading screen, cleaner setup screen.
