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
```

## Common problems

- `10.0.2.2` works only from Android Emulator. Use a reachable host address for hardware.
- If MinIO reports a signature mismatch after credentials were changed, recreate the MinIO and backend containers with the same root `.env`: `docker compose --env-file .env up -d --force-recreate minio backend`.
- A Windows virus scanner can briefly lock Android lint cache. Stop Gradle daemons and rerun lint serially: `.\gradlew.bat --stop`, then `.\gradlew.bat lintDebug --no-daemon --max-workers=1`.
- `docker compose down` preserves named data; `docker compose down -v` permanently deletes development database and media volumes.

## Production checklist

Use managed PostgreSQL/S3-compatible storage, HTTPS ingress, strong external secrets, restricted CORS, rate limiting/WAF, structured logs and alerting, backups, a real push provider, email/SMS recovery delivery, store signing, privacy/legal review, and a real billing provider. The development Premium/reset endpoints automatically disappear in production mode.
