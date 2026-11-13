#!/usr/bin/env bash
# Bring up the whole system on a local kind cluster (Apple Silicon).
# Usage: k8s-local/local-up.sh
# Only touches the kind cluster "order-local" (context kind-order-local).
set -euo pipefail

CLUSTER=order-local
CTX="kind-${CLUSTER}"
K8S_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "${K8S_DIR}/.." && pwd)"
SERVICES=(order-service inventory-service api-gateway)
KC=(kubectl --context "${CTX}")

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1"; exit 1; }; }
for tool in docker kind kubectl mvn python3 curl; do need "$tool"; done
docker info >/dev/null 2>&1 || { echo "Docker is not running. Start Docker Desktop first."; exit 1; }

cd "${REPO_DIR}"

echo "==> 1/6 Building jars (clean build, skipping tests)"
# 'clean' removes stale class files in target/ (e.g. "Foo 2.class" copies that
# break spring-boot repackage with "Unable to find a single main class").
for svc in "${SERVICES[@]}"; do
  (cd "${svc}" && mvn -q -DskipTests clean package)
done

echo "==> 2/6 Creating kind cluster '${CLUSTER}' if missing"
# Host port for the gateway. Default 8080. Set GATEWAY_PORT to use another
# port when 8080 is taken, e.g. GATEWAY_PORT=8095 k8s-local/local-up.sh
GATEWAY_PORT="${GATEWAY_PORT:-8080}"
KIND_CONFIG="$(mktemp)"
trap 'rm -f "${KIND_CONFIG}"' EXIT
sed "s/hostPort: 8080/hostPort: ${GATEWAY_PORT}/" "${K8S_DIR}/kind-config.yaml" > "${KIND_CONFIG}"

if kind get clusters 2>/dev/null | grep -qx "${CLUSTER}"; then
  echo "    cluster already exists, reusing it (gateway port set at creation time)"
else
  kind create cluster --name "${CLUSTER}" --config "${KIND_CONFIG}"
fi

echo "==> 3/6 Building arm64 images and loading them into kind"
for svc in "${SERVICES[@]}"; do
  image="${svc}:local"
  docker build --platform linux/arm64 -t "${image}" "${svc}"
  kind load docker-image "${image}" --name "${CLUSTER}"
done

# Apply in dependency order and wait for each stage before the next one.
# The gateway must start after the services it routes to, otherwise its
# DNS lookups for those services can fail and stay broken until restart.
wait_stage() {
  local label="$1"; shift
  echo "    waiting for: ${label} (up to 10 min)"
  if ! "${KC[@]}" -n default wait --for=condition=Available "$@" --timeout=600s; then
    echo "Stage '${label}' did not become Available. Current state:"
    "${KC[@]}" -n default get pods
    echo "Inspect a pod with: kubectl --context ${CTX} -n default logs <pod-name>"
    exit 1
  fi
}

echo "==> 4/6 Applying manifests in order, waiting for each stage"
echo "    stage 1: infrastructure (kafka, databases, redis)"
"${KC[@]}" apply -f "${K8S_DIR}/01-kafka.yaml" -f "${K8S_DIR}/02-postgres.yaml" -f "${K8S_DIR}/03-redis.yaml"
wait_stage "kafka, postgres, redis" deployment/kafka deployment/order-postgres \
  deployment/inventory-postgres deployment/redis

echo "    stage 2: order-service and inventory-service"
"${KC[@]}" apply -f "${K8S_DIR}/04-inventory-service.yaml" -f "${K8S_DIR}/05-order-service.yaml"
wait_stage "inventory-service, order-service" deployment/inventory-service deployment/order-service

echo "    stage 3: api-gateway"
"${KC[@]}" apply -f "${K8S_DIR}/06-api-gateway.yaml"
wait_stage "api-gateway" deployment/api-gateway

echo "==> 5/6 Checking all pods in the default namespace are Ready"
"${KC[@]}" -n default get pods
if ! "${KC[@]}" -n default wait --for=condition=Ready pod --all --timeout=120s; then
  echo "Some pods are not Ready. Current state:"
  "${KC[@]}" -n default get pods
  exit 1
fi

echo "==> 6/6 Smoke test"
BASE="http://localhost:${GATEWAY_PORT}" "${K8S_DIR}/smoke-test.sh"

echo
echo "Ready. Gateway: http://localhost:${GATEWAY_PORT}  (stop with k8s-local/local-down.sh)"
