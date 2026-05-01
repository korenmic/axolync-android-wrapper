import { execFileSync, spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEBUG_DEMO_ENTRIES = [
  'assets/public/demo/player.html',
  'assets/public/demo/plugins/demo-lyricflow.js',
  'assets/public/demo/assets/house_of_the_rising_sun_instrumental.ogg',
];

const LRCLIB_NATIVE_REQUIRED_ENTRIES = [
  'assets/public/native-service-companions/manifest.json',
  'assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/operator.json',
  'assets/public/plugins/preinstalled/axolync-addon-lrclib.zip',
];

const LRCLIB_NATIVE_FORBIDDEN_EXPLODED_DB_ENTRIES = [
  'assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/native/shared/lrclib_local/assets/db.sqlite3.br',
  'assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/native/shared/lrclib_local/assets/db.sqlite3.br.provenance.json',
];

const EXPLODED_NATIVE_PAYLOAD_ARCHIVE_PATTERN = /^assets\/public\/native-service-companions\/.+\/native\/shared\/.+\.(?:br|zip|tar|gz|xz|7z)$/u;

const ZIP_LIST_POWERSHELL = [
  "$ErrorActionPreference = 'Stop'",
  'Add-Type -AssemblyName System.IO.Compression.FileSystem',
  '$zip = [System.IO.Compression.ZipFile]::OpenRead($env:AXOLYNC_ARCHIVE_PATH)',
  'try {',
  '  foreach ($entry in $zip.Entries) { [Console]::Out.WriteLine($entry.FullName) }',
  '} finally {',
  '  $zip.Dispose()',
  '}'
].join('; ');

const ZIP_READ_POWERSHELL = [
  "$ErrorActionPreference = 'Stop'",
  'Add-Type -AssemblyName System.IO.Compression.FileSystem',
  '$zip = [System.IO.Compression.ZipFile]::OpenRead($env:AXOLYNC_ARCHIVE_PATH)',
  'try {',
  '  $entry = $zip.GetEntry($env:AXOLYNC_ARCHIVE_ENTRY)',
  '  if ($null -eq $entry) {',
  "    Write-Error ('missing zip entry: ' + $env:AXOLYNC_ARCHIVE_ENTRY)",
  '    exit 2',
  '  }',
  '  $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)',
  '  try {',
  '    [Console]::Out.Write($reader.ReadToEnd())',
  '  } finally {',
  '    $reader.Dispose()',
  '  }',
  '} finally {',
  '  $zip.Dispose()',
  '}'
].join('; ');

function commandAvailable(commandName) {
  const result = process.platform === 'win32'
    ? spawnSync('where.exe', [commandName], { stdio: 'ignore' })
    : spawnSync('sh', ['-lc', `command -v ${commandName}`], { stdio: 'ignore' });
  return result.status === 0;
}

function readZipEntry(apkPath, entryPath) {
  if (commandAvailable('unzip')) {
    return execFileSync('unzip', ['-p', apkPath, entryPath], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  }
  if (process.platform === 'win32') {
    return execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', ZIP_READ_POWERSHELL], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      env: {
        ...process.env,
        AXOLYNC_ARCHIVE_PATH: apkPath,
        AXOLYNC_ARCHIVE_ENTRY: entryPath,
      },
    });
  }
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
  if (commandAvailable('unzip')) {
    return execFileSync('unzip', ['-Z1', apkPath], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).split('\n').map((line) => line.trim()).filter(Boolean);
  }
  if (process.platform === 'win32') {
    return execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', ZIP_LIST_POWERSHELL], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      env: {
        ...process.env,
        AXOLYNC_ARCHIVE_PATH: apkPath,
      },
    }).split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  }
  return execFileSync('unzip', ['-Z1', apkPath], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).split('\n').map((line) => line.trim()).filter(Boolean);
}

function detectExpectedBuildFlavor(apkPath) {
  const lower = path.basename(apkPath).toLowerCase();
  return lower.includes('release') ? 'release' : 'debug';
}

function detectExpectedArtifactFlavor(apkPath) {
  const normalized = path.resolve(apkPath).replaceAll('\\', '/').toLowerCase();
  return normalized.includes('/demo/') || path.basename(normalized).includes('-demo-') ? 'demo' : 'normal';
}

export function resolveExpectedLrclibNativeAssetState(apkPath) {
  if (detectExpectedArtifactFlavor(apkPath) === 'demo') return false;
  const envOverride = String(process.env.AXOLYNC_ANDROID_EXPECT_LRCLIB_NATIVE_ASSETS ?? '').trim().toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(envOverride)) return true;
  if (['0', 'false', 'no', 'off'].includes(envOverride)) return false;
  const resolved = path.resolve(apkPath).replaceAll('\\', '/').toLowerCase();
  return path.basename(resolved).includes('lrclib-native') || resolved.includes('/lrclib-native/');
}

export function resolveExpectedNotificationCaptureState() {
  const envOverride = String(process.env.AXOLYNC_ANDROID_NOTIFICATION_CAPTURE_ENABLED ?? '').trim().toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(envOverride)) return true;
  if (['0', 'false', 'no', 'off'].includes(envOverride)) return false;
  return false;
}

export function assertDemoAssetState(zipEntries, shouldIncludeDemoAssets, resolved) {
  const hasAnyDemoTreeEntry = zipEntries.some((entry) => entry.startsWith('assets/public/demo/'));
  for (const expectedEntry of DEBUG_DEMO_ENTRIES) {
    const hasEntry = zipEntries.includes(expectedEntry);
    if (shouldIncludeDemoAssets && !hasEntry) {
      throw new Error(`APK is missing required demo asset in demo-enabled profile: ${resolved} (${expectedEntry})`);
    }
    if (!shouldIncludeDemoAssets && hasEntry) {
      throw new Error(`APK unexpectedly ships demo asset in demo-free profile: ${resolved} (${expectedEntry})`);
    }
  }

  if (!shouldIncludeDemoAssets && hasAnyDemoTreeEntry) {
    throw new Error(`APK unexpectedly ships demo asset tree in demo-free profile: ${resolved}`);
  }
}

