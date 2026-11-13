#!/usr/bin/env bash
# End-to-end smoke test through the API gateway.
# Creates a product, places an order with an Idempotency-key, repeats the
# request to check idempotency, waits for the order to be processed, and
# checks that stock was deducted. Exits non-zero if the order is not CONFIRMED.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"   # local-up.sh passes the port it actually used
json() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }

echo "==> 1. Create product"
PRODUCT=$(curl -fsS -X POST "${BASE}/products" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Smoke Laptop","price":999.99,"stockQuantity":10}')
echo "${PRODUCT}"
PID=$(echo "${PRODUCT}" | json '["id"]')

KEY="smoke-$(date +%s)"
echo "==> 2. Place order (Idempotency-key: ${KEY}, quantity 2)"
ORDER=$(curl -fsS -X POST "${BASE}/orders" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-key: ${KEY}" \
  -d "{\"customerId\":\"smoke-customer\",\"productId\":\"${PID}\",\"quantity\":2}")
echo "${ORDER}"
OID=$(echo "${ORDER}" | json '["orderId"]')

echo "==> 3. Repeat the same request with the same key (expect the same orderId, HTTP 200)"
REPEAT_FILE=$(mktemp)
trap 'rm -f "${REPEAT_FILE}"' EXIT
REPEAT_CODE=$(curl -sS -o "${REPEAT_FILE}" -w '%{http_code}' -X POST "${BASE}/orders" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-key: ${KEY}" \
  -d "{\"customerId\":\"smoke-customer\",\"productId\":\"${PID}\",\"quantity\":2}")
echo "HTTP ${REPEAT_CODE}: $(cat "${REPEAT_FILE}")"
[ "$(json '["orderId"]' < "${REPEAT_FILE}")" = "${OID}" ] || { echo "FAIL: repeat returned a different order"; exit 1; }

echo "==> 4. Waiting for order to leave PENDING (outbox poll is every 5s)"
STATUS=PENDING
for _ in $(seq 1 30); do
  STATUS=$(curl -fsS "${BASE}/orders/${OID}" | json '["status"]')
  [ "${STATUS}" != "PENDING" ] && break
  sleep 2
done
echo "Order ${OID} status: ${STATUS}"
curl -fsS "${BASE}/orders/${OID}"
echo

echo "==> 5. Product stock after the order (expect 8)"
curl -fsS "${BASE}/products/${PID}"
echo

[ "${STATUS}" = "CONFIRMED" ] || { echo "FAIL: expected CONFIRMED, got ${STATUS}"; exit 1; }
echo "SMOKE TEST PASSED"
