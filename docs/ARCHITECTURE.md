# Architecture

## Initial structure
- `android/`: native Android client (Kotlin, Jetpack Compose).
- `backend/`: backend contracts and migrations will be added in the identity batch after the provider and hosting design are validated.
- `docs/`: decisions, batch gates, security and operational guidance.
- `.github/workflows/`: hosted CI and release automation.

## Client principles
- UI state is driven by ViewModels and immutable UI state.
- Network and persistence concerns sit behind interfaces/repositories.
- Authentication tokens are stored using Android Keystore-backed encrypted storage; never in plain preferences or logs.
- OTP entry supports OS-assisted autofill but remains usable when autofill is unavailable.
- Do not request contacts, SMS, phone-state, or notification permissions before the corresponding user-facing feature needs them.

## Identity contract
The account identifier is a normalized E.164 phone number. OTP issuance and verification must be server-authoritative. The client must not generate, validate, or accept an OTP as proof of identity. OTPs must be short-lived, single-use, rate-limited, and stored only as a secure verifier server-side.

## Backend decision gate
No legacy backend URL, project, API key, or provider secret is assumed. Select and document a fresh backend and SMS delivery arrangement before Batch 2. A public client key may be shipped only when the backend's authorization model makes it safe; service-role credentials must remain server-side.
