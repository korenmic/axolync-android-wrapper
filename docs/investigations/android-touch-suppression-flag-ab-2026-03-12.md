# Android Touch Suppression Flag A/B Plan - 2026-03-12

## Purpose

Add one explicit Android build/runtime switch so touch-delivery debugging stops depending on guesswork.

## Flag

- `AXOLYNC_ANDROID_TOUCH_SUPPRESSION_MODE`

## Modes

- `full`
  - keep the current Android wrapped-WebView suppression bundle
  - includes viewport lock, `user-scalable=no`, `touchAction = 'none'`, and selection/touch-callout suppression
- `off`
  - disable the injected browser/WebView suppression bundle for investigation

## Why this flag exists

The current strongest source-level suspect is the suppression bundle, not the shared lyric interaction model.

Without an explicit A/B switch, every Android touch iteration risks mixing:
- native touch-delivery changes
- browser receipt changes
- WebView suppression changes

That makes failures hard to localize.

## Preferred investigation sequence

1. build one APK with `AXOLYNC_ANDROID_TOUCH_SUPPRESSION_MODE=off`
2. test touch drag and pinch in the lyric scene
3. if touch returns, treat the suppression bundle as confirmed culprit
4. re-enable protections incrementally until the minimal safe subset is found
5. only then resume any browser-side touch refinements if they are still needed

## Success criteria

The flag is doing its job if:

1. default APKs still preserve current behavior under `full`
2. `off` materially changes the injected wrapper script
3. the difference is visible in tests and in real APK behavior
4. the investigation can proceed without another broad rewrite
