> Current portfolio status: active development. This historical development document is not evidence of a beta release or current end-to-end readiness. The 2026-09-19 verification and limitations are recorded in [PORTFOLIO_AUDIT.md](PORTFOLIO_AUDIT.md).

# Stage 10 closed-beta report

Status: implementation and emulator/release verification complete on 2026-08-14. Physical-device reinstallation remains externally blocked because the Realme RMX3363 was not present in ADB during the final run.

## Product result

- `UserProfileScreen` is a first-class `user_profile/{userId}` route. Navigation carries only the ID and loads a safe server DTO.
- Public profiles open from Discover photo/name, Matches avatar, Chats avatar, Chat avatar/name/menu, Match, and the existing development Likes view.
- The dark premium profile shows the full bio, backend interests, goal, gender, verified state, privacy-filtered city/distance, authoritative presence, and relationship-aware controls.
- All ready photos are available through a `HorizontalPager`; indicators, loading/error states, fullscreen, horizontal paging, pinch zoom, system Back, explicit close, and swipe-down close are implemented.
- Discover retains its in-memory card position when returning from a profile. Matches and Chats refresh independently when opened so a match made on another device appears without resetting Discover.

## Backend and data

- `GET /users/:userId/profile` checks authentication, both block directions, account active/deleted/suspended state, onboarding, temporary hide, discoverability, and existing-match exceptions.
- Relationship state is aggregated server-side as `NONE`, `LIKED`, `LIKED_ME`, or `MATCHED`. A blocked profile is deliberately indistinguishable from unavailable and therefore returns 404 instead of exposing `BLOCKED`.
- The public DTO never includes email, phone, provider/OAuth subjects, exact coordinates, refresh tokens, device/IP data, or moderation internals.
- Migrations 008 and 009 add account lifecycle, privacy, processed photo metadata, message idempotency/delivery, moderation retention, notification lifecycle, cursor/rate indexes, and authoritative active presence.
- Cursor feeds cover Discover, Matches, Conversations, Messages, and Likes. Invalid/forged cursors fail with a stable error.

## Photo pipeline

- The configurable profile limit is 1-9 photos (default 9).
- Direct and presigned uploads are decoded and validated by actual JPEG/PNG/WebP content, MIME, bytes, dimensions, pixel count, and corruption state.
- Sharp applies orientation and creates private-cacheable thumbnail (360x480), medium (1080x1440), and full (2400x3200) JPEG variants without enlargement.
- Derivatives are written without metadata; automated tests inject GPS EXIF tags and verify EXIF/IPTC/XMP/GPS are absent afterward.
- PostgreSQL stores owner, opaque object keys, MIME/source bytes, dimensions, position, status, and timestamps—not binary images.
- Android supports upload/replace, ordered display, delete with gap closure, full reorder, and selection of the main photo. Coil uses authenticated requests plus memory/disk caching.

## Safety, privacy, and account lifecycle

- Block is server-enforced across profile/media/discovery/chat, removes likes, inactivates a match, and closes affected sockets immediately.
- Report accepts only Spam, Fake profile, Harassment, Inappropriate content, Underage, or Other plus a comment up to 1000 characters. Moderation state/retention is private.
- Unmatch marks the match inactive, removes it from feeds, closes sockets, and forbids new history/product access and writes while retaining database history for policy-controlled retention.
- Privacy controls for online state, distance, city, discovery, read receipts, push, and temporary profile hiding are persisted and enforced by backend queries/events.
- Delete account immediately deactivates/hides the user, clears active presence, revokes refresh tokens/device tokens, removes likes, inactivates matches, closes sockets, and rejects login. `purge_after` schedules a 30-day purge/anonymization operation; abuse/security/legal records may remain inaccessible for their documented retention window.

## Realtime, offline, and lifecycle

- WebSocket authentication uses the Authorization header rather than URL tokens. Membership/block state is rechecked per event; payloads are bounded and server ping/pong drives presence.
- Android reconnect uses capped exponential backoff, abnormal-close retry, connectivity callbacks, and foreground/background lifecycle triggers. Normal and policy closes are not retry loops.
- Text/photo writes use UUID `clientMessageId`; PostgreSQL uniqueness makes REST/WebSocket retry idempotent. Delivered and read events mutate existing messages instead of creating duplicates.
- The account-scoped DataStore cache covers public profiles and paged core feeds. It is offline-only, is cleared on account changes/logout/delete, and an authoritative 404 purges a cached public profile.
- Transient refresh network failure no longer destroys a valid local session; an actually invalid refresh token does.

## Push, errors, rate limits, and logging

- Notification device registration, disable/rebind lifecycle, delivery attempts, and events for Like, Super Like, Match, and Message are complete.
- FCM HTTP v1 is selected when configured through environment credentials. The credential-free development sender keeps closed-beta flows testable without committing secrets.
- The API error contract is `code`, safe `message`, optional `fieldErrors`, and `requestId`; the same ID is in `X-Request-Id` and structured JSON logs.
- Scoped persisted rate limits protect login, registration, OTP, profile views, likes, passes, messages, reports, and uploads. Subjects are hashed; logs omit bodies, passwords, OTPs, tokens, authorization headers, full phones, and coordinates.

## Verification evidence

| Check | Result |
| --- | --- |
| Backend TypeScript build | Passed |
| Backend unit tests | 18/18 passed |
| PostgreSQL/MinIO/API/WebSocket integration | 32/32 passed |
| npm production audit | 0 vulnerabilities |
| Android JVM tests | 11/11 passed |
| Compose instrumentation | 9/9 passed on Android 17 emulator |
| Android debug lint | 0 errors; 28 advisory warnings |
| Clean migrations | 001-009 passed in an isolated database |
| Stage 9 upgrade migrations | 001-007 -> 008-009 passed in an isolated database |
| Release APK | Built, 19,180,363 bytes |
| Source/APK secret scans | Passed |
| Release APK SHA-256 | `21B8757BF0C8EBB00596EC6944DE51BBEEB108D9E55E6DE5B60F20D2E524A95F` |

The manual emulator journey covered login, Discover, public profile, multi-photo/fullscreen gestures, Like, reciprocal Match, Matches, Chat, profile entry from Chat, persisted message, read receipt, force-stop restoration, and Unmatch. Logcat contained no fatal app exception. Runtime defects discovered during this pass—fullscreen vertical gesture arbitration, stale connection feeds, chat status-bar overlap, and navigation after safety closure—were fixed and rebuilt.

## Deployment boundaries

- The realtime hub is in-memory and production-ready for one beta backend instance. Horizontal scaling requires shared fan-out/presence coordination such as Redis.
- The account purge marker and retention contract are implemented; operations must schedule the documented purge/anonymization worker and object cleanup before production launch.
- FCM, SMS, provider, TLS, store signing, and production API credentials remain environment/deployment concerns.
- No Google Play Billing, real subscription purchase, paid Boost, paid Super Like, or other payment integration was added in Stage 10. Existing Premium UI and development entitlements were preserved unchanged, so the monetization checkpoint was not crossed.
- The Realme RMX3363 must be connected and unlocked before the final hardware install/reverse/logcat smoke can be repeated for this exact APK.
