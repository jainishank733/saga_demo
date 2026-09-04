# Saga Pattern Demo — Order / Payment / Inventory

A minimal, fully runnable example of the Saga Design Pattern using
orchestration, built with:

- Java 17
- Spring Boot 3.3.4
- Maven
- Spring Data JPA
- H2 in-memory database
- REST APIs
- RestTemplate

This project demonstrates a distributed business transaction across three
independent microservices:

1. order-service       → Saga ORCHESTRATOR
2. payment-service     → Saga PARTICIPANT
3. inventory-service   → Saga PARTICIPANT

There is:

- No Kafka
- No RabbitMQ
- No Docker
- No external database
- No distributed transaction

Everything runs locally with:

    mvn spring-boot:run

Each microservice owns its own in-memory H2 database.


===============================================================================
1. PROJECT STRUCTURE
   ===============================================================================

saga-demo/
│
├── README.md
│
├── order-service/                         # Saga Orchestrator
│   ├── pom.xml
│   │
│   └── src/main/
│       ├── java/com/example/orderservice/
│       │   ├── OrderServiceApplication.java
│       │   │
│       │   ├── config/
│       │   │   └── RestTemplateConfig.java
│       │   │
│       │   ├── controller/
│       │   │   └── OrderController.java
│       │   │
│       │   ├── client/
│       │   │   ├── InventoryClient.java
│       │   │   └── PaymentClient.java
│       │   │
│       │   ├── saga/
│       │   │   └── OrderSagaOrchestrator.java
│       │   │
│       │   ├── entity/
│       │   │   ├── Order.java
│       │   │   └── OrderStatus.java
│       │   │
│       │   ├── repository/
│       │   │   └── OrderRepository.java
│       │   │
│       │   └── dto/
│       │       ├── OrderRequest.java
│       │       ├── OrderResponse.java
│       │       ├── InventoryReserveRequest.java
│       │       ├── InventoryReserveResponse.java
│       │       ├── PaymentRequest.java
│       │       └── PaymentResponse.java
│       │
│       └── resources/
│           └── application.yml
│
├── payment-service/                       # Saga Participant
│   ├── pom.xml
│   │
│   └── src/main/
│       ├── java/com/example/paymentservice/
│       │   ├── PaymentServiceApplication.java
│       │   │
│       │   ├── controller/
│       │   │   └── PaymentController.java
│       │   │
│       │   ├── entity/
│       │   │   ├── Payment.java
│       │   │   └── PaymentStatus.java
│       │   │
│       │   ├── repository/
│       │   │   └── PaymentRepository.java
│       │   │
│       │   ├── dto/
│       │   │   ├── PaymentRequest.java
│       │   │   └── PaymentResponse.java
│       │   │
│       │   └── exception/
│       │       ├── PaymentDeclinedException.java
│       │       ├── PaymentNotFoundException.java
│       │       └── GlobalExceptionHandler.java
│       │
│       └── resources/
│           └── application.yml
│
└── inventory-service/                     # Saga Participant
├── pom.xml
│
└── src/main/
├── java/com/example/inventoryservice/
│   ├── InventoryServiceApplication.java
│   │
│   ├── controller/
│   │   └── InventoryController.java
│   │
│   ├── entity/
│   │   └── Product.java
│   │
│   ├── repository/
│   │   └── ProductRepository.java
│   │
│   ├── dto/
│   │   ├── InventoryReserveRequest.java
│   │   └── InventoryReserveResponse.java
│   │
│   └── exception/
│       ├── InsufficientStockException.java
│       ├── ProductNotFoundException.java
│       └── GlobalExceptionHandler.java
│
└── resources/
├── application.yml
└── data.sql


===============================================================================
2. WHAT IS THE SAGA PATTERN?
   ===============================================================================

The Saga Pattern is used to manage a business transaction that spans
multiple independent microservices.

Instead of using one distributed database transaction, the operation is
split into multiple local transactions.

Example:

    Create Order
         |
         v
    Reserve Inventory
         |
         v
    Charge Payment
         |
         v
    Confirm Order

