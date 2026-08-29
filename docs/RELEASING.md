# Releasing `full`

Every push to `main` that passes CI builds a signed release of the **`full`** flavour — the
rider-facing app — and publishes it as a GitHub Release. `diag` is a diagnostic harness for the
reverse-engineering work in this repo; it is never released.

## What gets published

Each release attaches three files:

- `BetterConnect-<version>.apk` — the installable APK, for sideloading directly onto a phone.
  This is the only distribution channel this app has today.
- `BetterConnect-<version>.aab` — the Android App Bundle, built alongside the APK because it
  costs one extra Gradle task and means a future Play Store upload needs no separate pipeline.
  Not currently used for anything.
- `SHA256SUMS.txt` — checksums for both, so anyone who downloads a release can confirm the file
  they have matches the one CI built, independent of GitHub's own integrity guarantees.

The release tag and title are `v<version>` — see below for what `<version>` is.

## Versioning

Two numbers, both derived from git history at build time — nothing is hand-incremented:

- **`versionCode`** (the integer Android uses to decide "is this an upgrade") is
  **`git rev-list --count HEAD`** — the number of commits reachable from the commit being
  released. It only ever goes up as `main` gains commits, it needs no external counter or
  stored state, and it is trivially reproducible later from `git log` alone.
- **`versionName`** (the human-readable string) is **`0.1.<that same count>`** — `0.1` is the
  source-controlled major/minor in `app/build.gradle.kts`, bumped by hand when there's a real
  reason to (e.g. the first release considered feature-complete for a ride); the commit count
  fills the patch slot automatically. Because both numbers come from the same count, either one
  identifies the exact build unambiguously — `versionCode 812` and `versionName 0.1.812` are
  the same commit.

This intentionally does **not** use a build timestamp. A timestamp tells you *when* a build
ran, not *what* it contains — two builds of the same commit on different days would get
different version numbers despite being byte-for-byte the same app, and a re-run of a failed CI
job would bump the version for no code reason. Commit count avoids both: it changes if and only
if the code does, and it's the same for a re-run of the same commit.

A local build (`./gradlew assembleFullDebug`, or opening the project in Android Studio) is
unaffected — `app/build.gradle.kts` falls back to `versionCode = 1`, `versionName = "0.1"` when
the CI-only Gradle properties (`-PVERSION_CODE`, `-PVERSION_NAME`) aren't set.

## Release signing

An Android release build needs a signing key. Without one configured, the release build type
falls back to the **debug** signing config — CI stays green and produces an installable APK/AAB,
but it is not properly signed for real distribution (anyone could rebuild and re-sign an
identical-looking app). To sign it for real:

### 1. Generate a release keystore, once, and keep it safe

```bash
keytool -genkeypair -v -keystore release.keystore -alias betterconnect \
  -keyalg RSA -keysize 2048 -validity 10000
```

Pick a real password for both the keystore and the key. **Losing this file means every future
release is a different signing identity from every past one** — Android treats that as a
different app for upgrade purposes, so anyone who installed an earlier release cannot upgrade
in place, only uninstall and reinstall. Back it up somewhere outside this repo. It must never
be committed — `.gitignore` already blocks `*.keystore`, `*.jks` and `keystore.properties`.

### 2. Add four repository secrets

Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore` (the whole file, base64-encoded) |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password from step 1 |
| `RELEASE_KEY_ALIAS` | `betterconnect` (or whatever `-alias` you used) |
| `RELEASE_KEY_PASSWORD` | the key password from step 1 |

Once all four are present, the `release` job in `.github/workflows/ci.yml` decodes the keystore,
writes `keystore.properties` (the same file `app/build.gradle.kts` looks for locally — the repo
convention, not something invented for CI), and every subsequent release is properly signed
with it. Nothing else in the workflow needs to change.

### Signing a local build the same way

To build a release APK locally with the same key instead of the debug-signing fallback, create
`keystore.properties` at the repo root (git-ignored, never commit it):

```properties
storeFile=/absolute/or/relative/path/to/release.keystore
storePassword=...
keyAlias=betterconnect
keyPassword=...
```

Then `./gradlew assembleFullRelease` picks it up automatically.

## Why only `full`, and why the `build` job stays separate

The existing `build` job (ktlint, tests, coverage, both debug flavours) runs on every push and
every pull request — it is the correctness gate and must stay cheap and always-on. The `release`
job depends on it (`needs: build`) and only runs for an actual push to `main`, never for a pull
request, so opening a PR from a fork never triggers a release build or touches the signing
secrets a fork PR shouldn't have access to. `diag` never appears here because it is a bench tool
for reverse-engineering this cluster's protocol, not something a rider installs.
