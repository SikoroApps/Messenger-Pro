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
No legacy backend URL, project, API key, or provider secret is assumed. Supabase plus a custom eSMS Verify flow is the recorded arrangement; its REST repositories and deny-by-default RLS migration are CI-tested but not deployed. A public client key may be shipped only when the backend's authorization model makes it safe; service-role and eSMS credentials must remain server-side/CI-only. See `docs/CREDENTIALS.md`.

## Batch 2 planned design (NOT IMPLEMENTED)
The evaluated design in `docs/BATCH_2_PLAN.md` is Supabase Postgres with RLS plus Edge Functions for a custom, server-authoritative phone-OTP flow. This is proposed because the preferred `+268` SMS provider (eSMS Africa, `esmsafrica.io`) is not a native Supabase Auth SMS provider; Supabase Auth's built-in phone login supports only MessageBird, Twilio, Vonage and TextLocal. Android will hold only a public key, generate no OTP, and never accept OTP success without a server-issued session. Provider keys and the service-role key remain in Edge Function secrets. An SMS gateway interface with sandbox, eSMS Verify and Lunyazi adapters keeps the OTP state machine provider-independent. Dev and prod projects are separate. All of this is planning only until deployed and verified in GitHub Actions.
