import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';

const templateSource = readFileSync(
  resolve(process.cwd(), 'wrappers/desktop/electron/workspace-template/main.cjs'),
  'utf8',
);

test('Electron template disables hardware acceleration before app readiness', () => {
  const hardeningCall = templateSource.indexOf('hardenChromiumGpuStartup();');
  const readyCall = templateSource.indexOf('app.whenReady()');

  assert.ok(hardeningCall >= 0, 'expected explicit Electron GPU startup hardening call');
  assert.ok(readyCall >= 0, 'expected Electron app readiness call');
  assert.ok(
    hardeningCall < readyCall,
    'GPU startup hardening must run before Electron creates the BrowserWindow',
  );
  assert.match(templateSource, /app\.disableHardwareAcceleration\(\);/);
  assert.match(templateSource, /AXOLYNC_ELECTRON_DISABLE_GPU_HARDENING/);
});
