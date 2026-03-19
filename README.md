# Microservice Order System

A event-driven microservices architecture built with Spring Boot 3.x, Apache Kafka, and PostgreSQL. The system demonstrates asynchronous communication between services using Kafka as the message broker, with an API Gateway as the single entry point.

---

## Architecture Overview

```
Client
  │
  ▼
API Gateway (8080)
  │
  ├──── /products/**  ──────► inventory-service (8081)
  │                                   │
  └──── /orders/**    ──────► order-service (8082)
                                      │
                          ┌───────────┴───────────┐
                          │        Kafka           │
                          │  topic: order.created  │
                          │  topic: inventory.updated│
                          └───────────────────────┘
```

## Event Flow

```
POST /orders
    │
    ▼
order-service saves order (PENDING)
    │
    ▼
Kafka: order.created
    │
    ▼
inventory-service consumes → checks stock → deducts stock
    │
    ▼
Kafka: inventory.updated (CONFIRMED or FAILED)
    │
    ▼
order-service consumes → updates order status
    │
    ▼
GET /orders/{id} → CONFIRMED or FAILED
```

---

## Services

### 1. API Gateway `:8080`
- Single entry point for all client requests
- Routes `/products/**` → inventory-service
- Routes `/orders/**` → order-service
- Built with Spring Cloud Gateway

### 2. inventory-service `:8081`
- Manages products and stock
- Exposes REST endpoints for browsing products
- Consumes `order.created` Kafka events
- Deducts stock on confirmed orders
- Publishes `inventory.updated` or `inventory.failed` events

### 3. order-service `:8082`
- Handles order placement
- Saves orders with `PENDING` status
- Publishes `order.created` Kafka events
- Consumes `inventory.updated` events
- Updates order status to `CONFIRMED` or `FAILED`

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 | Language |
| Spring Boot 3.x | Application framework |
| Spring Cloud Gateway | API Gateway / routing |
| Apache Kafka | Async event streaming |
| PostgreSQL | Persistent storage (one DB per service) |
| Docker & Docker Compose | Containerization |
| Lombok | Boilerplate reduction |

---

## Project Structure

```
microservice-order-system/
├── kafka-compose.yml               ← Shared Kafka + Zookeeper
├── api-gateway/
│   ├── src/main/java/com/orderSystem/gateway/
│   │   └── ApiGatewayApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
├── inventory-service/
│   ├── src/main/java/com/orderSystem/inventory/
│   │   ├── controller/
│   │   │   └── ProductController.java
│   │   ├── service/
│   │   │   └── ProductService.java
│   │   ├── repository/
│   │   │   └── ProductRepository.java
│   │   ├── model/
│   │   │   └── Product.java
│   │   ├── dto/
│   │   │   └── ProductResponseDto.java
│   │   ├── kafka/
│   │   │   ├── OrderCreatedEvent.java
│   │   │   ├── InventoryUpdatedEvent.java
│   │   │   ├── OrderEventConsumer.java
│   │   │   └── InventoryEventProducer.java
│   │   └── InventoryServiceApplication.java
│   ├── docker-compose.yml          ← inventory-postgres only
│   └── pom.xml
└── order-service/
    ├── src/main/java/com/orderSystem/order/
    │   ├── controller/
    │   │   └── OrderController.java
    │   ├── service/
    │   │   └── OrderService.java
    │   ├── repository/
    │   │   └── OrderRepository.java
    │   ├── model/
    │   │   └── Order.java
    │   ├── dto/
    │   │   ├── OrderRequestDto.java
    │   │   └── OrderResponseDto.java
    │   ├── kafka/
    │   │   ├── OrderCreatedEvent.java
    │   │   ├── InventoryUpdatedEvent.java
    │   │   ├── OrderEventProducer.java
    │   │   └── InventoryEventConsumer.java
    │   └── OrderServiceApplication.java
    ├── docker-compose.yml          ← order-postgres only
    └── pom.xml
```

---

## Getting Started

### Prerequisites
- Java 17
- Maven
- Docker & Docker Compose

