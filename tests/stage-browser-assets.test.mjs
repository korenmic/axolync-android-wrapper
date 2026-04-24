import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  restageDirectoryDeterministically,
  stageBrowserAssets,
} from '../scripts/stage-browser-assets.mjs';

function writeFile(filePath, contents) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, contents, 'utf8');
}

const capacitorConfig = JSON.parse(
  fs.readFileSync(path.join(process.cwd(), 'capacitor.config.json'), 'utf8'),
);

test('restageDirectoryDeterministically rewrites a staged directory without stale entries', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-restage-'));
  const stagedDir = path.join(tempRoot, 'preinstalled');
  try {
    writeFile(path.join(stagedDir, 'b.txt'), 'b');
    writeFile(path.join(stagedDir, 'a.txt'), 'a');

    restageDirectoryDeterministically(stagedDir);

    assert.deepEqual(fs.readdirSync(stagedDir), ['a.txt', 'b.txt']);
    assert.equal(fs.existsSync(`${stagedDir}.axolync-deterministic`), false);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('stageBrowserAssets copies demo plugins, demo player, and browser dist payload into capacitor public assets', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-stage-assets-'));
  const sourceRoot = path.join(tempRoot, 'browser-dist');
  const assetRoot = path.join(tempRoot, 'assets-root');
  const publicDir = path.join(tempRoot, 'public');
  const demoAssetsRoot = path.join(tempRoot, 'demo-assets');
  const demoPluginsRoot = path.join(tempRoot, 'demo-plugins');
  const demoPlayerHtml = path.join(tempRoot, 'demo-player', 'player.html');
  const nativeServiceCompanionAssetsRoot = path.join(tempRoot, 'native-service-companions');

  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');
  writeFile(path.join(sourceRoot, 'assets', 'main.js'), 'console.log("browser");');
  writeFile(path.join(sourceRoot, 'plugins', 'preinstalled', 'manifest.json'), JSON.stringify({
    plugins: [
      {
        id: 'axolync-addon-vibra',
        url: '/plugins/preinstalled/axolync-addon-vibra.zip',
      },
    ],
  }));
  writeFile(path.join(sourceRoot, 'themes', 'preinstalled', 'manifest.json'), JSON.stringify({
    themes: [
      {
        id: 'aurora-stage',
        url: '/themes/preinstalled/aurora-stage.zip',
      },
    ],
  }));
  writeFile(path.join(demoAssetsRoot, 'demo_track.wav'), 'wav');
  writeFile(path.join(demoAssetsRoot, 'house_of_the_rising_sun_instrumental.ogg'), 'ogg');
  writeFile(path.join(demoPluginsRoot, 'demo-lyricflow.js'), 'self.onmessage = () => {};');
  writeFile(path.join(demoPluginsRoot, 'metadata', 'demo-lyricflow.manifest.json'), '{"id":"demo-lyricflow"}');
  writeFile(demoPlayerHtml, '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');
  writeFile(path.join(nativeServiceCompanionAssetsRoot, 'manifest.json'), '{"companions":[{"addonId":"axolync-addon-vibra"},{"addonId":"axolync-addon-lrclib","companionId":"lrclib_local","wrapper":"capacitor","entrypoint":"axolync-addon-lrclib/lrclib_local/capacitor/operator.json"}]}');
  writeFile(path.join(nativeServiceCompanionAssetsRoot, 'axolync-addon-vibra', 'vibra_proxy', 'capacitor', 'vibraProxyRuntimeOperator.json'), '{"runtime_operator_kind":"shazam-discovery-loopback-v1"}');
  writeFile(path.join(nativeServiceCompanionAssetsRoot, 'axolync-addon-lrclib', 'lrclib_local', 'capacitor', 'operator.json'), '{"runtime_operator_kind":"lrclib-local-loopback-v1"}');
  writeFile(path.join(nativeServiceCompanionAssetsRoot, 'axolync-addon-lrclib', 'lrclib_local', 'capacitor', 'native', 'shared', 'lrclib_local', 'assets', 'db.sqlite3.br'), 'brotli-db');

  const result = stageBrowserAssets({
    sourceRoot,
    assetRoot,
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
  assert.equal(result.capacitorConfig.rootPath, path.join(assetRoot, 'capacitor.config.json'));
  assert.equal(result.capacitorConfig.nestedPath, path.join(assetRoot, 'capacitor', 'capacitor.config.json'));
  assert.deepEqual(result.capacitorPluginRegistry.entries, [
    {
      pkg: 'axolync-debug-archive-save',
      classpath: 'com.axolync.android.bridge.AxolyncDebugArchiveSavePlugin',
    },
    {
      pkg: 'axolync-native-bridge-host',
      classpath: 'com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin',
    },
  ]);
  assert.equal(result.nativeStartupSplashVariant, 'layered');
  assert.equal(result.nativeStartupSplashFitMode, 'contain');
  assert.equal(result.nativeStartupSplashMinDurationMs, 2200);
  const stagedDebugIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_BUILD_FLAVOR = "debug"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_PREINSTALLED_ADDON_MANIFEST_FALLBACK__/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_PREINSTALLED_THEME_MANIFEST_FALLBACK__/);
  assert.match(stagedDebugIndex, /"axolync-addon-vibra"/);
  assert.match(stagedDebugIndex, /"aurora-stage"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = true/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "layered"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "contain"/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_RUNTIME_HOST_BRIDGE__/);
  assert.match(stagedDebugIndex, /window\.__AXOLYNC_RUNTIME_STATE_RESET_HOST__/);
  assert.match(stagedDebugIndex, /BRIDGE_PUBLICATION = Object\.freeze/);
  assert.match(stagedDebugIndex, /BRIDGE_RUNTIME_SCRIPT_SRC = '\.\/native-bridge\.js'/);
  assert.match(stagedDebugIndex, /publicationMode: 'capacitor-plugin-registry'/);
  assert.match(stagedDebugIndex, /pluginRegistryAssetPath: 'capacitor\.plugins\.json'/);
  assert.match(stagedDebugIndex, /mirroredPluginRegistryAssetPath: 'capacitor\/capacitor\.plugins\.json'/);
  assert.match(stagedDebugIndex, /buildBootstrapSnapshot = function\(\)/);
  assert.match(stagedDebugIndex, /ensureBridgeRuntimeLoaded = function\(\)/);
  assert.match(stagedDebugIndex, /data-axolync-capacitor-native-bridge-runtime/);
  assert.match(stagedDebugIndex, /Failed to load packaged Capacitor native bridge runtime\./);
  assert.match(stagedDebugIndex, /buildUnavailableBridgeError = function\(methodName, payload\)/);
  assert.match(stagedDebugIndex, /window\.Capacitor\.Plugins\.AxolyncNativeServiceCompanionHost/);
  assert.match(stagedDebugIndex, /window\.Capacitor\.nativePromise/);
  assert.match(stagedDebugIndex, /hostPlatform: 'android'/);
  assert.match(stagedDebugIndex, /hostAbi: \{ enumerable: true, get\(\) \{ return hostMetadata\.hostAbi; \} \}/);
  assert.match(stagedDebugIndex, /invoke\('getHostInfo', \{\}\)/);
  assert.match(stagedDebugIndex, /invoke\('saveDebugArchiveBase64', \{ fileName, base64Payload \}\)/);
  assert.match(stagedDebugIndex, /code = 'axolync_capacitor_native_bridge_unavailable'/);
  assert.match(stagedDebugIndex, /bootstrapSnapshot: snapshot/);
  assert.match(stagedDebugIndex, /bridgePublication: BRIDGE_PUBLICATION/);
  assert.match(stagedDebugIndex, /getConnection/);
  assert.match(stagedDebugIndex, /getDiagnostics/);
  assert.match(stagedDebugIndex, /clearPersistedRuntimeState/);
  assert.equal(fs.readFileSync(path.join(publicDir, 'assets', 'main.js'), 'utf8'), 'console.log("browser");');
  assert.match(fs.readFileSync(path.join(publicDir, 'native-bridge.js'), 'utf8'), /nativePromise/);
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
    '{"companions":[{"addonId":"axolync-addon-vibra"},{"addonId":"axolync-addon-lrclib","companionId":"lrclib_local","wrapper":"capacitor","entrypoint":"axolync-addon-lrclib/lrclib_local/capacitor/operator.json"}]}',
  );
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'native-service-companions', 'axolync-addon-vibra', 'vibra_proxy', 'capacitor', 'vibraProxyRuntimeOperator.json'), 'utf8'),
    '{"runtime_operator_kind":"shazam-discovery-loopback-v1"}',
  );
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'native-service-companions', 'axolync-addon-lrclib', 'lrclib_local', 'capacitor', 'operator.json'), 'utf8'),
    '{"runtime_operator_kind":"lrclib-local-loopback-v1"}',
  );
  assert.equal(
    fs.readFileSync(path.join(publicDir, 'native-service-companions', 'axolync-addon-lrclib', 'lrclib_local', 'capacitor', 'native', 'shared', 'lrclib_local', 'assets', 'db.sqlite3.br'), 'utf8'),
    'brotli-db',
  );
  assert.equal(fs.readFileSync(path.join(publicDir, 'cordova.js'), 'utf8'), '');
  assert.equal(fs.readFileSync(path.join(publicDir, 'cordova_plugins.js'), 'utf8'), '');
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(assetRoot, 'capacitor.config.json'), 'utf8')),
    capacitorConfig,
  );
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(assetRoot, 'capacitor', 'capacitor.config.json'), 'utf8')),
    capacitorConfig,
  );
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(assetRoot, 'capacitor.plugins.json'), 'utf8')),
    result.capacitorPluginRegistry.entries,
  );
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(assetRoot, 'capacitor', 'capacitor.plugins.json'), 'utf8')),
    result.capacitorPluginRegistry.entries,
  );

  fs.rmSync(tempRoot, { recursive: true, force: true });
});

