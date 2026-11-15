#!/usr/bin/env node

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const PRODUCTION_NODE_BASELINE = '24.22.3';
const SHA256 = /^[a-f0-9]{64}$/;
const COMMIT_SHA = /^[a-f0-9]{40}$/;

export function validateRuntimeManifest(manifest, { forRelease = false } = {}) {
  const errors = [];
  const add = (message) => errors.push(message);

  if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) {
    return ['manifest must be a JSON object'];
  }
  if (manifest.schemaVersion !== 1) add('schemaVersion must equal 1');
  if (!['poc-only', 'production-approved'].includes(manifest.artifactClass)) {
    add('artifactClass must be poc-only or production-approved');
  }
  if (manifest.runtime !== 'nodejs-mobile') add('runtime must equal nodejs-mobile');
  if (manifest.abi !== 'arm64-v8a') add('Only arm64-v8a is supported for the shipping runtime');
  if (!['full', 'lite'].includes(manifest.profile)) add('profile must be full or lite');
  if (!Array.isArray(manifest.testAbis) || !manifest.testAbis.includes(manifest.abi) || manifest.testAbis.some((abi) => !['arm64-v8a', 'x86_64'].includes(abi))) {
    add('testAbis must contain the shipping ABI and may only include arm64-v8a or x86_64');
  }

  for (const field of ['nodeVersion', 'productionNodeVersion', 'sourceTag']) {
    if (typeof manifest[field] !== 'string' || manifest[field].trim() === '') {
      add(`${field} is required`);
    }
  }
  if (manifest.productionNodeVersion !== PRODUCTION_NODE_BASELINE) {
    add(`productionNodeVersion must remain pinned to ${PRODUCTION_NODE_BASELINE}`);
  }
  if (typeof manifest.sourceRepository !== 'string' || !manifest.sourceRepository.startsWith('https://')) {
    add('sourceRepository must be an HTTPS URL');
  }
  if (typeof manifest.archiveUrl !== 'string' || !manifest.archiveUrl.startsWith('https://')) {
    add('archiveUrl must be an HTTPS URL');
  }
  if (!SHA256.test(manifest.archiveSha256 ?? '')) {
    add('archiveSha256 must be a 64-character SHA-256 hex digest');
  }
  for (const field of ['sourceCommit', 'recipeCommit', 'sourceTree']) {
    if (!COMMIT_SHA.test(manifest[field] ?? '')) {
      add(`${field} must be a 40-character commit/tree SHA`);
    }
  }

  if (manifest.artifactClass === 'production-approved') {
    if (manifest.nodeVersion !== PRODUCTION_NODE_BASELINE || manifest.nodeVersion !== manifest.productionNodeVersion) {
      add(`production nodeVersion must equal productionNodeVersion (${PRODUCTION_NODE_BASELINE})`);
    }
    if (!SHA256.test(manifest.librarySha256 ?? '')) add('librarySha256 is required for a production-approved runtime');
    if (!SHA256.test(manifest.patchSetSha256 ?? '')) add('patchSetSha256 is required for a production-approved runtime');
    if (!Array.isArray(manifest.evidence) || manifest.evidence.length === 0 || manifest.evidence.some((item) => typeof item !== 'string' || item.trim() === '')) {
      add('evidence must contain at least one non-empty verification report reference');
    }
  }

  if (forRelease && manifest.artifactClass !== 'production-approved') {
    add('poc-only or unapproved runtime cannot be used for release');
  }
  return errors;
}

async function main() {
  const args = process.argv.slice(2);
  const forRelease = args.includes('--for-release');
  const manifestArg = args.find((arg) => !arg.startsWith('--')) ?? 'runtime/node-runtime.json';
  const manifestPath = path.resolve(process.cwd(), manifestArg);

  let manifest;
  try {
    manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  } catch (error) {
    console.error(`Unable to read runtime manifest ${manifestPath}: ${error.message}`);
    process.exitCode = 2;
    return;
  }

  const errors = validateRuntimeManifest(manifest, { forRelease });
  if (errors.length > 0) {
    console.error(`Runtime manifest rejected:\n- ${errors.join('\n- ')}`);
    process.exitCode = 1;
    return;
  }
  console.log(`Runtime manifest valid (${manifest.artifactClass}, Node ${manifest.nodeVersion}, ${manifest.profile}, ${manifest.abi}).`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
