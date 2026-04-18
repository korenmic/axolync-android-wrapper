import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const publicDir = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'public');
const BUILD_FLAVOR_SNIPPET_MARKER = 'id="axolync-build-flavor-override"';
const NATIVE_STARTUP_SPLASH_SNIPPET_MARKER = 'id="axolync-native-startup-splash-override"';
const NATIVE_SERVICE_COMPANION_HOST_SNIPPET_MARKER = 'id="axolync-native-service-companion-host-override"';
const DEFAULT_NATIVE_STARTUP_SPLASH_VARIANT = 'layered';
const DEFAULT_NATIVE_STARTUP_SPLASH_FIT_MODE = 'contain';
const DEFAULT_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = 2200;

function normalizeBuildFlavor(rawValue, fallbackValue = 'debug') {
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

function normalizePositiveInteger(rawValue, fallbackValue) {
  const normalized = Number.parseInt(String(rawValue ?? '').trim(), 10);
  return Number.isFinite(normalized) && normalized > 0 ? normalized : fallbackValue;
}

function normalizeSplashVariant(rawValue, fallbackValue = DEFAULT_NATIVE_STARTUP_SPLASH_VARIANT) {
  const normalized = String(rawValue ?? '').trim().toLowerCase();
  if (normalized === 'icon' || normalized === 'compact') return 'icon';
  if (normalized === 'layered' || normalized === 'android' || normalized === 'stacked') return 'layered';
  if (normalized === 'artwork' || normalized === 'fullscreen' || normalized === 'full') return 'artwork';
  return fallbackValue;
}

function buildBuildFlavorSnippet(buildFlavor) {
  return `<script ${BUILD_FLAVOR_SNIPPET_MARKER}>window.__AXOLYNC_BUILD_FLAVOR = ${JSON.stringify(buildFlavor)};</script>`;
}

function buildNativeStartupSplashSnippet({
  enabled = true,
  variant = DEFAULT_NATIVE_STARTUP_SPLASH_VARIANT,
  fitMode = DEFAULT_NATIVE_STARTUP_SPLASH_FIT_MODE,
  minDurationMs = DEFAULT_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS,
} = {}) {
  return `<script ${NATIVE_STARTUP_SPLASH_SNIPPET_MARKER}>window.__AXOLYNC_NATIVE_STARTUP_SPLASH_ENABLED = ${enabled ? 'true' : 'false'}; window.__AXOLYNC_NATIVE_STARTUP_SPLASH_VARIANT = ${JSON.stringify(variant)}; window.__AXOLYNC_NATIVE_STARTUP_SPLASH_FIT_MODE = ${JSON.stringify(fitMode)}; window.__AXOLYNC_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS = ${JSON.stringify(minDurationMs)};</script>`;
}

function buildNativeServiceCompanionHostSnippet() {
  const body = [
    '(function () {',
    '  const resolvePlugin = function() {',
    "    return window.Capacitor && window.Capacitor.Plugins ? window.Capacitor.Plugins.AxolyncNativeServiceCompanionHost : null;",
    '  };',
    '  const invoke = async function(methodName, payload) {',
    '    const plugin = resolvePlugin();',
    `    if (!plugin || typeof plugin[methodName] !== 'function') {`,
    "      throw new Error('Capacitor native service companion bridge is unavailable.');",
    '    }',
    '    return plugin[methodName](payload);',
    '  };',
    '  const host = Object.freeze({',
    "    hostFamily: 'capacitor',",
    "    hostPlatform: 'android',",
    '    hostAbi: null,',
    '    async getStatus(addonId, companionId) {',
    "      return invoke('getStatus', { addonId, companionId });",
    '    },',
    '    async setEnabled(addonId, companionId, enabled) {',
    "      return invoke('setEnabled', { addonId, companionId, enabled });",
    '    },',
    '    async start(addonId, companionId) {',
    "      return invoke('start', { addonId, companionId });",
    '    },',
    '    async stop(addonId, companionId) {',
    "      return invoke('stop', { addonId, companionId });",
    '    },',
    '    async request(addonId, companionId, request) {',
    "      return invoke('request', { addonId, companionId, request });",
    '    },',
    '    async getConnection(addonId, companionId) {',
    "      return invoke('getConnection', { addonId, companionId });",
    '    },',
    '    async clearPersistedRuntimeState() {',
    "      return { ok: true, details: 'Browser-owned runtime state reset is handled in the webview runtime.' };",
    '    }',
    '  });',
    '  window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__ = host;',
    '  window.__AXOLYNC_RUNTIME_STATE_RESET_HOST__ = Object.freeze({ clearPersistedRuntimeState: host.clearPersistedRuntimeState });',
    "  window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST_FAMILY = 'capacitor';",
    '})();',
  ].join('\n');
  return `<script ${NATIVE_SERVICE_COMPANION_HOST_SNIPPET_MARKER}>${body}</script>`;
}

function applyBuildFlavorOverrideToHtml(html, buildFlavor) {
  const snippet = buildBuildFlavorSnippet(buildFlavor);
  if (html.includes(BUILD_FLAVOR_SNIPPET_MARKER)) {
    return html.replace(
      /<script id="axolync-build-flavor-override">[\s\S]*?<\/script>/u,
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

function applyNativeStartupSplashOverrideToHtml(html, {
  enabled = true,
  variant = DEFAULT_NATIVE_STARTUP_SPLASH_VARIANT,
  fitMode = DEFAULT_NATIVE_STARTUP_SPLASH_FIT_MODE,
  minDurationMs = DEFAULT_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS,
} = {}) {
  const snippet = buildNativeStartupSplashSnippet({ enabled, variant, fitMode, minDurationMs });
  if (html.includes(NATIVE_STARTUP_SPLASH_SNIPPET_MARKER)) {
    return html.replace(
      /<script id="axolync-native-startup-splash-override">[\s\S]*?<\/script>/u,
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

function applyNativeServiceCompanionHostOverrideToHtml(html) {
  const snippet = buildNativeServiceCompanionHostSnippet();
  if (html.includes(NATIVE_SERVICE_COMPANION_HOST_SNIPPET_MARKER)) {
    return html.replace(
      /<script id="axolync-native-service-companion-host-override">[\s\S]*?<\/script>/u,
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

export function resolveNativeServiceCompanionAssetsRoot() {
  const builderNativeBridgeAssets = process.env.AXOLYNC_BUILDER_NATIVE_SERVICE_COMPANION_ASSETS?.trim();
  if (builderNativeBridgeAssets) {
    const resolved = path.resolve(builderNativeBridgeAssets);
    return fs.existsSync(resolved) ? resolved : null;
  }
  return null;
}

export function resolveAndroidBuildFlavor() {
  return normalizeBuildFlavor(process.env.AXOLYNC_ANDROID_BUILD_FLAVOR);
}

export function resolveAndroidIncludeDemoAssets(buildFlavor = resolveAndroidBuildFlavor()) {
  return normalizeBoolean(process.env.AXOLYNC_ANDROID_INCLUDE_DEMO_ASSETS, buildFlavor === 'debug');
}

export function resolveAndroidNativeStartupSplashVariant() {
  return normalizeSplashVariant(
    process.env.AXOLYNC_ANDROID_NATIVE_STARTUP_SPLASH_VARIANT,
    DEFAULT_NATIVE_STARTUP_SPLASH_VARIANT,
  );
}

export function resolveAndroidNativeStartupSplashFitMode(variant = resolveAndroidNativeStartupSplashVariant()) {
  const inferredFallback = variant === 'layered' ? 'contain' : 'cover';
  const normalized = String(process.env.AXOLYNC_ANDROID_NATIVE_STARTUP_SPLASH_FIT_MODE || inferredFallback).trim().toLowerCase();
  if (normalized === 'contain' || normalized === 'cover' || normalized === 'fill') {
    return normalized;
  }
  return inferredFallback;
}

export function resolveAndroidNativeStartupSplashMinDurationMs() {
  return normalizePositiveInteger(process.env.AXOLYNC_ANDROID_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS, DEFAULT_NATIVE_STARTUP_SPLASH_MIN_DURATION_MS);
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
  const buildFlavor = options.buildFlavor ?? resolveAndroidBuildFlavor();
  const includeDemoAssets = options.includeDemoAssets ?? resolveAndroidIncludeDemoAssets(buildFlavor);
  const nativeStartupSplashVariant = options.nativeStartupSplashVariant ?? resolveAndroidNativeStartupSplashVariant();
  const nativeStartupSplashFitMode = options.nativeStartupSplashFitMode ?? resolveAndroidNativeStartupSplashFitMode(nativeStartupSplashVariant);
  const nativeStartupSplashMinDurationMs = options.nativeStartupSplashMinDurationMs ?? resolveAndroidNativeStartupSplashMinDurationMs();
  const nativeServiceCompanionAssetsRoot = options.nativeServiceCompanionAssetsRoot ?? resolveNativeServiceCompanionAssetsRoot();

  ensureRequiredBrowserFiles(sourceRoot);

  fs.rmSync(targetPublicDir, { recursive: true, force: true });
  fs.mkdirSync(targetPublicDir, { recursive: true });
  fs.cpSync(sourceRoot, targetPublicDir, { recursive: true });
  const stagedIndexPath = path.join(targetPublicDir, 'index.html');
  fs.writeFileSync(
    stagedIndexPath,
    applyNativeServiceCompanionHostOverrideToHtml(
      applyNativeStartupSplashOverrideToHtml(
        applyBuildFlavorOverrideToHtml(fs.readFileSync(stagedIndexPath, 'utf8'), buildFlavor),
        {
          enabled: true,
          variant: nativeStartupSplashVariant,
          fitMode: nativeStartupSplashFitMode,
          minDurationMs: nativeStartupSplashMinDurationMs,
        },
      ),
    ),
    'utf8',
  );

  const nativeServiceCompanionTarget = path.join(targetPublicDir, 'native-service-companions');
  if (nativeServiceCompanionAssetsRoot) {
    fs.rmSync(nativeServiceCompanionTarget, { recursive: true, force: true });
    fs.cpSync(nativeServiceCompanionAssetsRoot, nativeServiceCompanionTarget, { recursive: true });
  } else {
    fs.rmSync(nativeServiceCompanionTarget, { recursive: true, force: true });
  }

  if (!includeDemoAssets) {
    fs.rmSync(path.join(targetPublicDir, 'demo'), { recursive: true, force: true });
  }

  if (includeDemoAssets && demoAssetsRoot) {
    const demoTarget = path.join(targetPublicDir, 'demo', 'assets');
    fs.mkdirSync(demoTarget, { recursive: true });
    for (const entry of fs.readdirSync(demoAssetsRoot)) {
      if (entry.toLowerCase().endsWith('.wav')) continue;
      const source = path.join(demoAssetsRoot, entry);
      const target = path.join(demoTarget, entry);
      fs.cpSync(source, target, { recursive: true });
    }
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
    buildFlavor,
    includeDemoAssets,
    nativeServiceCompanionAssetsRoot,
    nativeStartupSplashVariant,
    nativeStartupSplashFitMode,
    nativeStartupSplashMinDurationMs,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  stageBrowserAssets();
}
