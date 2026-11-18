# Security baseline

- Never commit API secrets, service-role keys, signing keystores, passwords, OTPs, private keys, or production user data.
- Keep public configuration separate from secrets. CI secrets are injected only at job runtime.
- Enforce authorization on the server for every user-owned object; client-side checks are not security boundaries.
  Derive the caller from a verified session, check ownership before looking up a row, and return
  one constant denial so an attacker cannot enumerate account ids. `AuthorizedAccountAccess` is a
  testable reference model only; deployed RLS must enforce the same rule in SQL.
- Apply per-number, per-IP, and global OTP request limits; use resend cooldowns and abuse monitoring.
- OTPs are single-use, expire quickly, are not logged, and are stored server-side as a keyed hash/verifier.
- Avoid account enumeration: authentication responses should not reveal whether a phone number is registered.
- Normalize phone numbers to E.164 before identity operations.
- Do not log phone numbers, message bodies, access tokens, or OTP values. Redact diagnostics.
- Use TLS for all remote calls. Reject cleartext production traffic.
- Retry only transient failures of idempotent work, with full jitter over a bounded exponential
  window, a hard attempt cap and a total-call budget (`RetryPolicy`/`RetryBudget`), so retries
  cannot amplify abuse or duplicate SMS requests.
- Attach bearer tokens only to allowlisted HTTPS hosts. Strip any pre-existing `Authorization`
  header and re-check the destination on redirects (the OkHttp interceptor is registered as a
  network interceptor), so a redirect cannot leak the token to another host.
- If a device label is stored for later session controls, sanitize it, cap its length and never
  log it. Server-side authorization and RLS remain required before any device-control workflow.
- Android permissions must be least-privilege and requested just in time.
- Use SMS Retriever / SMS User Consent for OTP autofill; do not use broad SMS-reading permissions.
- Keep verification messages to one isolated six-digit code and restrict parsing to locale-specific
  verification keywords with letter boundaries, so ordinary numbers in a message cannot become a
  code. Localized copy still needs human review before release.
- Add dependency scanning and review third-party SDK data collection before release.
- Rotate any secret suspected to have entered logs, commits, or client binaries.
- Release signing keys are stored as protected GitHub Actions secrets, never in repository files.

## Batch 2 planned controls (NOT IMPLEMENTED)
`docs/BATCH_2_PLAN.md` extends this baseline with: salted server-side code hashes (never plaintext), atomic single-use consumption before session issue, 5-attempt and 60-second resend limits, 5-minute expiry, per-phone/per-IP/global quotas, uniform auth error responses, rotating refresh tokens with reuse detection, RLS on all tables with no client policy on OTP/session/rate-limit tables, and redacted diagnostics. Sandbox or mocked-provider results never count as real SMS delivery; affected features stay `TESTED` or `BLOCKED` until a real `+268` end-to-end test is evidenced. Phone + OTP remains the only authentication foundation; SIM-based autofill is never treated as proof of ownership.
