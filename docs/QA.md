> Current portfolio status: active development. This historical development document is not evidence of a beta release or current end-to-end readiness. The 2026-09-19 verification and limitations are recorded in [PORTFOLIO_AUDIT.md](PORTFOLIO_AUDIT.md).

# QA

## Automated coverage

| Layer | Coverage |
| --- | --- |
| Android JVM | Credential/profile/message validation, repository mapping, account-scoped offline cache and authoritative cache purge, reconnect backoff, RU/EN persistence, and foreground/manual location outcomes. |
| Compose UI | Empty/retry states, Premium development gating, registration -> onboarding -> Like -> Match -> Chat, public-profile rendering/actions/fullscreen photos, provider availability, Connected Accounts and language choice. |
| Backend unit | Auth/security helpers, E.164/OTP/PKCE/OAuth, locale/handle validation, cursor/presence/rate rules, image signature/limits/derivatives, and explicit EXIF GPS stripping. |
| API integration | Health/migrations, validation, registration/onboarding, privacy-safe public profiles/discovery, processed MinIO variants/order/main/delete, reciprocal match, pagination, refresh expiration, report, block, unmatch, push-device lifecycle, and soft deletion. |
| Advanced integration | Atomic interests/preferences, existing development FREE/Premium limits, WebSocket typing/text/photo/delivered/read, reconnect and duplicate protection, authenticated media, device registration, unmatch/unblock, and deletion revocation. |
| Stage 9 integration | Stable Google/VK sessions, phone OTP success/wrong/expired/reused/cooldown/rate limits, identity link/conflict/unlink safety, locale, GPS/manual location, Instagram links and coordinate non-disclosure. |
| Stage 10 integration | Safe DTO/relationship state, EXIF-free variants, cursor feeds, immediate privacy/presence changes, idempotent messages, delivered/read privacy, reconnect, socket closure after safety actions, FCM/dev delivery boundary, token expiry and deletion. |

The integration suites create isolated users and delete them afterward. They use the real Docker PostgreSQL, MinIO, REST API, and WebSocket server.

Latest Stage 10 verification (2026-08-14): 18 backend unit tests, 32 real-service integration tests, 11 Android JVM tests, and 9/9 emulator Compose tests passed. Debug lint has 0 errors (28 advisory dependency/deprecation/configuration warnings). Clean and Stage 9 upgrade databases both applied all nine migrations. The 19,180,363-byte unsigned release APK built successfully; source and APK scans found no active credential/private-key leakage and no background-location permission. APK SHA-256: `21B8757BF0C8EBB00596EC6944DE51BBEEB108D9E55E6DE5B60F20D2E524A95F`.

## Verified product journey

1. Registration returns access and rotating refresh tokens; Android encrypts them before persistence.
2. Nine-step onboarding enforces 18+, at least one photo, three interests, identity, preference, city, bio, and goal.
3. Discover filters profiles server-side without exposing coordinates; tapping photo/name preserves the card position and opens an ID-addressed full profile.
4. Pass/Like/Super Like update quotas; reciprocal Like creates a match and conversation.
5. Public profile pages show every processed photo in a pager/fullscreen zoom viewer, full bio/interests, privacy-filtered location/presence, and relationship-aware Like/Pass/Super Like/Message/Unmatch actions.
6. Match opens persisted chat; text/photo/reply, typing, server-authoritative presence, delivered/read status, idempotent retry, reconnect, history pagination, and unread counters are wired.
7. Profile/photos/interests, privacy/search preferences, sessions, and cached read states survive process restart.
8. Block, report, unmatch, hide profile, logout, and soft account deletion have working UI/API/socket paths.
9. System/Russian/English locale selection persists and optional account locale synchronizes.
10. Approximate foreground GPS or manual city is stored privately; radius filtering occurs server-side without public coordinate fields.
11. Google/phone/X/VK buttons follow configuration, all successful providers end in Velora tokens, linked-account conflicts are explicit, and the final login method cannot be removed.

## Manual emulator smoke test

The Stage 10 debug APK was exercised on the Android 17 Pixel 10 Pro XL emulator against Docker PostgreSQL/MinIO/backend. The run covered cold login, Discover photo loading, public profile from Discover, two-photo swipe, fullscreen open/swipe-down close, one-way Like state, reciprocal Match, separate avatar/row actions in Matches, chat, public profile from the chat name, persisted message delivery, server-broadcast read receipt, force-stop/session restoration, and Unmatch. Runtime QA found and fixed fullscreen gesture arbitration, stale Matches refresh, chat status-bar overlap/profile tap, and post-Unmatch navigation; logcat contained no fatal app exception.

Stage 9 was previously installed over USB on a Realme RMX3363 using an ADB reverse tunnel. At the final Stage 10 verification point, `adb devices -l` exposed only `emulator-5554`; therefore the current Stage 10 APK could not truthfully be reinstalled on the absent physical device. When it is connected and unlocked, use the exact ADB-reverse commands in `SETUP.md`, then repeat the public-profile/chat/unmatch smoke flow without bypassing the keyguard.

## Release regression checklist

- Fresh install and upgrade from the previous public build.
- Offline launch with cached data, reconnect, expired access token, revoked refresh token.
- Rotation, background/foreground, process death, keyboard and system back behavior.
- TalkBack labels, font scaling, contrast, touch targets, and reduced-motion review.
- Slow/failed photo upload, duplicate likes, quota boundary, simultaneous reciprocal likes.
- WebSocket network switch, heartbeat timeout, repeated reconnect, unmatch while connected.
- Account deletion immediately revokes/hides access and schedules purge; blocked peers cannot discover, open media/profile, or chat.
- Release build reaches only HTTPS/WSS and contains no debug credentials or development endpoints.
