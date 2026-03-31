import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { stageBrowserAssets, resolveSourceRoot } from './stage-browser-assets.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DEFAULT_PUBLIC_DIR = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'public');
const IOS_REQUIRED_RELATIVE_PATHS = [
  'ios/App/App.xcodeproj/project.pbxproj',
  'ios/App/App.xcworkspace/xcshareddata/IDEWorkspaceChecks.plist',
  'ios/App/App/Info.plist',
  'ios/App/App/capacitor.config.json',
  'ios/App/Podfile',
  'ios/App/App/public/index.html',
];

function normalizeBuildFlavor(rawValue, fallbackValue = 'release') {
  const normalized = String(rawValue ?? '').trim().toLowerCase();
  if (normalized === 'debug' || normalized === 'release') {
    return normalized;
  }
  return fallbackValue;
}

function normalizeBoolean(rawValue, fallbackValue) {
  if (rawValue === undefined || rawValue === null || rawValue === '') {
    return fallbackValue;
  }
  const normalized = String(rawValue).trim().toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(normalized)) return true;
  if (['0', 'false', 'no', 'off'].includes(normalized)) return false;
  return fallbackValue;
}

function hostPlatformId(platform = process.platform, arch = process.arch) {
  return `${platform === 'darwin' ? 'macos' : platform}-${arch}`;
}

function fileExists(targetPath) {
  return fs.existsSync(targetPath);
}

export function resolveIosBuildFlavor() {
  return normalizeBuildFlavor(process.env.AXOLYNC_IOS_BUILD_FLAVOR);
}

export function resolveIosIncludeDemoAssets(buildFlavor = resolveIosBuildFlavor()) {
  return normalizeBoolean(process.env.AXOLYNC_IOS_INCLUDE_DEMO_ASSETS, buildFlavor === 'debug');
}

export function iosWorkspaceExpectedPaths(currentRepoRoot = repoRoot) {
  return IOS_REQUIRED_RELATIVE_PATHS.map((relativePath) => path.join(currentRepoRoot, relativePath));
}

export function detectIosHostAvailability(platform = process.platform) {
  return platform === 'darwin' ? 'mac-host-xcode-unknown' : 'workspace-only';
}

export function verifyIosWorkspace(options = {}) {
  const currentRepoRoot = options.repoRoot ?? repoRoot;
  const buildFlavor = options.buildFlavor ?? resolveIosBuildFlavor();
  const includeDemoAssets = options.includeDemoAssets ?? resolveIosIncludeDemoAssets(buildFlavor);
  const missing = iosWorkspaceExpectedPaths(currentRepoRoot).filter((targetPath) => !fileExists(targetPath));
  if (missing.length > 0) {
    throw new Error(`iOS workspace verification failed, missing required files: ${missing.map((p) => path.relative(currentRepoRoot, p)).join(', ')}`);
  }
  return {
    hostPlatform: hostPlatformId(options.platform ?? process.platform, options.arch ?? process.arch),
    buildFlavor,
    includeDemoAssets,
    workspaceGenerated: true,
    workspaceRoot: path.join(currentRepoRoot, 'ios'),
    xcodeProjectPath: path.join(currentRepoRoot, 'ios', 'App', 'App.xcodeproj'),
    xcodeWorkspacePath: path.join(currentRepoRoot, 'ios', 'App', 'App.xcworkspace'),
    publicDir: path.join(currentRepoRoot, 'ios', 'App', 'App', 'public'),
    hostAvailability: detectIosHostAvailability(options.platform ?? process.platform),
    requiresMacHost: true,
    runnable: false,
    installable: false,
  };
}

function defaultCapRunner(args, cwd) {
  const joinedArgs = args.map((part) => JSON.stringify(part)).join(' ');
  return execSync(`npx cap ${joinedArgs}`, {
    cwd,
    encoding: 'utf8',
    stdio: 'pipe',
  });
}

export function prepareIosWorkspace(options = {}) {
  const currentRepoRoot = options.repoRoot ?? repoRoot;
  const buildFlavor = options.buildFlavor ?? resolveIosBuildFlavor();
  const includeDemoAssets = options.includeDemoAssets ?? resolveIosIncludeDemoAssets(buildFlavor);
  const sourceRoot = options.sourceRoot ?? resolveSourceRoot(currentRepoRoot);
  const publicDir = options.publicDir ?? path.join(currentRepoRoot, 'app', 'src', 'main', 'assets', 'public');
  const runCapCommand = options.runCapCommand ?? defaultCapRunner;

  stageBrowserAssets({
    sourceRoot,
    publicDir,
    buildFlavor,
    includeDemoAssets,
    sourceRoot: sourceRoot,
  });

  const iosRoot = path.join(currentRepoRoot, 'ios');
  const commands = [];
  if (!fileExists(path.join(iosRoot, 'App', 'App.xcodeproj', 'project.pbxproj'))) {
    commands.push(['add', 'ios']);
  }
  commands.push(['sync', 'ios']);
  for (const args of commands) {
    runCapCommand(args, currentRepoRoot);
  }

  return verifyIosWorkspace({
    repoRoot: currentRepoRoot,
    buildFlavor,
    includeDemoAssets,
  });
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  const metadata = prepareIosWorkspace();
  process.stdout.write(`${JSON.stringify(metadata, null, 2)}\n`);
}
