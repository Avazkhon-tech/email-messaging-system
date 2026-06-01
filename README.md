# Email Messaging System

Bir nechta pochta qutilarini bitta hisobga ulab, xat yuborish, qidiruvli inboxni
ko'rish va yangi xatlar haqida real vaqtda bildirishnoma olish imkonini beruvchi
backend. Spring Boot 3 / Java 17 da yozilgan. **Gmail** OAuth2 + Gmail Watch API
(push) orqali, **Outlook / Yahoo** esa IMAP/SMTP orqali ishlaydi.

**Texnologiyalar:** Java 17, Spring Boot 3, Spring Security (JWT), Spring WebSocket
(STOMP), Spring Data JPA, Jakarta Mail (IMAP/SMTP), Gmail API + Google Cloud Pub/Sub,
PostgreSQL + Flyway, Caffeine cache.

---

## Ishga tushirish

/secrets papka ichida berilgan pubsub-sa.json fayl ni joylashtirish kerak
.env file ni ham / ga quyish kerak

private message da tashlab beriladi


ishga tushirish uchun docker o'rnatilgan bulishi kerak

```bash
docker compose up --build
```

kiritish mumkin bo'lgan `env` o'zgaruvchilar ro'yxati (
`.env.example`):

| O'zgaruvchi | Izoh |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL ulanishi |
| `JWT_SECRET` | base64, ≥32 bayt |
| `CREDENTIAL_AES_KEY` | base64 AES kalit (parol/refresh token shifrlash uchun) |
| `GOOGLE_ENABLED` | `true` bo'lsa Gmail ulanishi va Pub/Sub subscriber yoqiladi |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth2 web client (Google Cloud console) |
| `GCP_PROJECT_ID`, `GMAIL_PUBSUB_TOPIC`, `GMAIL_PUBSUB_SUBSCRIPTION` | Pub/Sub manzillari |


---

## Arxitektura

Kod **feature bo'yicha** bo'lingan (`com.emailsystem`)

```
com.emailsystem
├── config/     Security, WebSocket, Async, Cache, AppProperties
├── common/     GlobalExceptionHandler → ApiError (domen exception'lari)
├── security/   JwtService, JwtAuthFilter, AuthUser, @CurrentUser
├── crypto/     CredentialCipher (AES-GCM — pochta paroli shifrlangan saqlanadi)
├── auth/       register / login (JWT beradi)
├── user/       User entity + repository
├── account/    Ulangan pochta qutilari CRUD (egasi bo'yicha cheklangan)
├── message/    yuborish / inbox (sahifali, qidiruvli) / detail
├── provider/   EmailProviderClient, ProviderClientRouter (Gmail→API, qolgani→IMAP)
├── gmail/      Gmail API client, watch lifecycle, history sync, Pub/Sub subscriber
├── oauth/      Gmail OAuth2 ulash oqimi (authorize / callback)
├── sync/       MailSyncService (@Scheduled) + AccountSyncWorker
└── realtime/   STOMP config, JWT handshake, NotificationService
```

### Yangi xatni qabul qilish

```
Gmail (push):  Gmail watch → Pub/Sub → GmailPushSubscriber → history.list → dedup
                  → EmailMessage saqlash → NewMailEvent → WebSocket push
Outlook/Yahoo: @Scheduled poll → AccountSyncWorker → IMAP fetch → dedup → ... → push
```

---

## Realtime qanday ishlaydi

WebSocket ustida **STOMP** (brauzer tomonda SockJS) ishlatiladi.

1. **Ulanish:** klient `/ws` endpoint'iga ulanadi.
2. **Autentifikatsiya:** `StompAuthChannelInterceptor` STOMP `CONNECT` freymidagi
   `Authorization: Bearer <jwt>` sarlavhasini tekshiradi va `StompPrincipal`
   o'rnatadi. Token bo'lmasa yoki noto'g'ri bo'lsa — ulanish rad etiladi.
