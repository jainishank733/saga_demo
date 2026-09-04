# Saga Pattern Demo (Orchestration) — Order / Payment / Inventory

A minimal, fully runnable example of the **Saga design pattern** using
**orchestration**, built with **Java 17 + Spring Boot 3.3.4 + Maven**.

`order-service` acts as the **orchestrator**. It calls `inventory-service` and
`payment-service` over plain REST, in order, and runs a **compensating
transaction** if a later step fails — demonstrating both the happy path and
rollback.

No message broker, no Docker, no external database — everything runs with
`mvn spring-boot:run` and an in-memory H2 database per service.

---

## 1. Project layout

```
saga-demo/
├── order-service/        (port 8081) — the saga ORCHESTRATOR
│   ├── pom.xml
│   └── src/main/java/com/example/orderservice/
│       ├── OrderServiceApplication.java
│       ├── config/RestTemplateConfig.java
│       ├── controller/OrderController.java
│       ├── client/InventoryClient.java       (calls inventory-service)
│       ├── client/PaymentClient.java         (calls payment-service)
│       ├── saga/OrderSagaOrchestrator.java   (THE SAGA LOGIC)
│       ├── entity/Order.java, OrderStatus.java
│       ├── repository/OrderRepository.java
│       └── dto/ (OrderRequest, OrderResponse, Inventory*, Payment*)
│   └── src/main/resources/application.yml
│
├── payment-service/       (port 8082) — saga PARTICIPANT
│   ├── pom.xml
│   └── src/main/java/com/example/paymentservice/
│       ├── PaymentServiceApplication.java
│       ├── controller/PaymentController.java  (charge + refund/compensate)
│       ├── entity/Payment.java, PaymentStatus.java
│       ├── repository/PaymentRepository.java
│       ├── dto/PaymentRequest.java, PaymentResponse.java
│       └── exception/ (PaymentDeclinedException, PaymentNotFoundException,
│                        GlobalExceptionHandler)
│   └── src/main/resources/application.yml
│
├── inventory-service/     (port 8083) — saga PARTICIPANT
│   ├── pom.xml
│   └── src/main/java/com/example/inventoryservice/
│       ├── InventoryServiceApplication.java
│       ├── controller/InventoryController.java (reserve + release/compensate)
│       ├── entity/Product.java
│       ├── repository/ProductRepository.java
│       ├── dto/InventoryReserveRequest.java, InventoryReserveResponse.java
│       └── exception/ (InsufficientStockException, ProductNotFoundException,
│                        GlobalExceptionHandler)
│   └── src/main/resources/
│       ├── application.yml
│       └── data.sql   (seeds 2 products)
│
└── README.md   (this file)
```

Each service is an **independent Maven project** — there is no parent POM on
purpose, to keep every folder copy-pasteable and self-contained.

---

## 2. Prerequisites

- JDK 17 or newer (`java -version`)
- Maven 3.8+ (`mvn -version`) — or just use the projects in an IDE
  (IntelliJ IDEA / Eclipse / VS Code) that has Maven built in
- No database installation needed — each service starts its own **in-memory
  H2 database** automatically. Data resets every time a service restarts.

---

## 3. How the saga works

**Trigger:** `POST /api/orders` on `order-service`.

```
Client → order-service (orchestrator)
             │
             ├─ Step 1: POST inventory-service /api/inventory/reserve
             │      ok → continue
             │      fails (insufficient stock) → order = FAILED, stop (nothing to undo)
             │
             ├─ Step 2: POST payment-service /api/payments   (charge)
             │      ok → order = CONFIRMED  ✅  (saga complete)
             │      fails (declined) → COMPENSATE:
             │            POST inventory-service /api/inventory/release
             │            order = ROLLED_BACK  ↩️
             └─ done
```

This is **orchestration** (not choreography) because `order-service` is the
single place that knows the whole sequence of steps and which compensating
action undoes which forward action. `payment-service` and `inventory-service`
know nothing about the saga — they only expose local operations (reserve/
release, charge/refund).

**Simulated failure rules** (so you can trigger both outcomes without a real
payment gateway or a real warehouse):
- `inventory-service` seeds `ITEM123` with **10** units in stock. Requesting
  more than 10 → `409 Conflict` (insufficient stock).
- `payment-service` declines any charge with `amount > 1000` → `402 Payment
  Required` (insufficient funds).

---

## 4. Run it (3 terminals)

Each service must be started from its own folder.

```bash
# Terminal 1
cd inventory-service
mvn spring-boot:run

# Terminal 2
cd payment-service
mvn spring-boot:run

# Terminal 3
cd order-service
mvn spring-boot:run
```

