# Feature registry governance

The product registry reserves exactly 600 stable feature IDs: MP-001 through MP-600.

## Rules
- Preserve all existing IDs and their meanings.
- Do not add email registration, email login, or password-based registration/login.
- Every feature must declare dependencies, batch, implementation location, test evidence, and status.
- Allowed statuses: NOT_STARTED, IN_PROGRESS, BLOCKED, IMPLEMENTED, TESTED, COMPLETE. Only COMPLETE counts as finished.
- COMPLETE requires implementation plus passing automated tests and any applicable runtime/device evidence.
- Keep dependency edges acyclic. A feature cannot start until all prerequisites are COMPLETE.
- The authoritative 600-item catalogue was not found in the repository or its history. `feature-catalogue.json` and `feature-catalogue.md` are a **RECONSTRUCTED — NOT ORIGINAL** proposal for review: `MP-001` and `MP-600` are preserved as evidenced range endpoints, all other IDs, names and descriptions are reconstructed. Every entry starts at NOT_STARTED.
- Do not fabricate completion states to fill the count, and do not treat the reconstruction as the recovered original.
