# Email Messaging System

A multi-account email client backend: connect Gmail / Outlook / Yahoo mailboxes, send
mail, browse a searchable inbox, and receive **real-time** new-mail notifications. The
focus is backend logic, provider integration, and real-time delivery; a deliberately
simple static frontend is included.

- **Backend:** Java 17, Spring Boot 3.2, Spring Security (JWT), Spring WebSocket (STOMP),
  Spring Data JPA, Jakarta Mail (IMAP/SMTP), PostgreSQL, Flyway.
- **Frontend:** static HTML + vanilla JS (SockJS/STOMP), served by the backend.
- **Tests:** JUnit 5 + Mockito (unit), GreenMail (provider integration), Testcontainers
  (full API flow on real PostgreSQL).

---

## Architecture

Layered, feature-packaged. Controllers are thin (validation + delegation), services hold
business logic, repositories are Spring Data JPA, and DTOs sit at the API boundary so
entities never leak.

```
com.emailsystem
├── config/      Security, WebSocket, Async, Scheduling, typed app properties
├── common/      GlobalExceptionHandler + ApiError + domain exceptions
├── security/    JwtService, JwtAuthFilter, AuthUser principal, @CurrentUser
├── crypto/      CredentialCipher (AES-256-GCM for stored app passwords)
├── auth/        register / login
├── user/        User entity + repository
├── account/     EmailAccount CRUD + status toggle (ownership-scoped)
├── message/     send / inbox (paged, sortable, searchable) / detail (read flip)
├── provider/    EmailProviderClient → JakartaMailClient (IMAP fetch + SMTP send)
├── sync/        MailSyncService (scheduled) + AccountSyncWorker (per-account, dedup)
└── realtime/    STOMP config, JWT handshake auth, NotificationService
```

### Request → data flow (inbound mail)

```
@Scheduled poll ─▶ AccountSyncWorker (per active account, own tx)
                     │  fetch via IMAP (since last_synced_at)
                     │  skip rows already stored  (dedup)
                     │  persist new EmailMessages
                     └─▶ publish NewMailEvent  ── AFTER_COMMIT ──▶ NotificationService
                                                                      │ STOMP
                                                                      ▼
                                                  /user/queue/notifications  ──▶ browser
```

---

## How real-time works

1. A STOMP endpoint is exposed at `/ws` (SockJS fallback enabled).
2. The browser opens the socket and sends the JWT in the STOMP **CONNECT** frame
   (`Authorization: Bearer <token>`). `StompAuthChannelInterceptor` validates it and binds
   a principal **named by user id**.
3. When the sync pipeline persists new messages, `AccountSyncWorker` publishes an
   in-process `NewMailEvent`.
