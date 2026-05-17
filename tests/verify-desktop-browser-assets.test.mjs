import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { verifyDesktopBrowserAssets } from '../scripts/verify-desktop-browser-assets.mjs';

function writeFile(filePath, contents = '') {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, contents);
}

test('desktop browser asset verifier proves current runtime strings before publication', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-desktop-assets-'));
  try {
    const appDir = path.join(tempRoot, 'app');
    writeFile(path.join(appDir, 'index.html'), '<!doctype html><script src="./assets/main.js"></script>');
    writeFile(path.join(appDir, 'assets', 'main.js'), [
      'const query_methods = true;',
      'const default_placement = true;',
      'const VITE_AXOLYNC_ARTIFACT_REPORT_ID = "report";',
    ].join('\n'));

    const result = verifyDesktopBrowserAssets({
      appDir,
      requiredStrings: ['query_methods', 'default_placement', 'VITE_AXOLYNC_ARTIFACT_REPORT_ID'],
    });

    assert.equal(result.ok, true);
    assert.deepEqual(result.matchedStrings, ['query_methods', 'default_placement', 'VITE_AXOLYNC_ARTIFACT_REPORT_ID']);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('desktop browser asset verifier proves required sidecar files before publication', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-desktop-sidecar-assets-'));
  try {
    const appDir = path.join(tempRoot, 'app');
    writeFile(path.join(appDir, 'index.html'), '<!doctype html><script src="./assets/main.js"></script>');
    writeFile(path.join(appDir, 'assets', 'main.js'), 'const current_runtime = true;');
    writeFile(path.join(appDir, 'axolync', 'build-artifact-identity.json'), '{"artifactReportId":"report"}');

    const result = verifyDesktopBrowserAssets({
      appDir,
      requiredFiles: ['axolync/build-artifact-identity.json'],
    });

    assert.equal(result.ok, true);
    assert.deepEqual(result.matchedFiles, ['axolync/build-artifact-identity.json']);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('desktop browser asset verifier rejects staged roots missing required sidecar files', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-missing-sidecar-assets-'));
  try {
    const appDir = path.join(tempRoot, 'app');
    writeFile(path.join(appDir, 'index.html'), '<!doctype html><script src="./assets/main.js"></script>');
    writeFile(path.join(appDir, 'assets', 'main.js'), 'const current_runtime = true;');

    const result = verifyDesktopBrowserAssets({
      appDir,
      requiredFiles: ['axolync/build-artifact-identity.json'],
    });

    assert.equal(result.ok, false);
    assert.match(result.failures.join('\n'), /missing required file: axolync\/build-artifact-identity\.json/);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('desktop browser asset verifier rejects stale bundles missing required runtime strings', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-stale-desktop-assets-'));
  try {
    const appDir = path.join(tempRoot, 'app');
    writeFile(path.join(appDir, 'index.html'), '<!doctype html><script src="./assets/main.js"></script>');
    writeFile(path.join(appDir, 'assets', 'main.js'), 'const old_runtime = true;');

    const result = verifyDesktopBrowserAssets({
      appDir,
      requiredStrings: ['query_methods'],
    });

    assert.equal(result.ok, false);
    assert.match(result.failures.join('\n'), /missing required runtime string: query_methods/);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});
