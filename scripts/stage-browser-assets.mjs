import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assetsRoot = path.join(repoRoot, 'app', 'src', 'main', 'assets');
const publicDir = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'public');
const capacitorAssetsDir = path.join(assetsRoot, 'capacitor');
const CAPACITOR_PLUGIN_REGISTRY_FILENAME = 'capacitor.plugins.json';
const CAPACITOR_NATIVE_BRIDGE_PLUGIN_REGISTRY = Object.freeze([
  Object.freeze({
    pkg: 'axolync-native-bridge-host',
    classpath: 'com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin',
  }),
]);
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

function copyDirectoryDeterministically(sourceDir, targetDir) {
  fs.mkdirSync(targetDir, { recursive: true });
  for (const entry of fs.readdirSync(sourceDir).sort((left, right) => left.localeCompare(right))) {
    const source = path.join(sourceDir, entry);
    const target = path.join(targetDir, entry);
    const stat = fs.statSync(source);
    if (stat.isDirectory()) {
      copyDirectoryDeterministically(source, target);
      continue;
    }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.copyFileSync(source, target);
  }
}

function restageDirectoryDeterministically(rootDir) {
  if (!rootDir || !fs.existsSync(rootDir)) return;
  const tempDir = `${rootDir}.axolync-deterministic`;
  fs.rmSync(tempDir, { recursive: true, force: true });
  copyDirectoryDeterministically(rootDir, tempDir);
  fs.rmSync(rootDir, { recursive: true, force: true });
  fs.renameSync(tempDir, rootDir);
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
    '  const BRIDGE_PUBLICATION = Object.freeze({',
    "    publicationMode: 'capacitor-plugin-registry',",
    "    pluginName: 'AxolyncNativeServiceCompanionHost',",
    "    pluginRegistryAssetPath: 'capacitor.plugins.json',",
    "    mirroredPluginRegistryAssetPath: 'capacitor/capacitor.plugins.json',",
    "    packageName: 'axolync-native-bridge-host',",
    "    classpath: 'com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin',",
    '  });',
    "  const BRIDGE_RUNTIME_SCRIPT_SRC = '../native-bridge.js';",
    '  let bridgeRuntimeLoadPromise = null;',
    '  const buildBootstrapSnapshot = function() {',
    '    const capacitor = window.Capacitor || null;',
    '    const plugin = capacitor && capacitor.Plugins ? capacitor.Plugins.AxolyncNativeServiceCompanionHost : null;',
    '    return {',
    "      runtimeMode: window.__AXOLYNC_RUNTIME_MODE || null,",
    '      hasWindowCapacitor: Boolean(capacitor),',
    '      hasCapacitorPluginsContainer: Boolean(capacitor && capacitor.Plugins),',
    '      hasPublishedNativeBridgePlugin: Boolean(plugin),',
    "      publishedPluginMethodNames: plugin && typeof plugin === 'object' ? Object.keys(plugin).sort() : [],",
    "      hasCapacitorNativePromise: Boolean(capacitor && typeof capacitor.nativePromise === 'function'),",
    "      hasInjectedNativeBridgeHost: Boolean(window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__),",
    "      hasInjectedRuntimeHostBridge: Boolean(window.__AXOLYNC_RUNTIME_HOST_BRIDGE__),",
    "      hasInjectedRuntimeStateResetHost: Boolean(window.__AXOLYNC_RUNTIME_STATE_RESET_HOST__),",
    "      bridgeRuntimeScriptSrc: BRIDGE_RUNTIME_SCRIPT_SRC,",
    '    };',
    '  };',
    '  const resolvePlugin = function() {',
    "    return window.Capacitor && window.Capacitor.Plugins ? window.Capacitor.Plugins.AxolyncNativeServiceCompanionHost : null;",
    '  };',
    '  const resolveNativePromise = function() {',
    "    return window.Capacitor && typeof window.Capacitor.nativePromise === 'function'",
    '      ? window.Capacitor.nativePromise.bind(window.Capacitor)',
    '      : null;',
    '  };',
    '  const ensureBridgeRuntimeLoaded = function() {',
    '    if (resolvePlugin() || resolveNativePromise()) {',
    '      return Promise.resolve();',
    '    }',
    '    if (bridgeRuntimeLoadPromise) {',
    '      return bridgeRuntimeLoadPromise;',
    '    }',
    "    const existingScript = document.querySelector('script[data-axolync-capacitor-native-bridge-runtime=\"true\"]');",
    "    if (existingScript && existingScript.getAttribute('data-axolync-capacitor-native-bridge-runtime-loaded') === 'true') {",
    '      return Promise.resolve();',
    '    }',
    '    bridgeRuntimeLoadPromise = new Promise(function(resolve, reject) {',
    '      const script = existingScript || document.createElement("script");',
    '      if (!existingScript) {',
    "        script.src = BRIDGE_RUNTIME_SCRIPT_SRC;",
    "        script.async = false;",
    "        script.setAttribute('data-axolync-capacitor-native-bridge-runtime', 'true');",
    '      }',
    '      script.addEventListener("load", function handleLoad() {',
    "        script.setAttribute('data-axolync-capacitor-native-bridge-runtime-loaded', 'true');",
    '        bridgeRuntimeLoadPromise = Promise.resolve();',
    '        resolve();',
    '      }, { once: true });',
    '      script.addEventListener("error", function handleError() {',
    '        bridgeRuntimeLoadPromise = null;',
    '        reject(new Error("Failed to load packaged Capacitor native bridge runtime."));',
    '      }, { once: true });',
    '      if (!existingScript) {',
    '        document.head.appendChild(script);',
    '      }',
    '    });',
    '    return bridgeRuntimeLoadPromise;',
    '  };',
    '  const buildUnavailableBridgeError = function(methodName, payload) {',
    '    const snapshot = buildBootstrapSnapshot();',
    "    const error = new Error('Capacitor native service companion bridge is unavailable.');",
    "    error.code = 'axolync_capacitor_native_bridge_unavailable';",
    '    error.details = {',
    '      methodName: methodName || null,',
    "      requestedAddonId: payload && typeof payload === 'object' && 'addonId' in payload ? payload.addonId : null,",
    "      requestedCompanionId: payload && typeof payload === 'object' && 'companionId' in payload ? payload.companionId : null,",
    '      bootstrapSnapshot: snapshot,',
    '      bridgePublication: BRIDGE_PUBLICATION,',
    '    };',
    '    return error;',
    '  };',
    '  const invoke = async function(methodName, payload) {',
    '    let plugin = resolvePlugin();',
    "    if (plugin && typeof plugin[methodName] === 'function') {",
    '      return plugin[methodName](payload);',
    '    }',
    '    let nativePromise = resolveNativePromise();',
    "    if (nativePromise) {",
    "      return nativePromise('AxolyncNativeServiceCompanionHost', methodName, payload);",
    '    }',
    '    await ensureBridgeRuntimeLoaded();',
    '    plugin = resolvePlugin();',
    "    if (plugin && typeof plugin[methodName] === 'function') {",
    '      return plugin[methodName](payload);',
    '    }',
    '    nativePromise = resolveNativePromise();',
    "    if (nativePromise) {",
    "      return nativePromise('AxolyncNativeServiceCompanionHost', methodName, payload);",
    '    }',
    '    throw buildUnavailableBridgeError(methodName, payload);',
    '  };',
    '  const hostMetadata = {',
    "    hostFamily: 'capacitor',",
    "    hostPlatform: 'android',",
    '    hostAbi: null,',
    '  };',
    '  const refreshHostInfo = async function() {',
    '    try {',
    "      const info = await invoke('getHostInfo', {});",
    "      if (info && typeof info.hostFamily === 'string' && info.hostFamily.trim()) hostMetadata.hostFamily = info.hostFamily.trim();",
    "      if (info && typeof info.hostPlatform === 'string' && info.hostPlatform.trim()) hostMetadata.hostPlatform = info.hostPlatform.trim();",
    "      if (info && typeof info.hostAbi === 'string' && info.hostAbi.trim()) hostMetadata.hostAbi = info.hostAbi.trim();",
    '    } catch {',
    '      // Keep best-effort defaults when host metadata is not immediately available.',
    '    }',
    '  };',
    '  const host = {};',
    '  Object.defineProperties(host, {',
    '    hostFamily: { enumerable: true, get() { return hostMetadata.hostFamily; } },',
    '    hostPlatform: { enumerable: true, get() { return hostMetadata.hostPlatform; } },',
    '    hostAbi: { enumerable: true, get() { return hostMetadata.hostAbi; } },',
    '    getStatus: { enumerable: true, value: async function(addonId, companionId) {',
    "      return invoke('getStatus', { addonId, companionId });",
    '    } },',
    '    setEnabled: { enumerable: true, value: async function(addonId, companionId, enabled) {',
    "      return invoke('setEnabled', { addonId, companionId, enabled });",
    '    } },',
    '    start: { enumerable: true, value: async function(addonId, companionId) {',
    "      return invoke('start', { addonId, companionId });",
    '    } },',
    '    stop: { enumerable: true, value: async function(addonId, companionId) {',
    "      return invoke('stop', { addonId, companionId });",
    '    } },',
    '    request: { enumerable: true, value: async function(addonId, companionId, request) {',
    "      return invoke('request', { addonId, companionId, request });",
    '    } },',
    '    getConnection: { enumerable: true, value: async function(addonId, companionId) {',
    "      return invoke('getConnection', { addonId, companionId });",
    '    } },',
    '    getDiagnostics: { enumerable: true, value: async function() {',
    '      try {',
    "        const diagnostics = await invoke('getDiagnostics', {});",
    "        if (!diagnostics || typeof diagnostics !== 'object') return diagnostics;",
    "        if (!('bootstrapSnapshot' in diagnostics)) diagnostics.bootstrapSnapshot = buildBootstrapSnapshot();",
    "        if (!('bridgePublication' in diagnostics)) diagnostics.bridgePublication = BRIDGE_PUBLICATION;",
    '        return diagnostics;',
    '      } catch (error) {',
    '        const snapshot = buildBootstrapSnapshot();',
    '        return {',
    "          hostFamily: 'capacitor',",
    "          hostPlatform: 'android',",
    '          hostAbi: hostMetadata.hostAbi,',
    '          generatedAtMs: Date.now(),',
    "          collectionMethod: 'unavailable',",
    '          logs: [],',
    "          error: error && typeof error.message === 'string' ? error.message : String(error),",
    '          bootstrapSnapshot: snapshot,',
    '          bridgePublication: BRIDGE_PUBLICATION,',
    '        };',
    '      }',
    '    } },',
    '    clearPersistedRuntimeState: { enumerable: true, value: async function() {',
    "      return { ok: true, details: 'Browser-owned runtime state reset is handled in the webview runtime.' };",
    '    } },',
    '  });',
    '  Object.freeze(host);',
    '  const runtimeHostBridge = Object.freeze({',
    '    saveDebugArchiveBase64: async function(fileName, base64Payload) {',
    "      return invoke('saveDebugArchiveBase64', { fileName, base64Payload });",
    '    },',
    '  });',
    '  void refreshHostInfo();',
    '  window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__ = host;',
    '  window.__AXOLYNC_RUNTIME_HOST_BRIDGE__ = runtimeHostBridge;',
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

function writeCapacitorPluginRegistry(assetRoot) {
  const registryJson = `${JSON.stringify(CAPACITOR_NATIVE_BRIDGE_PLUGIN_REGISTRY, null, 2)}\n`;
  for (const targetPath of [
    path.join(assetRoot, CAPACITOR_PLUGIN_REGISTRY_FILENAME),
    path.join(assetRoot, 'capacitor', CAPACITOR_PLUGIN_REGISTRY_FILENAME),
  ]) {
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    fs.writeFileSync(targetPath, registryJson, 'utf8');
  }
  return {
    rootPath: path.join(assetRoot, CAPACITOR_PLUGIN_REGISTRY_FILENAME),
    nestedPath: path.join(assetRoot, 'capacitor', CAPACITOR_PLUGIN_REGISTRY_FILENAME),
    entries: CAPACITOR_NATIVE_BRIDGE_PLUGIN_REGISTRY,
  };
}

export function stageBrowserAssets(options = {}) {
  const sourceRoot = options.sourceRoot ?? resolveSourceRoot();
  const targetPublicDir = options.publicDir ?? publicDir;
  const targetAssetRoot = options.assetRoot ?? assetsRoot;
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
  const capacitorPluginRegistry = writeCapacitorPluginRegistry(targetAssetRoot);

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

  for (const stableDir of [
    path.join(targetPublicDir, 'plugins', 'preinstalled'),
    path.join(targetPublicDir, 'themes', 'preinstalled'),
    path.join(targetPublicDir, 'native-service-companions'),
    path.join(targetPublicDir, 'demo', 'assets'),
    path.join(targetPublicDir, 'demo', 'plugins'),
  ]) {
    restageDirectoryDeterministically(stableDir);
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
    capacitorPluginRegistry,
    nativeStartupSplashVariant,
    nativeStartupSplashFitMode,
    nativeStartupSplashMinDurationMs,
  };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  stageBrowserAssets();
}
