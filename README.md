# Microservice Order System

An event-driven microservices system built with Spring Boot 3, Apache Kafka, PostgreSQL, and Redis. Order and inventory services communicate asynchronously through Kafka, and an API Gateway is the single entry point for clients.

---

## Architecture Overview

```
Client
  │
  ▼
API Gateway (8080)  ── Redis (rate limiting)
  │
  ├── /products/**  ──► inventory-service (8081) ── PostgreSQL inventory_db (5433)
  │                          │  ▲
  │                          │  │ order.created
  │                          ▼  │
  │                         Kafka (9092)
  │                          │  ▲
  │                          │  │ inventory.updated
  │                          ▼  │
  └── /orders/**    ──► order-service (8082) ──── PostgreSQL order_db (5434)
```

## Event Flow

```
POST /orders  (requires Idempotency-key header)
    │
    ▼
order-service saves the order (PENDING) and an outbox row in one DB transaction
    │
    ▼
OutboxPoller (every 5s) publishes the outbox row to Kafka: order.created
    │
    ▼
inventory-service consumes order.created
    │   • enough stock  → deducts stock, status CONFIRMED
    │   • not enough    → status FAILED, stock unchanged
    │   • result cached in Redis for 24h (idempotency)
    ▼
Kafka: inventory.updated  (status + totalPrice)
    │
    ▼
order-service consumes inventory.updated → updates order status and totalPrice
    │
    ▼
GET /orders/{id} → CONFIRMED or FAILED
```

If processing an `order.created` message fails, the inventory consumer retries it 3 times (1 second apart). After the last failure the message is sent to the dead-letter topic `order.created.DLT`.

---

## Services

### 1. API Gateway `:8080`
- Single entry point for all client requests (Spring Cloud Gateway)
- Routes `/products/**` → `inventory-service:8081`
- Routes `/orders/**` → `order-service:8082`
- **Rate limiting** (Redis token bucket), per route:
  - products: 5 requests/second, burst 10
  - orders: 10 requests/second, burst 20
  - Clients are identified by the `X-Customer-Id` header, or by IP address if the header is missing
- **Circuit breaker** (Resilience4j) on each route. It opens when 50% of the last 10 calls fail, stays open for 10 seconds, then lets 3 trial calls through. While open, requests go to a fallback that returns `503` with a JSON error message.

### 2. inventory-service `:8081`
- Manages products and stock (`products` table)
- REST endpoints for listing, fetching, and creating products
- Consumes `order.created` and deducts stock when there is enough
- Publishes the result to `inventory.updated`
- Caches each order's result in Redis for 24 hours
- Dead-letter consumer for `order.created.DLT` (currently only logs the message)

### 3. order-service `:8082`
- Accepts orders and returns them as `PENDING`
- Writes each new order and its outbox event in one transaction
- `OutboxPoller` publishes unsent outbox events to `order.created` every 5 seconds
- Consumes `inventory.updated` and sets the order's status and total price
- Caches the response for each `Idempotency-key` in Redis for 24 hours, so repeated requests return the same order

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 | Language (Maven builds). The order-service Docker image uses JDK 21. |
| Spring Boot 3.x | Application framework (order-service uses 3.2.3) |
| Spring Cloud Gateway | API Gateway and routing |
| Resilience4j | Circuit breaker on gateway routes |
| Spring Kafka | Kafka producers, consumers, retries, and dead-letter topic |
| PostgreSQL 15 | Persistent storage, one database per service |
| Redis | Rate limiting, idempotency cache |
| Spring Boot Actuator + Micrometer | Health and Prometheus metrics |
| Prometheus + Grafana | Monitoring (Docker Compose) |
| Docker & Docker Compose | Containerization |
| Kubernetes | Deployment manifests in `k8s/` |
| Jenkins | Build, Docker image build, and push (`Jenkinsfile`) |
| Lombok | Boilerplate reduction |

---

## Project Structure

