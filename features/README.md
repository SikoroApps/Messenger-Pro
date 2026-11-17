# Feature registry governance

The product registry reserves exactly 600 stable feature IDs: MP-001 through MP-600.

## Rules
- Preserve all existing IDs and their meanings.
- Do not add email registration, email login, or password-based registration/login.
- Every feature must declare dependencies, batch, implementation location, test evidence, and status.
- Allowed statuses: NOT_STARTED, IN_PROGRESS, BLOCKED, COMPLETE, DEFERRED.
- COMPLETE requires implementation plus passing automated tests and any applicable runtime/device evidence.
- Keep dependency edges acyclic. A feature cannot start until all prerequisites are COMPLETE.
- The full 600-item catalogue is maintained as a separate reviewed artifact; do not fabricate completion states to fill the count.