If a later step fails, a compensating transaction is executed.

Example:

    Reserve Inventory
         |
         | SUCCESS
         v
    Charge Payment
         |
         | FAILED
         v
    Release Inventory
         |
         v
    Order ROLLED_BACK


===============================================================================
3. ORCHESTRATION
   ===============================================================================

This project uses Saga ORCHESTRATION.

The order-service is responsible for coordinating the complete workflow.

Architecture:

                         CLIENT
                           |
                           v
                 +---------------------+
                 |    ORDER SERVICE    |
                 |     ORCHESTRATOR    |
                 |        :8081        |
                 +----------+----------+
                            |
                 +----------+----------+
                 |                     |
                 v                     v
        +-------------------+   +-------------------+
        | INVENTORY SERVICE |   |  PAYMENT SERVICE  |
        |    PARTICIPANT    |   |    PARTICIPANT    |
        |       :8083       |   |       :8082       |
        +-------------------+   +-------------------+

The order-service knows:

- What step runs first.
- What step runs next.
- Which service to call.
- What to do when a step fails.
- Which compensation should be executed.
- What the final order status should be.

The participant services do not know about the complete Saga.

They only expose their own business operations.


===============================================================================
4. COMPLETE SAGA FLOW
   ===============================================================================

Client
|
| POST /api/orders
v
Order Service
|
| STEP 1
v
Inventory Service
|
| Reserve Inventory
|
+------ FAILURE ------> Order FAILED
|
| SUCCESS
v
Payment Service
|
| Charge Payment
|
+------ FAILURE ------> Release Inventory
|                              |
|                              v
|                       Order ROLLED_BACK
|
| SUCCESS
v
Order CONFIRMED


The three possible outcomes are:

    1. Inventory succeeds
       Payment succeeds
       -----------------
       Order = CONFIRMED


    2. Inventory succeeds
       Payment fails
       -----------------
       Release Inventory
       Order = ROLLED_BACK


    3. Inventory fails
       -----------------
       Payment is never called
       Order = FAILED


===============================================================================
5. SERVICE RESPONSIBILITIES
   ===============================================================================

ORDER SERVICE
-------------

Port:

    8081

Role:

    Saga Orchestrator

Responsibilities:

- Accept order requests.
- Create orders.
- Save orders as PENDING.
- Call inventory-service.
- Call payment-service.
- Execute compensation when required.
- Update final order status.


PAYMENT SERVICE
---------------

Port:

    8082

Role:

    Saga Participant

Responsibilities:

- Process payments.
- Save successful payments.
- Decline payments according to the demo rule.
- Provide payment compensation/refund operation.


INVENTORY SERVICE
-----------------

Port:

    8083

Role:

    Saga Participant

Responsibilities:

- Maintain product stock.
- Reserve stock.
- Release reserved stock.
- Reject reservations when stock is insufficient.


===============================================================================
6. DATABASE DESIGN
   ===============================================================================

Each microservice has its own database.

Order Service:

    jdbc:h2:mem:orderdb

Payment Service:

    jdbc:h2:mem:paymentdb

Inventory Service:

    jdbc:h2:mem:inventorydb

H2 is an in-memory database.

The data is lost when the corresponding service is restarted.

There is NO shared database.

Architecture:

    order-service
         |
         +---- orderdb


    payment-service
         |
         +---- paymentdb


    inventory-service
         |
         +---- inventorydb


This is important because each microservice owns its own data.


===============================================================================
7. PREREQUISITES
   ===============================================================================

Install:

1. Java 17 or newer
2. Maven 3.8 or newer

Check Java:

    java -version

Check Maven:

    mvn -version

You can also run the projects using:

- IntelliJ IDEA
- Eclipse
- VS Code

No external database is required.


===============================================================================
8. SERVICE PORTS
   ===============================================================================

+--------------------+-------+----------------------+
| Service            | Port  | Responsibility       |
+--------------------+-------+----------------------+
| order-service      | 8081  | Saga Orchestrator    |
| payment-service    | 8082  | Payment Participant  |
| inventory-service  | 8083  | Inventory Participant|
+--------------------+-------+----------------------+


