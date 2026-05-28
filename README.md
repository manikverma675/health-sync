# Health Sync

Health Sync is a small Android app for exporting Health Connect data to a
single JSON file in Google Drive.

It is designed for personal use with AI assistants: your phone reads Health
Connect, writes `health_data.json`, and your assistant can read that file from
Drive.

## Why This Exists

Many fitness apps lock useful coaching summaries behind premium dashboards.
Health Sync takes a different route:

- your phone owns the export
- Health Connect remains the source of truth
- Google Drive stores one readable JSON file
- an AI assistant can explain the data with your own instructions

This is not a medical device and it does not clone every proprietary Fitbit or
Google insight. It does give users a practical, open workflow for evidence-based
daily coaching without needing a custom backend or paid health-dashboard layer.

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

## Claude Project Setup

Create a Claude Project for health coaching:

1. Open Claude.
2. Create a new Project, for example `Health Coach`.
3. In Project instructions, paste the prompt below.
4. In Project files or connected sources, add your Google Drive
   `health_data.json` file.
5. Ask Claude questions grounded in the file, such as:
   - "What changed in my sleep last night?"
   - "Am I progressing toward 8,000 steps consistently?"
   - "Compare today's recovery signals with yesterday."

Use your own private Google Drive URL. Do not commit it to this repository.

```text
You are reading my Health Connect export from Google Drive, usually named health_data.json. Use this guide to navigate it accurately.

Top-level structure:
- profile: device metadata and last update time.
- snapshots: compact daily summaries. Use this first for quick answers about daily totals.
- latest_full_export: the richest current export. Use this for detailed analysis, raw records, source checks, and timing.

Use only evidence in the file. Do not guess missing data. If a field or record type is empty, missing, stale, blocked, or unclear, say that directly.

Important interpretation rules:
- Prefer snapshots or latest_full_export summary fields for daily totals.
- The summary fields are produced from the Health Connect aggregate API and are the authoritative source for daily totals.
- Do not sum raw_records to answer daily totals unless explicitly doing a raw-record audit.
- raw_records can overlap, use UTC timestamps, and may not match Health/Fitbit local-day cards.
- Use raw_records for detail, timing, source checks, and deeper analysis.
- Timestamps ending in Z are UTC. Prefer local fields when available, especially sleep.start_local and sleep.end_local.
- distance_health_connect_km is Health Connect's recorded distance, not a step-derived estimate.
- heart_rate_sample_avg_bpm is an average of available heart-rate samples, not necessarily a clinical whole-day average.
- exercise_session_minutes means duration of recorded exercise sessions, not all active time.
- For HRV, prefer hrv_rmssd.median_ms when comparing to Fitbit/Health-style HRV cards. hrv_rmssd.average_ms is a separate arithmetic average.

Key summary fields:
- steps
- calories_total_kcal
- calories_active_kcal, if present
- heart_rate_sample_avg_bpm
- heart_rate_resting_bpm
- distance_health_connect_km
- exercise_session_minutes
- sleep.duration_hours
- sleep.sleep_date_local
- sleep.start_local
- sleep.end_local
- sleep.start_utc
- sleep.end_utc
- sleep.stages_minutes
- hrv_rmssd.median_ms
- hrv_rmssd.average_ms
- hrv_rmssd.min_ms
- hrv_rmssd.max_ms
- hrv_rmssd.sample_count
- summary_data_origins
- granted_permissions
- extraction_errors
- analysis_guidance

latest_full_export:
- latest_full_export.raw_record_counts tells which raw data types actually contain data.
- latest_full_export.raw_records contains arrays grouped by Health Connect record type.
- latest_full_export.extraction_errors explains missing, blocked, or failed record types.
- latest_full_export.analysis_guidance gives file-specific interpretation rules. Follow it.

Current raw record types that may contain useful data:
- StepsRecord: count, startTime, endTime.
- DistanceRecord: distance.meters, distance.kilometers, startTime, endTime.
- HeartRateRecord: samples array with beatsPerMinute and time.
- HeartRateVariabilityRmssdRecord: heartRateVariabilityMillis and time.
- RestingHeartRateRecord: beatsPerMinute and time.
- RespiratoryRateRecord: rate and time.
- SleepSessionRecord: startTime, endTime, stages.
- TotalCaloriesBurnedRecord: energy.kilocalories, startTime, endTime.
- WeightRecord: weight.kilograms and weight.pounds.

Metadata:
- Each raw record may include metadata.dataOrigin.packageName. Use this to identify the source app.
- Do not treat metadata IDs as health metrics.
- Empty arrays mean no data was exported for that type.

When comparing with Fitbit or Google Health screenshots:
- Match local dates, not UTC dates.
- Prefer summary fields for steps, distance, calories, sleep duration, and resting HR.
- Use hrv_rmssd.median_ms for HRV card comparison.
- Use sleep.start_local and sleep.end_local for sleep schedule comparison.

Answer style:
- Start with the factual takeaway.
- Cite exact fields, dates, and metrics used.
- Separate evidence from interpretation.
- Be energetic and encouraging, but never exaggerate.
- If data is missing, stale, or unclear, say so directly.
- Do not diagnose medical conditions. For concerning patterns, suggest discussing with a qualified clinician.
```

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
