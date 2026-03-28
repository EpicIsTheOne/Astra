# Astra handoff notes

This file is the short operational map for the next session.

## Canonical locations
- **GitHub repo:** `https://github.com/EpicIsTheOne/Astra`
- **Repo Pages/homepage:** `https://epicistheone.github.io/Astra/`
- **Android install guide:** `https://epicistheone.github.io/Astra/install-android.html`
- **All releases:** `https://github.com/EpicIsTheOne/Astra/releases`
- **Rolling debug APK tag:** `astra-latest`
- **Latest signed test release used in this session:** `v0.2.1-signed-test`

## Important repo paths
- `astra-android/` — active Android app
- `astra-android/app/build.gradle.kts` — Android version/signing config
- `astra-android/app/src/main/java/com/astra/wakeup/ui/MainActivity.kt` — main app screen, includes updater UI logic
- `astra-android/app/src/main/java/com/astra/wakeup/ui/UpdateClient.kt` — GitHub Releases lookup logic
- `astra-android/app/src/main/java/com/astra/wakeup/ui/ApkUpdateInstaller.kt` — download/install handoff logic
- `astra-android/app/src/main/AndroidManifest.xml` — install permission + FileProvider setup
- `astra-android/app/src/main/res/layout/activity_main.xml` — main screen layout including updater card
- `.github/workflows/astra-build.yml` — rolling debug build workflow
- `.github/workflows/astra-release.yml` — manual signed release workflow
- `docs/index.html` — GitHub Pages redirect/landing page for latest signed APK
- `docs/install-android.html` — human install guide
- `astra-android/RELEASE_SIGNING.md` — signing workflow/setup notes

## Current Android identity notes
- App label / product branding: **Astra**
- Android package / applicationId intentionally still: `com.astra.wakeup`
- That package ID was deliberately left alone so update compatibility does not get broken by vanity renames.

## Release model
### Rolling debug release
- Trigger: pushes to `main`
- Workflow: **Build Astra Android**
- Publishes: debug APK to tag `astra-latest`
- Asset name: `app-debug.apk`

### Signed release
- Trigger: manual workflow dispatch
- Workflow: **Release Astra Android (signed)**
- Publishes: signed release APK
- Asset name: `app-release.apk`
- `versionName` comes from workflow `release_tag`
- `versionCode` comes from GitHub Actions `GITHUB_RUN_NUMBER`

## How to publish a fresh signed build
From GitHub Actions, run:
- **Release Astra Android (signed)**

Inputs:
- `release_tag`: e.g. `v0.2.2`
- `release_name`: optional title override
- `prerelease`: `true` or `false`

If using CLI:
```bash
gh workflow run .github/workflows/astra-release.yml \
  --repo EpicIsTheOne/Astra \
  -f release_tag='v0.2.2' \
  -f release_name='Astra Android 0.2.2 (signed prerelease)' \
  -f prerelease=true
```

## GitHub Actions secrets used for signing
- `ASTRA_KEYSTORE_B64`
- `ASTRA_KEY_ALIAS`
- `ASTRA_KEY_PASSWORD`
- `ASTRA_STORE_PASSWORD`

## Existing signing material on server
These were created during this session and should be treated as sensitive:
- keystore path: `/root/.openclaw/workspaces/orchestrator/tmp/astra-signing/astra-release.keystore`
- base64 copy: `/root/.openclaw/workspaces/orchestrator/tmp/astra-signing/astra-release.keystore.b64`

## Existing signing credentials from this session
- alias: `Astra`
- store password: `astra!Epic#7-NDi0T6yapn61CJU`
- key password: `astra%Epic&7%1-1sjmAFTEcL3Eg`

## In-app updater summary
The updater now:
- checks GitHub Releases for newest `app-release.apk`
- shows installed vs latest version
- shows a short release-notes preview
- supports auto-check on launch
- supports auto-download
- supports skip-this-version
- launches Android installer for downloaded APK

Reality check:
- stock Android still usually requires the final install confirmation tap
- silent install is not expected unless the device is privileged/rooted/device-owner

## Current known annoyance
GitHub Actions still warns that:
- `softprops/action-gh-release@v2`
- targets Node 20 and is being forced onto Node 24

This is currently only a warning. Workflows still pass.

## Useful URLs
- Repo: `https://github.com/EpicIsTheOne/Astra`
- Pages download: `https://epicistheone.github.io/Astra/`
- Install guide: `https://epicistheone.github.io/Astra/install-android.html`
- Releases: `https://github.com/EpicIsTheOne/Astra/releases`
- Latest signed test release asset: `https://github.com/EpicIsTheOne/Astra/releases/download/v0.2.1-signed-test/app-release.apk`

## Good next-session starting checks
1. Open the repo front page and verify README links still look sane.
2. Check latest signed release in GitHub Releases.
3. Test the updater on-device against the newest signed build.
4. If release automation breaks, inspect `.github/workflows/astra-release.yml` first.
5. If Android install/update behavior breaks, inspect signing inputs and version code generation first.