export function assertLrclibNativeAssetState(zipEntries, shouldIncludeLrclibNative, resolved) {
  const hasAnyLrclibNativeEntry = zipEntries.some((entry) => (
    entry.startsWith('assets/public/native-service-companions/axolync-addon-lrclib/lrclib_local/capacitor/')
  ));
  if (!shouldIncludeLrclibNative && !hasAnyLrclibNativeEntry) return;
  if (!shouldIncludeLrclibNative && hasAnyLrclibNativeEntry) {
    throw new Error(`APK unexpectedly ships LRCLIB native companion assets in a remote-only profile: ${resolved}`);
  }
  for (const expectedEntry of LRCLIB_NATIVE_REQUIRED_ENTRIES) {
    if (!zipEntries.includes(expectedEntry)) {
      throw new Error(`APK is missing required LRCLIB native asset in native-capable profile: ${resolved} (${expectedEntry})`);
    }
  }
  for (const forbiddenEntry of LRCLIB_NATIVE_FORBIDDEN_EXPLODED_DB_ENTRIES) {
    if (zipEntries.includes(forbiddenEntry)) {
      throw new Error(`APK ships a duplicate exploded LRCLIB DB payload outside the preinstalled addon zip: ${resolved} (${forbiddenEntry})`);
    }
  }
}

export function assertNoDuplicateCompressedNativePayloadRoots(zipEntries, resolved) {
  const forbiddenEntries = zipEntries.filter((entry) => EXPLODED_NATIVE_PAYLOAD_ARCHIVE_PATTERN.test(entry));
  if (forbiddenEntries.length > 0) {
    throw new Error(
      `APK ships compressed native payload archives outside addon zips or descriptor-only companion staging: ${resolved} (${forbiddenEntries.join(', ')})`,
    );
  }
}

export function assertNativeCompanionDescriptorsParse(apkPath, zipEntries, resolved) {
  const manifestEntry = 'assets/public/native-service-companions/manifest.json';
  if (!zipEntries.includes(manifestEntry)) return;
  let manifest;
  try {
    manifest = JSON.parse(readZipEntry(apkPath, manifestEntry));
  } catch (error) {
    throw new Error(`APK ships an invalid native companion manifest that can break startup: ${resolved} (${error.message || error})`);
  }
  for (const companion of manifest.companions || []) {
    const entrypoint = String(companion?.entrypoint || '').trim().replaceAll('\\', '/').replace(/^\/+/u, '');
    if (!entrypoint) continue;
    const descriptorEntry = `assets/public/native-service-companions/${entrypoint}`;
    if (!zipEntries.includes(descriptorEntry)) {
      throw new Error(`APK native companion descriptor is missing: ${resolved} (${descriptorEntry})`);
    }
    let descriptor;
    try {
      descriptor = JSON.parse(readZipEntry(apkPath, descriptorEntry));
    } catch (error) {
      throw new Error(`APK ships an invalid native companion descriptor that can break startup: ${resolved} (${descriptorEntry}: ${error.message || error})`);
    }
    if (!String(descriptor.runtime_operator_kind || '').trim()) {
      throw new Error(`APK native companion descriptor is missing runtime_operator_kind: ${resolved} (${descriptorEntry})`);
    }
  }
}

function verifyApk(apkPath) {
  const resolved = path.resolve(apkPath);
  const expectedBuildFlavor = detectExpectedBuildFlavor(resolved);
  const expectedArtifactFlavor = detectExpectedArtifactFlavor(resolved);
  const shouldIncludeDemoAssets = expectedArtifactFlavor === 'demo' || expectedBuildFlavor === 'debug';
  const shouldIncludeLrclibNative = resolveExpectedLrclibNativeAssetState(resolved);
  const shouldEnableNotificationCapture = resolveExpectedNotificationCaptureState();
  const indexHtml = readZipEntry(resolved, 'assets/public/index.html');
  const syncWorker = readZipEntry(resolved, 'assets/public/workers/syncengineBridgeWorker.js');
  const lyricWorker = readZipEntry(resolved, 'assets/public/workers/lyricflowBridgeWorker.js');
  const zipEntries = listZipEntries(resolved);
  assertNativeCompanionDescriptorsParse(resolved, zipEntries, resolved);

  assertIncludes(
    indexHtml,
    `window.__AXOLYNC_BUILD_FLAVOR = "${expectedBuildFlavor}";`,
    `APK is missing staged ${expectedBuildFlavor} build flavor override: ${resolved}`,
  );
  assertIncludes(
    indexHtml,
    `window.__AXOLYNC_NOTIFICATION_CAPTURE_ENABLED = ${shouldEnableNotificationCapture ? 'true' : 'false'};`,
    `APK notification-capture feature flag does not match the expected profile state: ${resolved}`,
  );
  assertIncludes(
    indexHtml,
    `window.__AXOLYNC_ANDROID_NOTIFICATION_CAPTURE_ENABLED = ${shouldEnableNotificationCapture ? 'true' : 'false'};`,
    `APK Android notification-capture feature flag does not match the expected profile state: ${resolved}`,
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
  assertLrclibNativeAssetState(zipEntries, shouldIncludeLrclibNative, resolved);
  assertNoDuplicateCompressedNativePayloadRoots(zipEntries, resolved);

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
