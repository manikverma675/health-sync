# Health Sync

Health Sync is a small Android app for exporting Health Connect data to a
single JSON file in Google Drive.

It is designed for personal use with AI assistants: your phone reads Health
Connect, writes `health_data.json`, and your assistant can read that file from
Drive.

## How It Works

1. Fitbit or another health app syncs data into Health Connect.
2. Health Sync requests Health Connect read permissions.
3. You choose or create `health_data.json` through Android's system file picker.
4. The app writes summaries and raw Health Connect records into that same file.
5. Optional background sync keeps the file updated.

No backend server is required. No Google OAuth client is required. No API keys
are stored in the app.

## Data Model

The exported file has three top-level sections:

- `profile`: device ID and last update timestamp.
- `snapshots`: compact daily summaries.
- `latest_full_export`: the latest detailed export, including raw records.

The summary fields use Health Connect's aggregate API. This helps avoid common
mistakes such as summing raw records across UTC boundaries or overlapping
records.

The raw records remain available for deeper analysis, source checks, and timing
details.

## Supported Data

The app requests all read record types supported by the bundled Health Connect
SDK, including:

- steps, distance, calories, exercise sessions, floors, speed, power, cadence
- heart rate, resting heart rate, HRV, respiratory rate, oxygen saturation
- sleep sessions and sleep stages
- weight, height, body fat, body water mass, bone mass, lean body mass
- nutrition and hydration
- menstrual health records where available
- mindfulness sessions

Only data that exists in Health Connect and is granted by the user can be
exported.

## Privacy

This repository should not contain personal health data or secrets.

Ignored by default:

- generated APK files
- signing keys and keystores
- `health_data.json`
- local Android config
- Gradle build output
- screenshots and OS metadata

## Build

See [SETUP.md](SETUP.md) for setup, build, install, and usage instructions.

GitHub Actions can build a debug APK from source and upload it as a workflow
artifact.
