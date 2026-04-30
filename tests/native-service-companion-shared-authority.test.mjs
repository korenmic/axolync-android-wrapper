import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const repoRoot = path.resolve(import.meta.dirname, '..');

test('native companion shared authority defines generic host states outside Android-only paths', () => {
  const layout = JSON.parse(
    fs.readFileSync(path.join(repoRoot, 'config', 'wrapper-layout.json'), 'utf8'),
  );
  const protocolPath = path.join(repoRoot, layout.nativeServiceCompanions.hostProtocol, 'capability-states.json');
  const protocol = JSON.parse(fs.readFileSync(protocolPath, 'utf8'));
  const states = new Map(protocol.states.map((state) => [state.id, state]));

  for (const required of ['unsupported', 'unavailable', 'refused', 'startup-failed', 'running']) {
    assert.equal(states.has(required), true, `missing ${required}`);
  }
  assert.equal(states.get('running').available, true);
  assert.equal(states.get('refused').lastErrorSource, 'trust-policy');
  assert.equal(protocolPath.includes(`${path.sep}app${path.sep}src${path.sep}main`), false);
  assert.equal(JSON.stringify(protocol).includes('vibra'), false);
  assert.equal(JSON.stringify(protocol).includes('lrclib'), false);
});