### 1. Start Shared Kafka + Zookeeper
```bash
cd microservice-order-system
docker-compose -f kafka-compose.yml up -d
```

### 2. Start Databases
```bash
# inventory-service DB
cd inventory-service
docker-compose up -d postgres

# order-service DB
cd ../order-service
docker-compose up -d postgres
```

### 3. Run Services
Start each Spring Boot service from IntelliJ or via terminal:

```bash
# inventory-service
cd inventory-service
./mvnw spring-boot:run

# order-service
cd order-service
./mvnw spring-boot:run

# api-gateway
cd api-gateway
./mvnw spring-boot:run
```

### 4. Verify All Containers Running
```bash
docker ps
# Should show: shared-kafka, shared-zookeeper, inventory-postgres, order-postgres
```

---

## API Reference

All requests go through the API Gateway on port `8080`.

### Products

#### Get All Products
```http
GET http://localhost:8080/products
```
**Response:**
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

### Orders

#### Place an Order
```http
POST http://localhost:8080/orders
Content-Type: application/json

{
  "customerId": "customer-1",
  "productId": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
  "quantity": 2
}
```
**Response:**
```json
{
  "orderId": "07638033-da9e-40fe-8066-0e5dbc0ca3e4",
  "customerId": "customer-1",
  "productId": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
  "quantity": 2,
  "status": "PENDING",
  "totalPrice": null
}
```

#### Get Order Status
```http
GET http://localhost:8080/orders/{id}
```
**Response:**
```json
{
  "orderId": "07638033-da9e-40fe-8066-0e5dbc0ca3e4",
  "customerId": "customer-1",
  "productId": "2f147273-7ca4-432e-84e4-41dd4d8b6be8",
  "quantity": 2,
  "status": "CONFIRMED",
  "totalPrice": 1999.98
}
```

---

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `order.created` | order-service | inventory-service | Notify inventory of new order |
| `inventory.updated` | inventory-service | order-service | Notify order of stock result |

---

## Database Schema

### inventory-service — `inventory_db` (port 5433)
```
products
├── id              UUID (PK)
├── name            VARCHAR
├── price           DECIMAL
└── stock_quantity  INTEGER
```

### order-service — `order_db` (port 5434)
```
orders
├── id           UUID (PK)
├── customer_id  VARCHAR
├── product_id   VARCHAR
├── quantity     INTEGER
├── status       VARCHAR (PENDING / CONFIRMED / FAILED)
└── total_price  DECIMAL
```

---

## Testing the Full Flow

```bash
# 1. Place an order
POST http://localhost:8080/orders
{ "customerId": "customer-1", "productId": "<uuid>", "quantity": 2 }
# → returns PENDING

# 2. Wait 2-3 seconds for Kafka to process

# 3. Check order status
GET http://localhost:8080/orders/{orderId}
# → should return CONFIRMED

# 4. Verify stock deducted
GET http://localhost:8080/products/{productId}
# → stockQuantity should be reduced by 2
```

---

## Key Design Decisions

**Why Kafka over REST between services?**
Services are fully decoupled. order-service doesn't need to know inventory-service exists. Each service can scale, fail, and restart independently.

**Why separate databases?**
Each service owns its data. No shared schema, no tight coupling at the DB level. True microservice independence.

**Why API Gateway?**
Single entry point for clients. Services can change ports or scale internally without clients knowing. Foundation for adding auth, rate limiting, and load balancing later.

**Why PENDING response on POST /orders?**
Kafka is async — the HTTP response returns before inventory processes the event. Client polls `GET /orders/{id}` to get the final status. This is the correct async pattern.

---

## Future Improvements

- [ ] JWT Authentication at API Gateway level
- [ ] Rate limiting per customer
- [ ] Idempotency keys to prevent duplicate order processing
- [ ] Outbox pattern to prevent message loss on service crash
- [ ] Dead letter queue for failed Kafka messages
- [ ] Docker Compose for full stack deployment
- [ ] Unit and integration tests
