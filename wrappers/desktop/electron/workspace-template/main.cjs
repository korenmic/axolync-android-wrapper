const { BrowserWindow, app, ipcMain } = require('electron');
const { createServer } = require('node:http');
const { readFile } = require('node:fs/promises');
const { extname, join, normalize, resolve, sep } = require('node:path');
const { createNativeServiceCompanionHost } = require('./nativeServiceCompanionHost.cjs');

const GPU_HARDENING_DISABLE_ENV = 'AXOLYNC_ELECTRON_DISABLE_GPU_HARDENING';

const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.ogg': 'audio/ogg',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.wav': 'audio/wav',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

let assetServer = null;
let assetServerRootUrl = null;
let nativeServiceCompanionHost = null;

function hardenChromiumGpuStartup() {
  if (process.env[GPU_HARDENING_DISABLE_ENV] === '1') {
    return;
  }

  // Release artifacts run on mixed Windows GPU/driver stacks; a failed GPU
  // utility process can terminate Electron before the app shell is usable.
  app.disableHardwareAcceleration();
}

hardenChromiumGpuStartup();

function contentTypeFor(filePath) {
  return MIME_TYPES[extname(filePath).toLowerCase()] || 'application/octet-stream';
}

function resolveAssetPath(appRoot, requestPathname) {
  const normalizedPath = normalize(requestPathname || '/')
    .replace(/^(\.\.[/\\])+/, '')
    .replace(/^[/\\]+/, '');
  const relativePath = normalizedPath || 'index.html';
  const resolvedPath = resolve(appRoot, relativePath);
  if (!resolvedPath.startsWith(appRoot + sep) && resolvedPath !== appRoot) {
    return null;
  }
  return resolvedPath;
}

async function ensureAssetServer() {
  if (assetServerRootUrl) {
    return assetServerRootUrl;
  }
  const appRoot = resolve(join(__dirname, 'app'));
  assetServer = createServer(async (request, response) => {
    const requestUrl = new URL(request.url || '/', 'http://127.0.0.1');
    const requestedPath = requestUrl.pathname === '/' ? '/index.html' : requestUrl.pathname;
    const assetPath = resolveAssetPath(appRoot, requestedPath);
    if (!assetPath) {
      response.writeHead(403);
      response.end('Forbidden');
      return;
    }
    try {
      const fileBuffer = await readFile(assetPath);
      response.writeHead(200, {
        'Content-Type': contentTypeFor(assetPath),
        'Cache-Control': 'no-store',
      });
      response.end(fileBuffer);
    } catch (error) {
      response.writeHead(404);
      response.end('Not found');
    }
  });
  await new Promise((resolveServer) => {
    assetServer.listen(0, '127.0.0.1', () => {
      const address = assetServer.address();
      assetServerRootUrl = `http://127.0.0.1:${address.port}`;
      resolveServer();
    });
  });
  return assetServerRootUrl;
}

function shutdownAssetServer() {
  if (!assetServer) {
    return;
  }
  assetServer.close();
  assetServer = null;
  assetServerRootUrl = null;
}

async function createWindow() {
  const assetServerUrl = await ensureAssetServer();
  if (!nativeServiceCompanionHost) {
    nativeServiceCompanionHost = createNativeServiceCompanionHost({
      appDir: resolve(join(__dirname, 'app')),
    });
    ipcMain.handle('axolync-native-service-companion-host:get-status', (_event, addonId, companionId) => (
      nativeServiceCompanionHost.getStatus(addonId, companionId)
    ));
    ipcMain.handle('axolync-native-service-companion-host:set-enabled', (_event, addonId, companionId, enabled) => (
      nativeServiceCompanionHost.setEnabled(addonId, companionId, enabled)
    ));
    ipcMain.handle('axolync-native-service-companion-host:start', (_event, addonId, companionId) => (
      nativeServiceCompanionHost.start(addonId, companionId)
    ));
    ipcMain.handle('axolync-native-service-companion-host:stop', (_event, addonId, companionId) => (
      nativeServiceCompanionHost.stop(addonId, companionId)
    ));
    ipcMain.handle('axolync-native-service-companion-host:request', (_event, addonId, companionId, request) => (
      nativeServiceCompanionHost.request(addonId, companionId, { addonId, companionId, request })
    ));
    ipcMain.handle('axolync-native-service-companion-host:get-connection', (_event, addonId, companionId) => (
      nativeServiceCompanionHost.getConnection(addonId, companionId)
    ));
    ipcMain.handle('axolync-native-service-companion-host:get-diagnostics', () => (
      nativeServiceCompanionHost.getDiagnostics()
    ));
  }
  const window = new BrowserWindow({
    width: 1280,
    height: 800,
    icon: join(__dirname, process.platform === 'win32' ? 'icon.ico' : 'icon.png'),
    webPreferences: {
      preload: join(__dirname, 'preload.cjs'),
    },
  });
  await window.loadURL(`${assetServerUrl}/index.html`);
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => {
  shutdownAssetServer();
  app.quit();
});
app.on('before-quit', shutdownAssetServer);
