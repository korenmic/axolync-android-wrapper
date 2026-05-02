import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { verifyWrapperTypedTopology } from '../scripts/verify-wrapper-typed-topology.mjs';

function writeFile(root, relativePath, content = `${relativePath}\n`) {
  const absolutePath = path.join(root, relativePath);
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });
  fs.writeFileSync(absolutePath, content);
}

function writeJson(root, relativePath, value) {
  writeFile(root, relativePath, `${JSON.stringify(value, null, 2)}\n`);
}

function makeRoot() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-wrapper-typed-topology-'));
}

test('typed topology verifier rejects interim desktop template roots as active source', () => {
  const root = makeRoot();
  writeJson(root, 'package.json', { name: 'axolync-android-wrapper' });
  writeJson(root, 'config/wrapper-layout.json', {
    targetRepo: 'axolync-platform-wrapper',
    families: {
      capacitor: {
        root: 'wrappers/capacitor',
        android: { authorityPath: 'wrappers/capacitor/android' },
      },
      tauri: {
        root: 'templates/desktop/tauri',
        desktop: { templateRoot: 'templates/desktop/tauri' },
      },
      electron: {
        root: 'templates/desktop/electron',
        desktop: { templateRoot: 'templates/desktop/electron' },
      },
    },
  });
  writeFile(root, 'wrappers/capacitor/android/app/build.gradle.kts');
  writeFile(root, 'templates/desktop/tauri/package.json');
  writeFile(root, 'templates/desktop/electron/package.json');

  const result = verifyWrapperTypedTopology({ root });

  assert.equal(result.ok, false);
  assert.match(result.failures.join('\n'), /wrappers\/mobile\/capacitor/);
  assert.match(result.failures.join('\n'), /templates\/desktop\/tauri/);
  assert.match(result.failures.join('\n'), /templates\/desktop\/electron/);
});

test('typed topology verifier accepts active wrapper families under wrappers type roots', () => {
  const root = makeRoot();
  writeJson(root, 'package.json', { name: 'axolync-platform-wrapper' });
  writeJson(root, 'config/wrapper-layout.json', {
    targetRepo: 'axolync-platform-wrapper',
    families: {
      capacitor: {
        root: 'wrappers/mobile/capacitor',
        android: { authorityPath: 'wrappers/mobile/capacitor/android' },
        shared: { authorityPath: 'wrappers/mobile/capacitor/shared' },
        ios: { authorityPath: 'wrappers/mobile/capacitor/ios', support: 'placeholder-only' },
      },
      tauri: {
        root: 'wrappers/desktop/tauri',
        desktop: { templateRoot: 'wrappers/desktop/tauri/workspace-template' },
      },
      electron: {
        root: 'wrappers/desktop/electron',
        desktop: { templateRoot: 'wrappers/desktop/electron/workspace-template' },
      },
    },
  });
  for (const relativePath of [
    'wrappers/mobile/capacitor/android/app/build.gradle.kts',
    'wrappers/mobile/capacitor/shared/capability-states.json',
    'wrappers/mobile/capacitor/ios/README.md',
    'wrappers/desktop/tauri/workspace-template/package.json',
    'wrappers/desktop/electron/workspace-template/package.json',
  ]) {
    writeFile(root, relativePath);
  }

  const result = verifyWrapperTypedTopology({ root, requireFinalPackageName: true });

  assert.equal(result.ok, true, result.failures.join('\n'));
  assert.deepEqual(result.failures, []);
});

test('typed topology verifier can enforce final package identity after rename cutover', () => {
  const root = makeRoot();
  writeJson(root, 'package.json', { name: 'axolync-android-wrapper' });
  writeJson(root, 'config/wrapper-layout.json', {
    targetRepo: 'axolync-platform-wrapper',
    families: {
      capacitor: { root: 'wrappers/mobile/capacitor', android: { authorityPath: 'wrappers/mobile/capacitor/android' } },
      tauri: { root: 'wrappers/desktop/tauri', desktop: { templateRoot: 'wrappers/desktop/tauri/workspace-template' } },
      electron: { root: 'wrappers/desktop/electron', desktop: { templateRoot: 'wrappers/desktop/electron/workspace-template' } },
    },
  });
  writeFile(root, 'wrappers/mobile/capacitor/android/app/build.gradle.kts');
  writeFile(root, 'wrappers/desktop/tauri/workspace-template/package.json');
  writeFile(root, 'wrappers/desktop/electron/workspace-template/package.json');

  const result = verifyWrapperTypedTopology({ root, requireFinalPackageName: true });

  assert.equal(result.ok, false);
  assert.match(result.failures.join('\n'), /package name must be axolync-platform-wrapper/);
});
