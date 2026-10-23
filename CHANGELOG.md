# Changelog

## 1.2
- **Back up and restore** (Settings): one zip with your projects and the on-phone signing key, saved wherever you choose.
- **Add AI provider** (Settings): opens OpenCode's provider list so you can connect your own API keys.
- **Build tools are prepared in the background** on Wi-Fi after setup (or on demand), with a notification, so the first build is not a long wait.
- **Build progress notification** while an app builds; one-time prompt to let Orlon Code keep running in the background.
- **Projects in the Files app**: the projects folder is available through Android's file picker (read, write, copy out).
- **More model logos** (OpenAI, Anthropic, Google, Meta, Mistral, xAI, and more), bundled so they also work offline.
- AMOLED black default, white home wordmark, icon-only model button and a toolbar that wraps instead of clipping.
- License: Apache-2.0.

## 1.1
- Renamed to **Orlon Code**; new pixel wordmark and launcher icon.
- Runs OpenCode 1.19.3 fully on the phone inside a proot/Alpine sandbox (first-launch setup downloads ~100 MB).
- Build Android apps on the phone: `orlon-new-android`, `orlon-build` (background job, auto-installs JDK 17 + Android SDK 36),
  signed debug/release APKs, tap-to-install links in the chat, install banner/notification.
- Themes: `/themes` and Settings > Themes (all OpenCode themes + light/dark); native screens follow the theme.
- Preview what was built (project files as a website, or any local dev-server port).
- AMOLED black default theme; white home wordmark; model logos (models.dev) in the prompt toolbar and model picker.
- Phone-fit fixes for OpenCode's UI (prompt toolbar, long commands), branded loading screen, cleaner setup screen.
