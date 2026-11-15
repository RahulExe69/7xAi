#!/usr/bin/env bash
# Fetches the pinned Android Node runtime for the POC only. This script never
# installs the POC runtime into the shipping APK and refuses production manifests.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="${ROOT_DIR}/runtime/node-runtime.json"
MANIFEST_INFO="$(node -e 'const m=require(process.argv[1]); console.log([m.artifactClass,m.nodeVersion,m.archiveUrl,m.archiveSha256,m.abi].join("\n"))' "${MANIFEST}")"
mapfile -t INFO <<<"${MANIFEST_INFO}"
ARTIFACT_CLASS="${INFO[0]}"
NODE_VERSION="${INFO[1]}"
ARCHIVE_URL="${INFO[2]}"
ARCHIVE_SHA256="${INFO[3]}"
ABI="${INFO[4]}"

if [[ "${ARTIFACT_CLASS}" != "poc-only" ]]; then
  echo "Refusing to fetch POC binaries for artifact class: ${ARTIFACT_CLASS}" >&2
  exit 2
fi
if [[ "${ABI}" != "arm64-v8a" ]]; then
  echo "This fetch script only supports arm64-v8a, got ${ABI}" >&2
  exit 2
fi
if [[ "${NODE_VERSION}" != "24.22.1-0" ]]; then
  echo "Runtime version does not match the reviewed POC pin" >&2
  exit 2
fi

ARCHIVE_NAME="nodejs-mobile-android-${NODE_VERSION}.zip"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
  --output "${WORK_DIR}/${ARCHIVE_NAME}" "${ARCHIVE_URL}"
printf '%s  %s\n' "${ARCHIVE_SHA256}" "${WORK_DIR}/${ARCHIVE_NAME}" | sha256sum --check --status || {
  echo "Node mobile archive SHA-256 mismatch" >&2
  exit 3
}

unzip -Z1 "${WORK_DIR}/${ARCHIVE_NAME}" >"${WORK_DIR}/files.txt"
grep -Fxq "bin/${ABI}/libnode.so" "${WORK_DIR}/files.txt" || {
  echo "Pinned archive does not contain bin/${ABI}/libnode.so" >&2
  exit 4
}
grep -Fxq "include/node/node.h" "${WORK_DIR}/files.txt" || {
  echo "Pinned archive does not contain include/node/node.h" >&2
  exit 4
}

mkdir -p "${WORK_DIR}/extract" "${ROOT_DIR}/runtime/poc/${ABI}"
unzip -q "${WORK_DIR}/${ARCHIVE_NAME}" \
  "bin/${ABI}/libnode.so" "include/node/*" -d "${WORK_DIR}/extract"
cp "${WORK_DIR}/extract/bin/${ABI}/libnode.so" "${ROOT_DIR}/runtime/poc/${ABI}/libnode.so"
rm -rf "${ROOT_DIR}/runtime/poc/include"
mkdir -p "${ROOT_DIR}/runtime/poc/include"
cp -R "${WORK_DIR}/extract/include/node" "${ROOT_DIR}/runtime/poc/include/node"
sha256sum "${ROOT_DIR}/runtime/poc/${ABI}/libnode.so" \
  >"${ROOT_DIR}/runtime/poc/${ABI}/libnode.so.sha256"
printf 'Fetched verified Node %s POC runtime for %s. This binary is not eligible for release APKs.\n' \
  "${NODE_VERSION}" "${ABI}"
