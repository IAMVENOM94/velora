# QA

## Automated coverage

| Layer | Coverage |
| --- | --- |
| Android JVM | Credential validation, 18+ onboarding and interests, message validation, repository mapping/cache behavior. |
| Compose UI | Empty Discover recovery, Premium gating/activation, dynamic match identity, and registration -> onboarding -> swipe Like -> Match -> Chat. |
| Backend unit | Auth helper behavior and security configuration. |
| API integration | Health/migrations, validation, registration, onboarding, privacy-safe discovery, direct/presigned MinIO photos, reciprocal match, persisted/read chat, notifications, refresh rotation, password reset, preferences, report, block. |
| Advanced integration | Atomic interests/preferences, FREE limits, Premium/rewind/boost, WebSocket typing/text/photo/read, authenticated media, device registration, unmatch/unblock, and account/media deletion. |

The integration suites create isolated users and delete them afterward. They use the real Docker PostgreSQL, MinIO, REST API, and WebSocket server.

Latest local verification: 5 backend unit tests, 16 real-service integration tests, 4 Android JVM tests, and 4 emulator Compose tests passed. Debug lint has no errors; its seven warnings only report newer dependency versions. A clean PostgreSQL database applied all five migrations, and the release APK secret scan returned no matches.

## Verified product journey

1. Registration returns access and rotating refresh tokens; Android encrypts them before persistence.
2. Nine-step onboarding enforces 18+, at least one photo, three interests, identity, preference, city, bio, and goal.
3. Discover filters profiles server-side without exposing coordinates.
4. Pass/Like/Super Like update quotas; reciprocal Like creates a match and conversation.
5. Match opens persisted chat; text/photo/reply, typing, online, read status, reconnect, and unread counters are wired.
6. Profile/photos/interests and search preferences survive process restart.
7. Block, report, unmatch, hide profile, logout, and account deletion have working UI/API paths.

## Manual emulator smoke test

The debug APK has been exercised on an Android emulator against Docker services: demo login, bottom navigation, profile, settings save, force-stop/session restoration, and logout. Android runtime logs contained no crash. Run the repeatable component suite with `scripts/run-ui-tests.ps1`.

## Release regression checklist

- Fresh install and upgrade from the previous public build.
- Offline launch with cached data, reconnect, expired access token, revoked refresh token.
- Rotation, background/foreground, process death, keyboard and system back behavior.
- TalkBack labels, font scaling, contrast, touch targets, and reduced-motion review.
- Slow/failed photo upload, duplicate likes, quota boundary, simultaneous reciprocal likes.
- WebSocket network switch, heartbeat timeout, repeated reconnect, unmatch while connected.
- Account deletion removes relational rows and S3 objects; blocked peers cannot discover or chat.
- Release build reaches only HTTPS/WSS and contains no debug credentials or development endpoints.
