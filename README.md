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
Batch 1 (foundation) is COMPLETE with verified CI, unit tests, lint, emulator launch test and APK evidence. Batch 2 remains **IN PROGRESS / BLOCKED**: CI-tested work includes a server-authoritative OTP/session reference core (`android/backend/`), the client identity boundary (phone normalisation, typed auth API that never fakes success, state machine that cannot authenticate on OTP-screen open, encrypted token storage/rotation, metadata-only audit/abuse rules, bounded jittered retry for idempotent transient failures), and bearer tokens scoped to allowlisted HTTPS hosts even across redirects. Latest green evidence is CI run 51 on `5679a9be2ae6b9e221abe11bb6d883544e586e2c`: 53 Android JVM tests (including a manifest assertion that cleartext traffic is disabled and no broad SMS permission is requested), 34 backend JVM tests, 11 API 35 emulator tests, lint clean, debug APK SHA-256 `2539d8015ffd08437e4da83945877782c70cda3fe39ad052147194f186da08c0`. Code-level foundations for batches 3–6 (privacy, messaging, media and account-control domain rules) are implemented and unit-tested but are not integrated, deployed or device-verified. No deployed backend, SMS provider, real `+268` delivered-code authentication, signed release or Play rollout exists. No feature is `COMPLETE`; 50 reconstructed catalogue rows are `TESTED`, and the 600-feature catalogue remains explicitly labelled as a reconstruction because the authoritative list was not found in the repository, its history, GitHub or the web. See [Build order and batch gates](docs/BUILD_ORDER.md) for the exact outstanding exit gates.

## Build
The Android project is under `android/`. CI runs unit tests and assembles a debug APK on GitHub-hosted runners.