===============================================================================
9. H2 CONSOLES
   ===============================================================================

Order Service:

    http://localhost:8081/h2-console

Payment Service:

    http://localhost:8082/h2-console

Inventory Service:

    http://localhost:8083/h2-console


H2 credentials:

    Username: sa
    Password: <empty>


JDBC URLs:

    jdbc:h2:mem:orderdb
    jdbc:h2:mem:paymentdb
    jdbc:h2:mem:inventorydb


===============================================================================
10. SIMULATED BUSINESS RULES
    ===============================================================================

This demo intentionally uses simple rules so that Saga success and failure
can easily be tested.


INVENTORY RULE
--------------

inventory-service seeds:

    Product Code: ITEM123
    Stock:        10


If the requested quantity is greater than available stock:

    HTTP 409 Conflict

Example:

    Available = 10
    Requested = 99

Result:

    Inventory reservation fails.


PAYMENT RULE
------------

payment-service declines payments when:

    amount > 1000


The service returns:

    HTTP 402 Payment Required

Example:

    amount = 250

Result:

    Payment succeeds.


Example:

    amount = 1500

Result:

    Payment is declined.


===============================================================================
11. ORDER STATUS
    ===============================================================================

An order initially starts as:

    PENDING


Possible final states:

    CONFIRMED
    FAILED
    ROLLED_BACK


Successful flow:

    PENDING
       |
       | Inventory SUCCESS
       |
       | Payment SUCCESS
       v
    CONFIRMED


Inventory failure:

    PENDING
       |
       | Inventory FAILURE
       v
    FAILED


Payment failure:

    PENDING
       |
       | Inventory SUCCESS
       |
       | Payment FAILURE
       v
    Release Inventory
       |
       v
    ROLLED_BACK


===============================================================================
12. STARTING THE APPLICATION
    ===============================================================================

Start all three services in separate terminals.


TERMINAL 1 - INVENTORY SERVICE
------------------------------

    cd inventory-service
    mvn spring-boot:run


TERMINAL 2 - PAYMENT SERVICE
----------------------------

    cd payment-service
    mvn spring-boot:run


TERMINAL 3 - ORDER SERVICE
--------------------------

    cd order-service
    mvn spring-boot:run


Wait until all three services show that Spring Boot has started.


Running services:

    Inventory Service -> http://localhost:8083
    Payment Service   -> http://localhost:8082
    Order Service     -> http://localhost:8081


It is recommended to start participants first:

    1. inventory-service
    2. payment-service
    3. order-service


The order-service does not call the other services during startup.
REST calls happen only when an order is created.


===============================================================================
13. SCENARIO A - SUCCESSFUL SAGA
    ===============================================================================

This demonstrates the happy path.

Initial inventory:

    ITEM123 = 10


Create an order:

    curl -s -X POST http://localhost:8081/api/orders \
      -H "Content-Type: application/json" \
      -d '{"productCode":"ITEM123","quantity":2,"amount":250.00}' \
      | python3 -m json.tool


Expected response:

    {
      "orderId": 1,
      "status": "CONFIRMED",
      "message": "Order confirmed successfully."
    }


The Saga executes:

    STEP 1
    Create Order
         |
         v
    PENDING
         |
         v
    Reserve 2 Inventory
         |
         | SUCCESS
         v
    Charge Payment 250
         |
         | SUCCESS
         v
    CONFIRMED


Inventory changes:

    Before:

        ITEM123 = 10


    After:

        ITEM123 = 8


Because:

    10 - 2 = 8


===============================================================================
14. VERIFY INVENTORY AFTER SUCCESS
    ===============================================================================

Run:

    curl -s http://localhost:8083/api/inventory/ITEM123 \
      | python3 -m json.tool


Expected stock:

    8


The successful Saga permanently reserved 2 units.


===============================================================================
15. SCENARIO B - PAYMENT FAILURE AND COMPENSATION
    ===============================================================================

