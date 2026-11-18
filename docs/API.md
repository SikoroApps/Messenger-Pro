# API contract (phone + OTP only)

The client contract is defined in `android/app/src/main/java/com/sikorokoro44/messengerpro/data/network/AuthApi.kt`
and implemented over HTTPS by `HttpAuthApi.kt`. The server-side rules are implemented by the
reference core in `android/backend/` and are exercised in CI without credentials.

## Public endpoints

| Method | Path | Request | Success | Failure codes |
| --- | --- | --- | --- | --- |
| POST | `/auth/otp/request` | `{ phone_e164, locale, client }` | `{ request_id, expires_in, retry_after }` | `INVALID_PHONE`, `RATE_LIMITED`, `COOLDOWN_ACTIVE`, `PROVIDER_UNAVAILABLE` |
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

## Transport

`TransportPolicy` requires an `https` scheme, a host, and no embedded credentials, query string
or fragment. Cleartext is rejected at construction. Non-default HTTPS ports are permitted so
TLS can be exercised against a local test server; this is not a downgrade of transport security.

## Not yet deployed

These contracts are implemented in code and unit-tested, but no hosted endpoint exists. Real
end-to-end delivered-code registration remains blocked on a deployed backend and an SMS provider
with `+268` delivery. See `docs/BATCH_2_PLAN.md`.
