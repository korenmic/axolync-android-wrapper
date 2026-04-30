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
  assert.equal(layout.compatibilityMode, true);
  assert.equal(android.authorityPath, 'wrappers/capacitor/android');
  assert.equal(android.gradleProjectPath, 'app');
  assert.equal(fs.existsSync(path.join(repoRoot, android.authorityPath, 'README.md')), true);
  assert.equal(fs.existsSync(path.join(repoRoot, 'wrappers', 'capacitor', 'shared', 'README.md')), true);
  assert.equal(fs.existsSync(path.join(repoRoot, 'wrappers', 'capacitor', 'ios', 'README.md')), true);
});
