import { execFileSync, spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEBUG_DEMO_ENTRIES = [
  'assets/public/demo/player.html',
  'assets/public/demo/plugins/demo-lyricflow.js',
  'assets/public/demo/assets/house_of_the_rising_sun_instrumental.ogg',
];

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
  const expectedBuildFlavor = detectExpectedBuildFlavor(resolved);
  const shouldIncludeDemoAssets = expectedBuildFlavor === 'debug';
  const indexHtml = readZipEntry(resolved, 'assets/public/index.html');
  const syncWorker = readZipEntry(resolved, 'assets/public/workers/syncengineBridgeWorker.js');
  const lyricWorker = readZipEntry(resolved, 'assets/public/workers/lyricflowBridgeWorker.js');
  const zipEntries = listZipEntries(resolved);

  assertIncludes(
    indexHtml,
    `window.__AXOLYNC_BUILD_FLAVOR = "${expectedBuildFlavor}";`,
    `APK is missing staged ${expectedBuildFlavor} build flavor override: ${resolved}`,
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
