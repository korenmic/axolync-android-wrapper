# Physical Device Validation

This document defines the physical-device validation flow for task `19` in `.kiro/specs/android-apk-wrapper/tasks.md`.

## Target Device Profile

- Android 10+ (2019 or newer device class)
- 4GB RAM minimum
- Microphone available
- Stable Wi-Fi connection for remote provider checks

## Execution

Run:

```bash
./scripts/run-physical-device-validation.sh
```

The script:

1. Detects connected `adb` device(s)
2. Captures device model + Android version metadata
3. Runs instrumentation sanity tests
4. Stores a run report at:
   - `tests/output/physical-device/latest.md`
   - `tests/output/physical-device/history.ndjson`

## Measurements

The validation run tracks:

- Startup readiness (`/health` reachable through localhost path)
- Audio-capture sanity pipeline enablement
- Device profile metadata for reproducibility

If no device is connected, the script writes a `SKIPPED` report entry with reason `no_adb_device_connected`.