3. **Broker:** in-memory broker `/queue` va `/topic` ustida ishlaydi;
   foydalanuvchi prefiksi `/user`, ilova prefiksi `/app`.
4. **Push:** yangi xat saqlanib tranzaksiya **commit bo'lgandan keyin**
   (`@TransactionalEventListener AFTER_COMMIT`) `NotificationService` hodisani
   asinxron qabul qiladi va `convertAndSendToUser` bilan faqat o'sha foydalanuvchiga
   yuboradi.
5. **Klient obunasi:** `/user/queue/notifications`. Payload `type` maydoni bilan
   keladi — `NEW_MAIL` (yangi xat) yoki `MESSAGE_SENT` (yuborish natijasi).

---

## Provider integratsiyasi

Tashqi email serverlari bilan ishlash `provider`, `gmail` va `oauth` papkalariga ajratilgan.
- `EmailProviderClient` — interface; `ProviderClientRouter` (@Primary) akkauntga qarab
  Gmail'ni `GmailApiClient` (Gmail REST API), qolganini `JakartaMailClient` (IMAP/SMTP) ga yo'naltiradi.
- Parol/refresh token hech qachon ochiq saqlanmaydi — `CredentialCipher` (AES-GCM) bilan
  shifrlanadi, faqat ishlatish paytida deshifrlanadi.

### Gmail Watch (real-time push)

Gmail faqat **OAuth2** bilan ishlaydi (app password emas). Ulanish: foydalanuvchi
*Connect Gmail* → Google consent → `/api/oauth/google/callback` refresh token oladi,
akkauntni saqlaydi va `users.watch` ni yoqadi. Gmail har bir o'zgarishda Pub/Sub
topic'ga `{emailAddress, historyId}` yuboradi; ilova ichidagi **pull subscriber**
(`GmailPushSubscriber`) buni qabul qilib `history.list` orqali yangi xatlarni oladi.
Watch 7 kunda tugaydi — `GmailWatchService` uni avtomatik yangilab turadi.

Sozlash uchun GCP'da: Gmail + Pub/Sub API yoqish, topic + pull subscription, OAuth web
client (`redirect: http://localhost:8080/api/oauth/google/callback`), va Pub/Sub uchun
ADC (`gcloud auth application-default login`).

---

## API (qisqacha)

| Metod | Endpoint | Izoh |
|---|---|---|
| POST | `/api/auth/register` | ro'yxatdan o'tish, JWT qaytaradi |
| POST | `/api/auth/login` | kirish, JWT qaytaradi |
| GET | `/api/accounts` | ulangan pochta qutilari ro'yxati |
| POST | `/api/accounts` | yangi pochta qutisi ulash (Outlook/Yahoo; Gmail rad etiladi) |
| PUT | `/api/accounts/{id}/status` | akkaunt statusini o'zgartirish |
| GET | `/api/oauth/google/authorize` | Gmail OAuth consent URL'ini qaytaradi |
| GET | `/api/oauth/google/callback` | OAuth callback (Gmail'ni ulaydi, public) |
| GET | `/api/messages?search=` | inbox (sahifali, qidiruvli) |
| GET | `/api/messages/{id}` | xat tafsiloti (o'qilgan deb belgilaydi) |
| POST | `/api/messages/send` | xat yuborish (asinxron, `202 Accepted`) |

Barcha `/api/**` so'rovlari (auth'dan tashqari) `Authorization: Bearer <jwt>` talab qiladi.

---

## Testlar

JUnit 5, uch xil uslub:
- **Unit (Mockito):** `AuthServiceTest`, `MessageServiceTest`, `AccountSyncWorkerTest` h.k.
- **`ApiFlowTest`:** `@SpringBootTest` + Testcontainers (haqiqiy PostgreSQL); Docker
  bo'lmasa avtomatik skip bo'ladi.
- **`JakartaMailClientGreenMailTest`:** GreenMail bilan in-memory IMAP/SMTP — provider
  integratsiyasini uchma-uch tekshiradi.
