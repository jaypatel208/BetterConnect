# Setup — bringing your own Maps/Routes API key

This is a personal, open-source app: **every user who runs `full` from source brings their own
Google key.** There is no shared project and no key baked into the repo — a fresh clone builds
and installs with no key at all (device list, connect, the cluster link and the hidden debug
menu all work), but the navigation screen cannot fetch a route until you add one.

None of this is required for `diag`, and none of it is required just to build `full` — only to
route.

## 1. Create a Google Cloud project

If you don't already have one: https://console.cloud.google.com/projectcreate. Any project
name; note the project you land in, the rest of these steps happen inside it.

## 2. Enable exactly two APIs

**Only these two** — nothing else this app uses needs a Cloud API:

- **Maps SDK for Android** — https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com
- **Routes API** — https://console.cloud.google.com/apis/library/routes.googleapis.com

Click "Enable" on each. (The legacy **Directions API** is not an option here — it went Legacy
on 2025-03-01 and a fresh Cloud project cannot enable it at all; Routes API's `computeRoutes` is
what this app calls instead.)

## 3. Create an Android-restricted API key

APIs & Services → Credentials → Create Credentials → API key.

Then **restrict it** — an unrestricted key billed to your account is a real liability:

1. Application restrictions → **Android apps**.
2. Add package name **`dev.jay.betterconnect`** (note: no `.diag` suffix — this restriction only
   ever needs to cover the `full` flavour, since `diag` never calls a Cloud API).
3. Add the **SHA-1** of the keystore you'll actually install with. For a debug build:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
     -storepass android -keypass android
   ```
   Copy the `SHA1:` fingerprint. If you build a release APK, add its keystore's SHA-1 too — a
   key restricted to only the debug fingerprint will silently fail Routes calls in a release
   build.
4. API restrictions → restrict the key to **Maps SDK for Android** and **Routes API** only.

## 4. Set a billing budget alert

Two-wheeler routing (`TWO_WHEELER` mode, which this app always uses — there is no car/transit
option) is billed at the **Enterprise SKU: $15 per 1,000 requests**, with **1,000 free per
month**. The app is designed to stay inside that free tier under normal riding — off-route
detection is local, reroutes are debounced and capped per trip, and routes are cached across a
process restart — but you are billed on your own key, so a budget alert is cheap insurance
against a bug (or a very long ride) burning through it.

Billing → Budgets & alerts → Create budget. A few dollars is plenty to notice a runaway loop
long before it costs anything real.

## 5. Drop the key in `secrets.properties`

At the repo root (same level as `settings.gradle.kts`), create `secrets.properties` (this file
is git-ignored — `.gitignore` already reserves the name, and it must never be committed):

```properties
MAPS_API_KEY="your-key-here"
```

The quotes are required — the value is injected as a Kotlin/Java string literal into both the
manifest and `BuildConfig`, not a raw substitution.

That's it. Rebuild `full` (`./gradlew assembleFullDebug` or just reinstall from Android Studio)
and the navigation screen will fetch real routes. No key at all is also a valid, supported
state: the app still builds, installs, connects to the cluster, and the nav screen explains
itself ("No Maps API key configured — see docs/SETUP.md") instead of failing silently.

## Why a committed default file also exists

`local.defaults.properties` (committed, not git-ignored) sets `MAPS_API_KEY=" "` — a single
space, not an empty string — so a keyless clone and CI both resolve the manifest placeholder and
`BuildConfig` field with no `secrets.properties` present at all. It has to be a space rather than
truly empty because of a quirk in `secrets-gradle-plugin` 2.0.1's default-value injection: its
`String.addParenthesisIfNeeded()` (`Extensions.kt`) emits an **unquoted, unparseable**
`BuildConfig` field for a value that is empty after its surrounding-quote strip — `""` fails to
compile, `" "` does not. `RoutesApiRepository` already treats a blank key as absent via
`isBlank()`, so a single space reads as "no key configured" everywhere in the app that checks —
this is purely a workaround for the plugin's own bug, not a real value. Never edit this file to
put a real key in it; that defeats the point of it being committed.

## Never

- **Never commit a real key** — not in `secrets.properties`, not inline anywhere, not in an
  issue template or a screenshot with the console still open.
- **Never ship a key in a release APK you distribute to others.** An Android-restricted key is
  low-risk (it can't be used from anywhere but this exact package + signing key), but a Routes
  API call still bills whoever owns the key — that should always be the person running the
  build, never you on their behalf.
