# Stage 9: localization, profile location and unified authentication

## Delivered behavior

- English is the default resource set and Russian is provided in `values-ru`. Every Compose label, validation message, error, dialog and content description is resource-backed.
- Settings offers System, Russian and English. AppCompat persists the per-app locale; the optional `profiles.preferred_locale` account value is synchronized independently.
- Onboarding explains location-based discovery, requests foreground coarse/fine permission, accepts approximate results and always offers a manual country/city path. Settings can refresh GPS or replace it with a manual city.
- `profile_locations` is owner-private. Coordinates are rounded to three decimals before storage. Discovery calculates and filters distance in PostgreSQL and does not serialize another user's latitude, longitude, accuracy or source.
- One `users` row can own email, Google, phone, X and VK `auth_identities`. Provider credentials are verified, then exchanged for normal encrypted Velora access/refresh sessions.
- Connected Accounts supports linking and unlinking with collision responses and final-identity protection. Instagram is a removable public profile link, never a login provider.

## Authentication flows

```text
Google Credential Manager ID token -----> backend verification ---+
Phone OTP challenge/verification --------> backend verification ---+--> AuthIdentity --> Velora JWT pair
X browser Authorization Code + PKCE -----> backend exchange -------+
VK ID Android SDK access token ----------> backend user_info ------+
```

Google and VK SDK calls are contained in provider adapters. X browser launch/deep-link handling is contained in a browser adapter. Compose selects an adapter and renders availability; it never owns provider protocol logic.

The backend does not merge by provider email. A previously unseen verified provider creates a new Velora user even if its metadata email already exists; connecting an identity owned by a different user returns `IDENTITY_CONFLICT` (`409`). Unlink acquires a transaction-level user lock and rejects the final identity with `LAST_IDENTITY` (`409`).

## Phone OTP policy

- Input must normalize to E.164 (`+` plus 8-15 digits).
- A challenge expires after five minutes and is consumed once with an atomic update.
- Resend cooldown is 60 seconds.
- Five incorrect attempts lock verification for 15 minutes.
- Fifteen-minute request windows limit a phone to 5, an IP to 20 and an app-installation device ID to 10 challenges.
- Only an HMAC-SHA-256 digest is stored. Production neither returns nor logs the code.
- `DevSmsSender` performs no external send and enables deterministic local/API testing. `ProductionSmsSender` is an explicit unconfigured boundary until a vendor and server credentials are selected.

## Configuration boundaries

All empty values mean disabled; the app does not present an enabled button that cannot complete.

- Google: create an OAuth consent screen, a web OAuth client ID for token audience, and an Android client entry for `app.velora` plus signing fingerprint. Set backend `GOOGLE_CLIENT_ID` and Android `GOOGLE_WEB_CLIENT_ID` to the web client ID. No Google client secret is required.
- X: create a native/public OAuth 2.0 app with Authorization Code + PKCE, callback `velora://auth/x`, and `users.read`. Set backend `X_CLIENT_ID` and `X_REDIRECT_URI`. No secret is placed in Android.
- VK: configure the VK ID application/package/fingerprints. Set backend `VK_CLIENT_ID` and `VK_REDIRECT_URI`; set backend-only `VK_CLIENT_SECRET` only if the configured browser exchange requires it. Build Android with `VK_CLIENT_ID` plus the official SDK's Android mobile key in `VK_ANDROID_CLIENT_SECRET`; never reuse the backend secret.
- Production phone: implement `ProductionSmsSender` for the selected vendor, provide its server-only values through the deployment secret store, and only then set `SMS_PROVIDER_CONFIGURED=true`.
- Instagram needs no application credential in Stage 9.

`OTP_SECRET`, `OAUTH_STATE_SECRET`, `DEV_PROVIDER_SECRET`, JWT secrets and all provider/server credentials belong only in `.env`/deployment secrets. `.env.example` contains placeholders. Release Android receives no database, JWT, OTP, SMS, X or backend VK secret.

## Verification coverage

Backend tests cover stable Google/VK identity sessions, E.164 OTP request/verify, wrong/expired/reused/cooldown/rate-limit behavior, link/conflict/unlink/final-provider safety, locale, GPS/manual location, Instagram links, server radius filtering and coordinate non-disclosure.

Android JVM tests cover RU/EN resources, persistent manual locale selection and granted/denied/unavailable location resolution. Compose tests cover provider availability, Connected Accounts link/unlink affordances, language selection and the pre-existing registration-to-chat product path.

Provider integrations are compiled and testable without live credentials through signed development assertions that are rejected when `NODE_ENV=production`. They do not make real third-party calls in automated tests.
