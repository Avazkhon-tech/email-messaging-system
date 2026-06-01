# Email Messaging System

Bir nechta pochta qutilarini (Gmail, Outlook, Yahoo) bitta hisobga ulab, xat
yuborish, qidiruvli inboxni ko'rish va yangi xatlar haqida real vaqtda
bildirishnoma olish imkonini beruvchi backend. Spring Boot 3 / Java 17 da yozilgan.

**Texnologiyalar:** Java 17, Spring Boot 3, Spring Security (JWT), Spring WebSocket
(STOMP), Spring Data JPA, Jakarta Mail (IMAP/SMTP), PostgreSQL + Flyway, Caffeine cache.

---

## Ishga tushirish


ishga tushirish uchun docker o'rnatilgan bulishi kerak

```bash
docker compose up --build
```

kiritish mumkin bo'lgan `env` o'zgaruvchilar ro'yxati (
`.env.example`):

hammasida default qiymatlar bor. testlash oson bo'lishi uchun

| O'zgaruvchi | Izoh |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL ulanishi |
| `JWT_SECRET` | base64, ≥32 bayt |
| `CREDENTIAL_AES_KEY` | base64 AES kalit (pochta parollarini shifrlash uchun) |

> `application.yml` da faqat dev uchun ishlaydigan default qiymatlar bor — ularni
> hech qachon production'ga chiqarmang.

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
├── provider/   EmailProviderClient → JakartaMailClient (IMAP/SMTP)
├── sync/       MailSyncService (@Scheduled) + AccountSyncWorker
└── realtime/   STOMP config, JWT handshake, NotificationService
```

### sync qilish jarayoni

```
@Scheduled poll → MailSyncService → AccountSyncWorker (faol akkauntlar uchun)
   → IMAP'dan fetch → (accountId, messageId) bo'yicha dedup → EmailMessage saqlash
   → NewMailEvent → NotificationService → WebSocket orqali foydalanuvchiga push
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

Tashqi email serverlari bilan ishlash to'liq `provider` papkasi ichiga ajratilgan,
- `EmailProviderClient` — interface, yagona implementatsiyasi
  `JakartaMailClient` (Jakarta Mail: IMAP'dan o'qish, SMTP orqali yuborish).
- Akkaunt yaratilganda `DefaultProviderEndpointResolver` `Provider` enum'iga qarab
  IMAP/SMTP host/port/TLS qiymatlarini avtomatik to'ldiradi.
- Pochta paroli (app password) hech qachon ochiq saqlanmaydi —
  `CredentialCipher` (AES-GCM) bilan shifrlanadi, faqat yuborish/o'qish paytida
  deshifrlanadi.
- Tarmoq xatolarida `JakartaMailClient` Spring Retry bilan qayta urinadi; bardosh
  qila olmasa `ProviderException` tashlaydi.

---

## API (qisqacha)

| Metod | Endpoint | Izoh |
|---|---|---|
| POST | `/api/auth/register` | ro'yxatdan o'tish, JWT qaytaradi |
| POST | `/api/auth/login` | kirish, JWT qaytaradi |
| GET | `/api/accounts` | ulangan pochta qutilari ro'yxati |
| POST | `/api/accounts` | yangi pochta qutisi ulash |
| PUT | `/api/accounts/{id}/status` | akkaunt statusini o'zgartirish |
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
