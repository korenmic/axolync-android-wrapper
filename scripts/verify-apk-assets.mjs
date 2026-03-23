import { execFileSync } from 'node:child_process';
import path from 'node:path';

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

  const hasDemoPlayer = zipEntries.includes('assets/public/demo/player.html');
  const hasDemoLyricflow = zipEntries.includes('assets/public/demo/plugins/demo-lyricflow.js');
  if (shouldIncludeDemoAssets) {
    if (!hasDemoPlayer || !hasDemoLyricflow) {
      throw new Error(`APK is missing staged demo assets in debug profile: ${resolved}`);
    }
  } else if (hasDemoPlayer || hasDemoLyricflow) {
    throw new Error(`APK unexpectedly ships demo assets in release profile: ${resolved}`);
  }

  console.log(`[verify-apk-assets] ok ${resolved}`);
}

const apkPaths = process.argv.slice(2);
if (apkPaths.length === 0) {
  throw new Error('Usage: node scripts/verify-apk-assets.mjs <apk-path> [more-apk-paths...]');
}

for (const apkPath of apkPaths) {
  verifyApk(apkPath);
}
