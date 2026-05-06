# CLAUDE.md — Microservice Order System

## Project Overview
Build a microservice-based order system with two fully independent services and one skeleton service.
No shared pom.xml, no shared docker-compose, no shared code — each service is a completely standalone Spring Boot application.

---

## Folder Structure
```
microservice-order-system/
├── inventory-service/
├── order-service/
└── api-gateway/
```

---

## General Rules
- Java 17
- Spring Boot 3.x
- Each service has its own `pom.xml`, `application.yml`, `docker-compose.yml`
- Nothing is shared between services — no common module, no parent pom
- Do NOT implement Kafka logic — only add Kafka configuration in `application.yml`
- Do NOT create Kafka producer/consumer classes
- Do NOT create Kafka event classes
- Do NOT touch `api-gateway` beyond the skeleton described below

---

## 1. inventory-service

### Purpose
Manages products and stock. Exposes REST endpoints for customers to browse products.

### Database
- PostgreSQL
- DB name: `inventory_db`
- Port: `5433` (to avoid conflict with order-service)

### Entity
```
Product
├── id (UUID, primary key, auto-generated)
├── name (String, not null)
├── price (BigDecimal, not null)
└── stockQuantity (int, not null)
```

### REST Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | /products | Returns list of all products |
| GET | /products/{id} | Returns single product by id |

### Layers
```
inventory-service/src/main/java/com/orderSystem/inventory/
├── controller/
│   └── ProductController.java
├── service/
│   └── ProductService.java
├── repository/
│   └── ProductRepository.java
├── model/
│   └── Product.java
├── dto/
│   └── ProductResponseDto.java
└── InventoryServiceApplication.java
```

### application.yml
```yaml
server:
  port: 8081

spring:
  application:
    name: inventory-service

  datasource:
    url: jdbc:postgresql://localhost:5433/inventory_db
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: inventory-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
```

### docker-compose.yml
Include:
- PostgreSQL container (port 5433)
- Kafka container (port 9092)
- Zookeeper container (required by Kafka)

### pom.xml dependencies
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- postgresql
- spring-kafka
- lombok
- spring-boot-starter-validation

---

## 2. order-service

### Purpose
Handles order placement. Receives order requests, saves them as PENDING, and will later communicate with inventory-service via Kafka.

### Database
- PostgreSQL
- DB name: `order_db`
- Port: `5434` (to avoid conflict with inventory-service)

### Entity
```
Order
├── id (UUID, primary key, auto-generated)
├── customerId (String, not null)
├── productId (String, not null)
├── quantity (int, not null)
├── status (Enum: PENDING, CONFIRMED, FAILED — default PENDING)
└── totalPrice (BigDecimal, nullable — will be calculated later)
```

### REST Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | /orders | Place a new order |

### Request Body
```json
{
  "customerId": "customer-123",
  "productId": "product-456",
  "quantity": 2
}
```

### Response Body
```json
{
  "orderId": "uuid",
  "customerId": "customer-123",
  "productId": "product-456",
  "quantity": 2,
  "status": "PENDING",
  "totalPrice": null
}
```

### Layers
```
order-service/src/main/java/com/orderSystem/order/
├── controller/
│   └── OrderController.java
├── service/
│   └── OrderService.java
├── repository/
│   └── OrderRepository.java
├── model/
│   └── Order.java
├── dto/
│   ├── OrderRequestDto.java
│   └── OrderResponseDto.java
└── OrderServiceApplication.java
```

### application.yml
```yaml
server:
  port: 8082

spring:
  application:
    name: order-service

  datasource:
    url: jdbc:postgresql://localhost:5434/order_db
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: order-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
```

### docker-compose.yml
Include:
- PostgreSQL container (port 5434)
- Kafka container (port 9092)
- Zookeeper container (required by Kafka)

### pom.xml dependencies
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- postgresql
- spring-kafka
- lombok
- spring-boot-starter-validation

---

## 3. api-gateway

### Purpose
Skeleton only. Do NOT implement anything beyond a basic runnable Spring Boot app.

### What to generate
- `pom.xml` with Spring Cloud Gateway dependency only
- `ApiGatewayApplication.java` — main class only
- `application.yml` — with port 8080 and placeholder routes commented out

### application.yml
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

# Routes will be configured later
# spring:
#   cloud:
#     gateway:
#       routes:
#         - id: inventory-service
#           uri: http://localhost:8081
#           predicates:
#             - Path=/products/**
#         - id: order-service
#           uri: http://localhost:8082
#           predicates:
#             - Path=/orders/**
```

### Do NOT add
- No filters
- No JWT logic
- No Redis
- No rate limiting
- No routing implementation

---

## What NOT to do (Global)
- Do NOT create any Kafka producer or consumer classes in any service
- Do NOT create any Kafka event/DTO classes (OrderCreatedEvent, InventoryUpdatedEvent, etc.)
- Do NOT implement any Kafka logic anywhere
- Do NOT create a shared/common module
- Do NOT create a root pom.xml
- Do NOT create a root docker-compose.yml
- Do NOT add any code to api-gateway beyond the skeleton above
- Do NOT add Spring Security or JWT to any service
