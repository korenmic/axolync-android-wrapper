import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const publicDir = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'public');
const RUNTIME_PROFILE_SNIPPET_MARKER = 'id="axolync-runtime-profile-override"';

function normalizeRuntimeProfile(rawValue, fallbackValue = 'debug') {
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

function buildRuntimeProfileSnippet(runtimeProfile) {
  return `<script ${RUNTIME_PROFILE_SNIPPET_MARKER}>window.__AXOLYNC_RUNTIME_PROFILE = ${JSON.stringify(runtimeProfile)};</script>`;
}

function applyRuntimeProfileOverrideToHtml(html, runtimeProfile) {
  const snippet = buildRuntimeProfileSnippet(runtimeProfile);
  if (html.includes(RUNTIME_PROFILE_SNIPPET_MARKER)) {
    return html.replace(
      /<script id="axolync-runtime-profile-override">[\s\S]*?<\/script>/u,
      snippet,
    );
  }
  if (html.includes('</head>')) {
    return html.replace('</head>', `  ${snippet}\n</head>`);
  }
  const doctypeMatch = html.match(/^<!doctype[^>]*>/iu);
  if (doctypeMatch) {
    return `${doctypeMatch[0]}\n${snippet}${html.slice(doctypeMatch[0].length)}`;
  }
  return `${snippet}\n${html}`;
}

export function resolveSourceRoot(currentRepoRoot = repoRoot) {
  const builderNormal = process.env.AXOLYNC_BUILDER_BROWSER_NORMAL?.trim();
  if (builderNormal) {
    return path.resolve(builderNormal);
  }
  return path.resolve(currentRepoRoot, '..', 'axolync-browser', 'dist');
}

export function resolveDemoAssetsRoot(currentRepoRoot = repoRoot) {
  const builderDemo = process.env.AXOLYNC_BUILDER_BROWSER_DEMO?.trim();
  if (builderDemo) {
    const demoAssets = path.resolve(builderDemo, 'demo', 'assets');
    if (fs.existsSync(demoAssets)) {
      return demoAssets;
    }
  }
  const fallback = path.resolve(currentRepoRoot, '..', 'axolync-browser', 'demo', 'assets');
  return fs.existsSync(fallback) ? fallback : null;
}

export function resolveDemoPluginsRoot(currentRepoRoot = repoRoot) {
  const builderDemo = process.env.AXOLYNC_BUILDER_BROWSER_DEMO?.trim();
  if (builderDemo) {
    const demoPlugins = path.resolve(builderDemo, 'demo', 'plugins');
    if (fs.existsSync(demoPlugins)) {
      return demoPlugins;
    }
  }
  const fallback = path.resolve(currentRepoRoot, '..', 'axolync-browser', 'demo', 'plugins');
  return fs.existsSync(fallback) ? fallback : null;
}

export function resolveDemoPlayerHtml(currentRepoRoot = repoRoot) {
  const builderDemo = process.env.AXOLYNC_BUILDER_BROWSER_DEMO?.trim();
  if (builderDemo) {
    const demoPlayer = path.resolve(builderDemo, 'demo', 'player.html');
    if (fs.existsSync(demoPlayer)) {
      return demoPlayer;
    }
  }
  const fallback = path.resolve(currentRepoRoot, '..', 'axolync-browser', 'demo', 'player.html');
  return fs.existsSync(fallback) ? fallback : null;
}

export function resolveAndroidRuntimeProfile() {
  return normalizeRuntimeProfile(process.env.AXOLYNC_ANDROID_RUNTIME_PROFILE);
}

export function resolveAndroidIncludeDemoAssets(runtimeProfile = resolveAndroidRuntimeProfile()) {
  return normalizeBoolean(process.env.AXOLYNC_ANDROID_INCLUDE_DEMO_ASSETS, runtimeProfile === 'debug');
}

function ensureRequiredBrowserFiles(sourceRoot) {
  if (!fs.existsSync(sourceRoot)) {
    throw new Error(`Browser source root not found: ${sourceRoot}`);
  }
  if (!fs.existsSync(path.join(sourceRoot, 'index.html'))) {
    throw new Error(`Browser source root is missing index.html: ${sourceRoot}`);
  }
}

export function stageBrowserAssets(options = {}) {
  const sourceRoot = options.sourceRoot ?? resolveSourceRoot();
  const targetPublicDir = options.publicDir ?? publicDir;
  const demoAssetsRoot = options.demoAssetsRoot ?? resolveDemoAssetsRoot();
  const demoPluginsRoot = options.demoPluginsRoot ?? resolveDemoPluginsRoot();
  const demoPlayerHtml = options.demoPlayerHtml ?? resolveDemoPlayerHtml();
  const runtimeProfile = options.runtimeProfile ?? resolveAndroidRuntimeProfile();
  const includeDemoAssets = options.includeDemoAssets ?? resolveAndroidIncludeDemoAssets(runtimeProfile);

  ensureRequiredBrowserFiles(sourceRoot);

  fs.rmSync(targetPublicDir, { recursive: true, force: true });
  fs.mkdirSync(targetPublicDir, { recursive: true });
  fs.cpSync(sourceRoot, targetPublicDir, { recursive: true });
  const stagedIndexPath = path.join(targetPublicDir, 'index.html');
  fs.writeFileSync(
    stagedIndexPath,
    applyRuntimeProfileOverrideToHtml(fs.readFileSync(stagedIndexPath, 'utf8'), runtimeProfile),
    'utf8',
  );

  if (includeDemoAssets && demoAssetsRoot) {
    const demoTarget = path.join(targetPublicDir, 'demo', 'assets');
    fs.mkdirSync(demoTarget, { recursive: true });
    fs.cpSync(demoAssetsRoot, demoTarget, { recursive: true });
  }

  if (includeDemoAssets && demoPluginsRoot) {
    const demoTarget = path.join(targetPublicDir, 'demo', 'plugins');
    fs.mkdirSync(demoTarget, { recursive: true });
    fs.cpSync(demoPluginsRoot, demoTarget, { recursive: true });
  }

  if (includeDemoAssets && demoPlayerHtml) {
    const playerTarget = path.join(targetPublicDir, 'demo', 'player.html');
    fs.mkdirSync(path.dirname(playerTarget), { recursive: true });
    fs.copyFileSync(demoPlayerHtml, playerTarget);
  }

  for (const stubName of ['cordova.js', 'cordova_plugins.js']) {
    const stubPath = path.join(targetPublicDir, stubName);
    if (!fs.existsSync(stubPath)) {
      fs.writeFileSync(stubPath, '', 'utf8');
    }
  }

  return {
    sourceRoot,
    publicDir: targetPublicDir,
    demoAssetsRoot,
    demoPluginsRoot,
    demoPlayerHtml,
    runtimeProfile,
    includeDemoAssets,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  stageBrowserAssets();
}
