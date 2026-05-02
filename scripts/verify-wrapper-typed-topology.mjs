import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function normalizePath(value) {
  return String(value ?? '').replaceAll('\\', '/').replace(/^\.\/+/, '');
}

function pathExists(root, relativePath) {
  return Boolean(relativePath) && fs.existsSync(path.join(root, relativePath));
}

function hasNonReadmeFile(root, relativePath) {
  const absoluteRoot = path.join(root, relativePath);
  if (!fs.existsSync(absoluteRoot) || !fs.statSync(absoluteRoot).isDirectory()) return false;
  const stack = [absoluteRoot];
  while (stack.length) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current)) {
      const fullPath = path.join(current, entry);
      const stat = fs.statSync(fullPath);
      if (stat.isDirectory()) {
        stack.push(fullPath);
        continue;
      }
      if (!/^readme(\.[a-z0-9]+)?$/i.test(path.basename(fullPath))) return true;
    }
  }
  return false;
}

function collectActivePaths(layout) {
  const paths = [];
  const families = layout?.families ?? {};
  for (const [familyName, family] of Object.entries(families)) {
    const root = normalizePath(family?.root);
    if (root) paths.push({ familyName, kind: 'root', path: root });
    for (const [sectionName, section] of Object.entries(family ?? {})) {
      if (!section || typeof section !== 'object' || Array.isArray(section)) continue;
      for (const key of ['authorityPath', 'projectRoot', 'templateRoot', 'gradleProjectPath', 'assetPublicPath', 'nativeSourcePath']) {
        const value = normalizePath(section[key]);
        if (value) paths.push({ familyName, sectionName, kind: key, path: value });
      }
    }
  }
  return paths;
}

function assertCanonicalPath({ familyName, kind, path: relativePath }, failures) {
  if (relativePath.startsWith('templates/desktop/')) {
    failures.push(`${familyName}.${kind} must not use top-level templates/desktop as active source: ${relativePath}`);
  }
  if (familyName === 'capacitor' && !relativePath.startsWith('wrappers/mobile/capacitor')) {
    failures.push(`capacitor.${kind} must be under wrappers/mobile/capacitor: ${relativePath}`);
  }
  if (familyName === 'tauri' && !relativePath.startsWith('wrappers/desktop/tauri')) {
    failures.push(`tauri.${kind} must be under wrappers/desktop/tauri: ${relativePath}`);
  }
  if (familyName === 'electron' && !relativePath.startsWith('wrappers/desktop/electron')) {
    failures.push(`electron.${kind} must be under wrappers/desktop/electron: ${relativePath}`);
  }
}

export function verifyWrapperTypedTopology({
  root = repoRoot,
  layoutPath = path.join(root, 'config', 'wrapper-layout.json'),
  packagePath = path.join(root, 'package.json'),
  requireFinalPackageName = false,
} = {}) {
  const layout = readJson(layoutPath);
  const packageJson = fs.existsSync(packagePath) ? readJson(packagePath) : {};
  const failures = [];
  const activePaths = collectActivePaths(layout);

  if (layout.targetRepo !== 'axolync-platform-wrapper') {
    failures.push(`targetRepo must be axolync-platform-wrapper, got ${layout.targetRepo ?? '<missing>'}`);
  }

  if (requireFinalPackageName && packageJson.name !== 'axolync-platform-wrapper') {
    failures.push(`package name must be axolync-platform-wrapper after repo identity cutover, got ${packageJson.name ?? '<missing>'}`);
  }

  for (const entry of activePaths) {
    assertCanonicalPath(entry, failures);
  }

  const requiredRoots = [
    'wrappers/mobile/capacitor/android',
    'wrappers/desktop/tauri',
    'wrappers/desktop/electron',
  ];
  for (const requiredRoot of requiredRoots) {
    if (!pathExists(root, requiredRoot)) failures.push(`missing typed wrapper root ${requiredRoot}`);
  }

  if (!hasNonReadmeFile(root, 'wrappers/mobile/capacitor/android')) {
    failures.push('wrappers/mobile/capacitor/android is placeholder-only or missing');
  }
  if (!hasNonReadmeFile(root, 'wrappers/desktop/tauri')) {
    failures.push('wrappers/desktop/tauri is placeholder-only or missing');
  }
  if (!hasNonReadmeFile(root, 'wrappers/desktop/electron')) {
    failures.push('wrappers/desktop/electron is placeholder-only or missing');
  }

  return {
    ok: failures.length === 0,
    failures,
    paths: activePaths.map((entry) => entry.path),
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  const result = verifyWrapperTypedTopology({
    requireFinalPackageName: process.argv.includes('--require-final-package-name'),
  });
  if (!result.ok) {
    for (const failure of result.failures) console.error(failure);
    process.exit(1);
  }
  console.log('Wrapper typed topology verified');
}