```
Microservice-order-kafka_managment/
├── docker-compose.yml              ← Kafka, Zookeeper, Prometheus, Grafana
├── prometheus.yml                  ← Prometheus scrape config
├── Jenkinsfile                     ← CI: build, Docker build and push
├── k8s/                            ← Kubernetes manifests
│   ├── api-gateway/deployment.yaml
│   ├── inventory-service/{deployment,postgres}.yaml
│   ├── order-service/{deployment,postgres}.yaml
│   ├── kafka/{kafka,zookeeper}.yaml
│   └── redis.yaml
├── api-gateway/
│   ├── src/main/java/com/orderSystem/
│   │   ├── ApiGatewayApplication.java
│   │   └── gateway/
│   │       ├── RateLimiter/RateLimiterConfig.java   ← rate limit key resolver
│   │       └── controller/FallBackController.java   ← circuit breaker fallbacks
│   ├── src/main/resources/application.yml           ← routes, rate limits, circuit breakers
│   ├── Dockerfile
│   └── pom.xml
├── inventory-service/
│   ├── src/main/java/com/orderSystem/inventory/
│   │   ├── controller/ProductController.java
│   │   ├── service/ProductService.java
│   │   ├── repository/ProductRepository.java
│   │   ├── model/Product.java
│   │   ├── dto/ProductResponseDto.java
│   │   └── kafka/
│   │       ├── OrderEventConsumer.java       ← consumes order.created
│   │       ├── InventoryEventProducer.java   ← publishes inventory.updated
│   │       ├── KafkaConsumerConfig.java      ← retries and dead-letter topic
│   │       ├── DLQConsumer.java              ← consumes order.created.DLT
│   │       ├── OrderCreatedEvent.java
│   │       └── InventoryUpdatedEvent.java
│   ├── src/main/resources/application.yml
│   ├── docker-compose.yml          ← inventory-postgres and pgAdmin
│   ├── Dockerfile
│   └── pom.xml
└── order-service/
    ├── src/main/java/com/orderSystem/order/
    │   ├── controller/OrderController.java
    │   ├── service/
    │   │   ├── OrderService.java
    │   │   └── OutboxPoller.java             ← publishes outbox events
    │   ├── repository/
    │   │   ├── OrderRepository.java
    │   │   └── OutBoxRepository.java
    │   ├── model/
    │   │   ├── Order.java
    │   │   └── OutBoxEvent.java
    │   ├── dto/
    │   │   ├── OrderRequestDto.java
    │   │   └── OrderResponseDto.java
    │   └── kafka/
    │       ├── OrderEventProducer.java       ← publishes order.created
    │       ├── InventoryEventConsumer.java   ← consumes inventory.updated
    │       ├── OrderCreatedEvent.java
    │       └── InventoryUpdatedEvent.java
    ├── src/main/resources/application.yml
    ├── docker-compose.yml          ← order-postgres and pgAdmin
    ├── Dockerfile
    └── pom.xml
```

---

## Getting Started

### Prerequisites
- Java 17
- Maven (or the included `./mvnw` wrapper)
- Docker & Docker Compose

### 1. Start Kafka, Zookeeper, Prometheus, and Grafana
```bash
# from the repository root
docker-compose up -d
```
Kafka listens on `localhost:9092`. Prometheus is on `:9090` and Grafana on `:3000` (default login `admin` / `admin`).

### 2. Start the Databases
```bash
# inventory-service DB (inventory_db on port 5433)
cd inventory-service
docker-compose up -d postgres

# order-service DB (order_db on port 5434)
cd ../order-service
docker-compose up -d postgres
```
pgAdmin is available on `:5051` (inventory) and `:5050` (order). Login: `admin@admin.com` / `admin`.

### 3. Start Redis
No compose file starts Redis yet, so start it manually:
```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### 4. Run the Services
```bash
cd inventory-service && ./mvnw spring-boot:run
cd order-service     && ./mvnw spring-boot:run
cd api-gateway       && ./mvnw spring-boot:run
```

> **Note on the gateway:** its routes use the Docker/Kubernetes hostnames `inventory-service` and `order-service`, and its Redis host is `redis`. Running the gateway on your machine therefore only routes correctly if those names resolve, for example by running all services as containers on one Docker network. Otherwise, run the gateway in Docker or Kubernetes. Services that run directly on the host use `localhost` for Kafka, Redis, and PostgreSQL by default.

### 5. Verify
```bash
docker ps
# Should show: kafka, zookeeper, prometheus, grafana, inventory-postgres, order-postgres, redis
```

Health endpoints (each service):
- `http://localhost:8081/actuator/health` (inventory)
- `http://localhost:8082/actuator/health` (order)
- Prometheus metrics: `/actuator/prometheus` on each service

