import assert from 'node:assert/strict';
import test from 'node:test';

import {
  assertDemoAssetState,
  assertLrclibNativeAssetState,
} from '../scripts/verify-apk-assets.mjs';

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

test('assertLrclibNativeAssetState accepts complete native-capable LRCLIB payloads', () => {
  const zipEntries = [
    'assets/public/index.html',
    'assets/public/native-service-companions/manifest.json',
    'assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/operator.json',
    'assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/native/shared/lrclib_local/assets/db.sqlite3.br',
  ];

  assert.doesNotThrow(() => {
    assertLrclibNativeAssetState(zipEntries, true, '/tmp/axolync-lrclib-native.apk');
  });
});

test('assertLrclibNativeAssetState rejects partial or remote-only LRCLIB native staging drift', () => {
  const partialZipEntries = [
    'assets/public/index.html',
    'assets/public/native-service-companions/manifest.json',
    'assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/operator.json',
  ];

  assert.throws(() => {
    assertLrclibNativeAssetState(partialZipEntries, true, '/tmp/axolync-lrclib-native.apk');
  }, /missing required LRCLIB native asset/);

  assert.throws(() => {
    assertLrclibNativeAssetState(partialZipEntries, false, '/tmp/axolync.apk');
  }, /unexpectedly ships LRCLIB native companion assets/);
});
