import assert from 'node:assert/strict';
import test from 'node:test';

import { assertDemoAssetState } from '../scripts/verify-apk-assets.mjs';

test('assertDemoAssetState accepts the full debug demo payload', () => {
  const zipEntries = [
    'assets/public/index.html',
    'assets/public/demo/player.html',
    'assets/public/demo/plugins/demo-lyricflow.js',
    'assets/public/demo/assets/house_of_the_rising_sun_instrumental.ogg',
  ];

  assert.doesNotThrow(() => {
    assertDemoAssetState(zipEntries, true, '/tmp/debug.apk');
  });
});

test('assertDemoAssetState rejects demo-free payloads that still ship demo media or player assets', () => {
  const zipEntries = [
    'assets/public/index.html',
    'assets/public/demo/player.html',
    'assets/public/demo/assets/house_of_the_rising_sun_instrumental.ogg',
  ];

  assert.throws(() => {
    assertDemoAssetState(zipEntries, false, '/tmp/release.apk');
  }, /unexpectedly ships demo asset in demo-free profile|unexpectedly ships demo asset tree in demo-free profile/);
});
