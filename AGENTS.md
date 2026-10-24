# Agent Instructions — WeChat/QQ Unified Inbox

These rules are hard constraints. They override convenience, feature requests, and "clever" workarounds.
If a request conflicts with them, refuse that part and explain why.

## Security invariants (never violate)

1. **Android collection uses only `NotificationListenerService`.**
   - Never add `AccessibilityService`, accessibility automation, UI scraping, gesture simulation, or `UiAutomation`.
   - Never add hooks, method swizzling, Xposed/LSPosed, Frida, objection, VirtualXposed, Magisk modules, or runtime instrumentation.
   - Never reverse engineer, implement, or import a WeChat/QQ protocol, database reader, or unofficial SDK.
   - Never log in, scan a QR code, request credentials, or modify/patch the WeChat/QQ clients.
   - Never read WeChat/QQ private app databases or files, even on a rooted device.
   - Do not add `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, or other unrelated powerful permissions.
   - The source manifest declares only the documented normal permissions plus the NLS binding permission. WorkManager may merge `WAKE_LOCK`/`FOREGROUND_SERVICE`; these are tolerated, but no camera, accessibility, or notification-posting permission is allowed.

2. **Only rule-matched messages leave the phone.**
   - Local rules are evaluated before upload. No matched rule ⇒ no upload, no body text in logs or analytics.
   - Heartbeats may contain counters, timestamps, queue sizes, and **rule metadata (id/type/enabled) — never rule patterns**. Pattern text is uploaded only with a matched message.
   - Never upload notification text "for debugging". Debug locally with redacted data.
   - The backend must reject a batch entry with an empty `matched_rules` list.

3. **Transport and secrets.**
   - Release Android builds must refuse cleartext HTTP. Only `debug` may allow local cleartext.
   - API authentication uses a bearer token. Never put the token in a URL query string.
   - WebSocket auth uses a one-time ticket sent in the first frame, never a long-lived token in the URL.
   - Never commit `.env`, `data/`, tokens, APKs, `local.properties`, or signing keys.
   - Do not log authorization headers, tokens, message bodies, contact names, or group names.

4. **Privacy.**
   - No multi-user accounts, no telemetry, no analytics SDKs, no cloud dependency by default.
   - The app must not back up its token (`allowBackup=false`).
   - Browser notifications are the single optional push channel. Do not add Bark/ntfy/Web Push simultaneously.

## What this project is

A personal, local-first inbox for **specific** WeChat/QQ group, contact, and keyword notifications.
It reads the Android system's notification copy, filters locally, stores in SQLite, and displays a PWA.
It is not a WeChat/QQ client, bot, automation tool, or scraping product.

## Repository layout

```
backend/   FastAPI + SQLite API and static hosting
web/       Vue 3 + Vite PWA
android/   Kotlin NotificationListenerService collector
docs/      API, deployment, testing, troubleshooting
scripts/   local bootstrap/run/security checks
```

## Definition of done for changes

- `python -m pytest backend/tests` passes.
- `pnpm --dir web build` succeeds.
- `python scripts/security_check.py` passes.
- Android: `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeds when an SDK is available.
- Any change to notification parsing includes a rule-boundary test (group/contact/keyword/toggle/dedup).
- Any new dependency is justified; security-sensitive dependencies are rejected by default.
- Update `docs/` when behavior or configuration changes.

## Don't

- Don't add reply/send-message features.
- Don't add AI classification/summaries.
- Don't add regex or a visual rule builder in v1.
- Don't make the backend multi-tenant.
- Don't "fix" missing notifications by touching the WeChat/QQ client.
