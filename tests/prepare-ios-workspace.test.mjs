import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  detectIosHostAvailability,
  prepareIosWorkspace,
  verifyIosWorkspace,
} from '../scripts/prepare-ios-workspace.mjs';

function writeFile(filePath, contents) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, contents, 'utf8');
}

function seedIosWorkspace(tempRoot) {
  writeFile(path.join(tempRoot, 'ios', 'App', 'App.xcodeproj', 'project.pbxproj'), 'pbxproj');
  writeFile(path.join(tempRoot, 'ios', 'App', 'App.xcworkspace', 'xcshareddata', 'IDEWorkspaceChecks.plist'), 'plist');
  writeFile(path.join(tempRoot, 'ios', 'App', 'App', 'Info.plist'), 'plist');
  writeFile(path.join(tempRoot, 'ios', 'App', 'App', 'capacitor.config.json'), '{}');
  writeFile(path.join(tempRoot, 'ios', 'App', 'Podfile'), 'platform :ios');
  writeFile(path.join(tempRoot, 'ios', 'App', 'App', 'public', 'index.html'), '<!doctype html><title>iOS</title>');
}

test('verifyIosWorkspace reports workspace-only metadata on Linux-style hosts', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-ios-verify-'));
  seedIosWorkspace(tempRoot);

  const metadata = verifyIosWorkspace({
    repoRoot: tempRoot,
    runtimeProfile: 'release',
    includeDemoAssets: false,
    platform: 'linux',
    arch: 'x64',
  });

  assert.equal(metadata.workspaceGenerated, true);
  assert.equal(metadata.hostPlatform, 'linux-x64');
  assert.equal(metadata.hostAvailability, 'workspace-only');
  assert.equal(metadata.requiresMacHost, true);
  assert.equal(metadata.runnable, false);
  assert.equal(metadata.installable, false);

  fs.rmSync(tempRoot, { recursive: true, force: true });
});

test('prepareIosWorkspace stages assets, adds ios when missing, and syncs ios before verification', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'axolync-ios-prepare-'));
  const sourceRoot = path.join(tempRoot, 'browser-dist');
  const publicDir = path.join(tempRoot, 'app', 'src', 'main', 'assets', 'public');
  writeFile(path.join(sourceRoot, 'index.html'), '<!doctype html><title>Axolync</title>');
  writeFile(path.join(sourceRoot, 'assets', 'main.js'), 'console.log("ios");');

  const seenCommands = [];
  const metadata = prepareIosWorkspace({
    repoRoot: tempRoot,
    sourceRoot,
    publicDir,
    runtimeProfile: 'release',
    includeDemoAssets: false,
    runCapCommand(args) {
      seenCommands.push(args.join(' '));
      if (args[0] === 'add' && args[1] === 'ios') {
        seedIosWorkspace(tempRoot);
      }
    },
  });

  assert.deepEqual(seenCommands, ['add ios', 'sync ios']);
  assert.equal(fs.existsSync(path.join(publicDir, 'index.html')), true);
  assert.equal(fs.existsSync(path.join(publicDir, 'demo')), false);
  assert.equal(metadata.workspaceGenerated, true);
  assert.equal(metadata.runtimeProfile, 'release');
  assert.equal(metadata.includeDemoAssets, false);

  fs.rmSync(tempRoot, { recursive: true, force: true });
});

test('detectIosHostAvailability distinguishes Linux workspace-only hosts from mac-host paths', () => {
  assert.equal(detectIosHostAvailability('linux'), 'workspace-only');
  assert.equal(detectIosHostAvailability('darwin'), 'mac-host-xcode-unknown');
});
