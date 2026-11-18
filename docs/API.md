# API contract (phone + OTP only)

The client contract is defined in `android/app/src/main/java/com/sikorokoro44/messengerpro/data/network/AuthApi.kt`
and implemented over HTTPS by `HttpAuthApi.kt`. The server-side rules are implemented by the
reference core in `android/backend/` and are exercised in CI without credentials. A loopback-only
HTTP adapter (`android/backend/.../AuthHttp.kt`) lets the same JSON contract be tested over real
sockets; it is a test harness, not a hosted TLS deployment.

## Public endpoints

| Method | Path | Request | Success | Failure codes |
| --- | --- | --- | --- | --- |
| POST | `/auth/otp/request` | `{ phone_e164, locale, client }` + `Idempotency-Key` header | `{ request_id, expires_in, retry_after }` | `INVALID_PHONE`, `RATE_LIMITED`, `COOLDOWN_ACTIVE`, `PROVIDER_UNAVAILABLE` |
| POST | `/auth/otp/verify` | `{ phone_e164, request_id, code }` | `{ verified, account_state, access_token, refresh_token, expires_in }` | `INVALID_CODE`, `CODE_EXPIRED`, `TOO_MANY_ATTEMPTS`, `CODE_CONSUMED`, `REQUEST_NOT_FOUND` |
| POST | `/auth/session/refresh` | `{ refresh_token }` | rotated token pair | `SESSION_REVOKED` |
| POST | `/auth/session/logout` | `{ refresh_token }` | `{}` | generic failure |

Rules enforced by the reference core:

- The OTP is generated and hashed server-side; the request response never contains a code.
- No account or session exists before a successful verification.
- Verification consumes the request atomically before tokens are issued.
- Refresh rotation marks the old token hash consumed; reuse of a consumed token revokes every
  session for that account.
- Errors use stable codes and constant generic user messages so registration state is not
  discoverable. Details from upstream providers never reach the client.

## Loopback HTTP adapter status mapping

`AuthHttpHandler` maps reference-core failures to stable HTTP responses. Error bodies contain only
an `error` code, a constant generic `message` and an optional `retry_after`; they never contain a
phone number, OTP, token, provider detail or stack trace.

| Failure | Status | Retry-After |
| --- | --- | --- |
| `INVALID_PHONE`, `INVALID_CODE`, `REQUEST_NOT_FOUND` | 400 | no |
| `CODE_EXPIRED`, `CODE_CONSUMED`, `SESSION_REVOKED` | 401 | no |
| `RATE_LIMITED`, `COOLDOWN_ACTIVE`, `TOO_MANY_ATTEMPTS` | 429 | yes |
| `PROVIDER_UNAVAILABLE`, `UNAVAILABLE` | 503 | no |

The client sends one fresh `Idempotency-Key` per `OtpRequest` instance, and the adapter scopes it
to a keyed hash of the submitted number. Reusing the same request object after a transport failure
therefore cannot send a second SMS or read another number's cached result.

## Transport

`TransportPolicy` requires an `https` scheme, a host, and no embedded credentials, query string
or fragment. Cleartext is rejected at construction. Non-default HTTPS ports are permitted so
TLS can be exercised against a local test server; this is not a downgrade of transport security.

`AuthorizationHeaderInterceptor` removes any pre-existing `Authorization` header and attaches the
current access token only when the final request URL is HTTPS and its host is in the configured
allowlist. It is installed as an OkHttp network interceptor so redirects are re-evaluated before
the header is attached. Client tests cover allowlisted attachment, stripping on unrelated hosts,
cleartext rejection and redirect leakage.

Verification responses return a `session_id`, and the verify endpoint accepts an optional
`device_label`. `DeviceLabelPolicy` trims the label, removes control characters and angle brackets,
collapses whitespace and caps it at 64 characters before it is stored on the session record; the
label is preserved when a refresh token rotates. This is reference-core contract evidence only:
no durable RLS table, device list or revocation UI is deployed.

Verification SMS copy is a short, locale-tagged template (`VerificationMessageTemplate`): one
isolated six-digit code, an English (`en`) or siSwati (`ss`) verification phrase, and an optional
trailing retriever hash. `SmsOtpExtractor` accepts locale-specific keyword lists with Unicode-safe
letter boundaries and rejects ambiguous or non-verification messages. This is a client contract;
no delivered message, OS retriever consent flow or human-reviewed siSwati copy is evidenced.

Client-side retry is deliberately narrow: `RetryPolicy` retries only transient failures of
idempotent work, applies full jitter within a bounded exponential window (maximum delay 8 s,
at most 3 attempts), and `RetryBudget` caps total network calls for a sign-in attempt. Unit tests
verify the doubling window, seeded jitter bounds, the transient/idempotent gate and the hard call
ceiling. This is a transport-policy control, not a deployed retry executor or a substitute for
server-side rate limits.

## Not yet deployed

These contracts are implemented in code and unit-tested, including real loopback HTTP socket tests,
but no hosted TLS endpoint exists. Real end-to-end delivered-code registration remains blocked on a
deployed backend and an SMS provider with `+268` delivery. See `docs/BATCH_2_PLAN.md`.
