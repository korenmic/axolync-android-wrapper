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

  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');
  writeFile(path.join(sourceRoot, 'assets', 'main.js'), 'console.log("browser");');
  writeFile(path.join(demoAssetsRoot, 'demo_track.wav'), 'wav');
  writeFile(path.join(demoAssetsRoot, 'house_of_the_rising_sun_instrumental.ogg'), 'ogg');
  writeFile(path.join(demoPluginsRoot, 'demo-lyricflow.js'), 'self.onmessage = () => {};');
  writeFile(path.join(demoPluginsRoot, 'metadata', 'demo-lyricflow.manifest.json'), '{"id":"demo-lyricflow"}');
  writeFile(demoPlayerHtml, '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');

  const result = stageBrowserAssets({
    sourceRoot,
    publicDir,
    demoAssetsRoot,
    demoPluginsRoot,
    demoPlayerHtml,
    runtimeProfile: 'debug',
    includeDemoAssets: true,
  });

  assert.equal(result.publicDir, publicDir);
  assert.equal(result.runtimeProfile, 'debug');
  assert.equal(result.nativeStartupSplashVariant, 'layered');
  assert.equal(result.nativeStartupSplashFitMode, 'contain');
  assert.equal(result.nativeStartupSplashMinDurationMs, 2200);
  const stagedDebugIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_RUNTIME_PROFILE = "debug"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = true/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "layered"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "contain"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200/);
  assert.equal(fs.readFileSync(path.join(publicDir, 'assets', 'main.js'), 'utf8'), 'console.log("browser");');
  assert.equal(fs.readFileSync(path.join(publicDir, 'demo', 'assets', 'house_of_the_rising_sun_instrumental.ogg'), 'utf8'), 'ogg');
  assert.equal(fs.readFileSync(path.join(publicDir, 'demo', 'plugins', 'demo-lyricflow.js'), 'utf8'), 'self.onmessage = () => {};');
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'demo', 'plugins', 'metadata', 'demo-lyricflow.manifest.json'), 'utf8'),
    '{"id":"demo-lyricflow"}'
  );
  assert.equal(fs.existsSync(path.join(publicDir, 'demo', 'assets', 'demo_track.wav')), false);
  assert.equal(fs.readFileSync(path.join(publicDir, 'demo', 'player.html'), 'utf8'), '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');
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
    runtimeProfile: 'release',
    includeDemoAssets: false,
  });

  assert.equal(result.runtimeProfile, 'release');
  assert.equal(result.includeDemoAssets, false);
  const stagedReleaseIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_RUNTIME_PROFILE = "release"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = true/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "layered"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "contain"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200/);
  assert.equal(fs.existsSync(path.join(publicDir, 'demo')), false);

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
    runtimeProfile: 'debug',
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
