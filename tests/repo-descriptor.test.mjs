import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { test } from 'node:test';

const require = createRequire(import.meta.url);
const repoRoot = resolve(import.meta.dirname, '..');
const contract = require('../../axolync-contract/tools/repo-descriptor.js');

test('platform-wrapper descriptor validates through axolync-contract', () => {
  const descriptorPath = resolve(repoRoot, 'axolync.repo.toml');
  const descriptorText = readFileSync(descriptorPath, 'utf8');
  const result = contract.parseRepoDescriptorToml(descriptorText, { path: descriptorPath });

  assert.equal(result.ok, true, JSON.stringify(result.errors, null, 2));
  assert.equal(result.descriptor.repo.id, 'axolync-platform-wrapper');
  assert.deepEqual(result.descriptor.repo.roles, ['consumer', 'consumable']);
});

test('platform-wrapper descriptor declares required contract and browser dependencies', () => {
  const descriptor = contract.loadRepoDescriptorFile(resolve(repoRoot, 'axolync.repo.toml')).descriptor;
  const consumes = descriptor.consumes.repos.map((repo) => ({
    id: repo.id,
    use: repo.use,
    required: repo.required
  }));

  assert.deepEqual(consumes, [
    {
      id: 'axolync-contract',
      use: 'schema-and-runtime-contracts',
      required: true
    },
    {
      id: 'axolync-browser',
      use: 'browser-runtime',
      required: true
    }
  ]);
});