---

## API Reference

All requests go through the API Gateway on port `8080`.

### Products

#### List Products
```http
GET http://localhost:8080/products
```
**Response `200`:**
```json
[
  {
    "id": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
    "name": "Laptop",
    "price": 999.99,
    "stockQuantity": 50
  }
]
```

#### Get Product by ID
```http
GET http://localhost:8080/products/{id}
```
Returns `200` with the product, or `404` if it does not exist.

#### Create Product
```http
POST http://localhost:8080/products
Content-Type: application/json

{
  "name": "Laptop",
  "price": 999.99,
  "stockQuantity": 50
}
```
**Response `201`:** the saved product, including its generated `id`. This endpoint does not validate its input yet.

### Orders

#### Place an Order
```http
POST http://localhost:8080/orders
Content-Type: application/json
Idempotency-key: <unique-key-per-request>

{
  "customerId": "customer-1",
  "productId": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
  "quantity": 2
}
```

- `Idempotency-key` header is **required**. Sending the same key again returns the original order (with `200`) and does not create a second order. Keys are remembered for 24 hours.
- Validation: `customerId` and `productId` must not be blank, and `quantity` must be at least 1. Invalid requests return `400`.

**Response `201`:**
```json
{
  "orderId": "07638033-da9e-40fe-8066-0e5dbc0ca3e4",
  "customerId": "customer-1",
  "productId": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
  "quantity": 2,
  "status": "PENDING",
  "totalPrice": 0.0
}
```

#### Get Order
```http
GET http://localhost:8080/orders/{id}
```
**Response `200`:**
```json
{
  "id": "07638033-da9e-40fe-8066-0e5dbc0ca3e4",
  "customerId": "customer-1",
  "productId": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
  "quantity": 2,
  "status": "CONFIRMED",
  "totalPrice": 1999.98
}
```
`status` is `PENDING`, `CONFIRMED`, or `FAILED`. Unknown IDs currently return `500` (see Known Limitations).

#### Rate Limiting and Fallbacks
- Requests over the rate limit return `429 Too Many Requests`.
- If a service's circuit is open, the gateway returns `503` with `{"status": "error", "message": "... temporarily unavailable ..."}`.

---

## Kafka Topics

| Topic | Producer | Consumer (group) | Purpose |
|---|---|---|---|
| `order.created` | order-service (via outbox) | inventory-service (`inventory-group`) | New order to check and reserve stock |
| `inventory.updated` | inventory-service | order-service (`order-group`) | Stock result: `CONFIRMED` or `FAILED`, with total price |
| `order.created.DLT` | inventory-service (retry recovery) | inventory-service (`inventory-dlq-group`) | Messages that failed 3 retries; logged for manual investigation |

Messages are keyed by `orderId`.

---

## Database Schema

### inventory-service: `inventory_db` (port 5433)
```
products
├── id              UUID (PK)
├── name            VARCHAR NOT NULL
├── price           DOUBLE PRECISION NOT NULL
└── stock_quantity  INTEGER NOT NULL
```

### order-service: `order_db` (port 5434)
```
orders
├── id           UUID (PK)
├── customer_id  VARCHAR NOT NULL
├── product_id   VARCHAR NOT NULL
├── quantity     INTEGER NOT NULL
├── status       VARCHAR NOT NULL (PENDING / CONFIRMED / FAILED)
└── total_price  DOUBLE PRECISION

outbox_events
├── id           UUID (PK)
├── order_id     UUID NOT NULL
├── event_type   VARCHAR NOT NULL       (e.g. "Order Created")
├── payload      VARCHAR NOT NULL       (JSON of the order.created event)
├── published    BOOLEAN NOT NULL       (false until sent to Kafka)
└── created_at   TIMESTAMP
```

Both services use `spring.jpa.hibernate.ddl-auto: update`, so tables are created automatically on startup.

