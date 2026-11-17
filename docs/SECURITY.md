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
