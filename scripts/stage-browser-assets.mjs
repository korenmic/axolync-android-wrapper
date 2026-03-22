import fs from 'node:fs';
import path from 'node:path';

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const publicDir = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'public');

function resolveSourceRoot() {
  const builderNormal = process.env.AXOLYNC_BUILDER_BROWSER_NORMAL?.trim();
  if (builderNormal) {
    return path.resolve(builderNormal);
  }
  return path.resolve(repoRoot, '..', 'axolync-browser', 'dist');
}

function resolveDemoAssetsRoot() {
  const builderDemo = process.env.AXOLYNC_BUILDER_BROWSER_DEMO?.trim();
  if (builderDemo) {
    const demoAssets = path.resolve(builderDemo, 'demo', 'assets');
    if (fs.existsSync(demoAssets)) {
      return demoAssets;
    }
  }
  const fallback = path.resolve(repoRoot, '..', 'axolync-browser', 'demo', 'assets');
  return fs.existsSync(fallback) ? fallback : null;
}

const sourceRoot = resolveSourceRoot();
if (!fs.existsSync(sourceRoot)) {
  throw new Error(`Browser source root not found: ${sourceRoot}`);
}
if (!fs.existsSync(path.join(sourceRoot, 'index.html'))) {
  throw new Error(`Browser source root is missing index.html: ${sourceRoot}`);
}

fs.rmSync(publicDir, { recursive: true, force: true });
fs.mkdirSync(publicDir, { recursive: true });
fs.cpSync(sourceRoot, publicDir, { recursive: true });

const demoAssetsRoot = resolveDemoAssetsRoot();
if (demoAssetsRoot) {
  const demoTarget = path.join(publicDir, 'demo', 'assets');
  fs.mkdirSync(demoTarget, { recursive: true });
  fs.cpSync(demoAssetsRoot, demoTarget, { recursive: true });
}

for (const stubName of ['cordova.js', 'cordova_plugins.js']) {
  const stubPath = path.join(publicDir, stubName);
  if (!fs.existsSync(stubPath)) {
    fs.writeFileSync(stubPath, '', 'utf8');
  }
}