Now we intentionally make payment fail.

Payment fails when:

    amount > 1000


Create an order with:

    Product:  ITEM123
    Quantity: 1
    Amount:   1500


Run:

    curl -s -X POST http://localhost:8081/api/orders \
      -H "Content-Type: application/json" \
      -d '{"productCode":"ITEM123","quantity":1,"amount":1500.00}' \
      | python3 -m json.tool


The Saga executes:

    STEP 1
    Reserve Inventory
         |
         | SUCCESS
         v
    Stock:
        8 -> 7
         |
         v
    STEP 2
    Charge Payment
         |
         | FAILURE
         v
    COMPENSATION
         |
         v
    Release Inventory
         |
         v
    Stock:
        7 -> 8
         |
         v
    Order = ROLLED_BACK


Expected response:

    {
      "orderId": 2,
      "status": "ROLLED_BACK",
      "message": "Order rolled back: payment declined - ... Inventory reservation was compensated (released)."
    }


This is the most important example in the project.

The inventory operation succeeded, but the payment operation failed.

Therefore, the orchestrator compensates the successful inventory operation.


===============================================================================
16. VERIFY COMPENSATION
    ===============================================================================

Check inventory:

    curl -s http://localhost:8083/api/inventory/ITEM123 \
      | python3 -m json.tool


Expected:

    stockQuantity = 8


Before failed order:

    8


After inventory reservation:

    7


After compensation:

    8


Therefore:

    Reservation:
        8 -> 7

    Compensation:
        7 -> 8


The inventory reservation was successfully undone.


===============================================================================
17. SCENARIO C - INVENTORY FAILURE
    ===============================================================================

Now demonstrate failure during the first Saga step.

At this point:

    ITEM123 = 8


Request:

    quantity = 99


Run:

    curl -s -X POST http://localhost:8081/api/orders \
      -H "Content-Type: application/json" \
      -d '{"productCode":"ITEM123","quantity":99,"amount":50.00}' \
      | python3 -m json.tool


Inventory does not have 99 units.

Therefore:

    Inventory Reservation
            |
            | FAILURE
            v
        Order FAILED


Expected response:

    {
      "orderId": 3,
      "status": "FAILED",
      "message": "Order failed: inventory reservation rejected - ..."
    }


Payment is NEVER called.

Why?

Because inventory is the first Saga step and it failed.

There is nothing to compensate.


===============================================================================
18. THREE SCENARIOS SUMMARY
    ===============================================================================

+--------------------+-------------------+------------------+------------------+
| Scenario           | Inventory         | Payment          | Final Status     |
+--------------------+-------------------+------------------+------------------+
| Happy Path         | SUCCESS           | SUCCESS          | CONFIRMED        |
| Payment Failure    | SUCCESS           | FAILURE          | ROLLED_BACK      |
| Inventory Failure  | FAILURE           | NOT CALLED       | FAILED           |
+--------------------+-------------------+------------------+------------------+


Detailed view:


SCENARIO A

    Inventory SUCCESS
         |
         v
    Payment SUCCESS
         |
         v
    CONFIRMED


SCENARIO B

    Inventory SUCCESS
         |
         v
    Payment FAILURE
         |
         v
    Release Inventory
         |
         v
    ROLLED_BACK


SCENARIO C

    Inventory FAILURE
         |
         v
    FAILED


===============================================================================
19. INSPECT ORDERS
    ===============================================================================

Get all orders:

    curl -s http://localhost:8081/api/orders \
      | python3 -m json.tool


Get a specific order:

    curl -s http://localhost:8081/api/orders/1 \
      | python3 -m json.tool


After running all three scenarios, the result should conceptually be:

    Order 1 -> CONFIRMED
    Order 2 -> ROLLED_BACK
    Order 3 -> FAILED


===============================================================================
20. IMPORTANT FILES
    ===============================================================================

If you want to understand the project quickly, read the following files
in this order.