test('stageBrowserAssets can stage a release payload without demo assets', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-stage-assets-release-'));
  const sourceRoot = path.join(tempRoot, 'browser-dist');
  const assetRoot = path.join(tempRoot, 'assets-root');
  const publicDir = path.join(tempRoot, 'public');
  const demoAssetsRoot = path.join(tempRoot, 'demo-assets');
  const demoPluginsRoot = path.join(tempRoot, 'demo-plugins');
  const demoPlayerHtml = path.join(tempRoot, 'demo-player', 'player.html');

  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');
  writeFile(path.join(sourceRoot, 'assets', 'main.js'), 'console.log("browser");');
  writeFile(path.join(sourceRoot, 'plugins', 'preinstalled', 'manifest.json'), JSON.stringify({
    plugins: [],
  }));
  writeFile(path.join(sourceRoot, 'themes', 'preinstalled', 'manifest.json'), JSON.stringify({
    themes: [],
  }));
  writeFile(path.join(demoAssetsRoot, 'demo_track.wav'), 'wav');
  writeFile(path.join(sourceRoot, 'demo', 'plugins', 'demo-lyricflow.js'), 'stale demo payload');
  writeFile(path.join(demoAssetsRoot, 'house_of_the_rising_sun_instrumental.ogg'), 'ogg');
  writeFile(path.join(demoPluginsRoot, 'demo-lyricflow.js'), 'self.onmessage = () => {};');
  writeFile(demoPlayerHtml, '<audio src="./assets/house_of_the_rising_sun_instrumental.ogg"></audio>');

  const result = stageBrowserAssets({
    sourceRoot,
    assetRoot,
    publicDir,
    demoAssetsRoot,
    demoPluginsRoot,
    demoPlayerHtml,
    buildFlavor: 'release',
    includeDemoAssets: false,
  });

  assert.equal(result.buildFlavor, 'release');
  assert.equal(result.includeDemoAssets, false);
  assert.equal(result.capacitorConfig.rootPath, path.join(assetRoot, 'capacitor.config.json'));
  const stagedReleaseIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_BUILD_FLAVOR = "release"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_PREINSTALLED_ADDON_MANIFEST_FALLBACK__ = \{\"plugins\":\[\]\}/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_PREINSTALLED_THEME_MANIFEST_FALLBACK__ = \{\"themes\":\[\]\}/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = true/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = "layered"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = "contain"/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST_FAMILY = 'capacitor'/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_RUNTIME_HOST_BRIDGE__/);
  assert.match(stagedReleaseIndex, /window\.__AXOLYNC_RUNTIME_STATE_RESET_HOST__/);
  assert.match(stagedReleaseIndex, /hostPlatform: 'android'/);
  assert.match(stagedReleaseIndex, /BRIDGE_RUNTIME_SCRIPT_SRC = '\.\/native-bridge\.js'/);
  assert.match(fs.readFileSync(path.join(publicDir, 'native-bridge.js'), 'utf8'), /nativePromise/);
  assert.match(stagedReleaseIndex, /window\.Capacitor\.nativePromise/);
  assert.match(stagedReleaseIndex, /ensureBridgeRuntimeLoaded = function\(\)/);
  assert.match(stagedReleaseIndex, /invoke\('getHostInfo', \{\}\)/);
  assert.match(stagedReleaseIndex, /invoke\('saveDebugArchiveBase64', \{ fileName, base64Payload \}\)/);
  assert.match(stagedReleaseIndex, /getDiagnostics/);
  assert.equal(fs.existsSync(path.join(publicDir, 'demo')), false);
  assert.equal(fs.existsSync(path.join(publicDir, 'native-service-companions')), false);
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(assetRoot, 'capacitor.config.json'), 'utf8')),
    capacitorConfig,
  );
  assert.deepEqual(
    JSON.parse(fs.readFileSync(path.join(assetRoot, 'capacitor.plugins.json'), 'utf8')),
    result.capacitorPluginRegistry.entries,
  );

  fs.rmSync(tempRoot, { recursive: true, force: true });
});