4. `NotificationService` listens **after the transaction commits** (so the client never
   gets a notification for a message it can't yet read) and pushes a payload to
   `/user/queue/notifications`. Spring's user-destination resolution routes it to the
   correct user's socket only.
5. The frontend shows a "New message received" banner and auto-refreshes the inbox.

The event → transport split (Spring `ApplicationEvent`) means swapping the in-process bus
for **Kafka/RabbitMQ** later only touches the publisher/listener, not the sync logic.

---

## Provider integration

`JakartaMailClient` talks **IMAP** (fetch) and **SMTP** (send) over SSL/STARTTLS, uniformly
across providers. Host/port per provider live in `ProviderEndpoints`, resolved through
`ProviderEndpointResolver` (a seam that also lets tests point at an in-memory server).

| Provider | IMAP                       | SMTP                      |
|----------|----------------------------|---------------------------|
| Gmail    | imap.gmail.com:993         | smtp.gmail.com:587        |
| Outlook  | outlook.office365.com:993  | smtp.office365.com:587    |
| Yahoo    | imap.mail.yahoo.com:993    | smtp.mail.yahoo.com:587   |

**Credentials = app passwords.** Gmail and Yahoo require an *app password* (with 2FA
enabled) rather than the normal login password; this maps to the spec's "access token or
app password". The app password is **encrypted at rest** with AES-256-GCM
(`CredentialCipher`) and never returned by any API. When an account is connected, the
backend first verifies the credentials against the provider's IMAP server and rejects them
fast (HTTP 502) if they don't authenticate.

> Generating a Gmail app password: Google Account → Security → 2-Step Verification →
> App passwords. Use that 16-character value as `appPassword`.

### Sync & deduplication

A scheduled poller (`app.sync.interval-ms`, default 60s) syncs every **ACTIVE** account in
its own transaction. Deduplication is enforced two ways: a `UNIQUE (account_id,
external_message_id)` constraint in the database, plus an existence check before insert.
Each account stores `last_synced_at` and `last_sync_status`, and one failing mailbox never
aborts the others.

---

## Running

### Option A — Docker (recommended)

```bash
cp env.example env
# (optional) generate strong secrets:
#   openssl rand -base64 48   # -> JWT_SECRET
#   openssl rand -base64 32   # -> CREDENTIAL_AES_KEY
docker compose up --build
```

App: http://localhost:8080  ·  PostgreSQL: localhost:5432

### Option B — Local (Maven + your own PostgreSQL)

```bash
createdb emaildb   # or use an existing instance
export DB_URL=jdbc:postgresql://localhost:5432/emaildb
export DB_USERNAME=emailuser DB_PASSWORD=emailpass
export JWT_SECRET=$(openssl rand -base64 48)
export CREDENTIAL_AES_KEY=$(openssl rand -base64 32)
mvn spring-boot:run
```

Flyway creates the schema on first boot. Open http://localhost:8080 → register → connect
an account → inbox.

### Configuration (environment variables)

See `.env.example`. Key ones: `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, `JWT_SECRET`
(Base64, ≥32 bytes), `CREDENTIAL_AES_KEY` (Base64 16/24/32 bytes), `SYNC_INTERVAL_MS`.

---

## API

All `/api/**` routes except `/api/auth/**` require `Authorization: Bearer <jwt>`.

| Method | Path                          | Description                                   |
|--------|-------------------------------|-----------------------------------------------|
| POST   | `/api/auth/register`          | Create account → `{token, userId, ...}`       |
| POST   | `/api/auth/login`             | Authenticate → `{token, ...}`                 |
| POST   | `/api/accounts`               | Connect a mailbox (verifies + encrypts)       |
| GET    | `/api/accounts`               | List the caller's accounts                    |
| PUT    | `/api/accounts/{id}/status`   | Activate / deactivate an account              |
| POST   | `/api/messages/send`          | Send an email (HTML supported)                |
| GET    | `/api/messages`               | Inbox: `?page&size&sort&search`               |
| GET    | `/api/messages/{id}`          | Full message; marks it read                   |
| WS     | `/ws` → `/user/queue/notifications` | Real-time new-mail notifications        |

### Examples

```bash
# Register
curl -s localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"fullname":"Jane","email":"jane@example.com","password":"password123"}'

TOKEN=...   # token from the response

# Connect a Gmail account (use a Gmail app password)
curl -s localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"provider":"GMAIL","emailAddress":"you@gmail.com","appPassword":"abcd efgh ijkl mnop"}'

# Inbox (paged + search)
curl -s "localhost:8080/api/messages?page=0&size=20&search=invoice" \
  -H "Authorization: Bearer $TOKEN"

# Send
curl -s localhost:8080/api/messages/send -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountId":1,"recipients":["bob@example.com"],"subject":"Hi","body":"<b>Hello</b>","html":true}'
```

---

## Security

- Passwords hashed with BCrypt; stateless JWT auth; no server-side sessions.
- Email app passwords encrypted with AES-256-GCM; never exposed by the API.
- Strict per-user isolation: every account/message query is scoped by `userId`
  (`findByIdAndUserId`, ownership-checked inbox/detail queries).
- WebSocket connections are authenticated on the STOMP CONNECT frame.
- All errors return a consistent `ApiError` JSON (`GlobalExceptionHandler`): 400 validation,
  401 auth, 404 not-found, 409 conflict, 502 provider failures.
- All secrets are supplied via environment variables.

---

## Testing

```bash
mvn test
```

- **Unit (Mockito):** auth, message service (validation + read-status flip), credential
  cipher, and sync **dedup** logic.
- **Provider integration (GreenMail):** real IMAP/SMTP send→fetch round-trip, no network.
- **Full API flow (Testcontainers + PostgreSQL + Flyway):** register → connect → sync →
  inbox/search → read → send, plus ownership isolation and auth enforcement.
  *Auto-skips when Docker is unavailable.*

---

## Notable design choices & possible extensions

- Real-time uses **in-process Spring events**; the event/transport seam makes a
  **Kafka/RabbitMQ** swap localized. (Listed as optional in the brief.)
- Provider access uses **IMAP/SMTP app passwords** for uniform multi-provider support;
  the `EmailProviderClient` interface leaves room for a **Gmail-API/OAuth2** implementation.
- Retry on transient SMTP failures via Spring Retry (`@Retryable`).
- A **Redis** cache for hot inbox reads is a natural next optimization.
