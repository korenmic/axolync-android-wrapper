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
        android: { authorityPath: 'wrappers/capacitor/android' },
        ios: { authorityPath: 'wrappers/capacitor/ios', support: 'placeholder-only' },
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
    'wrappers/capacitor/android/app/build.gradle.kts',
    'wrappers/capacitor/android/app/src/main/AndroidManifest.xml',
    'wrappers/capacitor/android/app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt',
    'wrappers/capacitor/android/capacitor.config.json',
    'wrappers/capacitor/android/settings.gradle.kts',
    'templates/desktop/tauri/package.json',
    'templates/desktop/tauri/src-tauri/Cargo.toml',
    'templates/desktop/tauri/src-tauri/tauri.conf.json',
    'templates/desktop/tauri/src-tauri/src/main.rs',
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
