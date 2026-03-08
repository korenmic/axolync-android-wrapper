# Demo Assets

This folder intentionally contains deterministic demo media used by browser runners.

## Why the default track is silent

`demo_track.wav` is intentionally silent.  
It validates pipeline/control-flow behavior without coupling demo state transitions to real audio recognition:
- fake microphone wiring
- app state transitions
- deterministic lyric/sync demo flow
- screenshot/video automation stability

This is useful for repeatable sanity checks, but it is **not** a real audio-identification quality test.

## Where real audio-content validation happens

Audio-content transfer validation is covered by:
- `npm run test:demo:audio-e2e` (tone profile)
- `npm run test:demo:audio-e2e:song` (song-like synthetic profile)

Those tests generate non-silent source audio per run and compare captured output artifacts.

## File policy

- Keep deterministic baseline assets here.
- If assets are intentionally non-obvious (such as silence), document the reason in this README.

## Current assets

- `demo_track.wav`: deterministic fake-mic baseline used by runner/harness flows.
- `house_of_the_rising_sun_instrumental.ogg`: demo-only playback asset for in-app demo mode.
- `house_of_the_rising_sun_instrumental.lrc`: source timed lyric lines for the House demo variant.

## Attribution

- `house_of_the_rising_sun_instrumental.ogg` source:
  - https://commons.wikimedia.org/wiki/File:Anonimo_-_The_House_of_the_Rising_Sun.ogg
