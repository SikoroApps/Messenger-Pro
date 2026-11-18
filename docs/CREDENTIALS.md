# Credential availability and isolation evidence

## Runtime accessibility

Workflow: `.github/workflows/credentials-probe.yml`
Verified run: `44336159672` (https://github.com/SikoroApps/Messenger-Pro/actions/runs/44336159672), success on commit `b5d54f9`.

The probe reads organization secrets only through the workflow environment. It never prints,
uploads or persists a value, provider payload, OTP, verification id or API key. It confirmed:

- All eight expected organization secrets are visible to Messenger-Pro workflows:
  `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_URL`,
  `SUPABASE_SERVICE_ROLE_KEY` and `ESMS_API_KEY`.
- The base64 keystore decodes, the configured alias exists, and the store/key passwords meet the
  minimum length policy. `keytool` was invoked with environment-backed password inputs.
- Both Supabase keys were distinguished from an invalid control key: a deliberately absent
  PostgREST relation returned HTTP 404 for the configured keys and HTTP 401 for the control. No
  project data was read.
- `ESMS_API_KEY` has the required `esms_test_` sandbox shape. `POST /v1/verify/start` and
  `POST /v1/verify/check` succeeded against the pinned HTTPS provider host using the documented
  fixed test code. This is **test-mode verification only**; no real `+268` SMS was sent or
  charged, and the sandbox code was not printed.

## Isolation rules

- Provider and Supabase service credentials are consumed only by server-side JVM code or CI.
  No secret value is placed in Android `BuildConfig`, resources, tracked files, docs or artifacts.
- The eSMS adapter refuses any key that is not prefixed `esms_test_`, so automated tests and CI
  cannot accidentally use a production wallet.
- Supabase secret/service-role keys are sent only on the `apikey` header to the configured
  HTTPS `*.supabase.co` host. Legacy JWT keys are additionally accepted on bearer authorization.
  Both the REST client and HTTPS transport reject other hosts and redirects.
- The SQL migration enables RLS on every new table and grants no `anon`/`authenticated` access.
  Sensitive OTP, delivery, rate-limit and audit tables are service-role only.

## Restriction status

The authenticated GitHub CLI account is an active organization admin, but its token lacks the
`admin:org` scope required by `GET /orgs/SikoroApps/actions/secrets`. Therefore the secrets'
selected-repository metadata cannot be inspected or changed with the current token. Runtime
accessibility to Messenger-Pro is proven; restricting the secrets to Messenger-Pro remains a
one-time user action:

```sh
gh auth refresh -h github.com -s admin:org
```

After that refresh, organization secret visibility can be set to `selected` with Messenger-Pro
as the only repository. No secret value needs to be revealed.
