# Velora

Velora is a native Android dating application with a dark premium visual system and a self-hosted backend. The repository contains a working end-to-end development stack: unified authentication, localized onboarding, privacy-safe location discovery, likes and matches, realtime chat, photo storage, safety controls, search preferences, notifications, and a development Premium plan.

## Stack

- Android: Kotlin, Jetpack Compose, Material 3, Navigation Compose, Coroutines/Flow, ViewModel, Hilt, Retrofit, Kotlin Serialization, Coil, DataStore, Android Keystore, Credential Manager, Fused Location and VK ID SDK.
- Backend: TypeScript, NestJS, PostgreSQL 17, provider-neutral identities, JWT access/rotating refresh tokens, WebSocket, Swagger/OpenAPI.
- Media: MinIO through an S3-compatible storage boundary.
- Development infrastructure: Docker Compose and database migrations applied automatically on backend startup.

## Quick start on Windows

Prerequisites: JDK 17 or newer, Android Studio with Android SDK 35, Docker Desktop with Compose v2, Node.js 24, and `adb` for device tests.

```powershell
.\scripts\setup-dev.ps1 -StartServices
cd backend
npm ci
cd ..
.\gradlew.bat assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on an emulator. The debug build defaults to `http://10.0.2.2:8080/`, which routes an Android Emulator to the host backend.

The development seed login is read from `DEMO_EMAIL` and `DEMO_PASSWORD` in the ignored local `.env`. The Windows setup script generates the password; no usable password is stored in Git.

Swagger UI is available at `http://localhost:8080/docs`; health is at `http://localhost:8080/health`; the MinIO console is at `http://localhost:9001`.

Russian and English are included. Velora follows the device language by default; the user can persistently choose System, Russian, or English in Settings. Email and local development phone OTP work without third-party credentials. Google, X, VK and production SMS are configuration-gated; see [Stage 9 configuration](docs/STAGE9.md).

## macOS/Linux setup

Create `.env` from `.env.example`, replace every `change-me` value, export or configure `ANDROID_HOME`, then run:

```bash
docker compose --env-file .env up -d --build
cd backend && npm ci && cd ..
./gradlew assembleDebug
```

Generate strong development secrets with `openssl rand -hex 48`. Keep PostgreSQL and MinIO values synchronized with `DATABASE_URL`, `MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY` as shown in `.env.example`.

## Verification

The complete Windows verification command is:

```powershell
.\scripts\verify.ps1
```

It builds and tests the backend, runs the API/PostgreSQL/MinIO/WebSocket integration suite, audits npm dependencies, builds Android and its test APK, runs JVM tests, and runs Android lint. With one emulator running:

```powershell
.\scripts\run-ui-tests.ps1
```

Individual commands are documented in [docs/SETUP.md](docs/SETUP.md) and the test matrix is in [docs/QA.md](docs/QA.md).

## Configuration

- Do not commit `.env`, `local.properties`, keystores, or production credentials; they are ignored.
- Override the Android API URL at build time with `-PVELORA_API_BASE_URL=https://api.example.com/` or the `VELORA_API_BASE_URL` environment variable.
- Supply public mobile provider IDs at build time with `GOOGLE_WEB_CLIENT_ID`, `VK_CLIENT_ID`, and the VK ID SDK mobile key `VK_ANDROID_CLIENT_SECRET`. The native X flow is a public PKCE client and has no APK secret; never pass backend `VK_CLIENT_SECRET` to Gradle.
- Cleartext HTTP is allowed only by the debug manifest. Release builds require HTTPS/WSS.
- Change the replaceable product name in `app/src/main/res/values/strings.xml`.
- Development Premium activation and the development password-reset token are disabled when `NODE_ENV=production`.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [API](docs/API.md)
- [Database](docs/DATABASE.md)
- [Setup and operations](docs/SETUP.md)
- [Security](docs/SECURITY.md)
- [QA](docs/QA.md)
- [Roadmap](docs/ROADMAP.md)
- [Stage 9: localization, location and unified authentication](docs/STAGE9.md)
