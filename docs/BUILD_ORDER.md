# Dependency-first build order

This document is authoritative. Do not reorder batches or begin a dependent batch while an earlier batch is open.

## Operating rules
1. Preserve every feature identifier from MP-001 through MP-600. Never renumber an existing ID.
2. Phone number is the only account identifier used for registration and sign-in. Do not add email or password registration/login.
3. Implement foundations before features that depend on them.
4. For each batch: implement -> review security -> run automated tests -> inspect CI evidence -> close defects -> record exit evidence. Only then start the next batch.
5. A registry status is not proof of implementation. Mark COMPLETE only when code, tests, and relevant runtime evidence support it.
6. Device-only behavior must remain IN_PROGRESS until verified on a physical device or a documented emulator with the required capability.
7. GitHub Actions is the build environment. Do not require a local Android SDK build.

## Batch sequence

### Batch 1 — Repository and platform foundation
Scope: project structure, Kotlin/Compose app shell, dependency/version policy, CI workflow, test harness, lint/static checks, secure configuration boundaries, architecture/security docs, feature registry governance.
Exit gate:
- CI can configure the Android project, run unit tests, and assemble a debug APK on a GitHub-hosted runner.
- No credentials or signing keys exist in tracked files.
- App launches to a stable shell; baseline tests pass.
- Architecture, security, and batch-order docs are committed.

### Batch 2 — Identity and phone OTP
Scope: phone-number normalization (E.164), registration/login state machine, OTP request/verify backend contract, rate limits, abuse controls, SMS provider adapter, safe resend/cooldown, OTP autofill integration.
Exit gate:
- Real end-to-end registration and sign-in using a delivered SMS code.
- Invalid, expired, replayed, throttled, and wrong codes are rejected.
- Account/session persistence and logout are tested.
- SMS provider behavior and cost/availability are documented.
- No email or password flows are present.

### Batch 3 — User profile and contact discovery
Scope: minimal profile, permissions, contact matching with privacy protections, block/report primitives.
Exit gate: permission denial and revocation tested; contact data minimized; server-side authorization verified.

### Batch 4 — One-to-one messaging
Scope: conversation creation, message persistence, delivery/read state, pagination, retry/idempotency, realtime updates.
Exit gate: two independent accounts exchange messages end-to-end; offline/reconnect and authorization tests pass.

### Batch 5 — Media and notifications
Scope: attachment upload/download, media validation, push notifications, background handling.
Exit gate: storage policies, upload limits, notification permission paths, and failure recovery tested.

### Batch 6 — Safety, privacy, and account controls
Scope: privacy settings, blocking/reporting workflows, session/device controls, account deletion/export, optional app lock.
Exit gate: security review and negative authorization tests pass; device-dependent features have device evidence.

### Batch 7 — Hardening and release
Scope: performance, accessibility, localization, crash reporting policy, release signing, staged rollout readiness.
Exit gate: signed release artifact verified, checksum published, smoke tests completed, known limitations documented.

## Batch status record
A batch is not complete merely because files or registry rows exist. Record commit SHA, CI run result, test names/results, and any device evidence before advancing.
