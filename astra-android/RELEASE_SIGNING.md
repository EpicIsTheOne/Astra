# Signed Release Setup

Astra can build a signed `release` APK through GitHub Actions.

## Required repository secrets
The repo uses these GitHub Actions secrets:

- `ASTRA_KEYSTORE_B64` — base64 of the `.jks` / `.keystore` file
- `ASTRA_KEY_ALIAS`
- `ASTRA_KEY_PASSWORD`
- `ASTRA_STORE_PASSWORD`

## Existing setup in this repo
This repository already has a working signed-release pipeline wired up.
The current manual workflow is:

- **Release Astra Android (signed)**

That workflow will:
1. decode the keystore from secrets
2. derive `versionName` from the provided release tag
3. derive `versionCode` from the GitHub Actions run number
4. build `:app:assembleRelease`
5. upload `app-release.apk` as an artifact
6. publish a GitHub Release with the signed APK attached

## Create a release keystore
Example:

```bash
keytool -genkeypair \
  -v \
  -storetype JKS \
  -keystore astra-release.keystore \
  -alias Astra \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650
```

Back up the keystore and passwords before using it in CI.
If you lose this key, installed apps signed with it cannot be updated normally.

## Encode keystore for GitHub secret
GNU coreutils example:

```bash
base64 -w 0 astra-release.keystore
```

macOS / BSD example:

```bash
base64 < astra-release.keystore | tr -d '\n'
```

Save the resulting single-line output as `ASTRA_KEYSTORE_B64`.

## Workflow inputs
Run **Release Astra Android (signed)** and provide:

- `release_tag` — clean format like `v0.2.1`
- `release_name` — optional title override; if blank-ish conceptually, the workflow derives a sensible title
- `prerelease` — whether the release should be marked prerelease

## Local project properties
The Android app reads these project properties for signing:

- `ASTRA_KEYSTORE_PATH`
- `ASTRA_KEY_ALIAS`
- `ASTRA_KEY_PASSWORD`
- `ASTRA_STORE_PASSWORD`
- `ASTRA_VERSION_NAME` (optional)
- `ASTRA_VERSION_CODE` (optional)

You can provide them via Gradle properties or environment-backed property injection for local signed builds.

## First install migration note
If your phone currently has a debug build or a build signed with a different key, you will likely need one final uninstall/reinstall before future signed updates can install over the top.
