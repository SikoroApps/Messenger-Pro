# Database design (contract, not deployed)

No hosted database or applied migration exists yet. This document records the schema contract
that a deployed adapter must satisfy; the in-memory repositories in `android/backend/` implement
the same semantics for CI.

| Table | Columns | Client access |
| --- | --- | --- |
| `phone_identities` | `id uuid pk`, `phone_e164 text unique`, `created_at`, `updated_at` | none (service role only) |
| `accounts` | `id uuid pk`, `phone_identity_id uuid fk`, `state text`, `created_at` | owner-scoped `SELECT` only |
| `otp_requests` | `id uuid pk`, `phone_e164 text`, `verifier text`, `salt text`, `expires_at`, `attempts int`, `consumed_at`, `created_at` | none |
| `sessions` | `id uuid pk`, `account_id uuid fk`, `refresh_hash text unique`, `expires_at`, `revoked_at`, `created_at` | owner-scoped `SELECT` only |
| `rate_limit_events` | `id bigserial pk`, `scope text`, `subject_hash text`, `occurred_at` | none |
| `sms_delivery_events` | `id uuid pk`, `otp_request_id uuid fk`, `provider text`, `provider_message_id text`, `status text`, `occurred_at` | none |

## Mandatory invariants

- Only a keyed verifier and per-request salt are stored. Plaintext OTPs and provider responses
  are never persisted or logged.
- `consume(id)` is a single atomic conditional update: it succeeds at most once per request.
- Refresh token hashes are unique; rotation atomically records the old hash as consumed before
  the replacement session is created. Reuse revokes all sessions for the account.
- Rate-limit subjects are keyed hashes of phone/IP, never raw personal data.
- RLS is enabled on every table. OTP, rate-limit and delivery tables have no client policy.
- All schema changes are reviewed, numbered SQL migrations in `supabase/migrations/`.

None of the above is evidenced as deployed. See `docs/BUILD_ORDER.md` for the exit gate.
