# Setup and operations

## Requirements

- Windows 10/11, macOS, or Linux.
- JDK 17+ (`java -version`). The Gradle wrapper is included.
- Android Studio and Android SDK Platform 35; an Android 8.0/API 26+ device or emulator.
- Docker Engine/Desktop and Docker Compose v2.
- Node.js 24 and npm for local backend tests.

## First run

On Windows, the setup script creates random local credentials, detects the normal Android SDK location, and can start infrastructure:

```powershell
.\scripts\setup-dev.ps1 -StartServices
cd backend
npm ci
cd ..
```

On other systems, copy `.env.example` to `.env`, replace every placeholder, and set `sdk.dir` in untracked `local.properties` if `ANDROID_HOME` is unavailable.

Start or rebuild services only from the repository root so Compose always uses the canonical root `.env`:

```powershell
docker compose --env-file .env up -d --build
docker compose ps
Invoke-RestMethod http://localhost:8080/health
```

Migrations and three development profiles are inserted automatically on the first database startup. The login for the primary profile comes from `DEMO_EMAIL` and `DEMO_PASSWORD` in the ignored `.env`; `setup-dev.ps1` generates a random password. Named volumes retain PostgreSQL and MinIO data between restarts.

## Android

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

API URL resolution order is Gradle property, environment variable, then emulator default:

```powershell
.\gradlew.bat assembleDebug -PVELORA_API_BASE_URL=http://192.168.1.10:8080/
```

Use the host LAN address for a physical device and permit it through the local firewall. Release builds reject cleartext traffic, so production requires HTTPS/WSS.

For a USB-connected development phone, ADB reverse avoids changing the host firewall:

```powershell
adb -s <serial> reverse tcp:8080 tcp:8080
.\gradlew.bat assembleDebug -PVELORA_API_BASE_URL=http://127.0.0.1:8080/
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
```

## Stage 9 provider configuration

Providers stay hidden or visibly development-disabled until both server and required Android configuration are present. Keep backend values in ignored `.env`; pass mobile values as Gradle properties or exported environment variables.

| Provider | Backend `.env` | Android build | External console setup |
| --- | --- | --- | --- |
| Google | `GOOGLE_CLIENT_ID=<web OAuth client ID>` | `GOOGLE_WEB_CLIENT_ID=<same web client ID>` | OAuth consent screen; web OAuth client; Android OAuth client for package `app.velora` and the signing certificate SHA fingerprint. No client secret is used by the ID-token flow. |
| Phone | `SMS_PROVIDER_CONFIGURED=true` only after an adapter is installed | None | Implement/inject `ProductionSmsSender`, then provide that chosen SMS vendor's server-only credentials. Development returns a test code and sends no SMS. |
| X | `X_CLIENT_ID`, `X_REDIRECT_URI=velora://auth/x` | None | X developer app with OAuth 2.0 Authorization Code + PKCE, native/public client, callback `velora://auth/x`, and `users.read` scope. No APK client secret. |
| VK | `VK_CLIENT_ID`, `VK_REDIRECT_URI`; `VK_CLIENT_SECRET` only for the server browser exchange if the provider application requires it | `VK_CLIENT_ID`, `VK_ANDROID_CLIENT_SECRET` | VK ID application configured for package `app.velora`, its signing certificate fingerprints and SDK redirect scheme. The Android SDK mobile key is not the backend secret. |
| Instagram | None | None | No login credential. The user supplies a public username; Velora normalizes the link and never requests a password. |

Example configured Google debug build:

```powershell
$env:GOOGLE_WEB_CLIENT_ID='<web-client-id>.apps.googleusercontent.com'
.\gradlew.bat assembleDebug
```

Restart/rebuild the backend after changing `.env`. Rebuild Android after changing a Gradle/provider value. Details and security boundaries are in [STAGE9.md](STAGE9.md).

`MINIO_ENDPOINT` is the backend's internal S3 address. `MINIO_PUBLIC_ENDPOINT` is embedded into presigned upload URLs and must be reachable by the uploading client (for example an HTTPS storage hostname in production). Android currently uses the authenticated direct-upload route, so the default host-only MinIO URL is sufficient for emulator development.

## Backend without Docker build

Keep PostgreSQL and MinIO running through Compose, then:

```powershell
cd backend
npm ci
npm run start:dev
```

The command reads `../.env`. Never create a second `backend/.env`; this avoids credential drift with Compose.

## Tests

```powershell
.\scripts\verify.ps1
.\scripts\run-ui-tests.ps1 -Device emulator-5554
```

For focused work:

```powershell
cd backend
npm run build
npm test
npm run test:integration
cd ..
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug --no-daemon --max-workers=1
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

## Common problems

- `10.0.2.2` works only from Android Emulator. Use a reachable host address for hardware.
- If MinIO reports a signature mismatch after credentials were changed, recreate the MinIO and backend containers with the same root `.env`: `docker compose --env-file .env up -d --force-recreate minio backend`.
- A Windows virus scanner can briefly lock Android lint cache. Stop Gradle daemons and rerun lint serially: `.\gradlew.bat --stop`, then `.\gradlew.bat lintDebug --no-daemon --max-workers=1`.
- `docker compose down` preserves named data; `docker compose down -v` permanently deletes development database and media volumes.

## Production checklist

Use managed PostgreSQL/S3-compatible storage, HTTPS ingress, strong external secrets, restricted CORS, rate limiting/WAF, structured logs and alerting, backups, a real push provider, email/SMS recovery delivery, store signing, privacy/legal review, and a real billing provider. The development Premium/reset endpoints automatically disappear in production mode.
