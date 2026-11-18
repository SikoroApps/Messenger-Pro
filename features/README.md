# Feature registry governance

The product registry reserves exactly 600 stable feature IDs: MP-001 through MP-600.

## Rules
- Preserve all existing IDs and their meanings.
- Do not add email registration, email login, or password-based registration/login.
- Every feature must declare dependencies, batch, implementation location, test evidence, and status.
- Allowed statuses: NOT_STARTED, IN_PROGRESS, BLOCKED, IMPLEMENTED, TESTED, COMPLETE. Only COMPLETE counts as finished.
- COMPLETE requires implementation plus passing automated tests and any applicable runtime/device evidence.
- Keep dependency edges acyclic. A feature cannot start until all prerequisites are COMPLETE.
- The authoritative 600-item catalogue was not found in the repository or its history. `feature-catalogue.json` and `feature-catalogue.md` are a **RECONSTRUCTED — NOT ORIGINAL** proposal for review: `MP-001` and `MP-600` are preserved as evidenced range endpoints, all other IDs, names and descriptions are reconstructed. Every entry started at NOT_STARTED; 46 independently verifiable client-boundary, reference-core, SMS-rule and loopback-HTTP entries have since been advanced to TESTED with GitHub Actions evidence, not COMPLETE.
- Do not fabricate completion states to fill the count, and do not treat the reconstruction as the recovered original.
- The reconstructed catalogue contains a linear dependency chain through backend and SMS features that are blocked on credentials. Pure client work is recorded as TESTED where it has real CI evidence, but no entry may become COMPLETE until its full prerequisite chain and the Batch 2 exit gate are satisfied.
- `android/backend/` contains a CI-tested, server-authoritative *reference core* for the identity rules (hashed verifiers, expiry, single-use consume, cooldown/rate limits, session rotation) plus a loopback-only HTTP adapter for the JSON contract and metadata-only SMS receipt rules. These raise affected rows to `TESTED` only: no hosted backend, SMS delivery or runtime authorization evidence exists, so no row is `COMPLETE`. See `android/backend/README.md` and `docs/TESTING.md`.
- CI run 35 (`cda3bc0`) evidences 44 app JVM tests, 28 backend JVM tests and 9 emulator tests; no evidence in that run supports deployment, real SMS delivery or OS SMS-retriever completion.
