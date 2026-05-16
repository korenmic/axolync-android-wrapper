#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function listFilesRecursive(rootDir) {
  if (!fs.existsSync(rootDir)) return [];
  const entries = [];
  for (const entry of fs.readdirSync(rootDir, { withFileTypes: true })) {
    const fullPath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      entries.push(...listFilesRecursive(fullPath));
    } else {
      entries.push(fullPath);
    }
  }
  return entries;
}

function normalizeRequiredStrings(values = []) {
  return Array.from(new Set(
    (Array.isArray(values) ? values : [])
      .map((value) => String(value || '').trim())
      .filter(Boolean),
  ));
}

export function verifyDesktopBrowserAssets({
  appDir = path.join(repoRoot, 'app'),
  requiredStrings = [],
} = {}) {
  const resolvedAppDir = path.resolve(appDir);
  const failures = [];
  const indexPath = path.join(resolvedAppDir, 'index.html');
  if (!fs.existsSync(indexPath)) {
    failures.push(`desktop browser asset root is missing index.html: ${indexPath}`);
  }

  const jsFiles = listFilesRecursive(path.join(resolvedAppDir, 'assets'))
    .filter((filePath) => filePath.endsWith('.js'));
  if (jsFiles.length === 0) {
    failures.push(`desktop browser asset root has no JavaScript assets under ${path.join(resolvedAppDir, 'assets')}`);
  }

  const combinedJs = jsFiles.map((filePath) => fs.readFileSync(filePath, 'utf8')).join('\n');
  const required = normalizeRequiredStrings(requiredStrings);
  const matchedStrings = [];
  for (const requiredString of required) {
    if (combinedJs.includes(requiredString) || (fs.existsSync(indexPath) && fs.readFileSync(indexPath, 'utf8').includes(requiredString))) {
      matchedStrings.push(requiredString);
    } else {
      failures.push(`desktop browser asset root is missing required runtime string: ${requiredString}`);
    }
  }

  return {
    ok: failures.length === 0,
    appDir: resolvedAppDir,
    indexPath,
    jsFiles,
    requiredStrings: required,
    matchedStrings,
    failures,
  };
}

function parseCliArgs(argv) {
  const args = {
    appDir: '',
    requiredStrings: [],
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--app-dir') {
      args.appDir = argv[index + 1] || '';
      index += 1;
    } else if (arg === '--require-string') {
      args.requiredStrings.push(argv[index + 1] || '');
      index += 1;
    }
  }
  return args;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = parseCliArgs(process.argv.slice(2));
  const result = verifyDesktopBrowserAssets({
    appDir: args.appDir || path.join(repoRoot, 'app'),
    requiredStrings: args.requiredStrings,
  });
  if (!result.ok) {
    console.error(result.failures.join('\n'));
    process.exit(1);
  }
  console.log(`Desktop browser assets verified: ${JSON.stringify({
    appDir: result.appDir,
    jsFileCount: result.jsFiles.length,
    requiredStrings: result.requiredStrings,
  })}`);
}