Start order last, since it doesn't matter for cold start (REST calls only
happen when you actually place an order), but it's good practice to have the
participants up first.

Wait for each to print `Started ...Application in ... seconds` before testing.

Ports:
| Service            | Port | H2 console                          |
|---------------------|------|--------------------------------------|
| inventory-service    | 8083 | http://localhost:8083/h2-console     |
| payment-service       | 8082 | http://localhost:8082/h2-console     |
| order-service          | 8081 | http://localhost:8081/h2-console     |

H2 console JDBC URLs (username `sa`, empty password):
`jdbc:h2:mem:inventorydb`, `jdbc:h2:mem:paymentdb`, `jdbc:h2:mem:orderdb`.

---

## 5. Test — Scenario A: Successful saga (happy path)

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productCode":"ITEM123","quantity":2,"amount":250.00}' | python3 -m json.tool
```

Expected response:
```json
{
  "orderId": 1,
  "status": "CONFIRMED",
  "message": "Order confirmed successfully."
}
```

Verify inventory decreased and payment was recorded:
```bash
curl -s http://localhost:8083/api/inventory/ITEM123 | python3 -m json.tool
# stockQuantity should now be 8 (10 - 2)
```

---

## 6. Test — Scenario B: Compensation / rollback (payment declines)

Use an amount over the 1000 decline threshold. Inventory is reserved first,
then payment is declined, so the orchestrator **compensates** by releasing
the reserved stock:

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productCode":"ITEM123","quantity":1,"amount":1500.00}' | python3 -m json.tool
```

Expected response:
```json
{
  "orderId": 2,
  "status": "ROLLED_BACK",
  "message": "Order rolled back: payment declined - ... Inventory reservation was compensated (released)."
}
```

Verify stock was returned to its previous level:
```bash
curl -s http://localhost:8083/api/inventory/ITEM123 | python3 -m json.tool
# stockQuantity should be back to 8 (unchanged from before this failed order)
```

---

## 7. Test — Scenario C: Fails at step 1 (nothing to compensate)

Request more stock than exists (only 10 units of `ITEM123` are seeded):

```bash
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productCode":"ITEM123","quantity":99,"amount":50.00}' | python3 -m json.tool
```

Expected response:
```json
{
  "orderId": 3,
  "status": "FAILED",
  "message": "Order failed: inventory reservation rejected - ..."
}
```

No compensation is needed here because step 1 (inventory) never succeeded —
payment was never even attempted.

---

## 8. Inspect saved orders

```bash
curl -s http://localhost:8081/api/orders | python3 -m json.tool
curl -s http://localhost:8081/api/orders/1 | python3 -m json.tool
```

You'll see the final `status` for every order you created: `CONFIRMED`,
`ROLLED_BACK`, or `FAILED`.

---

## 9. Key files to read first

1. `order-service/.../saga/OrderSagaOrchestrator.java` — the entire saga:
   forward steps, compensation logic, and status transitions, in one class.
2. `order-service/.../client/InventoryClient.java` and `PaymentClient.java`
   — how the orchestrator talks to participants over REST.
3. `payment-service/.../controller/PaymentController.java` — `POST` charges,
   `DELETE /{orderId}` is the compensating "refund" action.
4. `inventory-service/.../controller/InventoryController.java` — `POST
   /reserve` and `POST /release` (the compensating action).

## 10. Notes on design choices (kept intentionally simple for learning)

- **Synchronous REST + RestTemplate** instead of a message broker
  (Kafka/RabbitMQ) — a real production saga usually uses asynchronous
  messaging for resilience, but that adds significant infrastructure. This
  demo favors clarity: the whole saga executes inside one HTTP request/
  response cycle to `order-service`.
- **No saga state machine library** — `OrderStatus` (`PENDING` →
  `CONFIRMED`/`FAILED`/`ROLLED_BACK`) is tracked directly on the `Order`
  entity, which is enough to see the pattern clearly.
- **H2 in-memory** per service instead of Postgres/MySQL — zero setup, but
  each service owns its own database, which is the core saga/microservices
  requirement (no shared database, no distributed transaction).
- If step-1's compensation itself fails (rare, e.g. inventory-service is
  down), the orchestrator does **not** silently continue — it marks the
  order `FAILED` and surfaces that manual reconciliation is needed, since
  automatically retrying compensations indefinitely is a separate, more
  advanced concern (typically handled with a retry queue in production).
#   s a g a _ d e m o  
 