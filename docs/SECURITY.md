# Security

## Implemented controls

- Passwords use bcrypt cost 12; API accepts 8-128 characters.
- Access JWTs are short-lived. Refresh tokens rotate once, are stored only as SHA-256 digests server-side, and are revoked on logout/password reset. Every protected HTTP request also verifies that the account is still active, so deletion invalidates access immediately.
- Android encrypts both tokens with AES-256-GCM under Android Keystore and excludes all app data from backup/device transfer.
- Secrets and URLs come from environment variables or Gradle configuration. Production rejects missing, short, or placeholder JWT secrets.
- SQL values are parameterized. UUIDs and bounded request fields are validated before queries.
- Profiles are 18+ at both application and database layers.
- Direct and presigned images validate ownership, size, MIME, extension, and magic bytes. Chat media requires active conversation membership.
- WebSocket authentication and active-match membership are checked before joining a room. Heartbeats remove dead connections.
- Blocks work in both directions for discovery and atomically end an active match. Report reasons/details are bounded.
- Exact coordinates stay in PostgreSQL; clients receive only rounded approximate distance.
- Release Android networking rejects cleartext. The backend container runs as an unprivileged user on a read-only filesystem with no-new-privileges.
- Development-only Premium activation and reset-token exposure return no usable production route/data.

## Secret handling

`.env`, `local.properties`, signing keys, build products, and captures are ignored. `.env.example` contains no usable secrets. Rotate a credential immediately if it appears in logs, screenshots, issue trackers, or Git history.

## Production threat controls still external

The repository supplies application boundaries, not an entire production platform. Before launch add ingress/API rate limiting (especially auth/recovery/report), bot and abuse detection, device attestation as appropriate, centralized audit logs, moderation tooling, malware/image moderation, encrypted managed backups, TLS termination and certificate rotation, KMS-backed secrets, dependency/image scanning, and incident response procedures.

FCM/APNs and payment credentials are intentionally absent. Implementations plug into `PushRegistration`/`PushGateway` and subscription provider fields without changing feature code.

## Reporting vulnerabilities

Do not open a public issue containing credentials, user data, or exploit details. Send a minimal reproduction through the project's private security channel and include affected version, impact, and mitigation if known.
