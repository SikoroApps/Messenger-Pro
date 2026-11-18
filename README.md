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
Batch 1 (foundation) is COMPLETE with verified CI, unit tests, lint, emulator launch test and APK evidence. Batch 2 remains **IN PROGRESS / BLOCKED**: CI-tested work includes a server-authoritative OTP/session reference core (`android/backend/`), the client identity boundary (phone normalisation, typed auth API that never fakes success, state machine that cannot authenticate on OTP-screen open, encrypted token storage/rotation, metadata-only audit/abuse rules, bounded jittered retry for idempotent transient failures), bearer tokens scoped to allowlisted HTTPS hosts even across redirects, a provider-independent SMS failover policy that refuses ambiguous failures, a localized English/siSwati verification-message template contract, bounded server-side device-session labels, server-side consumed-code replay prevention, and a cross-account ownership guard that models the RLS row check with session-derived callers. Latest green code evidence is CI run 66 `44279051934` on `9de1861a9752bb69da95064ec1133bb8d33b9ade`: 57 Android JVM tests (including manifest-policy and localized-template extraction cases), 48 backend JVM tests (including 5 safe-failover cases, a bounded device-session record test, atomic consumed-code replay tests and 6 cross-account authorization cases), 11 API 35 emulator tests, lint clean, debug APK SHA-256 `569b837dff0d3703f08cf5800c0b39a75b08c5a547020799247ef42853b32bba`. Code-level foundations for batches 3–6 (privacy, messaging, media and account-control domain rules) are implemented and unit-tested but are not integrated, deployed or device-verified. No deployed backend, SMS provider, real `+268` delivered-code authentication, signed release or Play rollout exists. No feature is `COMPLETE`; 55 reconstructed catalogue rows are `TESTED`, and the 600-feature catalogue remains explicitly labelled as a reconstruction because the authoritative list was not found in the repository, its history, GitHub or the web. See [Build order and batch gates](docs/BUILD_ORDER.md) for the exact outstanding exit gates.

## Build
The Android project is under `android/`. CI runs unit tests and assembles a debug APK on GitHub-hosted runners.
