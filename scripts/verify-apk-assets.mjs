import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEBUG_DEMO_ENTRIES = [
  'assets/public/demo/player.html',
  'assets/public/demo/plugins/demo-lyricflow.js',
  'assets/public/demo/assets/demo_track.wav',
  'assets/public/demo/assets/house_of_the_rising_sun_instrumental.ogg',
];

function readZipEntry(apkPath, entryPath) {
  return execFileSync('unzip', ['-p', apkPath, entryPath], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

function assertIncludes(haystack, needle, message) {
  if (!haystack.includes(needle)) {
    throw new Error(message);
  }
}

function assertExcludes(haystack, needle, message) {
  if (haystack.includes(needle)) {
    throw new Error(message);
  }
}

function listZipEntries(apkPath) {
  return execFileSync('unzip', ['-Z1', apkPath], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).split('\n').map((line) => line.trim()).filter(Boolean);
}

function detectExpectedRuntimeProfile(apkPath) {
  const lower = path.basename(apkPath).toLowerCase();
  return lower.includes('release') ? 'release' : 'debug';
}

export function assertDemoAssetState(zipEntries, shouldIncludeDemoAssets, resolved) {
  const hasAnyDemoTreeEntry = zipEntries.some((entry) => entry.startsWith('assets/public/demo/'));
  for (const expectedEntry of DEBUG_DEMO_ENTRIES) {
    const hasEntry = zipEntries.includes(expectedEntry);
    if (shouldIncludeDemoAssets && !hasEntry) {
      throw new Error(`APK is missing required demo asset in debug profile: ${resolved} (${expectedEntry})`);
    }
    if (!shouldIncludeDemoAssets && hasEntry) {
      throw new Error(`APK unexpectedly ships demo asset in release profile: ${resolved} (${expectedEntry})`);
    }
  }

  if (!shouldIncludeDemoAssets && hasAnyDemoTreeEntry) {
    throw new Error(`APK unexpectedly ships demo asset tree in release profile: ${resolved}`);
  }
}

function verifyApk(apkPath) {
  const resolved = path.resolve(apkPath);
  const expectedRuntimeProfile = detectExpectedRuntimeProfile(resolved);
  const shouldIncludeDemoAssets = expectedRuntimeProfile === 'debug';
  const indexHtml = readZipEntry(resolved, 'assets/public/index.html');
  const syncWorker = readZipEntry(resolved, 'assets/public/workers/syncengineBridgeWorker.js');
  const lyricWorker = readZipEntry(resolved, 'assets/public/workers/lyricflowBridgeWorker.js');
  const zipEntries = listZipEntries(resolved);

  assertIncludes(
    indexHtml,
    `window.__AXOLYNC_RUNTIME_PROFILE = "${expectedRuntimeProfile}";`,
    `APK is missing staged ${expectedRuntimeProfile} runtime profile override: ${resolved}`,
  );

  assertExcludes(
    syncWorker,
    'require("./capacitorSyncFallback.js")',
    `APK ships stale SyncEngine worker CommonJS import: ${resolved}`,
  );
  assertIncludes(
    syncWorker,
    'function buildCapacitorSyncFallbackResult(',
    `APK is missing inlined Capacitor SyncEngine fallback helper: ${resolved}`,
  );

  assertExcludes(
    lyricWorker,
    'require("./directLrcLibFallback.js")',
    `APK ships stale LyricFlow worker CommonJS import: ${resolved}`,
  );
  assertIncludes(
    lyricWorker,
    'function fetchDirectLrcLibLyrics(',
    `APK is missing inlined direct LRCLIB helper: ${resolved}`,
  );

  const hasLegacyAssetTree = zipEntries.some((entry) => entry.startsWith('assets/axolync-browser/'));

  if (hasLegacyAssetTree) {
    throw new Error(`APK unexpectedly ships legacy axolync-browser asset tree: ${resolved}`);
  }
  assertDemoAssetState(zipEntries, shouldIncludeDemoAssets, resolved);

  console.log(`[verify-apk-assets] ok ${resolved}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  const apkPaths = process.argv.slice(2);
  if (apkPaths.length === 0) {
    throw new Error('Usage: node scripts/verify-apk-assets.mjs <apk-path> [more-apk-paths...]');
  }

  for (const apkPath of apkPaths) {
    verifyApk(apkPath);
  }
}
