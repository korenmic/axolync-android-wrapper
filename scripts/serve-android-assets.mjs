#!/usr/bin/env node
import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const assetRoot = path.join(projectRoot, 'app', 'src', 'main', 'assets', 'axolync-browser');

const args = new Map(
  process.argv.slice(2).map((arg) => {
    const [k, v] = arg.split('=');
    return [k.replace(/^--/, ''), v ?? 'true'];
  })
);
const port = Number(args.get('port') ?? 4173);
const host = args.get('host') ?? '127.0.0.1';

const mimeTypes = new Map([
  ['.html', 'text/html'],
  ['.js', 'application/javascript'],
  ['.mjs', 'application/javascript'],
  ['.css', 'text/css'],
  ['.json', 'application/json'],
  ['.map', 'application/json'],
  ['.svg', 'image/svg+xml'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.woff', 'font/woff'],
  ['.woff2', 'font/woff2'],
  ['.ttf', 'font/ttf'],
  ['.wasm', 'application/wasm'],
]);

function normalizePath(urlPath) {
  try {
    const decoded = decodeURIComponent(urlPath);
    const normalized = decoded.replace(/\/+/g, '/');
    if (normalized.includes('..') || normalized.includes('\\') || normalized.includes('\0')) {
      return null;
    }
    return normalized;
  } catch {
    return null;
  }
}

async function fileExists(filePath) {
  try {
    const stat = await fs.stat(filePath);
    return stat.isFile();
  } catch {
    return false;
  }
}

async function resolveAsset(uri) {
  const candidates = [uri];
  const lastSegment = uri.split('/').pop() ?? '';
  const hasExtension = lastSegment.includes('.');

  if (!hasExtension) {
    candidates.push(`${uri}.js`, `${uri}.mjs`, `${uri}/index.js`, `${uri}/index.mjs`, `${uri}/index.html`);
  }

  for (const candidate of candidates) {
    const candidatePath = path.join(assetRoot, candidate.replace(/^\//, ''));
    if (await fileExists(candidatePath)) {
      return { filePath: candidatePath, requestPath: candidate };
    }
  }
  return null;
}

function shouldFallbackToIndex(uri) {
  if (uri === '/health') return false;
  if (uri.startsWith('/api/')) return false;
  if (uri.startsWith('/core/') || uri.startsWith('/ui/') || uri.startsWith('/plugins/') || uri.startsWith('/workers/') || uri.startsWith('/demo/')) {
    return false;
  }
  const lastSegment = uri.split('/').pop() ?? '';
  return !lastSegment.includes('.');
}

function contentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  return mimeTypes.get(ext) ?? 'application/octet-stream';
}

const server = http.createServer(async (req, res) => {
  if (!req.url) {
    res.writeHead(400, { 'Content-Type': 'text/plain' });
    res.end('Bad Request');
    return;
  }

  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { 'Content-Type': 'text/plain' });
    res.end('Method Not Allowed');
    return;
  }

  const requestUrl = new URL(req.url, `http://${host}:${port}`);
  if (requestUrl.pathname === '/health') {
    const body = JSON.stringify({ status: 'ok', server: 'serve-android-assets' });
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(req.method === 'HEAD' ? '' : body);
    return;
  }

  const normalized = normalizePath(requestUrl.pathname);
  if (!normalized) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('Forbidden');
    return;
  }

  let uri = normalized;
  if (uri === '/' || uri === '') uri = '/index.html';

  const resolved = await resolveAsset(uri);
  if (resolved) {
    const body = req.method === 'HEAD' ? null : await fs.readFile(resolved.filePath);
    res.writeHead(200, { 'Content-Type': contentType(resolved.filePath) });
    res.end(body ?? '');
    return;
  }

  if (shouldFallbackToIndex(uri)) {
    const indexPath = path.join(assetRoot, 'index.html');
    if (await fileExists(indexPath)) {
      const body = req.method === 'HEAD' ? null : await fs.readFile(indexPath);
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(body ?? '');
      return;
    }
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end(`Not Found: ${uri}`);
});

server.listen(port, host, () => {
  console.log(`[android-assets-server] serving ${assetRoot}`);
  console.log(`[android-assets-server] open http://${host}:${port}/index.html`);
});
