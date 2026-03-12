# Android Touch Gesture Delivery Status - 2026-03-12

## Short answer

The previous Android touch parity implementation was not enough. It added logging and removed one obvious native multi-touch swallow path, but real-device testing still shows touch is effectively blocked in the lyric scene.

## What is proven in source right now

### Android wrapper

In `MainActivity.kt`:
- the WebView touch listener now returns `false`, so it does not intentionally consume the event stream itself
- native touch-delivery logging is present via `recordTouchDelivery(...)`
- the wrapper injects these browser-side suppressions on page load:
  - `maximum-scale=1, user-scalable=no`
  - `document.body.style.touchAction = 'none'`
  - `document.documentElement.style.webkitUserSelect = 'none'`
  - `document.body.style.userSelect = 'none'`
  - `document.body.style.webkitTouchCallout = 'none'`

### Browser

In `axolync-browser/src/main.ts`:
- `#lyric-display` has `touchstart`, `touchmove`, `touchend`, and `touchcancel` handlers
- those handlers already log:
  - `touch.received`
  - `touch.drag.primed`
  - `touch.drag.moved`
  - `touch.pinch.started`
  - `touch.pinch.applied`
  - `touch.cleared`
- `#lyric-display` itself also has CSS `touch-action: none`

## Why the previous work did not restore touch

The previous work was not pure waste, but it was incomplete.

It proved and changed these things:
1. native multi-touch is no longer obviously blanket-swallowed by the same old path
2. native and browser diagnostics now exist
3. browser lyric-scene touch handlers are still wired in source

But it did **not** prove which suppression layer is actually blocking real-device touch behavior.

The strongest remaining suspect is now the WebView/browser suppression stack, not the old single native swallow branch.

## Strongest current hypothesis

The likely culprit is one or more of these suppression layers acting too broadly:
1. injected viewport lock + `user-scalable=no`
2. injected `document.body.style.touchAction = 'none'`
3. injected `userSelect` / `webkitTouchCallout` suppression
4. `#lyric-display` also using `touch-action: none`

Any one of those may be fine in isolation, but the current stack is strong enough that real-device touch may still never reach the lyric scene in the intended way.

## User suspicions that remain valid

These suspicions are still coherent with the code and should be preserved:
- the earlier anti-browser-zoom / anti-selection work may have disabled the intended Axolync touch gestures too
- mouse working while touch fails suggests the shared lyric interaction model is not the main blocker
- touch may be blocked before or during WebView delivery rather than merely misinterpreted after arrival

## Are more iterations useful?

### Blind iterations
Mostly no.

More speculative code changes without isolating the culprit first are likely to waste time again.

### One targeted investigation iteration
Yes.

A single tightly scoped experiment is high value:
- add one runtime/build flag that disables the current WebView/browser suppression layer
- run a real-device APK with that flag flipped
- observe whether lyric touch comes back

That would answer the core question quickly.

## Recommended next investigation

### Best next move

Add one Android-wrapper-controlled flag for the browser suppression layer.

Suggested behavior:
- default: keep current suppression behavior on
- experimental mode: disable the injected browser suppression block temporarily

Then test on device:
1. if touch starts working again, the suppression layer is confirmed as the blocker
2. re-enable the protections one piece at a time to find the minimal safe subset
3. keep only the narrow protections needed to block browser-native zoom/selection while preserving Axolync gestures

### Why this is better than another broad fix pass

Because it isolates the likely culprit in one step.

Without that isolation, every new patch risks repeating the same failure mode:
- touch still blocked
- no proof why
- another guess-driven rewrite

## Permanent A/B formulation

This is the current preferred next step and should be preserved explicitly:

1. add one Android-wrapper-controlled flag that disables the injected browser/WebView suppression bundle
2. build one APK with the flag flipped
3. if touch returns, the suppression bundle is confirmed as the blocker
4. re-enable protections one piece at a time until the minimal safe subset is found

This is a better use of time than another blind rewrite because it isolates the strongest remaining suspect first.

## Rough iteration count

For the exact Android touch-parity issue, the project has already spent multiple real passes on:
1. native diagnosis
2. browser diagnosis
3. native gate rewrite
4. export/log proof
5. packaged asset sync and APK retest

So this is not a fresh bug. It has already consumed enough iterations that another non-isolated rewrite would be low-value.

## Realistic roadmap to done

1. Add the suppression flag and surface it through runtime config / build config
2. Test one APK with suppression disabled
3. Confirm whether touch drag and pinch return
4. Re-enable protections incrementally until the real blocker is identified
5. Keep the narrowest working suppression policy
6. Only after that, revisit any remaining browser-side touch binding gaps

## Bottom line

The previous touch work was insufficient, not final.

The best current direction is not “more of the same.”
It is one explicit A/B experiment against the browser/WebView suppression layer, because that is the strongest remaining culprit in source.
