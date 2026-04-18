import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { stageBrowserAssets } from '../scripts/stage-browser-assets.mjs';

function writeFile(filePath, contents) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, contents, 'utf8');
}

test('stageBrowserAssets copies demo plugins, demo player, and browser dist payload into capacitor public assets', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-stage-assets-'));
  const sourceRoot = path.join(tempRoot, 'browser-dist');
  const publicDir = path.join(tempRoot, 'public');
  const demoAssetsRoot = path.join(tempRoot, 'demo-assets');
  const demoPluginsRoot = path.join(tempRoot, 'demo-plugins');
  const demoPlayerHtml = path.join(tempRoot, 'demo-player', 'player.html');
  const nativeServiceCompanionAssetsRoot = path.join(tempRoot, 'native-service-companions');

  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');
  writeFile(path.join(sourceRoot, 'assets', 'main.js'), 'console.log("browser");');
  writeFile(path.join(demoAssetsRoot, 'demo_track.wav'), 'wav');
  writeFile(path.join(demoAssetsRoot, 'house_of_the_rising_sun_instrumental.ogg'), 'ogg');
  writeFile(path.join(demoPluginsRoot, 'demo-lyricflow.js'), 'self.onmessage = () => {};');
  writeFile(path.join(demoPluginsRoot, 'metadata', 'demo-lyricflow.manifest.json'), '{"id":"demo-lyricflow"}');
  writeFile(demoPlayerHtml, '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');
  writeFile(path.join(nativeServiceCompanionAssetsRoot, 'manifest.json'), '{"companions":[{"addonId":"axolync-addon-vibra"}]}');
  writeFile(path.join(nativeServiceCompanionAssetsRoot, 'axolync-addon-vibra', 'vibra_proxy', 'capacitor', 'vibraProxyRuntimeOperator.json'), '{"runtime_operator_kind":"shazam-discovery-loopback-v1"}');

  const result = stageBrowserAssets({
    sourceRoot,
    publicDir,
    demoAssetsRoot,
    demoPluginsRoot,
    demoPlayerHtml,
    nativeServiceCompanionAssetsRoot,
    buildFlavor: 'debug',
    includeDemoAssets: true,
  });

  assert.equal(result.publicDir, publicDir);
  assert.equal(result.buildFlavor, 'debug');
  assert.equal(result.nativeServiceCompanionAssetsRoot, nativeServiceCompanionAssetsRoot);
  assert.equal(result.nativeStartupSplashVariant, 'layered');
  assert.equal(result.nativeStartupSplashFitMode, 'contain');
  assert.equal(result.nativeStartupSplashMinDurationMs, 2200);
  const stagedDebugIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_BUILD_FLAVOR = "debug"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = true/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "layered"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "contain"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_RUNTIME_STATE_RESET_HOST__/);
  assert.match(stagedDebugIndex, /window\.Capacitor\.Plugins\.AxolyncNativeServiceCompanionHost/);
  assert.match(stagedDebugIndex, /window\.Capacitor\.nativePromise/);
  assert.match(stagedDebugIndex, /hostPlatform: 'android'/);
  assert.match(stagedDebugIndex, /hostAbi: \{ enumerable: true, get\(\) \{ return hostMetadata\.hostAbi; \} \}/);
  assert.match(stagedDebugIndex, /invoke\('getHostInfo', \{\}\)/);
  assert.match(stagedDebugIndex, /getConnection/);
  assert.match(stagedDebugIndex, /clearPersistedRuntimeState/);
  assert.equal(fs.readFileSync(path.join(publicDir, 'assets', 'main.js'), 'utf8'), 'console.log("browser");');
  assert.equal(fs.readFileSync(path.join(publicDir, 'demo', 'assets', 'house_of_the_rising_sun_instrumental.ogg'), 'utf8'), 'ogg');
  assert.equal(fs.readFileSync(path.join(publicDir, 'demo', 'plugins', 'demo-lyricflow.js'), 'utf8'), 'self.onmessage = () => {};');
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'demo', 'plugins', 'metadata', 'demo-lyricflow.manifest.json'), 'utf8'),
    '{"id":"demo-lyricflow"}'
  );
  assert.equal(fs.existsSync(path.join(publicDir, 'demo', 'assets', 'demo_track.wav')), false);
  assert.equal(fs.readFileSync(path.join(publicDir, 'demo', 'player.html'), 'utf8'), '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'native-service-companions', 'manifest.json'), 'utf8'),
    '{"companions":[{"addonId":"axolync-addon-vibra"}]}',
  );
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'native-service-companions', 'axolync-addon-vibra', 'vibra_proxy', 'capacitor', 'vibraProxyRuntimeOperator.json'), 'utf8'),
    '{"runtime_operator_kind":"shazam-discovery-loopback-v1"}',
  );
  assert.equal(fs.readFileSync(path.join(publicDir, 'cordova.js'), 'utf8'), '');
  assert.equal(fs.readFileSync(path.join(publicDir, 'cordova_plugins.js'), 'utf8'), '');

  fs.rmSync(tempRoot, { recursive: true, force: true });
});

