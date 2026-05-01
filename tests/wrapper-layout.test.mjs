import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');

test('wrapper layout exposes Capacitor Android under the neutral wrapper-family authority path', () => {
  const layout = JSON.parse(
    fs.readFileSync(path.join(repoRoot, 'config', 'wrapper-layout.json'), 'utf8'),
  );
  const android = layout.families.capacitor.android;

  assert.equal(layout.targetRepo, 'axolync-platform-wrapper');
  assert.equal(layout.compatibilityMode, false);
  assert.equal(android.authorityPath, 'wrappers/capacitor/android');
  assert.equal(android.projectRoot, 'wrappers/capacitor/android');
  assert.equal(android.gradleProjectPath, 'wrappers/capacitor/android/app');
  assert.equal(android.legacyCompatibilityMode, 'root-shim');
  assert.equal(fs.existsSync(path.join(repoRoot, android.authorityPath, 'app', 'build.gradle.kts')), true);
  assert.equal(fs.existsSync(path.join(repoRoot, 'wrappers', 'capacitor', 'shared', 'README.md')), true);
  assert.equal(fs.existsSync(path.join(repoRoot, 'wrappers', 'capacitor', 'ios', 'README.md')), true);
});

test('promoted layout canonical path points at required Android and Capacitor build files', () => {
  const layout = JSON.parse(
    fs.readFileSync(path.join(repoRoot, 'config', 'wrapper-layout.json'), 'utf8'),
  );
  const android = layout.families.capacitor.android;
  const required = [
    path.join(android.gradleProjectPath, 'build.gradle.kts'),
    'settings.gradle.kts',
    'capacitor.config.json',
    path.join(android.nativeSourcePath, 'activities', 'MainActivity.kt'),
    path.join(android.nativeSourcePath, 'bridge', 'AxolyncNativeServiceCompanionHostPlugin.kt'),
  ];

  assert.equal(layout.compatibilityMode, false);
  for (const relativePath of required) {
    assert.equal(fs.existsSync(path.join(repoRoot, relativePath)), true, `missing ${relativePath}`);
  }
});
