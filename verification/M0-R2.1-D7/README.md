# D7 verification and provenance

Source: `ColdAtomCraft-M0-R2.1-D6-DirectorCheckpoint.zip`

SHA-256 of D6 input ZIP:
`ee32c94ac0093cf170e7e32edddfc64196b8e86be03c4e2234803bfb2526a122`

D6 input manifest: **267 / 267** entries matched on extraction. No input
file was silently replaced with a guessed source. D7 changes are additive
apart from documented validation-script and doc updates.

## Tests executed on the local Linux JDK 21 host

- `sh verification/M0-R2.1-D7/run_d7_offline.sh`:
  previously existing smoke/asset checks, full reflection **NON-JUNIT** sweep;
  D7 19-check optical path smoke; new 29 scalar Decimal80 oracle.
- `javac --release 21 -Xlint:all` on pure production modules.
- `python tools/verify_m0.py` checks for true JDK25 and is expected to
  stop with `BLOCKED` on this host, without inventing a Gradle result.

**The JUnit test sources are real JUnit 5 code, but this environment has
not executed them under the JUnit Platform.** Do not count the older D6
client/server logs as D7 runtime evidence.

## Remaining verification

On a Windows or Linux JDK25 build host with dependencies and NeoForge:

```sh
python tools/verify_m0.py
```

This executes JDK25 smoke, the D5/D6/D7 independent scientific oracles,
asset tests, clean no-cache Gradle/JUnit, and distributable JAR inspection.
It does **not** validate installed JAR in an independent Minecraft client
or server. That last step requires a separate runtime session and logs.

## Additional notes

`source-diff.csv` / `source-diff.patch` and the SHA-256 manifest are
regenerated at final packaging, after documentation and test changes.
