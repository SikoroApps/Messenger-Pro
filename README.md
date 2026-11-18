# Messenger Pro

A phone-number-first Android messenger built with Kotlin and Jetpack Compose.

## Product rules
- Registration and sign-in use a phone number and one-time verification code only.
- Email registration, email login, and password-based account creation are out of scope.
- SMS delivery is the authoritative fallback. Android may offer OTP auto-fill through SMS Retriever or SMS User Consent; the app must never claim silent SIM ownership verification.
- Build and release through GitHub Actions. No local Android SDK build is required.
- Secrets and signing material must never be committed to this public repository.

## Engineering approach
Work is dependency-first. Each batch must pass its documented exit criteria before dependent work starts. The feature registry preserves IDs MP-001 through MP-600; IDs are stable identifiers, not a reason to ship unimplemented features.

See:
- [Build order and batch gates](docs/BUILD_ORDER.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Security baseline](docs/SECURITY.md)
- [Batch 2 plan](docs/BATCH_2_PLAN.md)
- [Feature registry policy](features/README.md)
- [Reconstructed feature catalogue (NOT ORIGINAL)](features/feature-catalogue.md)

## Current status
Batch 1 (foundation) is COMPLETE with verified CI, unit tests, lint, emulator launch test and APK evidence. Batch 2 remains **IN PROGRESS / BLOCKED**: CI-tested work includes a server-authoritative OTP/session reference core (`android/backend/`), the client identity boundary, host-scoped bearer tokens, safe SMS failover, localized OTP templates, bounded device-session labels, replay/cross-session/cross-account guards and metadata-only abuse logging. Organization secrets are now proven available to Messenger-Pro workflows by credential probe run `44336159672`; the eSMS sandbox Verify adapter, provider-managed OTP state machine, Supabase REST adapters and deny-by-default RLS migration are CI-tested. Latest green code evidence is CI run `44337788301` on `df30bffe8feb13db06ed22e307feddd62b81e01a`: 57 Android JVM tests, 67 backend JVM tests (including eSMS, provider-managed OTP and Supabase adapter cases), API 35 emulator smoke job green, lint clean, debug APK SHA-256 `9af9007adb5d6983649ac3c9a9c4a442beab1d174a08100ae64cba4a8a5936ab`. Code-level foundations for batches 3–6 are implemented and unit-tested but are not integrated, deployed or device-verified. No hosted backend/RLS, real `+268` delivered-code authentication, signed release or Play rollout exists; eSMS evidence is sandbox-only. No feature is `COMPLETE`; 61 reconstructed catalogue rows are `TESTED`, and the 600-feature catalogue remains explicitly labelled as a reconstruction because the authoritative list was not found. See [Build order and batch gates](docs/BUILD_ORDER.md) and [credential evidence](docs/CREDENTIALS.md) for the exact outstanding gates.

## Build
The Android project is under `android/`. CI runs unit tests and assembles a debug APK on GitHub-hosted runners.
