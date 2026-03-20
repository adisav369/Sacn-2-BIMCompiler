# BIM BackOffice — Deployment Guide

## Quick Start (Local / LAN)

```bash
# 1. Build
mvn package -q -DskipTests

# 2. Run
java -cp "BIMBackOffice/target/bim-backoffice-1.0-SNAPSHOT.jar:BIMBackOffice/target/dependency/*" \
     com.bim.backoffice.server.BackOfficeServer library 9877

# 3. Test
curl http://localhost:9877/api/health
curl http://localhost:9877/api/portfolio
```

## WAN Deployment (Docker)

### Prerequisites
- Docker + Docker Compose
- A server with ports 80/443 open

### Steps

```bash
# 1. Generate TLS certificates (self-signed for testing)
./deploy/generate-certs.sh

# 2. Set a session signing secret (optional — auto-generated if omitted)
export BIM_SESSION_SECRET="your-secret-here"

# 3. Launch
docker-compose up -d

# 4. Verify
curl -k https://your-server/api/health
```

### Production TLS (Let's Encrypt)

```bash
# On the server:
sudo certbot certonly --standalone -d your-domain.com

# Copy certs:
cp /etc/letsencrypt/live/your-domain.com/fullchain.pem deploy/certs/server.crt
cp /etc/letsencrypt/live/your-domain.com/privkey.pem deploy/certs/server.key

# Restart nginx:
docker-compose restart nginx
```

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `BIM_SESSION_SECRET` | (random per JVM) | HMAC-SHA256 key for session token signing |
| `BIM_LIBRARY_DIR` | `./library` | Path to directory containing `*_BOM.db` and `component_library.db` |

## Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/health` | GET | Server status |
| `/api/login` | POST | Create session (`{"userId":"...", "displayName":"..."}`) |
| `/api/sessions` | GET | Who's online |
| `/api/portfolio` | GET | All projects overview |
| `/api/kanban` | GET | Kanban board by DocStatus |
| `/api/bsc` | GET | Balanced scorecard |
| `/api/cost?id=SH` | GET | 5D cost breakdown |
| `/api/schedule?id=SH` | GET | 4D construction schedule |
| `/api/carbon?id=SH` | GET | 6D carbon footprint |
| `/api/maintenance?id=SH` | GET | 7D maintenance schedule |

All endpoints return JSON. Session token via `X-Session-Token` header.

## Architecture

```
Internet → nginx (443/TLS) → BackOfficeServer (9877/HTTP) → SQLite DBs
                                     ↓
                              SessionManager (HMAC tokens, per-DB write locks)
```

- **nginx** terminates TLS, proxies to Java backend
- **BackOfficeServer** handles all API logic, no framework dependency
- **SessionManager** signs tokens with HMAC-SHA256, serializes writes per DB file
- **SQLite WAL mode** allows concurrent reads alongside serialized writes

## Security

- Session tokens are HMAC-SHA256 signed — cannot be forged without the secret
- Constant-time signature comparison prevents timing attacks
- Unsigned (legacy) UUID tokens accepted for backward compatibility in local/test use
- CORS headers configured for browser-based clients
- TLS via nginx reverse proxy (not in Java — keeps the server simple)

## Scaling Notes

This deployment handles 3-10 concurrent users on a single server. For larger teams:

| Concern | Current | Future |
|---------|---------|--------|
| Sessions | In-memory (single JVM) | Redis-backed (BO-5) |
| Database | SQLite (single-writer) | PostgreSQL (BO-6) |
| Auth | Token-based (no roles) | AD_Role + AD_User (BO-5) |
| Export | JSON only | PDF/CSV/XLSX (BO-4) |

The DAO abstraction isolates all database access — swapping SQLite for PostgreSQL
changes the connection factory, not the business logic.
