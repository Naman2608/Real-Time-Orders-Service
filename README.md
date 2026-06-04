# Real-Time Orders Service

Clients receive database change notifications **instantly** — no polling.
Any INSERT / UPDATE / DELETE on the `orders` table is pushed to every connected browser in real time.

---

## Run It (Choose One)

### Option 1 — Pre-built JAR (easiest, just Java needed)

**Requirement:** Java 17+

```bash
# Windows
run-jar.bat

# Mac / Linux
chmod +x run-jar.sh && ./run-jar.sh

# Or directly
java -jar orders-realtime.jar
```

`orders-realtime.jar` is included in the project root. It is a self-contained fat JAR — PostgreSQL is embedded inside it. No database installation required.

---

### Option 2 — Build from source (Maven wrapper included)

**Requirement:** Java 17+  — Maven is **not** required; `mvnw` downloads it automatically.

```bash
# Windows
mvnw.cmd spring-boot:run

# Mac / Linux
chmod +x mvnw && ./mvnw spring-boot:run
```

---

### Option 3 — Docker Compose

**Requirement:** Docker Desktop

```bash
docker-compose up --build
```

---

## Open the Dashboard

Once the backend is running, open `client/index.html` in any browser.
No server needed — it is a plain HTML file.

Use the form to create, update, or delete orders. Every change appears in the live feed within milliseconds.

---

## How It Works

```
REST call
   │
   ▼
PostgreSQL (embedded)
   │  INSERT / UPDATE / DELETE fires a trigger
   │  trigger calls pg_notify('orders_channel', <json>)
   ▼
PostgresListenerService   ← dedicated JDBC connection with LISTEN active
   │  blocks on getNotifications() — woken up by the DB, zero polling
   ▼
SimpMessagingTemplate.convertAndSend("/topic/orders", payload)
   │
   ▼
WebSocket (STOMP)  →  Browser dashboard
```

### Why PostgreSQL LISTEN/NOTIFY?

| Approach | Trade-off |
|---|---|
| **Client polling** | Simple but wastes bandwidth and adds latency |
| **LISTEN/NOTIFY** ✓ | Event-driven, zero extra infrastructure, low latency |
| **Debezium + Kafka** | Production-grade durability and fan-out, but heavy setup |
| **Redis Pub/Sub** | Fast, but requires a separate broker |

LISTEN/NOTIFY is built into PostgreSQL. The database itself is the event bus — no extra services needed.

### The Listener (no polling)

`PostgresListenerService` holds **one dedicated JDBC connection** (outside the HikariCP pool) that has issued `LISTEN orders_channel`. It calls `pgConnection.getNotifications(5000)` which **blocks** until the database fires a notification or the 5-second timeout elapses. This is not polling — the thread sleeps until the database wakes it up.

When a notification arrives, the JSON payload is broadcast to all WebSocket subscribers via Spring's `SimpMessagingTemplate`.

---

## REST API

```bash
# List all orders
curl http://localhost:8080/api/orders

# Create — triggers INSERT notification
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Alice","productName":"Laptop","status":"pending"}'

# Update status — triggers UPDATE notification
curl -X PUT http://localhost:8080/api/orders/1 \
  -H "Content-Type: application/json" \
  -d '{"status":"shipped"}'

# Delete — triggers DELETE notification
curl -X DELETE http://localhost:8080/api/orders/1
```

---

## WebSocket Message Format

Every message on `/topic/orders`:

```json
{
  "operation": "INSERT",
  "data": {
    "id": 1,
    "customer_name": "Alice",
    "product_name": "Laptop",
    "status": "pending",
    "updated_at": "2026-06-04T10:30:00"
  }
}
```

---

## Project Structure

```
orders-realtime.jar              ← pre-built, run with: java -jar orders-realtime.jar
run-jar.bat / run-jar.sh         ← one-click launch scripts
docker-compose.yml               ← Docker alternative
client/index.html                ← browser dashboard (self-contained, no server)

src/main/java/com/realtime/orders/
├── config/
│   ├── EmbeddedPostgresConfig.java   starts embedded PostgreSQL (no install needed)
│   ├── TriggerInitializer.java       installs pg_notify trigger on startup
│   └── WebSocketConfig.java          STOMP endpoint configuration
├── controller/OrderController.java   REST API
├── service/
│   ├── PostgresListenerService.java  LISTEN/NOTIFY → WebSocket bridge  ← core logic
│   └── OrderService.java
├── model/Order.java
└── repository/OrderRepository.java
```

---

## Scalability

The current design is single-instance. To scale out:

- **Multiple app instances**: Add Redis Pub/Sub between the listener and the WebSocket servers. One instance holds the PG connection; all instances subscribe to Redis.
- **High-throughput / durability**: Replace LISTEN/NOTIFY with Debezium reading the PostgreSQL WAL, publishing to Kafka.
- **The listener reconnects automatically** on connection loss with a 3-second backoff — no manual restart needed.
