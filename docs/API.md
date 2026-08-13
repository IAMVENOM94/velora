# API

Base development URL: `http://localhost:8080`. All endpoints except auth, health, Swagger, and public profile media require `Authorization: Bearer <access-token>`.

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
| POST | `/users/me/photos/:position/upload` | Direct multipart image upload, positions 0-8. |
| POST | `/users/me/photos/upload-url` | Create a 15-minute S3-compatible presigned PUT URL. |
| POST | `/users/me/photos` | Attach a validated presigned object to a position. |
| DELETE | `/users/me/photos/:id` | Delete the caller's photo and object. |
| DELETE | `/users/me` | Delete the account and owned media. |
| GET | `/media/:id` | Stream an opaque public profile image. |

Images are limited to 10 MB and JPEG, PNG, or WebP. MIME type, extension, size, and file signature are validated. Objects are never stored in PostgreSQL.

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

## Chat

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/conversations` | Active conversations, last message, unread count, and peer summary. |
| GET | `/conversations/:id/messages?before=<ISO time>` | Up to 50 persisted messages. |
| POST | `/conversations/:id/messages` | Send text and optional `replyToId`. |
| POST | `/conversations/:id/messages/photo` | Send multipart image and optional `replyToId`. |
| PATCH | `/conversations/:id/read` | Persist read state and broadcast it. |
| GET | `/media/messages/:id` | Authenticated image stream for conversation members. |

WebSocket: `/ws/chat?token=<access-token>&conversationId=<uuid>`.

Client events: `typing { active }`, `read`. Server events: `connected`, `message`, `typing`, `read`, `online`, `error`. The connection is rejected when the caller is not a member of an active match.

## Preferences, safety, and notifications

| Method | Path | Purpose |
| --- | --- | --- |
| GET/PATCH | `/preferences` | Age 18-100, distance 1-500 km, shown genders, notifications. |
| POST/GET | `/blocks` | Block a user or list blocked accounts. Blocking also unmatches. |
| DELETE | `/blocks/:userId` | Unblock. |
| POST | `/reports` | Report `harassment`, `spam`, `fake`, `inappropriate`, `underage`, or `other`. |
| GET | `/notifications` | Latest 100 notifications. |
| PATCH | `/notifications/:id/read` | Mark one notification read. |
| POST/DELETE | `/notifications/devices` | Register or remove a push-device token. |

Standard errors are JSON with an HTTP status and `message`. `402` means Premium is required, `409` is a state conflict, `429` is a daily quota, and `401` triggers one client token-refresh attempt.
