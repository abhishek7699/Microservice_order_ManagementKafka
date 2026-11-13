#!/usr/bin/env bash
# Delete the local kind cluster. Only removes the 'order-local' cluster;
# other kubectl contexts (including GKE) are not touched.
set -euo pipefail

CLUSTER=order-local

if ! command -v kind >/dev/null 2>&1; then
  echo "kind is not installed; nothing to delete."
  exit 0
fi

if kind get clusters 2>/dev/null | grep -qx "${CLUSTER}"; then
  kind delete cluster --name "${CLUSTER}"
  echo "Deleted kind cluster '${CLUSTER}'."
else
  echo "Cluster '${CLUSTER}' does not exist."
fi
