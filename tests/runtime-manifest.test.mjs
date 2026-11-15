import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { validateRuntimeManifest } from '../scripts/validate-runtime-manifest.mjs';

const base = JSON.parse(await readFile(new URL('../runtime/node-runtime.json', import.meta.url), 'utf8'));
const lite = JSON.parse(await readFile(new URL('../runtime/node-runtime-lite.json', import.meta.url), 'utf8'));

test('accepts the pinned Android full and lite POC manifests for development', () => {
  assert.deepEqual(validateRuntimeManifest(base), []);
  assert.deepEqual(validateRuntimeManifest(lite), []);
});

test('rejects a POC runtime for release builds', () => {
  assert.match(validateRuntimeManifest(base, { forRelease: true }).join('\n'), /poc-only.*cannot be used for release/i);
  assert.match(validateRuntimeManifest(lite, { forRelease: true }).join('\n'), /poc-only.*cannot be used for release/i);
});

test('rejects malformed archive hashes and source commits', () => {
  const invalid = { ...base, archiveSha256: '1234', sourceCommit: 'deadbeef' };
  const errors = validateRuntimeManifest(invalid);
  assert.match(errors.join('\n'), /archiveSha256.*64-character SHA-256/i);
  assert.match(errors.join('\n'), /sourceCommit.*40-character commit\/tree SHA/i);
});

test('rejects an unknown runtime profile', () => {
  const invalid = { ...base, profile: 'debugger-plus' };
  assert.match(validateRuntimeManifest(invalid).join('\n'), /profile must be full or lite/i);
});

test('rejects unsupported ABIs and incomplete test ABI lists', () => {
  const invalid = { ...base, abi: 'armeabi-v7a' };
  assert.match(validateRuntimeManifest(invalid).join('\n'), /Only arm64-v8a is supported/i);
  const missingShipping = { ...base, testAbis: ['x86_64'] };
  assert.match(validateRuntimeManifest(missingShipping).join('\n'), /testAbis must contain the shipping ABI/i);
});

test('requires exact production Node baseline and measured runtime evidence', () => {
  const invalid = {
    ...base,
    artifactClass: 'production-approved',
    nodeVersion: '24.22.1-0',
    librarySha256: null,
    patchSetSha256: null,
    evidence: [],
  };
  const errors = validateRuntimeManifest(invalid, { forRelease: true });
  assert.match(errors.join('\n'), /must equal productionNodeVersion/i);
  assert.match(errors.join('\n'), /librarySha256.*required/i);
  assert.match(errors.join('\n'), /patchSetSha256.*required/i);
  assert.match(errors.join('\n'), /evidence.*at least one/i);
});

test('accepts production manifests only with pinned version, binary hashes and verification evidence', () => {
  const production = {
    ...base,
    artifactClass: 'production-approved',
    nodeVersion: '24.22.3',
    sourceTag: 'v24.22.3-mobile-reviewed',
    sourceCommit: 'c'.repeat(40),
    recipeCommit: 'd'.repeat(40),
    sourceTree: 'e'.repeat(40),
    librarySha256: 'a'.repeat(64),
    patchSetSha256: 'b'.repeat(64),
    evidence: ['native-smoke-report.json'],
  };
  assert.deepEqual(validateRuntimeManifest(production, { forRelease: true }), []);
});
