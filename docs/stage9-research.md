# Stage 9 research

## Existing beta

- Android is a single `:app` module with package/application ID `app.velora`, Kotlin 2.1, Compose Material 3, Navigation Compose, Hilt, Retrofit/Kotlin Serialization, Coil and DataStore.
- Navigation is a typed-enum route graph in `MainActivity.kt`. Session phase (`Checking`, `SignedOut`, `NeedsOnboarding`, `Ready`) is owned by `MainViewModel` and determines auth/onboarding/feed routing.
- The client already separates `domain`, `data`, `presentation`, `di` and UI packages. Stage 9 extends these boundaries without splitting the working beta into additional Gradle modules.
- Access and refresh tokens are Velora tokens. They are encrypted with AES-GCM using a non-exportable Android Keystore key before DataStore persistence. OkHttp performs serialized refresh rotation.
- Backend is TypeScript/NestJS over PostgreSQL 17 and MinIO. The current beta has five transactional SQL migrations, UUID user IDs, bcrypt password login, short-lived JWT access tokens and hashed rotating refresh tokens.
- Profiles currently contain city and legacy private latitude/longitude fields. Discovery calculates distance server-side and exposes only a rounded distance; no existing public endpoint returns coordinates.
- Existing users are keyed by `users.id`. Migration 006 preserves those IDs and creates an `email` identity for every existing account instead of replacing or duplicating users.

## Stage 9 decisions

- `auth_identities` is the provider-neutral login model. `(provider, provider_subject)` is globally unique and `(user_id, provider)` prevents accidental duplicate links. Provider email is metadata and is never used for automatic account merging.
- Existing email/password login remains supported as provider `email`, including the development demo account.
- Provider credentials establish identity only. Every successful Google/Phone/X/VK flow ends by issuing the existing Velora access/refresh pair.
- `profile_locations` is private server data. GPS coordinates are quantized before storage; manual locations may omit coordinates. Public profile/discovery DTOs expose city/country and a rounded distance only.
- Locale selection uses AndroidX AppCompat per-app locales (`LocaleManagerCompat`/`AppCompatDelegate`) so system, `ru`, and `en` choices persist through the official Android mechanism. Backend preference is optional account metadata, not a second client locale engine.
- Foreground location uses `FusedLocationProviderClient.getCurrentLocation`. Coarse permission is sufficient; no background permission or continuous tracking is introduced.
- Google uses Credential Manager and an ID token verified by the backend. X uses Authorization Code with PKCE in the system browser. VK is isolated behind a provider adapter for the current official VK ID SDK. Provider buttons are driven by backend/config availability.
- Phone OTP codes are E.164-only, short-lived, one-time, peppered before storage, attempt-limited, cooldown-protected, and never logged in production. Local/test delivery uses `DevSmsSender`; production delivery remains an injected boundary.
- Instagram is a user-controlled social-profile link, not a login identity.

## Compatibility and migration risk

- Migration 006 is additive apart from allowing nullable email/password fields for provider-only users. Existing values, user UUIDs, profiles, likes, matches, conversations and refresh tokens remain untouched.
- Legacy profile coordinates are copied into the new private location table. Discovery reads the new table after migration.
- A user can never unlink the final identity. A provider identity already linked to another user returns an explicit conflict and is never merged by email or display name.
- All provider secrets remain server-side environment values. Android build configuration contains only public client IDs/redirect metadata where a provider requires it.

## Primary references checked

- Android Sign in with Google via Credential Manager: https://developer.android.com/identity/sign-in/credential-manager-siwg
- Android foreground and approximate location: https://developer.android.com/develop/sensors-and-location/location/permissions
- Fused current-location API: https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
- X OAuth 2.0 Authorization Code with PKCE: https://docs.x.com/fundamentals/authentication/oauth-2-0/authorization-code
- Official VK ID Android SDK (OAuth 2.1): https://github.com/VKCOM/vkid-android-sdk
