# Astra Wake-Up Android MVP

Yes, this is the real Android app scaffold for Astra wake-ups at **5:50 AM ET**.

## What it does now
- Schedules an exact daily alarm for 5:50 AM (America/New_York).
- Opens a full-screen wake activity at alarm time.
- Plays system alarm sound now (placeholder until Astra TTS audio is wired).
- Supports **I'm awake** and **Snooze 10 min**.
- Reschedules after reboot.

## What still needs to be wired
- Replace placeholder audio with generated Astra TTS (female voice).
- Add punishment mode timer (2nd louder blast if no ack).
- Build + sign APK.

## Build prerequisites (on a machine with Android SDK)
- Java 17
- Android SDK + platform tools
- Gradle (or Android Studio)

## Build commands
```bash
cd astra-wakeup-android
./gradlew assembleDebug
```

APK path:
`app/build/outputs/apk/debug/app-debug.apk`

## Public download link plan
Once APK is built, upload to one of:
- GitHub Releases
- Cloudflare R2 (public object URL)
- S3 static URL

Then share that URL with Epic.
