# Database design (contract, not deployed)

No hosted database or applied migration exists yet. This document records the schema contract
that a deployed adapter must satisfy; the in-memory repositories in `android/backend/` implement
the same semantics for CI.

The reviewed migration `supabase/migrations/0001_identity_foundation.sql` defines these tables.
It enables RLS on all of them and grants no `anon` or `authenticated` access. The gated workflow
`.github/workflows/supabase-migration.yml` can dry-run, apply and verify that contract after
`SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF` and `SUPABASE_DB_PASSWORD` are configured and the
`DEPLOY-IDENTITY-0001` confirmation is supplied. It has not been dispatched: the deployment
credentials were re-probed in run `44374465003` and are absent.

| Table | Columns | Client access |
| --- | --- | --- |
| `accounts` | `id text pk`, `phone_e164 text unique`, `state text`, `display_name text`, `created_at`, `updated_at` | none (service role only) |
| `otp_requests` | `id text pk`, `phone_e164 text`, `verifier text`, `salt text`, `expires_at`, `created_at`, `attempts int`, `consumed_at`, `failed_at` | none |
| `provider_otp_requests` | `id text pk`, `phone_e164 text`, `verification_id text`, `expires_at`, `created_at`, `attempts int`, `consumed_at`, `failed_at` | none |
| `sessions` | `id text pk`, `account_id text fk`, `refresh_hash text unique`, `expires_at`, `created_at`, `device_label text`, `revoked_at` | none |
| `consumed_refresh_tokens` | `refresh_hash text pk`, `account_id text`, `consumed_at` | none |
| `rate_limit_events` | `id bigserial pk`, `scope text`, `subject_hash text`, `occurred_at` | none |
| `sms_delivery_events` | `id text pk`, `otp_request_id text`, `provider_message_id text`, `state text`, `occurred_at`, `failure_reason text` | none |
| `audit_events` | `id bigserial pk`, `action`, `subject_hash`, `correlation_hash`, `outcome`, `occurred_at` | none |

The `mp_upsert_account` SQL function atomically preserves the original account id on duplicate
verification. `SupabaseRestClient` sends the service key only in the `apikey` header to the
configured HTTPS `*.supabase.co` host and disables redirects.

## Mandatory invariants

- The reference core stores only a keyed verifier and per-request salt. The provider-backed path
  stores only the opaque provider verification id. Plaintext OTPs, sandbox codes and provider
  responses are never persisted or logged.
- `consume(id)` is a single atomic conditional update: it succeeds at most once per request.
  Attempts and terminal failure markers are also persisted so a retried HTTP request cannot reset
  the counter or revive an expired request.
- Refresh token hashes are unique; rotation atomically records the old hash as consumed before
  the replacement session is created. Reuse revokes all sessions for the account.
- Rate-limit subjects are keyed hashes of phone/IP, never raw personal data.
- RLS is enabled on every table. OTP, rate-limit and delivery tables have no client policy.
- Account rows are readable and writable only by their owning account. The reference-core
  `AuthorizedAccountAccess` model re-derives ownership from a live server-side session and checks
  it before the row lookup, so denials do not reveal whether a row exists. A deployed Supabase RLS
  policy must implement the same ownership rule; the Kotlin guard is not a database boundary.
- All schema changes are reviewed, numbered SQL migrations in `supabase/migrations/`.

None of the above is evidenced as deployed. See `docs/BUILD_ORDER.md` for the exit gate.
