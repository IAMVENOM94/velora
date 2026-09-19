# Verification and scope вЂ” Velora

Date: 2026-09-19. Status: active development.

| Check | Result |
|---|---|
| `npm run build` | PASS; emitted local entrypoint is `dist/src/main.js` |
| `npm test` | PASS; 18 tests in four Vitest files |
| `gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` | PASS; lint has 28 warnings, no errors |
| JVM tests | PASS: 11 tests, zero failures/errors; forced fresh run completed, 37 tasks executed |
| API/PostgreSQL/MinIO/WebSocket integration tests | NOT AVAILABLE: Docker Engine not running |
| Android device/Compose UI tests | NOT AVAILABLE: no device/emulator attached |
| Dependency audit | FAIL: 12 vulnerable package entries (5 high, 7 moderate) |

## Evidence map

- `MainActivity.kt`, `UserProfileScreen.kt`, `presentation/MainViewModel.kt`: screens and flow orchestration.
- `domain/usecase/`, `data/repository/`: validation, repository integration and cache behavior.
- `backend/src/main.ts`, `platform.ts`: bootstrapping, persistence/auth foundations and API integration.
- `stage9.ts`: provider adapters, account identities, location and locale; production SMS explicitly throws 503.
- `stage10.ts`, `realtime.ts`, `storage.ts`, `image-processing.ts`: later profile/privacy/chat/media work; substantial prior uncommitted work was retained.
- `DevelopmentPushRegistration.kt`: development-token registration only; no production Android FCM registration.
- `backend/test/` and Android `src/test`, `src/androidTest`: existing unit, integration and UI-test definitions.

## Safe changes

Honest portfolio README/status annotations, expanded ignore rules and demo folders, local npm startup path correction. No application features removed. Existing architecture and business logic retained. Source inventory is in `SOURCE_INVENTORY.md`.

## Demo to record

Use synthetic accounts. Show registration, profile editing/photo upload, discovery, reciprocal match and a two-client chat; then offline/reconnect, block/unmatch and Russian/English switch. Label provider/payment/push screens that remain development-only. Record failures as well as successful paths; never display `.env`, tokens or real user profiles.
