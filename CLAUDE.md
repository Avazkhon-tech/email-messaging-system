# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 3 / Java 17 backend that lets users register, connect multiple real
mailboxes (Gmail, Outlook, Yahoo, iCloud, GMX, or custom IMAP/SMTP), and read/send
mail through a unified REST API plus a static web UI. Incoming mail is fetched by a
scheduled background worker and pushed to the browser over WebSocket/STOMP.

## Commands

```bash
mvn spring-boot:run            # run locally (needs PostgreSQL + env vars, see below)
mvn test                       # run the full test suite
mvn test -Dtest=AuthServiceTest            # single test class
mvn test -Dtest=ApiFlowTest#registerLoginSendRead   # single test method
mvn package                    # build the runnable jar into target/
docker compose up --build      # run app + PostgreSQL together (API on :8080)
```

Local runs require env vars (`mvn spring-boot:run` reads them from the shell, not
`.env` — `.env` is only wired into `docker-compose.yml`). At minimum set `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (base64, ≥32 bytes), `CREDENTIAL_ENC_KEY`
(base64 AES key). `application.yml` has insecure dev defaults for all of these — never
ship them. Full list in `.env.example`.

## Architecture

Package-by-feature under `com.emailsystem` (`src/main/java/com/emailsystem`). Each
feature is a vertical slice: `Controller` → `Service` → JPA `Repository` + entity,
with a `dto/` subpackage for request/response records.

- **auth / user / security** — register/login issue JWTs (jjwt, HMAC-SHA256).
  `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`, validates the
  `Bearer` token, loads the `User`, and sets an `AuthUser` principal. Controllers get
  the caller via the `@CurrentUser AuthUser` parameter annotation (a meta-annotation
  over `@AuthenticationPrincipal`). Sessions are stateless; CORS allows all origins.
- **account** — CRUD for connected mailboxes. On create, `DefaultProviderEndpointResolver`
  fills IMAP/SMTP host/port/TLS from the `Provider` enum via `ProviderEndpoints`
  (`CUSTOM` uses caller-supplied values). The mailbox password is encrypted before
  persistence — see crypto.
- **provider** — the mail boundary. `EmailProviderClient` interface, implemented by
  `JakartaMailClient` (Jakarta Mail IMAP fetch + SMTP send). `FetchedMessage` /
  `OutgoingMessage` are the transfer types crossing this boundary. Keep all raw
  IMAP/SMTP concerns inside this package.
- **sync** — `AccountSyncWorker` (`@Scheduled`, `app.sync.interval-ms`) iterates
  `ACTIVE` accounts and calls `MailSyncService.syncAccount`, which fetches messages
  since `lastSyncedAt`, dedupes on `(accountId, providerMessageId)`, persists inbound
  `EmailMessage`s, and fires a realtime notification per new message.
- **realtime** — STOMP over WebSocket (SockJS). `WebSocketConfig` enables a simple
  broker on `/queue` + `/topic` with user prefix `/user`. `StompAuthChannelInterceptor`
  authenticates the STOMP `CONNECT` frame from its `Authorization` header and sets a
  `StompPrincipal`. `NotificationService` pushes new-mail events to `/user/queue/mail`.
- **crypto** — `CredentialCipher` does AES-GCM (random 12-byte IV prepended to
  ciphertext, base64-encoded) keyed by `app.crypto.credential-key`. Mailbox passwords
  are stored only in this encrypted form.
- **common** — `GlobalExceptionHandler` maps the `common.exception.*` exceptions
  (`NotFound`, `Conflict`, `BadRequest`, `Unauthorized`, `Provider`) to `ApiError` JSON.
  Throw these from services rather than returning error responses from controllers.
- **config** — `AppProperties` (`@ConfigurationProperties` prefix `app`) binds the
  `jwt` / `crypto` / `mail` / `sync` config groups.

Lombok is used throughout (`@RequiredArgsConstructor`, `@Builder`, `@Getter/@Setter`,
`@Slf4j`); constructor injection is the norm.

## Persistence

PostgreSQL in production, schema owned by **Flyway** (`src/main/resources/db/migration`,
`V1__init.sql`). JPA runs with `ddl-auto: validate` — entities must match the migration,
so schema changes mean a new `V_N__*.sql` migration, not an entity-only change.

## Tests

JUnit 5. Three styles, no `application-test.yml`:

- **Unit (Mockito):** `AuthServiceTest`, `MessageServiceTest`, `CredentialCipherTest`,
  `AccountSyncWorkerTest` — plain, no Spring context.
- **`ApiFlowTest`** — `@SpringBootTest` + `@AutoConfigureMockMvc` against a real
  PostgreSQL via **Testcontainers** (`@DynamicPropertySource` points the datasource at
  the container; Flyway runs the real migrations). `EmailProviderClient` is `@MockBean`
  so no network/credentials are needed, and the sync scheduler is pushed out with a long
  `initial-delay-ms`. **Auto-skips when Docker is unavailable** (`disabledWithoutDocker`).
- **`JakartaMailClientGreenMailTest`** — plain JUnit (no Spring context); spins up an
  in-memory IMAP/SMTP server (**GreenMail**) to exercise `JakartaMailClient` end to end.

The `h2` test dependency is present but not currently wired into any test.
