import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { verifyWrapperSourceOwnership } from '../scripts/verify-wrapper-source-ownership.mjs';

function writeJson(filePath, value) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`);
}

function makeFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-wrapper-source-ownership-'));
  writeJson(path.join(root, 'config', 'wrapper-layout.json'), {
    families: {
      capacitor: {
        android: { authorityPath: 'wrappers/mobile/capacitor/android' },
        ios: { authorityPath: 'wrappers/mobile/capacitor/ios', support: 'placeholder-only' },
      },
    },
  });
  return root;
}

test('wrapper source ownership verifier rejects placeholder-only Android and missing desktop templates', () => {
  const root = makeFixture();
  fs.mkdirSync(path.join(root, 'wrappers', 'capacitor', 'android'), { recursive: true });
  fs.writeFileSync(path.join(root, 'wrappers', 'capacitor', 'android', 'README.md'), 'placeholder\n');
  fs.mkdirSync(path.join(root, 'native-service-companions', 'host-protocol'), { recursive: true });
  fs.writeFileSync(path.join(root, 'native-service-companions', 'host-protocol', 'capability-states.json'), '{"states":[]}\n');
  fs.mkdirSync(path.join(root, 'native-service-companions', 'deployment'), { recursive: true });
  fs.writeFileSync(path.join(root, 'native-service-companions', 'deployment', 'README.md'), 'deployment\n');
  fs.mkdirSync(path.join(root, 'native-service-companions', 'diagnostics'), { recursive: true });
  fs.writeFileSync(path.join(root, 'native-service-companions', 'diagnostics', 'README.md'), 'diagnostics\n');

  const result = verifyWrapperSourceOwnership({ root });

  assert.equal(result.ok, false);
  assert.match(result.failures.join('\n'), /placeholder-only/);
  assert.match(result.failures.join('\n'), /canonical Tauri template source missing/);
  assert.match(result.failures.join('\n'), /canonical Electron template source missing/);
});

test('wrapper source ownership verifier accepts complete canonical source fixture', () => {
  const root = makeFixture();
  for (const relativePath of [
    'wrappers/mobile/capacitor/android/app/build.gradle.kts',
    'wrappers/mobile/capacitor/android/app/src/main/AndroidManifest.xml',
    'wrappers/mobile/capacitor/android/app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt',
    'wrappers/mobile/capacitor/android/capacitor.config.json',
    'wrappers/mobile/capacitor/android/settings.gradle.kts',
    'wrappers/desktop/tauri/workspace-template/package.json',
    'wrappers/desktop/tauri/workspace-template/src-tauri/Cargo.toml',
    'wrappers/desktop/tauri/workspace-template/src-tauri/tauri.conf.json',
    'wrappers/desktop/tauri/workspace-template/src-tauri/src/main.rs',
    'templates/desktop/electron/package.json',
    'templates/desktop/electron/main.cjs',
    'templates/desktop/electron/preload.cjs',
    'templates/desktop/electron/nativeServiceCompanionHost.cjs',
    'native-service-companions/host-protocol/capability-states.json',
    'native-service-companions/deployment/README.md',
    'native-service-companions/diagnostics/README.md',
  ]) {
    fs.mkdirSync(path.dirname(path.join(root, relativePath)), { recursive: true });
    fs.writeFileSync(path.join(root, relativePath), `${relativePath}\n`);
  }

  const result = verifyWrapperSourceOwnership({ root });

  assert.equal(result.ok, true);
  assert.deepEqual(result.failures, []);
});

test('wrapper repo source ownership proof passes for builder consumption', () => {
  const repoRoot = path.resolve(import.meta.dirname, '..');
  const packageJson = JSON.parse(fs.readFileSync(path.join(repoRoot, 'package.json'), 'utf8'));
  const result = verifyWrapperSourceOwnership({ root: repoRoot });

  assert.equal(packageJson.scripts['proof:wrapper-source'], 'node scripts/verify-wrapper-source-ownership.mjs');
  assert.equal(result.ok, true);
  assert.deepEqual(result.failures, []);
  assert.deepEqual(result.paths, {
    androidRoot: 'wrappers/mobile/capacitor/android',
    tauriRoot: 'wrappers/desktop/tauri/workspace-template',
    electronRoot: 'templates/desktop/electron',
    nativeRoot: 'native-service-companions',
  });
});

test('wrapper repo publishes canonical Tauri desktop template source', () => {
  const repoRoot = path.resolve(import.meta.dirname, '..');
  for (const relativePath of [
    'wrappers/desktop/tauri/workspace-template/package.json',
    'wrappers/desktop/tauri/workspace-template/src-tauri/Cargo.toml',
    'wrappers/desktop/tauri/workspace-template/src-tauri/tauri.conf.json',
    'wrappers/desktop/tauri/workspace-template/src-tauri/src/main.rs',
    'wrappers/desktop/tauri/workspace-template/src-tauri/src/native_service_companion.rs',
  ]) {
    assert.equal(fs.existsSync(path.join(repoRoot, relativePath)), true, `missing ${relativePath}`);
  }
});

test('wrapper repo publishes canonical Electron desktop template source', () => {
  const repoRoot = path.resolve(import.meta.dirname, '..');
  for (const relativePath of [
    'templates/desktop/electron/package.json',
    'templates/desktop/electron/main.cjs',
    'templates/desktop/electron/preload.cjs',
    'templates/desktop/electron/nativeServiceCompanionHost.cjs',
  ]) {
    assert.equal(fs.existsSync(path.join(repoRoot, relativePath)), true, `missing ${relativePath}`);
  }
});

test('wrapper-owned native companion host source remains generic and payload-free', () => {
  const repoRoot = path.resolve(import.meta.dirname, '..');
  const ownedHostFiles = [
    'native-service-companions/host-protocol/capability-states.json',
    'wrappers/desktop/tauri/workspace-template/src-tauri/src/native_service_companion.rs',
    'templates/desktop/electron/nativeServiceCompanionHost.cjs',
    'wrappers/mobile/capacitor/android/app/src/main/kotlin/com/axolync/android/bridge/AxolyncNativeServiceCompanionHostPlugin.kt',
  ];
  const combined = ownedHostFiles
    .map((relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8'))
    .join('\n');

  assert.match(combined, /native service companion|NativeServiceCompanion|capability/i);
  assert.match(combined, /db\.sqlite3/);
  assert.doesNotMatch(combined, /vibra_proxy|lrclib_local|axolync-addon-vibra|axolync-addon-lrclib/);
  assert.equal(fs.existsSync(path.join(repoRoot, 'native-service-companions', 'axolync-addon-vibra')), false);
  assert.equal(fs.existsSync(path.join(repoRoot, 'native-service-companions', 'axolync-addon-lrclib')), false);
});