1. OrderSagaOrchestrator.java

   order-service/
   └── src/main/java/com/example/orderservice/
   └── saga/
   └── OrderSagaOrchestrator.java


This is the most important file.

It contains the complete Saga logic:

- Forward steps
- REST calls
- Success handling
- Failure handling
- Compensation
- Status transitions


2. InventoryClient.java

   order-service/
   └── src/main/java/com/example/orderservice/
   └── client/
   └── InventoryClient.java


This shows how order-service communicates with inventory-service.


3. PaymentClient.java

   order-service/
   └── src/main/java/com/example/orderservice/
   └── client/
   └── PaymentClient.java


This shows how order-service communicates with payment-service.


4. InventoryController.java

   inventory-service/
   └── src/main/java/com/example/inventoryservice/
   └── controller/
   └── InventoryController.java


Important operations:

    POST /api/inventory/reserve
    POST /api/inventory/release


The release operation is the compensation for inventory reservation.


5. PaymentController.java

   payment-service/
   └── src/main/java/com/example/paymentservice/
   └── controller/
   └── PaymentController.java


Important operations:

    POST   /api/payments
    DELETE /api/payments/{orderId}


The DELETE operation represents payment compensation/refund.


===============================================================================
21. WHY RESTTEMPLATE?
    ===============================================================================

This demo uses:

    RestTemplate


The purpose is simplicity.

The complete Saga executes synchronously inside the HTTP request:

    Client
      |
      v
    Order Service
      |
      +----> Inventory Service
      |
      +----> Payment Service
      |
      v
    Response


This makes the project easy to understand and run.

A production Saga may use asynchronous messaging such as Kafka or RabbitMQ,
especially when higher resilience and loose coupling are required.

However, adding a message broker would make this learning example much more
complex.

Therefore, this demo intentionally uses simple synchronous REST.


===============================================================================
22. WHY NO SHARED DATABASE?
    ===============================================================================

Each microservice owns its own data.

Correct microservice architecture:

    Order Service
         |
         +---- orderdb


    Payment Service
         |
         +---- paymentdb


    Inventory Service
         |
         +---- inventorydb


There is no:

    Shared Database
          |
          +---- Order
          +---- Payment
          +---- Inventory


A shared database would make the services tightly coupled.

The purpose of this Saga demo is to show how multiple services can maintain
business consistency while owning separate databases.


===============================================================================
23. SAGA VS NORMAL DATABASE TRANSACTION
    ===============================================================================

Traditional transaction:

    BEGIN TRANSACTION

        Update Order
        Update Inventory
        Insert Payment

    COMMIT


If something fails:

    ROLLBACK


This works well when everything belongs to one transactional database.


With microservices:

    Order DB
        |
        X
    Inventory DB
        |
        X
    Payment DB


A single normal database transaction cannot easily span all these
independent databases.


Saga solves this by using local transactions:

    Order Local Transaction
            |
            v
    Inventory Local Transaction
            |
            v
    Payment Local Transaction


If payment fails:

    Payment FAILED
          |
          v
    Inventory Compensation
          |
          v
    Order ROLLED_BACK


There is no global database rollback.


===============================================================================
24. COMPENSATING TRANSACTIONS
    ===============================================================================

A compensation is a business operation that logically undoes a previous
successful business operation.


Example:

    Forward Operation:
        Reserve Inventory


    Compensation:
        Release Inventory


Another example:

    Forward Operation:
        Charge Payment


    Compensation:
        Refund Payment


Other real-world examples:

    Create Shipment
        ->
    Cancel Shipment


    Apply Discount
        ->
    Reverse Discount


    Book Hotel
        ->
    Cancel Hotel Booking


The compensation is NOT a database rollback.

It is another business operation.


===============================================================================
25. WHAT IF COMPENSATION FAILS?
    ===============================================================================

Consider:

    Inventory Reservation
        |
        | SUCCESS
        v
    Payment
        |
        | FAILURE
        v
    Release Inventory
        |
        | FAILURE
        v
    Problem


Now the inventory reservation may still exist even though the order failed.


This demo intentionally keeps this situation simple.

