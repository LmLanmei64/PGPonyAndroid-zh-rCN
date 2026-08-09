# Reproducible builds: the release gate

Written after the 4.1.1 F-Droid failure (issue #28, fdroiddata job
15796256381), and updated with the actual root cause found on 9 August.

## What actually went wrong with 4.1.1

Not the JDK, not macOS versus Linux, not baseline profiles. A dex-level diff
of F-Droid's failed build against the uploaded `PGPony-4.1.1-foss.apk`
showed the two APKs identical everywhere except three methods, and those
three methods are the three composables patched in 4.1.1 (the onboarding
slide, the bundle encryption result sheet, the lock screen). The signed APK
was compiled from a variant of those three files with one extra composable
parameter each. The committed tag `v4.1.1` carries the final form.

**The signed reference APK was not built from the tagged commit.** F-Droid
rebuilt the tag faithfully and correctly reported that the uploaded binary
did not match it.

`RELEASE_VERIFY_4.1.0.md` established "compare clone against clone, never a
working tree", and the 4.1.1 release did run the clean-clone double build.
What it never did was compare the SIGNED UPLOAD against that clean build.
That missing comparison is the whole gate:

**No foss APK gets uploaded until it is proven content-identical to a fresh
clean-clone build of the tag it claims to be.**

## The two moving parts

1. `tools/verify_repro.sh` is the gate, runnable anywhere:

   ```
   tools/verify_repro.sh rebuild v4.1.1 PGPony-4.1.1-foss.apk
   tools/verify_repro.sh compare a.apk b.apk
   tools/verify_repro.sh content-hash some.apk
   ```

   `rebuild` clones the tag twice into isolated build roots (separate
   `GRADLE_USER_HOME`s, `--no-daemon`), proves the two builds are
   content-identical, then proves the candidate APK matches them.
   "Content" means every byte of every ZIP entry, ignoring only the
   signature (`META-INF/*.SF`, `*.RSA`, `*.DSA`, `*.EC`, `MANIFEST.MF`).
   That is the same comparison F-Droid's signature-copy verification
   performs, so a pass here predicts a pass there.

2. `.github/workflows/reproducible.yml` runs the same gate on Linux on
   every `v*` tag push (and on demand with any tag). It rebuilds the tag
   twice per JDK (17 and 21), fails unless the build is deterministic,
   compares against the `PGPony-*-foss.apk` release asset when one exists,
   and uploads the unsigned APK as a run artifact. It exists to catch the
   failure this one turned out not to be: environment drift between the
   Mac and F-Droid's Debian buildserver. If Mac and CI ever disagree,
   the CI artifact is the canonical one, because it is the one built in
   an F-Droid-like environment.

## Release procedure, replacing RELEASE_VERIFY_4.1.0 §4 and §5 step order

1. Commit, tag `vX.Y.Z`, push with tags. The tag push starts the workflow.
2. Produce the signed APK by building inside the gate's clean clone with
   the keystore present, so the signed file IS the clean tag build plus a
   signature, with the ZIP layout untouched:

   ```
   cp keystore.properties <workdir>/srcA/
   cd <workdir>/srcA
   env GRADLE_USER_HOME=<workdir>/gradleA ./gradlew --no-daemon :app:assembleFossRelease
   ```

   Only the package and sign tasks rerun; the dex outputs are the ones the
   gate already verified. The result is signed v2-only with the cert
   F-Droid pins in `AllowedAPKSigningKeys`.

   Do NOT re-sign with apksigner from build-tools 35 or newer. It rewrites
   the ZIP alignment fields (0xd935 extra fields replace Gradle's zero
   padding), so the signed file stops matching a Gradle build byte for
   byte, and F-Droid's apksigcopier-based verification then fails with a
   digest mismatch even when every file inside matches. F-Droid's docs
   confirm: apksigner from build-tools 34 produces verifiable APKs, newer
   versions fail (--alignment-preserved on newer versions also works where
   available). Found the hard way on 9 Aug 2026: the first replacement
   4.1.1 asset was apksigner-signed and would have failed the retry
   despite byte-identical contents.

3. Run the gate against the exact file that will be uploaded:

   ```
   tools/verify_repro.sh rebuild vX.Y.Z PGPony-X.Y.Z-foss.apk
   ```

4. Wait for the workflow legs to pass their determinism check as well.
5. Only after 3 and 4: `gpg --detach-sign --armor`, `shasum -a 256`,
   upload. Then re-run the workflow (workflow_dispatch with the tag) so
   the release-asset comparison runs end to end against what is actually
   published.
6. Publish the content hash the gate prints in the release notes as the
   reproducibility reference. It ignores signatures, so anyone's clean
   rebuild can be checked against it with `content-hash` directly.

There is no fast path. The fast path is what produced issues #4 and #28.

## When the gate fails

- Two clean builds on the same machine differ: real nondeterminism.
  Suspect floating dependency resolution first, then AGP or R8 changes.
- Clean builds match each other but not the candidate: the candidate was
  built from different source or a dirty tree. Rebuild it from a clean
  clone of the tag. This was 4.1.1.
- Mac gate passes but CI differs from Mac: environment drift. Ship the
  CI artifact signed, and pin the divergent input in-tree.
- F-Droid still fails after all green: pull the failing job's artifacts
  (their built APK is under `tmp/`), run `compare` against the release
  asset, and read the diff. Ask linsui which JDK the buildserver used if
  the dex differs; add that JDK to the workflow matrix.
- F-Droid reports a digest mismatch but `compare` says the contents are
  identical: the ZIP layout differs, meaning the release APK was signed
  by a tool that rewrote the archive. See the signing rule in step 2.

## Environment facts, verified 9 August 2026

- AGP 8.13.2, Gradle 8.14.3, Kotlin 2.1.0, build-tools 35.0.0, R8 8.13.19,
  all pinned in-tree at v4.1.1. Release JDK: Temurin 21 on the Mac.
- APK entry timestamps are normalized by AGP (1981-01-01);
  `vcsInfo.include = false` and `dependenciesInfo.includeInApk = false`
  keep environment-specific blobs out of the APK.
- R8 embeds its map hash in every dex (the `~~R8{...}` marker and an
  `r8-map-id-...` string). Two content-identical builds share it; it never
  needs excluding.
- `assets/dexopt/baseline.prof` is derived from the dex. If it differs,
  the dex differs. It has never been an independent problem.
- The F-Droid build of v4.1.1 differed from the upload ONLY in the three
  4.1.1-patched composables plus the derived map-id and profile. Every
  library, resource, and other class matched, which also means JDK 21 on
  macOS and F-Droid's Debian toolchain currently agree on this codebase.
  With signatures stripped, a Gradle-signed Mac clean build of v4.1.1 is
  byte-identical to the F-Droid buildserver's build. Verified 9 Aug 2026.
- The APK contains resource entries whose names differ only by letter
  case (AGP shortened res/ names). Never extract an APK to a macOS
  filesystem to compare or hash it; 12 files silently collapse. The
  script reads the ZIP directly for exactly this reason.
