# Roadmap

## Completed development MVP

- Dark premium Compose design system and all required product screens.
- Clean domain/data/presentation boundaries, Hilt, navigation, validation, state and offline cache.
- NestJS REST API, PostgreSQL migrations, JWT access/rotating refresh auth and password recovery.
- Discovery, quotas, likes, reciprocal matches, preferences, blocks, reports, unmatch, account visibility/deletion.
- Persisted realtime chat with text, photo, reply, typing, online, read state, heartbeat and reconnect.
- S3-compatible profile/chat media through MinIO.
- FREE/PREMIUM entitlement architecture, received likes, rewind, boost and Super Like limits.
- Provider-neutral notification registration plus development implementation.
- Russian/English resources, persistent per-app locale selection and optional account locale preference.
- Foreground approximate/manual profile location with private storage and server-side distance filtering.
- Provider-neutral account identities, Google Credential Manager, phone OTP, X PKCE, official VK ID adapter, account linking/unlink safety and Instagram profile links.
- JVM, Compose, backend unit, API/database/object-storage and WebSocket integration tests.
- Secure environment templates, Docker Compose, verification scripts and operations documentation.

## Pre-production

- Connect FCM/APNs credentials and production message delivery.
- Integrate Play Billing or another billing provider; validate purchases server-side and handle webhooks.
- Connect a production SMS provider to `SmsSender` and add production email delivery for recovery.
- Register production Google, X and VK applications, redirect URIs and consent/publishing settings.
- Add moderation console, image moderation, appeals and abuse-risk signals.
- Deploy managed PostgreSQL and S3, edge rate limiting, observability, backups, KMS secrets, staging and CI/CD.
- Add more locale resource packs, accessibility audit, adaptive tablet layouts and comprehensive end-to-end device farm coverage.

## Product evolution

- Rich profile prompts, voice/video introductions and compatibility signals.
- Travel mode and geocoded city search without revealing coordinates.
- Notification preferences by event, quiet hours, and granular privacy controls.
- Experiments and analytics behind consent-aware interfaces.
- Split feature packages into independent Gradle modules once team ownership/build-time data justifies the cost.