It does not retry compensation forever.

Instead, it marks the order as FAILED and surfaces that manual reconciliation
may be required.


In a production system, compensation normally needs:

- Retry policies
- Retry queues
- Dead-letter queues
- Idempotency
- Persistent Saga state
- Monitoring
- Alerting
- Manual reconciliation
- Timeouts
- Circuit breakers


These features are intentionally outside the scope of this minimal demo.


===============================================================================
26. IDEMPOTENCY
    ===============================================================================

Idempotency is extremely important in a real Saga.


Example:

    Order Service
         |
         | Release Inventory
         v
    Inventory Service
         |
         | SUCCESS
         |
         X Network timeout


The orchestrator may not know whether the operation succeeded.

It may retry:

    Release Inventory


If the inventory service blindly releases stock twice:

    Stock = 10
       |
       | Release
       v
    Stock = 11
       |
       | Retry
       v
    Stock = 12


That would be incorrect.


Therefore, production Saga operations should generally be idempotent.

The same logical operation should be safe to execute more than once.


===============================================================================
27. RETRIES
    ===============================================================================

Network failures can happen even when the target service successfully
processed a request.

Example:

    Order Service
         |
         | Reserve Inventory
         v
    Inventory Service
         |
         | SUCCESS
         |
         X Network timeout


Order Service may think:

    Reservation FAILED


while Inventory Service actually performed:

    Reservation SUCCESS


This is why real Saga implementations need:

- Idempotency
- Retry policies
- Unique operation IDs
- Persistent Saga state
- Proper error handling


===============================================================================
28. TIMEOUTS
    ===============================================================================

A participant service may become unavailable.

Example:

    Order Service
         |
         | Charge Payment
         v
    Payment Service
         |
         |..................
         |
         | no response


The orchestrator should not wait forever.

Production implementations should configure:

- Connection timeout
- Read timeout
- Retry timeout
- Overall Saga timeout


===============================================================================
29. PERSISTENT SAGA STATE
    ===============================================================================

This demo keeps the Saga state simple using the Order status.

A production Saga may maintain a dedicated Saga record containing:

    Saga ID
    Order ID
    Current Step
    Completed Steps
    Compensation Steps
    Retry Count
    Last Error
    Created Time
    Updated Time


For example:

    Saga ID:        SAGA-1001
    Order ID:       101
    Current Step:   PAYMENT
    Inventory:      SUCCESS
    Payment:        FAILED
    Compensation:   INVENTORY_RELEASE
    Retry Count:    2


This makes recovery much easier.


===============================================================================
30. OBSERVABILITY
    ===============================================================================

Production Saga implementations should provide:

- Structured logging
- Correlation IDs
- Distributed tracing
- Metrics
- Monitoring
- Alerts
- Saga execution history


For example:

    correlationId = ORDER-123


The same ID can be included in:

    Order Service logs
    Inventory Service logs
    Payment Service logs


This makes troubleshooting distributed transactions much easier.


===============================================================================
31. IMPORTANT DESIGN PRINCIPLE
    ===============================================================================

The most important concept in this project is:

    The Saga does NOT provide a distributed rollback.

Instead:

    Saga = Sequence of local transactions
           +
           Compensating transactions


Example:

    Reserve Inventory
           |
           | SUCCESS
           v
    Charge Payment
           |
           | FAILURE
           v
    Release Inventory
           |
           v
    Order ROLLED_BACK


The inventory database is not rolled back by the order database.

Instead, the inventory service receives another business request:

    RELEASE


