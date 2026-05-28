# Health Sync Setup

Health Sync is a personal Android app that exports Health Connect data to a
single `health_data.json` file in Google Drive.

The app does not use Google OAuth, Supabase, a backend server, or any shared
cloud credentials. Google Drive access is handled through Android's system file
picker: the user chooses or creates one Drive file, and the app keeps updating
that same file.

## What It Exports

The app reads from Health Connect and writes:

- `profile`: device metadata and last update time.
- `snapshots`: compact daily summaries for quick answers.
- `latest_full_export`: the latest rich export with raw typed records.

Daily summary values are generated through the Health Connect aggregate API.
This is intentional because aggregate totals are safer for steps, distance,
calories, sleep duration, and other daily totals than manually summing raw
records.

## Privacy Notes

Do not commit any of these:

- `health_data.json`
- APK files
- debug keystores or signing keys
- `local.properties`
- Gradle build output
- screenshots containing personal health data

The repository should contain source code, docs, and build configuration only.

## Requirements

- Android phone with Health Connect available.
- Fitbit, Google Fit, or another source syncing data into Health Connect.
- Google Drive app installed if you want the file stored in Drive.
- Android Studio or command-line Android build tools.
- JDK 17.

## Build Locally

From the Android project directory:

```bash
cd android
gradle assembleDebug
```

The APK is generated at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

If you use Android Studio, open the `android` directory and run the `app`
configuration.

## Install And Use

1. Install the APK on your Android phone.
2. Open Health Sync.
3. Tap **Connect Health Connect**.
4. Grant the Health Connect categories you want exported.
5. Tap **Connect Google Drive File**.
6. In the Android file picker, choose Google Drive and save/create
   `health_data.json`.
7. Tap **Sync Now**.
8. Optional: tap **Start Auto Sync**.

## Auto Sync

Auto sync uses Android WorkManager.

- It requests a background sync about every 15 minutes.
- Android may delay it for battery or system scheduling reasons.
- It updates the same selected `health_data.json` file.
- If the app is reinstalled, the Drive file and Health Connect permissions must
  be connected again.

## File Interpretation

When using Claude or another assistant with `health_data.json`:

- Prefer `snapshots` or `latest_full_export` summary fields for daily totals.
- Do not sum `raw_records` for daily totals unless explicitly auditing raw data.
- Prefer local sleep fields such as `sleep.start_local` and `sleep.end_local`.
- UTC fields ending in `Z` are not local bedtime/wake time.
- Use `hrv_rmssd.median_ms` when comparing with Fitbit-style HRV cards.

## GitHub Actions

The included workflow builds a debug APK on pushes to `main` and manual runs.
The APK artifact is uploaded to the workflow run.
