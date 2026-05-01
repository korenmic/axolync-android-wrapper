import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function hasNonReadmeFile(dir) {
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) return false;
  const stack = [dir];
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

function assertFiles(root, files, label, failures) {
  for (const relativePath of files) {
    if (!fs.existsSync(path.join(root, relativePath))) {
      failures.push(`${label} missing ${relativePath}`);
    }
  }
}

export function verifyWrapperSourceOwnership({
  root = repoRoot,
  layoutPath = path.join(root, 'config', 'wrapper-layout.json'),
} = {}) {
  const layout = readJson(layoutPath);
  const android = layout?.families?.capacitor?.android ?? {};
  const ios = layout?.families?.capacitor?.ios ?? {};
  const androidRoot = path.join(root, android.authorityPath ?? 'wrappers/capacitor/android');
  const tauriRoot = path.join(root, 'templates', 'desktop', 'tauri');
  const electronRoot = path.join(root, 'templates', 'desktop', 'electron');
  const nativeRoot = path.join(root, 'native-service-companions');
  const failures = [];

  if (!hasNonReadmeFile(androidRoot)) {
    failures.push('wrappers/capacitor/android is placeholder-only; canonical Android source is missing');
  }

  assertFiles(androidRoot, [
    'app/build.gradle.kts',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt',
    'capacitor.config.json',
    'settings.gradle.kts',
  ], 'canonical Capacitor Android source', failures);

  assertFiles(tauriRoot, [
    'package.json',
    'src-tauri/Cargo.toml',
    'src-tauri/tauri.conf.json',
    'src-tauri/src/main.rs',
  ], 'canonical Tauri template source', failures);

  assertFiles(electronRoot, [
    'package.json',
    'main.cjs',
    'preload.cjs',
    'nativeServiceCompanionHost.cjs',
  ], 'canonical Electron template source', failures);

  assertFiles(nativeRoot, [
    'host-protocol/capability-states.json',
    'deployment/README.md',
    'diagnostics/README.md',
  ], 'native companion host source', failures);

  if (ios.support !== 'placeholder-only') {
    failures.push('wrappers/capacitor/ios must remain placeholder-only until a separate runnable iOS seed proves support');
  }

  return {
    ok: failures.length === 0,
    failures,
    paths: {
      androidRoot: path.relative(root, androidRoot).replaceAll('\\', '/'),
      tauriRoot: path.relative(root, tauriRoot).replaceAll('\\', '/'),
      electronRoot: path.relative(root, electronRoot).replaceAll('\\', '/'),
      nativeRoot: path.relative(root, nativeRoot).replaceAll('\\', '/'),
    },
  };
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url))) {
  const result = verifyWrapperSourceOwnership();
  if (!result.ok) {
    for (const failure of result.failures) console.error(failure);
    process.exit(1);
  }
  console.log(`Wrapper source ownership verified: ${JSON.stringify(result.paths)}`);
}