---

## Testing the Full Flow

```bash
# 1. Create a product
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","price":999.99,"stockQuantity":50}'
# → copy the "id" from the response

# 2. Place an order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-key: test-1" \
  -d '{"customerId":"customer-1","productId":"<product-id>","quantity":2}'
# → returns 201 with status PENDING; copy the "orderId"

# 3. Wait 5-10 seconds (outbox poll + Kafka processing)

# 4. Check the order status
curl http://localhost:8080/orders/<order-id>
# → status CONFIRMED, totalPrice 1999.98

# 5. Check that stock was deducted
curl http://localhost:8080/products/<product-id>
# → stockQuantity 48
```

---

## Key Design Decisions

**Why Kafka over REST between services?**
Services are decoupled. order-service doesn't call inventory-service directly, so each service can fail, restart, or scale independently.

**Why the outbox pattern?**
The order and its event are saved in one database transaction. A background poller then publishes the event, so an order is never saved without its event being queued for publishing.

**Why separate databases?**
Each service owns its data. No shared schema and no direct database coupling.

**Why an API Gateway?**
One entry point for clients. Services can change ports or scale without clients knowing. It also handles rate limiting and circuit breaking.

**Why return PENDING from POST /orders?**
Inventory is checked asynchronously, so the HTTP response returns before the stock result is known. Clients poll `GET /orders/{id}` for the final status.

**Why an idempotency key?**
Clients can retry a failed request safely. The same key returns the same order instead of creating a duplicate.

---

## Known Limitations

These are known gaps in the current code. They are listed so they can be fixed.

- **Overselling is possible.** Stock is read, reduced, and saved without a transaction or lock in `ProductService.processOrder`. Two orders for the last unit can both succeed.
- **Duplicate orders are possible under concurrency.** The idempotency check reads Redis and then writes to it. Two identical requests that arrive together can both pass the check. The Redis write also happens inside the database transaction, so a rollback can leave a cached response for an order that was never saved.
- **Outbox delivery is not fully reliable.** `OutboxPoller` doesn't wait for Kafka to confirm each send before marking the row published, and it doesn't lock rows. A failed send can be lost, and running two order-service instances can publish the same event twice.
- **Inventory cache key:** results are cached under `orderId<uuid>` with no separator, and the cache is only written on success, so failed orders are reprocessed on redelivery.
- **Unknown order ID returns `500`** instead of `404`, because `OrderService.viewOrder` throws a plain `RuntimeException`.
- **Entities are returned directly** from `GET /orders/{id}` and the product endpoints. Use DTOs for stable API contracts.
- **Endpoints are not authenticated.** Anyone can create products or orders, and the `X-Customer-Id` header is trusted without verification.
- **Secrets are hardcoded** as `postgres` / `postgres` in the `application.yml` files and the Kubernetes manifests. The Kubernetes manifests also use the `latest` image tag, have no resource limits, and have no health probes.
- **Root `docker-compose.yml` does not start the application services**, and Kafka's advertised listener is `localhost:9092`, so containers that connect to `kafka:9092` may fail to reach it.
- **No automated tests.** The Jenkins pipeline builds with `-DskipTests`.

---

## Future Improvements

- [ ] Atomic stock updates (conditional `UPDATE` or optimistic locking with `@Version`)
- [ ] Atomic idempotency check (database unique constraint or `SETNX`)
- [ ] Outbox hardening: wait for Kafka acknowledgements and lock rows (`SELECT ... FOR UPDATE SKIP LOCKED`)
- [ ] JWT authentication at the API Gateway
- [ ] Verified per-customer rate limiting (currently keyed on an unverified header)
- [ ] Input validation on `POST /products`, and DTOs in responses
- [ ] Proper `404` for unknown orders
- [ ] Move credentials into environment variables or Kubernetes Secrets
- [ ] Health probes, resource limits, and commit-SHA image tags in Kubernetes
- [ ] Full-stack Docker Compose (all services, Redis, and databases)
- [ ] Unit and integration tests (e.g. Testcontainers for Kafka and PostgreSQL)
- [ ] A dashboard in Grafana built on the Prometheus metrics
