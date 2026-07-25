# ⚡ ChargeManager — Real-Time EV Charger Management System

A Spring Boot backend that manages EV chargers over the **OCPP 1.6** protocol via WebSocket, tracks live charger status, and exposes a REST API for monitoring charging activity.

## Overview

ChargeManager acts as a Central System (CSMS) for EV chargers. Chargers connect over WebSocket and speak OCPP 1.6; the server processes their messages, keeps charger state up to date, persists transaction history, and exposes that data through a REST API for dashboards or ops tooling.

## Features

- **OCPP 1.6 message handling** over WebSocket
  - `BootNotification` — charger registration
  - `Heartbeat` — liveness signal
  - `StatusNotification` — status changes (`Available`, `Charging`, `Faulted`, etc.)
  - `StartTransaction` / `StopTransaction` — charging session lifecycle
- **Automatic stale-charger detection** — a charger with no heartbeat for 5+ minutes is marked `Unavailable`
- **Persistent state** — charger status and full transaction history stored in the database (survives disconnects/restarts)
- **REST API**
  - List all chargers with current status
  - Retrieve transaction history, filterable by charger ID and time range
- **Authentication** on REST endpoints (see [Authentication](#authentication))
- Designed to handle **100+ concurrent WebSocket connections** without data loss on disconnect

## Tech Stack

- Java 21, Spring Boot
- Spring WebSocket
- Spring Data JPA
- \[Your DB — e.g. PostgreSQL / MySQL]
- \[Your auth mechanism — e.g. Spring Security + JWT]

## Architecture

```
EV Charger (OCPP 1.6 client)
        │  WebSocket
        ▼
 WebSocket Handler ──► OCPP Message Router ──► Service Layer ──► Database
                                                     │
                                                     ▼
                                              REST API (status, history)
```

*Add a real diagram or link to one here once you have it — even a simple draw.io export helps a reviewer a lot.*

## Getting Started

### Prerequisites
- Java 21+
- Maven (or Gradle — match whatever you used)
- \[Database] running locally or via Docker

### Run locally
```bash
git clone https://github.com/krupa128/chargemanager.git
cd chargemanager
./mvnw spring-boot:run
```

### Configuration
Set the following in `application.yml` / `application.properties`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chargemanager
    username: <username>
    password: <password>
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/chargers` | List all chargers with current status |
| GET | `/api/transactions?chargerId=&from=&to=` | Transaction history, filterable by charger and time range |

*Fill in the rest of your actual endpoints and add request/response examples — this is the part reviewers read most closely.*

## Testing the WebSocket / OCPP Flow

Since there's no physical charger, simulate one with a dummy OCPP payload via Postman, `wscat`, or a small script:

```bash
wscat -c ws://localhost:8080/ocpp/CHARGER_001
```

Then send a sample `BootNotification`:
```json
[2, "1", "BootNotification", {
  "chargePointVendor": "Acme",
  "chargePointModel": "AC-22kW"
}]
```

*Include a link to your demo video here once uploaded.*

## Authentication

*Describe what you implemented — e.g. JWT-based auth on REST endpoints, API key for chargers, etc.*

## Design Decisions

*A short "why" section goes a long way for reviewers — e.g. why you chose your DB, how you handle reconnects without data loss, how the heartbeat-timeout check is scheduled.*
