# API

Base development URL: `http://localhost:8080`. All endpoints except auth, health, and Swagger require `Authorization: Bearer <access-token>`. Profile and chat media are authenticated and re-check visibility, block, account, match, and membership state.

Interactive OpenAPI documentation is generated at `/docs`. JSON field names shown below match the wire format; the Android serialization layer maps them into Kotlin models.

## Authentication

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/auth/register` | Create an email account and return access/refresh tokens. |
| POST | `/auth/login` | Authenticate an active account. |
| POST | `/auth/refresh` | Rotate a valid refresh token and issue a new pair. Old token becomes unusable. |
| POST | `/auth/logout` | Revoke the supplied refresh token. |
| POST | `/auth/recover` | Create a single-use 30-minute reset token without revealing account existence. |
| POST | `/auth/reset` | Set a new password, consume the reset token, and revoke all sessions. |
| GET | `/auth/providers` | Return configuration-driven availability for email, phone, Google, X, VK and Instagram profile linking. |
| POST | `/auth/google` | Verify a Google ID token and issue a Velora session. |
| POST | `/auth/phone/request` | Create a five-minute E.164 OTP challenge with cooldown/rate limiting. |
| POST | `/auth/phone/verify` | Consume a six-digit OTP once and issue a Velora session, or link it to an authenticated user. |
| POST | `/auth/vk/sdk` | Verify the official VK ID SDK access token server-side and issue a Velora session. |
| GET | `/auth/x/start` | Start X OAuth 2.0 Authorization Code + PKCE (`mode=login|link`). |
| POST | `/auth/x/callback` | Consume X state/code, verify the provider subject, and login/link. |
| GET/POST | `/auth/vk/start`, `/auth/vk/callback` | Browser PKCE compatibility flow; Android normally uses `/auth/vk/sdk`. |

Credentials use `{ "email": "person@example.com", "password": "8-128 characters" }`. Password reset returns `developmentToken` only outside production; a production mail/SMS adapter can deliver the same opaque token.

Provider tokens establish identity only and are never used as the Velora session. Every successful provider flow returns the existing access/rotating-refresh pair. Provider email is metadata: the backend never merges two users merely because emails match. Development OTP responses include `developmentCode` only outside production; production never logs or returns the code.

## Account identities, locale, location and social profiles

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/users/me/identities` | List connected login methods with masked email/phone metadata. |
| POST | `/users/me/identities/link/google` | Verify and connect Google to the authenticated account. |
| POST | `/users/me/identities/link/vk` | Verify and connect an official VK ID result. |
| DELETE | `/users/me/identities/:provider` | Disconnect a provider; rejects removing the final method. |
| GET/PUT | `/users/me/locale` | Read or save optional `preferredLocale` (`ru`, `en`, or `null`). |
| GET/PUT | `/users/me/location` | Read/save the owner's private GPS/manual profile location. |
| GET/POST | `/users/me/social-profiles` | List or add normalized Instagram/X/VK profile links. |
| PATCH/DELETE | `/users/me/social-profiles/:provider` | Update or remove a social profile link. |

`PUT /users/me/location` accepts `country`, `region`, required `city`, optional latitude/longitude pair, `accuracyMeters`, and `locationSource` (`gps` or `manual`). Coordinates are quantized before storage. This owner-only DTO is intentionally separate from public discovery/profile DTOs.

## Profile and photos

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/users/me` | Current account, profile, interests, visibility, and primary photo. |
| PATCH | `/users/me/profile` | Create or update an adult profile. |
| PUT | `/users/me/onboarding` | Atomically save profile, 3-12 interests, and shown genders. |
| GET/PUT | `/users/me/interests` | Read or replace profile interests. |
| PATCH | `/users/me/visibility` | Hide or publish the profile. |
| GET | `/users/me/photos` | Ordered photo collection. |
| POST | `/users/me/photos/:position/upload` | Direct multipart image upload, position below configured `PROFILE_PHOTO_LIMIT`. Uploading into an occupied position replaces it. |
| POST | `/users/me/photos/upload-url` | Create a 15-minute S3-compatible presigned PUT URL. |
| POST | `/users/me/photos` | Attach a validated presigned object to a position. |
| PUT | `/users/me/photos/order` | Reorder the complete owned collection with `{ "photoIds": [...] }`. |
| PUT | `/users/me/photos/:id/main` | Move one owned photo to position 0. |
| DELETE | `/users/me/photos/:id` | Delete all variants and close the position gap. |
| GET | `/users/:userId/profile` | Safe public profile DTO with photos, interests, presence, and aggregate relationship state. |
| DELETE | `/users/me` | Soft-delete now, revoke sessions/devices/sockets, hide the account, and return `purgeAfter`. |
| GET | `/media/:id/:variant` | Authenticated `thumbnail`, `medium`, or `full` JPEG stream after access checks. |

Images are limited by `PROFILE_IMAGE_MAX_BYTES` (10 MB default), decoded as JPEG/PNG/WebP, and rejected when corrupt, oversized in dimensions/pixels, or inconsistent with their claimed type. Sharp emits bounded thumbnail/medium/full JPEG derivatives without EXIF, IPTC, XMP, or GPS metadata. Objects are never stored in PostgreSQL.

`PublicProfileDto` contains `userId`, `displayName`, `age`, `gender`, full `bio`, `relationshipGoal`, privacy-filtered `city`/`distanceKm`, `verified`, `onlineStatus`/`lastSeen`, `relationshipState`, match/conversation IDs only when applicable, `canMessage`, photos, interests, and visible social profiles. It never contains email, phone, provider/OAuth identities, coordinates, refresh tokens, internal moderation data, IPs, or device identifiers. Blocked, deleted, suspended, or invisible profiles return the same non-enumerating `PROFILE_UNAVAILABLE` response.

## Discovery, matching, and Premium

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/discover?limit=20` | Eligible profiles after server-side radius filtering; maximum 50 records. Returns city/country and approximate distance but never location coordinates. |
| POST | `/passes/:userId` | Pass a profile and consume a daily action. |
| POST | `/likes/:userId` | Send `{ "kind": "like" | "super_like" }`; creates a match on reciprocity. |
| GET | `/likes` | Count received likes; profile list is Premium-gated. |
| POST | `/discover/rewind` | Remove the most recent pass; Premium only. |
| GET | `/matches` | Active matches. |
| GET/DELETE | `/matches/:id` | Inspect or unmatch. |
| GET | `/subscription` | Effective tier, expiry, limits, and current daily usage. |
| POST | `/subscription/boost` | Add a 30-minute Premium boost. |
| POST | `/subscription/development/activate-premium` | Development-only 30-day activation. |

