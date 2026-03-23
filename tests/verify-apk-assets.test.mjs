import assert from 'node:assert/strict';
import test from 'node:test';

import { assertDemoAssetState } from '../scripts/verify-apk-assets.mjs';

test('assertDemoAssetState accepts the full debug demo payload', () => {
  const zipEntries = [
    'assets/public/index.html',
    'assets/public/demo/player.html',
    'assets/public/demo/plugins/demo-lyricflow.js',
    'assets/public/demo/assets/demo_track.wav',
    'assets/public/demo/assets/house_of_the_rising_sun_instrumental.ogg',
  ];

  assert.doesNotThrow(() => {
    assertDemoAssetState(zipEntries, true, '/tmp/debug.apk');
  });
});

test('assertDemoAssetState rejects release payloads that still ship demo media or player assets', () => {
  const zipEntries = [
    'assets/public/index.html',
    'assets/public/demo/player.html',
    'assets/public/demo/assets/demo_track.wav',
  ];

  assert.throws(() => {
    assertDemoAssetState(zipEntries, false, '/tmp/release.apk');
  }, /unexpectedly ships demo asset in release profile|unexpectedly ships demo asset tree in release profile/);
});

