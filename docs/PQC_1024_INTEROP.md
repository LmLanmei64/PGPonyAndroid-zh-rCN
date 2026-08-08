# PGPony PQC ML-KEM-1024 Interop Matrix (4.2.0 §1.1)

The device-side proof for the ML-KEM-1024 + X448 composite work. The JVM
suite (`Composite1024Test`) proves PGPony is self-consistent at 1024;
this runbook is the part it cannot cover: that PGPony's 1024 wire output is
read by an independent implementation, and that PGPony reads theirs.

There are two composite schemes, and they validate differently:

- **LibrePGP (algorithm 8, `ky1024_cv448`)** interops live with **GnuPG
  2.5.x**, both directions. This is the runnable matrix, section 1.
- **IETF (algorithm 36, draft-ietf-openpgp-pqc)** has no live partner right
  now: `sq` implements a divergent draft revision and fails to parse the
  draft's own sample key (§1.3), and GnuPG implements the LibrePGP composite,
  not the IETF one. So algo 36 is validated against the published draft
  vectors offline plus PGPony's own round-trip. Section 2.

PGPony is an Android app, not a command-line tool, so it is the manual node
in every row: you produce an artifact with `gpg` on the desktop and consume
it in PGPony (import cert, share-in or open a file, read the result), and the
reverse. Each cell is one round-trip you run once and tick off.

---

## 0. Prerequisites

Use a recent GnuPG 2.5.x. The 768 work was validated against 2.5.21, and
`ky1024_cv448` encryption was broken in 2.5.9 through 2.5.11, so do not test
on those. Confirm the build first:

```
gpg --version
```

Work in a throwaway keyring so nothing touches your real one:

```
export GNUPGHOME=$(mktemp -d)
gpgconf --kill gpg-agent
```

Make a probe message:

```
printf 'pgpony ml-kem-1024 interop probe' > msg.txt
```

---

## 1. LibrePGP 1024 (`ky1024_cv448`) against GnuPG 2.5.x

Two independent keys are needed, because each direction requires the secret
to live where the decrypt happens:

- **Key G** is generated in GnuPG (gpg holds its secret). It drives the
  "PGPony encrypts, gpg decrypts" cell.
- **Key P** is generated in PGPony (PGPony holds its secret). It drives the
  "gpg encrypts, PGPony decrypts" cell.

### 1a. Generate key G in GnuPG

```
gpg --quick-gen-key --batch --passphrase='' "Interop G <g@example.com>" Ed448 cert 1y
```

Take the primary key fingerprint from the output as `$G`, then add the
composite encryption subkey:

```
gpg --quick-add-key --batch --passphrase='' --pinentry-mode loopback $G ky1024_cv448 encrypt 1y
```

Confirm the subkey is present and reads as `ky1024_cv448`:

```
gpg -K $G
```

Export G's public certificate for PGPony:

```
gpg --export --armor $G > G.cert
```

### 1b. Generate key P in PGPony

In PGPony: Keyring, generate a key, choose **ML-KEM-1024+X448 (LibrePGP)**.
Then export its public key (Key detail, share or export) to the desktop as
`P.cert`, and import G:

Import `G.cert` into PGPony (Keyring, import), and import P into gpg:

```
gpg --import P.cert
```

Read back the fingerprint gpg assigned to P's composite subkey from:

```
gpg -k
```

Call P's composite subkey key id `$P`.

### 1c. The matrix

**A. gpg encrypts, PGPony decrypts.** Force the composite subkey with the
trailing `!` so gpg does not fall back to a classical subkey:

```
gpg --encrypt --yes -r "$P!" -o to-pgpony.gpg msg.txt
```

In PGPony: Decrypt, File, open `to-pgpony.gpg` with key P. Expect the probe
text. (File decrypt is the path issue #33 fixed; keep the test message small,
a large composite file can still exceed memory until the 4.2.0 streaming
fix lands.)

**B. PGPony encrypts, gpg decrypts.** In PGPony: encrypt `msg.txt`'s text (or
a small file) to the imported G cert, save the armored output as
`from-pgpony.asc`. Then:

```
gpg --decrypt --passphrase='' --pinentry-mode loopback from-pgpony.asc
```

Expect the probe text on stdout.

**C. Cert import both ways.** Already exercised above: gpg imported P
(`gpg --import P.cert` succeeded and `gpg -k` shows `ky1024_cv448`), and
PGPony imported G and labeled it ML-KEM-1024 (LibrePGP) in the keyring. Tick
both only if each side shows the key with the right algorithm, not just
"imported".

### 1d. Record

| Round-trip | Result |
|---|---|
| A. gpg encrypt to P -> PGPony decrypt |  |
| B. PGPony encrypt to G -> gpg decrypt |  |
| C1. PGPony imports G, labels ML-KEM-1024 (LibrePGP) |  |
| C2. gpg imports P, shows ky1024_cv448 |  |

A green A and B is the real proof that PGPony's Kyber-1024 + X448 KMAC256
combiner, PKESK and v5 key material are byte-correct against an independent
implementation, the same bar the 768 work cleared against 2.5.21.

---

## 2. IETF 1024 (algorithm 36)

No live partner is available: `sq 1.4.0-pqc.1` is on a divergent draft
revision (§1.3) and GnuPG does not implement the IETF composite. So algo 36
is held to two checks:

1. **Self round-trip**, already automated in `Composite1024Test` (generate a
   v6 1024 key, encrypt, decrypt both buffered and streamed). Re-run:

```
./gradlew :app:testFossDebugUnitTest --tests "com.pgpony.android.crypto.pqc.Composite1024Test"
```

2. **Draft vectors.** When the draft publishes ML-KEM-1024 + X448 test
   vectors for the KEM combiner and PKESK, add them alongside the algo-35
   vectors and assert PGPony reproduces the KEK and packet bytes. Until then,
   algo 36 rests on the shared-combiner argument: the SHA3-256 construction is
   identical to the sq-validated algo-35 path except the algorithm-id octet,
   the X448 curve and the ML-KEM-1024 parameter set, all carried by the suite.

Re-check `sq` on each release: if a build lands that parses the draft's own
Appendix-A sample key, run PGPony's algo-36 artifact through `sq decrypt` and
a reverse `sq` message, exactly as the algo-35 path is checked.

---

## 3. Out of scope here

Importing a composite **secret** key into GnuPG is the §1.2 wall (gpg 2.5.x
cannot import a v5 algo-8 secret from any app), tracked separately. This
matrix moves **messages and public certificates** only, which is what §1.1
commits to. When you finish a pass, record the gpg version tested next to the
result so the FAQ carries a version, not folklore.
