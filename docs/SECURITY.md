# Security baseline

- Never commit API secrets, service-role keys, signing keystores, passwords, OTPs, private keys, or production user data.
- Keep public configuration separate from secrets. CI secrets are injected only at job runtime.
- Enforce authorization on the server for every user-owned object; client-side checks are not security boundaries.
- Apply per-number, per-IP, and global OTP request limits; use resend cooldowns and abuse monitoring.
- OTPs are single-use, expire quickly, are not logged, and are stored server-side as a keyed hash/verifier.
- Avoid account enumeration: authentication responses should not reveal whether a phone number is registered.
- Normalize phone numbers to E.164 before identity operations.
- Do not log phone numbers, message bodies, access tokens, or OTP values. Redact diagnostics.
- Use TLS for all remote calls. Reject cleartext production traffic.
- Android permissions must be least-privilege and requested just in time.
- Use SMS Retriever / SMS User Consent for OTP autofill; do not use broad SMS-reading permissions.
- Add dependency scanning and review third-party SDK data collection before release.
- Rotate any secret suspected to have entered logs, commits, or client binaries.
- Release signing keys are stored as protected GitHub Actions secrets, never in repository files.

## Batch 2 planned controls (NOT IMPLEMENTED)
`docs/BATCH_2_PLAN.md` extends this baseline with: salted server-side code hashes (never plaintext), atomic single-use consumption before session issue, 5-attempt and 60-second resend limits, 5-minute expiry, per-phone/per-IP/global quotas, uniform auth error responses, rotating refresh tokens with reuse detection, RLS on all tables with no client policy on OTP/session/rate-limit tables, and redacted diagnostics. Sandbox or mocked-provider results never count as real SMS delivery; affected features stay `TESTED` or `BLOCKED` until a real `+268` end-to-end test is evidenced. Phone + OTP remains the only authentication foundation; SIM-based autofill is never treated as proof of ownership.
