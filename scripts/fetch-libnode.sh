#!/usr/bin/env bash
# Fetches a pinned Node Android POC archive and extracts only approved test ABIs.
# All output is ignored by Git and is never eligible for a release APK.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST_ARG="${1:-runtime/node-runtime.json}"
MANIFEST="${ROOT_DIR}/${MANIFEST_ARG}"
if [[ ! -f "${MANIFEST}" ]]; then
  echo "Runtime manifest not found: ${MANIFEST}" >&2
  exit 2
fi

MANIFEST_INFO="$(node -e 'const m=require(process.argv[1]); console.log([m.artifactClass,m.nodeVersion,m.archiveUrl,m.archiveSha256,m.abi,m.profile,...m.testAbis].join("\n"))' "${MANIFEST}")"
mapfile -t INFO <<<"${MANIFEST_INFO}"
ARTIFACT_CLASS="${INFO[0]}"
NODE_VERSION="${INFO[1]}"
ARCHIVE_URL="${INFO[2]}"
ARCHIVE_SHA256="${INFO[3]}"
SHIPPING_ABI="${INFO[4]}"
PROFILE="${INFO[5]}"
TEST_ABIS=("${INFO[@]:6}")

if [[ "${ARTIFACT_CLASS}" != "poc-only" ]]; then
  echo "Refusing to fetch POC binaries for artifact class: ${ARTIFACT_CLASS}" >&2
  exit 2
fi
if [[ "${NODE_VERSION}" != "24.22.1-0" ]]; then
  echo "Runtime version does not match the reviewed POC pin" >&2
  exit 2
fi
if [[ "${SHIPPING_ABI}" != "arm64-v8a" ]]; then
  echo "Shipping ABI must remain arm64-v8a" >&2
  exit 2
fi
if [[ "${PROFILE}" != "full" && "${PROFILE}" != "lite" ]]; then
  echo "Runtime profile must be full or lite" >&2
  exit 2
fi
if [[ "${#TEST_ABIS[@]}" -eq 0 ]]; then
  echo "Manifest has no test ABIs" >&2
  exit 2
fi

ARCHIVE_NAME="$(node -e 'console.log(new URL(process.argv[1]).pathname.split("/").pop())' "${ARCHIVE_URL}")"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

curl --fail --location --retry 3 --retry-all-errors --silent --show-error \
  --output "${WORK_DIR}/${ARCHIVE_NAME}" "${ARCHIVE_URL}"
printf '%s  %s\n' "${ARCHIVE_SHA256}" "${WORK_DIR}/${ARCHIVE_NAME}" | sha256sum --check --status || {
  echo "Node mobile archive SHA-256 mismatch" >&2
  exit 3
}

unzip -Z1 "${WORK_DIR}/${ARCHIVE_NAME}" >"${WORK_DIR}/files.txt"
EXTRACT_ARGS=()
for abi in "${TEST_ABIS[@]}"; do
  if [[ "${abi}" != "arm64-v8a" && "${abi}" != "x86_64" ]]; then
    echo "Unsupported test ABI: ${abi}" >&2
    exit 2
  fi
  grep -Fxq "bin/${abi}/libnode.so" "${WORK_DIR}/files.txt" || {
    echo "Pinned archive does not contain bin/${abi}/libnode.so" >&2
    exit 4
  }
  EXTRACT_ARGS+=("bin/${abi}/libnode.so")
done
grep -Fxq "include/node/node.h" "${WORK_DIR}/files.txt" || {
  echo "Pinned archive does not contain include/node/node.h" >&2
  exit 4
}
EXTRACT_ARGS+=("include/node/*")

DEST_ROOT="${ROOT_DIR}/runtime/poc/${PROFILE}"
rm -rf "${DEST_ROOT}"
mkdir -p "${WORK_DIR}/extract" "${DEST_ROOT}"
unzip -q "${WORK_DIR}/${ARCHIVE_NAME}" "${EXTRACT_ARGS[@]}" -d "${WORK_DIR}/extract"
for abi in "${TEST_ABIS[@]}"; do
  mkdir -p "${DEST_ROOT}/${abi}"
  cp "${WORK_DIR}/extract/bin/${abi}/libnode.so" "${DEST_ROOT}/${abi}/libnode.so"
  sha256sum "${DEST_ROOT}/${abi}/libnode.so" >"${DEST_ROOT}/${abi}/libnode.so.sha256"
done
mkdir -p "${DEST_ROOT}/include"
cp -R "${WORK_DIR}/extract/include/node" "${DEST_ROOT}/include/node"
cp "${WORK_DIR}/files.txt" "${DEST_ROOT}/archive-files.txt"
printf 'Fetched verified Node %s %s POC runtime. Shipping ABI: %s. Test ABIs: %s. This runtime is not eligible for release APKs.\n' \
  "${NODE_VERSION}" "${PROFILE}" "${SHIPPING_ABI}" "${TEST_ABIS[*]}"