===============================================================================
32. COMPLETE ARCHITECTURE
    ===============================================================================

                         CLIENT
                           |
                           |
                    POST /api/orders
                           |
                           v
                +----------------------+
                |    ORDER SERVICE     |
                |                      |
                |    ORCHESTRATOR      |
                |                      |
                |       :8081          |
                +----------+-----------+
                           |
                           |
                    Step 1: Reserve
                           |
                           v
                +----------------------+
                |  INVENTORY SERVICE   |
                |                      |
                |    PARTICIPANT       |
                |                      |
                |       :8083          |
                +----------+-----------+
                           |
                           |
                     SUCCESS / FAIL
                           |
                           | SUCCESS
                           v
                +----------------------+
                |   PAYMENT SERVICE    |
                |                      |
                |    PARTICIPANT       |
                |                      |
                |       :8082          |
                +----------+-----------+
                           |
                           |
                     SUCCESS / FAIL
                           |
             +-------------+-------------+
             |                           |
             | SUCCESS                   | FAILURE
             v                           v
    ORDER CONFIRMED             RELEASE INVENTORY
    |
    v
    ORDER ROLLED_BACK


If inventory fails:

    Order
      |
      v
    Inventory
      |
      | FAILURE
      v
    Order FAILED


===============================================================================
33. COMPLETE TESTING FLOW
    ===============================================================================

Start with:

    ITEM123 = 10


Run Scenario A:

    Quantity = 2
    Amount   = 250

Result:

    Inventory = 8
    Order     = CONFIRMED


Run Scenario B:

    Quantity = 1
    Amount   = 1500

Result:

    Inventory temporarily becomes 7
    Payment fails
    Inventory is released
    Inventory returns to 8
    Order = ROLLED_BACK


Run Scenario C:

    Quantity = 99
    Amount   = 50

Result:

    Inventory reservation fails
    Payment is never called
    Inventory remains 8
    Order = FAILED


Final state:

    Order 1 -> CONFIRMED
    Order 2 -> ROLLED_BACK
    Order 3 -> FAILED

    Inventory ITEM123 -> 8


===============================================================================
34. QUICK START
    ===============================================================================

Step 1:

    cd inventory-service
    mvn spring-boot:run


Step 2:

    cd payment-service
    mvn spring-boot:run


Step 3:

    cd order-service
    mvn spring-boot:run


Step 4 - Test successful Saga:

    curl -X POST http://localhost:8081/api/orders \
      -H "Content-Type: application/json" \
      -d '{"productCode":"ITEM123","quantity":2,"amount":250.00}'


Expected:

    CONFIRMED


Step 5 - Test compensation:

    curl -X POST http://localhost:8081/api/orders \
      -H "Content-Type: application/json" \
      -d '{"productCode":"ITEM123","quantity":1,"amount":1500.00}'


Expected:

    ROLLED_BACK


Step 6 - Test inventory failure:

    curl -X POST http://localhost:8081/api/orders \
      -H "Content-Type: application/json" \
      -d '{"productCode":"ITEM123","quantity":99,"amount":50.00}'


Expected:

    FAILED


Step 7 - Check orders:

    curl -s http://localhost:8081/api/orders \
      | python3 -m json.tool


Step 8 - Check inventory:

    curl -s http://localhost:8083/api/inventory/ITEM123 \
      | python3 -m json.tool


===============================================================================
35. FINAL TAKEAWAY
    ===============================================================================

The entire Saga can be summarized as:

    1. Create Order
    2. Save Order as PENDING
    3. Reserve Inventory
    4. If inventory fails:
           Order = FAILED
           Stop
    5. Charge Payment
    6. If payment fails:
           Release Inventory
           Order = ROLLED_BACK
    7. If payment succeeds:
           Order = CONFIRMED


The key flow is:

    Reserve Inventory
           |
           v
    Charge Payment
           |
           v
    Confirm Order


If payment fails:

    Reserve Inventory
           |
           v
    Payment FAILED
           |
           v
    Release Inventory
           |
           v
    Order ROLLED_BACK


Therefore:

    ORDER SERVICE
         |
         | orchestrates
         v
    INVENTORY SERVICE
         |
         | compensation when needed
         v
    PAYMENT SERVICE


The order-service is the brain of the Saga.

The inventory-service and payment-service are independent participants.

Each service owns its own database.

The Saga maintains business consistency using:

    Local Transactions
          +
    REST Communication
          +
    Orchestration
          +
    Compensating Transactions


That is the core idea behind the Saga Pattern using orchestration.
