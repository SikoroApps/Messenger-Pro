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
Batch 1 (foundation) is COMPLETE with verified CI, unit tests, lint, emulator launch test and APK evidence on commit `e68344b5f76e08f7f18d8cea176cc62b785b8a8c`. Batch 2 (identity, backend and core security) is IN PROGRESS at the client boundary: phone normalization, a typed auth API contract that never fakes success, a state machine that cannot authenticate on OTP-screen open, encrypted token storage/rotation, redaction and Compose identity screens are implemented and CI-tested. Latest green evidence is CI run 19 on `daaf9431d21a10dc9cb53fff522500a036e3e63b`: 21 JVM tests, 5 emulator tests, lint and debug APK (SHA-256 `9cc3a4f937c64679c6824c87de3baa7e3b3286c91f4cef73f898ace7015e66d7`). No backend, SMS integration or real delivered-code authentication exists yet; 19 client features are `TESTED` and none is `COMPLETE`. The authoritative 600-feature catalogue was not found; the published catalogue is an explicitly labelled reconstruction awaiting review. No production authentication provider, SMS delivery integration, or device-verified release is claimed until implemented and tested with evidence.

## Build
The Android project is under `android/`. CI runs unit tests and assembles a debug APK on GitHub-hosted runners.