FREE allows 20 daily discovery actions and 1 Super Like; development PREMIUM allows 1000 and 5. Limits are transactionally serialized per user.

Production Android uses cursor feeds instead of the compatibility list endpoints:

| Method | Path | Cursor order |
| --- | --- | --- |
| GET | `/feed/discover?limit=20&cursor=...` | Stable user ID, maximum 40. |
| GET | `/feed/matches?limit=20&cursor=...` | Match creation time + ID. |
| GET | `/feed/conversations?limit=30&cursor=...` | Last activity time + conversation ID. |
| GET | `/feed/conversations/:id/messages?limit=50&cursor=...` | Message creation time + ID; returned oldest-to-newest per page. |
| GET | `/feed/likes?limit=20&cursor=...` | Like time + sender ID; people remain entitlement-gated by the existing development Premium architecture. |

Each response is `{ "items": [...], "nextCursor": "..." | null }` (likes also includes `count` and `locked`). Cursors are opaque base64url values; malformed cursors return `INVALID_CURSOR`.

## Chat

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/conversations` | Active conversations, last message, unread count, and peer summary. |
| GET | `/conversations/:id/messages?before=<ISO time>` | Up to 50 persisted messages. |
| POST | `/conversations/:id/messages` | Send text with optional `replyToId` and UUID `clientMessageId`. |
| POST | `/conversations/:id/messages/photo` | Send multipart image with optional `replyToId` and UUID `clientMessageId`. |
| PATCH | `/conversations/:id/read` | Persist read state and broadcast it. |
| GET | `/media/messages/:id` | Authenticated image stream for conversation members. |

WebSocket: `/ws/chat?conversationId=<uuid>` with `Authorization: Bearer <access-token>` during the HTTP upgrade. Tokens are deliberately excluded from URLs and logs.

Client events: `typing { active }`, `read`. Server events: `connected`, `message`, `typing`, `delivered`, `read`, `online`, `error`. The server rechecks active membership/block state for every incoming event, rejects payloads over 16 KiB, pings connections, and closes affected sockets immediately after block, unmatch, or account deletion. REST and WebSocket retries converge on the unique `(sender_id, client_message_id)` record instead of duplicating messages.

## Preferences, safety, and notifications

| Method | Path | Purpose |
| --- | --- | --- |
| GET/PATCH | `/preferences` | Age 18-100, distance 1-500 km, shown genders, notifications. |
| GET/PATCH | `/privacy` | Online, distance, city, discovery, read-receipt, push, and temporary-hide switches enforced server-side. |
| POST/GET | `/blocks` | Block a user or list blocked accounts. Blocking also unmatches. |
| DELETE | `/blocks/:userId` | Unblock. |
| POST | `/reports` | Report `harassment`, `spam`, `fake`, `inappropriate`, `underage`, or `other`. |
| GET | `/notifications` | Latest 100 notifications. |
| PATCH | `/notifications/:id/read` | Mark one notification read. |
| POST/DELETE | `/notifications/devices` | Register or remove a push-device token. |

Reports retain reason/comment server-side for the moderation window but never return internal moderation fields to the reporter. Unmatch marks the match inactive and preserves message history for retention/audit; it immediately removes active feeds and prevents further reads/writes through product APIs. Block additionally removes mutual likes and prevents public-profile/media access.

## Error, correlation, and rate-limit contract

Every error is `{ "code": "STABLE_CODE", "message": "safe text", "fieldErrors": { ... }?, "requestId": "..." }`. The same request ID is returned in `X-Request-Id` and structured logs. Raw SQL, exception messages, stack traces, credentials, authorization headers, bodies, full phone numbers, and coordinates are not logged or returned. Android maps known codes to localized user-facing messages and retries a 401 once after serialized refresh rotation.

Scoped backend limits cover login, registration, OTP, profile views, likes, passes, messages, reports, and photo upload. Responses include `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` for 429. `RATE_LIMIT_MULTIPLIER` must remain 1 in production.
