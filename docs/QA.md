# QA

## Automated coverage

| Layer | Coverage |
| --- | --- |
| Android JVM | Credential validation, 18+ onboarding/interests, message validation, repository mapping/cache, RU/EN locale application/persistence and foreground/manual location outcomes. |
| Compose UI | Empty Discover recovery, Premium gating/activation, dynamic match identity, registration -> onboarding -> Like -> Match -> Chat, provider availability, Connected Accounts link/unlink affordances and language choice. |
| Backend unit | Auth/security helpers, E.164 normalization, OTP HMAC, PKCE, encrypted OAuth state, locale/handle validation and signed development assertions. |
| API integration | Health/migrations, validation, registration, onboarding, privacy-safe location discovery, direct/presigned MinIO photos, reciprocal match, persisted/read chat, notifications, refresh rotation, password reset, preferences, report and block. |
| Advanced integration | Atomic interests/preferences, FREE limits, Premium/rewind/boost, WebSocket typing/text/photo/read, authenticated media, device registration, unmatch/unblock, and account/media deletion. |
| Stage 9 integration | Stable Google/VK sessions, phone OTP success/wrong/expired/reused/cooldown/rate limits, identity link/conflict/unlink safety, locale, GPS/manual location, Instagram links and coordinate non-disclosure. |

The integration suites create isolated users and delete them afterward. They use the real Docker PostgreSQL, MinIO, REST API, and WebSocket server.

Latest Stage 9 verification: 12 backend unit tests, 24 real-service integration tests, 9 Android JVM tests, and 7 emulator Compose tests passed. Debug lint has zero errors. A clean PostgreSQL database applied all seven migrations. The unsigned release APK built successfully; scanning every APK entry against the active development credentials and private-key/vendor signatures returned zero findings, and the manifest contains no background-location permission.

## Verified product journey

1. Registration returns access and rotating refresh tokens; Android encrypts them before persistence.
2. Nine-step onboarding enforces 18+, at least one photo, three interests, identity, preference, city, bio, and goal.
3. Discover filters profiles server-side without exposing coordinates.
4. Pass/Like/Super Like update quotas; reciprocal Like creates a match and conversation.
5. Match opens persisted chat; text/photo/reply, typing, online, read status, reconnect, and unread counters are wired.
6. Profile/photos/interests and search preferences survive process restart.
7. Block, report, unmatch, hide profile, logout, and account deletion have working UI/API paths.
8. System/Russian/English locale selection persists and optional account locale synchronizes.
9. Approximate foreground GPS or manual city is stored privately; radius filtering occurs server-side without public coordinate fields.
10. Google/phone/X/VK buttons follow configuration, all successful providers end in Velora tokens, linked-account conflicts are explicit, and the final login method cannot be removed.

## Manual emulator smoke test

The Stage 9 debug APK has been exercised on an Android emulator against Docker services: the new provider-aware auth screen, existing demo login, profile/settings, English-to-Russian switch, force-stop locale/session restoration, location-permission denial, manual city fallback, owner API persistence, and restoration of the seed profile. Android runtime logs contained no crash. Run the repeatable component suite with `scripts/run-ui-tests.ps1`.

The same APK was installed over USB on a Realme RMX3363 using an ADB reverse tunnel. Android reported a successful install and cold `MainActivity` launch, the activity reached resumed state, and logcat contained no fatal app exception. The phone's secure keyguard remained locked, so credential entry and visible language/location interaction were completed on the emulator rather than attempting to bypass the device lock.

## Release regression checklist

- Fresh install and upgrade from the previous public build.
- Offline launch with cached data, reconnect, expired access token, revoked refresh token.
- Rotation, background/foreground, process death, keyboard and system back behavior.
- TalkBack labels, font scaling, contrast, touch targets, and reduced-motion review.
- Slow/failed photo upload, duplicate likes, quota boundary, simultaneous reciprocal likes.
- WebSocket network switch, heartbeat timeout, repeated reconnect, unmatch while connected.
- Account deletion removes relational rows and S3 objects; blocked peers cannot discover or chat.
- Release build reaches only HTTPS/WSS and contains no debug credentials or development endpoints.
