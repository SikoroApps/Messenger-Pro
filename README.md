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
Batch 1 (foundation) is COMPLETE with verified CI, unit tests, lint, emulator launch test and APK evidence. Batch 2 remains **IN PROGRESS / BLOCKED**: CI-tested work includes a server-authoritative OTP/session reference core (`android/backend/`), the client identity boundary (phone normalisation, typed auth API that never fakes success, state machine that cannot authenticate on OTP-screen open, encrypted token storage/rotation, metadata-only audit/abuse rules, bounded jittered retry for idempotent transient failures), bearer tokens scoped to allowlisted HTTPS hosts even across redirects, and a provider-independent SMS failover policy that refuses ambiguous failures, a localized English/siSwati verification-message template contract, and bounded server-side device-session labels. Latest green code evidence is CI run 60 `44272939936` on `dc98268b7213d83aa8f83c831e953224c3ea5923`: 57 Android JVM tests (including manifest-policy and localized-template extraction cases), 40 backend JVM tests (including 5 safe-failover cases and a bounded device-session record test), 11 API 35 emulator tests, lint clean, debug APK SHA-256 `02f0d1e1e8f50e08a8a2e8578e71dd662a75bd5781a7d86faa2d36f23ea3dc76`. Code-level foundations for batches 3–6 (privacy, messaging, media and account-control domain rules) are implemented and unit-tested but are not integrated, deployed or device-verified. No deployed backend, SMS provider, real `+268` delivered-code authentication, signed release or Play rollout exists. No feature is `COMPLETE`; 53 reconstructed catalogue rows are `TESTED`, and the 600-feature catalogue remains explicitly labelled as a reconstruction because the authoritative list was not found in the repository, its history, GitHub or the web. See [Build order and batch gates](docs/BUILD_ORDER.md) for the exact outstanding exit gates.

## Build
The Android project is under `android/`. CI runs unit tests and assembles a debug APK on GitHub-hosted runners.
