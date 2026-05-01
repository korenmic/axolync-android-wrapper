#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT/tests/output/physical-device"
mkdir -p "$OUT_DIR"

timestamp_local="$(date '+%Y-%m-%dT%H-%M-%S%z')"
latest_report="$OUT_DIR/latest.md"
history_file="$OUT_DIR/history.ndjson"

adb_path="$(command -v adb || true)"
if [[ -z "$adb_path" ]]; then
  cat > "$latest_report" <<EOF
# Physical Device Validation

- timestamp: $timestamp_local
- status: SKIPPED
- reason: adb_not_found
EOF
  printf '{"timestamp":"%s","status":"SKIPPED","reason":"adb_not_found"}\n' "$timestamp_local" >> "$history_file"
  echo "SKIPPED: adb not found"
  exit 0
fi

mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
if [[ "${#devices[@]}" -eq 0 ]]; then
  cat > "$latest_report" <<EOF
# Physical Device Validation

- timestamp: $timestamp_local
- status: SKIPPED
- reason: no_adb_device_connected
EOF
  printf '{"timestamp":"%s","status":"SKIPPED","reason":"no_adb_device_connected"}\n' "$timestamp_local" >> "$history_file"
  echo "SKIPPED: no adb device connected"
  exit 0
fi

device="${devices[0]}"
model="$(adb -s "$device" shell getprop ro.product.model | tr -d '\r')"
release="$(adb -s "$device" shell getprop ro.build.version.release | tr -d '\r')"
sdk="$(adb -s "$device" shell getprop ro.build.version.sdk | tr -d '\r')"

set +e
AXOLYNC_SKIP_BROWSER_ASSET_SYNC=true ./gradlew :app:connectedNormalDebugAndroidTest --console=plain
test_exit=$?
set -e

status="PASS"
reason="connected_android_test_passed"
if [[ $test_exit -ne 0 ]]; then
  status="FAIL"
  reason="connected_android_test_failed"
fi

cat > "$latest_report" <<EOF
# Physical Device Validation

- timestamp: $timestamp_local
- status: $status
- reason: $reason
- device_serial: $device
- model: $model
- android_release: $release
- sdk: $sdk
EOF

printf '{"timestamp":"%s","status":"%s","reason":"%s","device":"%s","model":"%s","android_release":"%s","sdk":"%s"}\n' \
  "$timestamp_local" "$status" "$reason" "$device" "$model" "$release" "$sdk" >> "$history_file"

if [[ $test_exit -ne 0 ]]; then
  exit $test_exit
fi

echo "Physical-device validation completed: $status"

