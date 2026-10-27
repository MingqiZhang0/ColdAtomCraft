# M0-R2 evidence index

This directory contains the retained M0-R2 verification evidence. See
`docs/M0_R2_AUDIT.md` for interpretation and `docs/TEST_PLAN.md` for the
PASS/FAIL/BLOCKED/NOT RUN classification.

## Contents

| Path | Contents |
| --- | --- |
| `logs/build-final.log` | Final no-build-cache Gradle build, 18/18 tasks executed, `BUILD SUCCESSFUL` |
| `logs/build-attempt-*.log` | Failed build-path attempts retained for review; not final results |
| `logs/probe-package-info-write-fail.log` | Standalone JavaCompiler investigation lead, not a NeoForge defect proof |
| `logs/client.log`, `logs/client-reenter.log` | Development client, world creation, exit and re-entry |
| `logs/server-first-boot-forced.log` | First dedicated boot; preliminary forced teardown for RCON setup, not a graceful-stop claim |
| `logs/server-run1.log`, `logs/server-run2.log` | Two restart/graceful-RCON-stop cycles |
| `logs/mdk-control.log` | Pristine official MDK control project, same baseline |
| `logs/python-tests.log`, `logs/smoke.log`, `logs/asset-validator.log` | Offline checks |
| `logs/static-scan.txt` | Core-boundary, leakage and JAR checks |
| `junit/` | Raw JUnit XML for all 49 passing tests |
| `artifacts/` | Final mod JAR, JAR listing and generated mod metadata |
| `screenshots/` | Cropped client UI/world evidence |
| `environment.txt` | Host/toolchain/version facts |
| `file-diff.csv` | Machine-readable R1→R2 path/status/SHA-256 diff |

## Not in this directory

- `build/`, `run/`, Gradle caches and local proxy configuration are intentionally excluded.
- The RCON test password was supplied at runtime to the local test server only; it is not stored here.
- No EULA acceptance was written.
- No owner Blender asset or physical model exists.