test('stageBrowserAssets can stage a release payload without demo assets', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-stage-assets-release-'));
  const sourceRoot = path.join(tempRoot, 'browser-dist');
  const publicDir = path.join(tempRoot, 'public');
  const demoAssetsRoot = path.join(tempRoot, 'demo-assets');
  const demoPluginsRoot = path.join(tempRoot, 'demo-plugins');
  const demoPlayerHtml = path.join(tempRoot, 'demo-player', 'player.html');

  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');
  writeFile(path.join(sourceRoot, 'assets', 'main.js'), 'console.log("browser");');
  writeFile(path.join(demoAssetsRoot, 'demo_track.wav'), 'wav');
  writeFile(path.join(sourceRoot, 'demo', 'plugins', 'demo-lyricflow.js'), 'stale demo payload');
  writeFile(path.join(demoAssetsRoot, 'house_of_the_rising_sun_instrumental.ogg'), 'ogg');
  writeFile(path.join(demoPluginsRoot, 'demo-lyricflow.js'), 'self.onmessage = () => {};');
  writeFile(demoPlayerHtml, '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');

  const result = stageBrowserAssets({
    sourceRoot,
    publicDir,
    demoAssetsRoot,
    demoPluginsRoot,
    demoPlayerHtml,
    buildFlavor: 'release',
    includeDemoAssets: false,
  });

  assert.equal(result.buildFlavor, 'release');
  assert.equal(result.includeDemoAssets, false);
  const stagedReleaseIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_BUILD_FLAVOR = "release"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = true/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "layered"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "contain"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST_FAMILY = 'capacitor'/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_RUNTIME_STATE_RESET_HOST__/);
  assert.match(stagedReleaseIndex, /hostPlatform: 'android'/);
  assert.match(stagedReleaseIndex, /window\.Capacitor\.nativePromise/);
  assert.match(stagedReleaseIndex, /invoke\('getHostInfo', \{\}\)/);
  assert.equal(fs.existsSync(path.join(publicDir, 'demo')), false);
  assert.equal(fs.existsSync(path.join(publicDir, 'native-service-companions')), false);

  fs.rmSync(tempRoot, { recursive: true, force: true });
});

test('stageBrowserAssets infers cover fit mode when artwork splash is requested without an explicit fit override', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-stage-assets-artwork-'));
  const sourceRoot = path.join(tempRoot, 'browser-dist');
  const publicDir = path.join(tempRoot, 'public');

  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');

  const result = stageBrowserAssets({
    sourceRoot,
    publicDir,
    buildFlavor: 'debug',
    includeDemoAssets: false,
    nativeStartupSplashVariant: 'artwork',
  });

  assert.equal(result.nativeStartupSplashVariant, 'artwork');
  assert.equal(result.nativeStartupSplashFitMode, 'cover');
  const stagedIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "artwork"/);
  assert.match(stagedIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "cover"/);

  fs.rmSync(tempRoot, { recursive: true, force: true });
});
