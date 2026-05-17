import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');

function readRepoFile(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function assertContains(source, expected, label) {
  assert.equal(
    source.includes(expected),
    true,
    `${label} must include ${expected}`,
  );
}

test('Tauri wrapper owns active LRCLIB local loopback runtime support', () => {
  const tauriRuntime = readRepoFile(
    'wrappers/desktop/tauri/workspace-template/src-tauri/src/native_service_companion.rs',
  );
  const cargoToml = readRepoFile('wrappers/desktop/tauri/workspace-template/src-tauri/Cargo.toml');

  assertContains(cargoToml, 'brotli = "7"', 'Tauri native runtime dependencies');
  assertContains(cargoToml, 'rusqlite', 'Tauri native runtime dependencies');
  assertContains(
    tauriRuntime,
    '"lrclib-local-loopback-v1" => start_lrclib_runtime_operator(',
    'Tauri runtime dispatch',
  );
  assertContains(tauriRuntime, 'fn start_lrclib_runtime_operator(', 'Tauri LRCLIB starter');
  assertContains(tauriRuntime, 'fn deploy_brotli_db_once(', 'Tauri LRCLIB DB deployment');
  assertContains(tauriRuntime, 'brotli::Decompressor::new', 'Tauri Brotli DB deployment');
  assertContains(tauriRuntime, 'Connection::open_with_flags', 'Tauri SQLite query engine');
  assertContains(tauriRuntime, '"/api/get"', 'Tauri LRCLIB get route');
  assertContains(tauriRuntime, '"/api/search"', 'Tauri LRCLIB search route');
  assertContains(tauriRuntime, '"runtime-operator.dispatch.selected"', 'Tauri dispatch diagnostics');
  assertContains(tauriRuntime, '"runtime-operator.dispatch.unsupported"', 'Tauri unsupported diagnostics');
  assertContains(tauriRuntime, '"runtime-operator.loopback.request.completed"', 'Tauri request diagnostics');
  assertContains(tauriRuntime, '"x-axolync-lrclib-local-result"', 'Tauri local-result header');
});

test('Capacitor wrapper owns active LRCLIB local loopback runtime support', () => {
  const capacitorRuntime = readRepoFile(
    'wrappers/mobile/capacitor/android/app/src/main/kotlin/com/axolync/android/bridge/AxolyncNativeServiceCompanionHostPlugin.kt',
  );
  const buildGradle = readRepoFile('wrappers/mobile/capacitor/android/app/build.gradle.kts');

  assertContains(buildGradle, 'org.brotli:dec', 'Capacitor native runtime dependencies');
  assertContains(
    capacitorRuntime,
    'private const val OPERATOR_KIND_LRCLIB_LOCAL = "lrclib-local-loopback-v1"',
    'Capacitor runtime dispatch constant',
  );
  assertContains(
    capacitorRuntime,
    'OPERATOR_KIND_LRCLIB_LOCAL -> LrclibLocalLoopbackServer(context, registration, registration.operator, logger)',
    'Capacitor runtime dispatch',
  );
  assertContains(capacitorRuntime, 'fun start(call: PluginCall)', 'Capacitor native start method');
  assertContains(capacitorRuntime, 'fun request(call: PluginCall)', 'Capacitor native request method');
  assertContains(capacitorRuntime, 'fun getDiagnostics(call: PluginCall)', 'Capacitor diagnostics method');
  assertContains(capacitorRuntime, 'BrotliInputStream', 'Capacitor Brotli DB deployment');
  assertContains(capacitorRuntime, 'SQLiteDatabase.openDatabase', 'Capacitor SQLite query engine');
  assertContains(capacitorRuntime, 'deployLrclibDbOnce', 'Capacitor LRCLIB DB deployment');
  assertContains(capacitorRuntime, 'LrclibNativeQueryEngine', 'Capacitor LRCLIB query engine');
  assertContains(capacitorRuntime, '"/api/get"', 'Capacitor LRCLIB get route');
  assertContains(capacitorRuntime, '"/api/search"', 'Capacitor LRCLIB search route');
  assertContains(capacitorRuntime, 'runtime-operator.dispatch.selected', 'Capacitor dispatch diagnostics');
  assertContains(capacitorRuntime, 'runtime-operator.dispatch.unsupported', 'Capacitor unsupported diagnostics');
  assertContains(capacitorRuntime, 'loopback-route-miss', 'Capacitor route diagnostics');
  assertContains(capacitorRuntime, 'upstream-json-parse-failure', 'Capacitor JSON diagnostics');
  assertContains(capacitorRuntime, 'runtime-operator-crash', 'Capacitor startup failure containment');
  assertContains(capacitorRuntime, 'x-axolync-lrclib-local-result', 'Capacitor local-result header');
});