test('stageBrowserAssets uses the published demo browser bundle as the base payload when demo assets are enabled', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-android-stage-assets-demo-root-'));
  const normalSourceRoot = path.join(tempRoot, 'browser-normal');
  const demoSourceRoot = path.join(tempRoot, 'browser-demo');
  const assetRoot = path.join(tempRoot, 'assets-root');
  const publicDir = path.join(tempRoot, 'public');
  const previousNormal = process.env.AXOLYNC_BUILDER_BROWSER_NORMAL;
  const previousDemo = process.env.AXOLYNC_BUILDER_BROWSER_DEMO;

  writeFile(path.join(normalSourceRoot, 'index.html'), '<!doctype html><title>Normal Axolync</title>');
  writeFile(path.join(normalSourceRoot, 'assets', 'main.js'), 'console.log("normal browser");');
  writeFile(path.join(normalSourceRoot, 'plugins', 'preinstalled', 'manifest.json'), JSON.stringify({
    plugins: [{ id: 'axolync-addon-vibra' }],
  }));
  writeFile(path.join(normalSourceRoot, 'themes', 'preinstalled', 'manifest.json'), JSON.stringify({
    themes: [],
  }));

  writeFile(path.join(demoSourceRoot, 'index.html'), '<!doctype html><title>Demo Axolync</title>');
  writeFile(path.join(demoSourceRoot, 'assets', 'main.js'), 'console.log("demo browser");');
  writeFile(path.join(demoSourceRoot, 'plugins', 'preinstalled', 'manifest.json'), JSON.stringify({
    plugins: [{ id: 'demo-stage1-addon' }],
  }));
  writeFile(path.join(demoSourceRoot, 'themes', 'preinstalled', 'manifest.json'), JSON.stringify({
    themes: [],
  }));

  process.env.AXOLYNC_BUILDER_BROWSER_NORMAL = normalSourceRoot;
  process.env.AXOLYNC_BUILDER_BROWSER_DEMO = demoSourceRoot;

  try {
    const result = stageBrowserAssets({
      assetRoot,
      publicDir,
      buildFlavor: 'release',
      includeDemoAssets: true,
    });

    assert.equal(result.sourceRoot, demoSourceRoot);
    assert.equal(fs.readFileSync(path.join(publicDir, 'assets', 'main.js'), 'utf8'), 'console.log("demo browser");');
    assert.deepEqual(
      JSON.parse(fs.readFileSync(path.join(publicDir, 'plugins', 'preinstalled', 'manifest.json'), 'utf8')),
      { plugins: [{ id: 'demo-stage1-addon' }] },
    );
    const stagedIndex = fs.readFileSync(path.join(publicDir, 'index.html'), 'utf8');
    assert.match(stagedIndex, /Demo Axolync/);
    assert.match(stagedIndex, /"demo-stage1-addon"/);
    assert.doesNotMatch(stagedIndex, /"axolync-addon-vibra"/);
  } finally {
    if (previousNormal === undefined) delete process.env.AXOLYNC_BUILDER_BROWSER_NORMAL;
    else process.env.AXOLYNC_BUILDER_BROWSER_NORMAL = previousNormal;
    if (previousDemo === undefined) delete process.env.AXOLYNC_BUILDER_BROWSER_DEMO;
    else process.env.AXOLYNC_BUILDER_BROWSER_DEMO = previousDemo;
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
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
