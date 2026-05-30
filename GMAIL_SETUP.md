# Gmail Watch API — Google Cloud Setup

## Prerequisites
- Google account with billing enabled (Pub/Sub free tier is sufficient)
- Your app's public HTTPS URL. For local dev use **ngrok** (Step 0).

---

## Step 0 — Local dev only: expose localhost via ngrok

Gmail Pub/Sub push requires a public HTTPS endpoint.

```bash
ngrok http 8080
# → https://abc123.ngrok.io  — use this as "yourhost" in all steps below
```

---

## Step 1 — Create a Google Cloud Project

1. Go to https://console.cloud.google.com
2. Project dropdown → **New Project** → name it (e.g. `email-messaging-system`) → **Create**
3. Note your **Project ID** — needed for the Pub/Sub topic name

```bash
# via gcloud CLI
gcloud projects create email-messaging-system --name="Email Messaging System"
gcloud config set project email-messaging-system
```

---

## Step 2 — Enable Required APIs

**APIs & Services → Library** — search and enable:
- **Gmail API**
- **Cloud Pub/Sub API**

```bash
gcloud services enable gmail.googleapis.com pubsub.googleapis.com
```

---

## Step 3 — Configure OAuth Consent Screen

1. **APIs & Services → OAuth consent screen**
2. User Type: `External` (for any Gmail user)
3. Fill in: App name, User support email, Developer contact email
4. **Scopes → Add or Remove Scopes** → add:
   ```
   https://www.googleapis.com/auth/gmail.readonly
   ```
5. Add your own Gmail address as a **Test user**
6. Save and continue

> While in **Testing** mode only listed test users can authorize.  
> To open the app to all Gmail users, publish the app — Google review is required for sensitive scopes.

---

## Step 4 — Create OAuth2 Client Credentials

1. **APIs & Services → Credentials → Create Credentials → OAuth 2.0 Client ID**
2. Application type: `Web application`
3. Name: `Email Messaging System Web Client`
4. **Authorized redirect URIs** — add all that apply:
   ```
   http://localhost:8080/api/gmail/oauth/callback
   https://abc123.ngrok.io/api/gmail/oauth/callback
   https://api.yourdomain.com/api/gmail/oauth/callback
   ```
5. Click **Create** — copy the **Client ID** and **Client Secret** immediately

---

## Step 5 — Create a Pub/Sub Topic

1. **Pub/Sub → Topics → Create Topic**
2. Topic ID: `gmail-notifications`
3. Uncheck "Add a default subscription"
4. Click **Create**

Full topic name (used in env var):
```
projects/YOUR_PROJECT_ID/topics/gmail-notifications
```

```bash
gcloud pubsub topics create gmail-notifications
```

---

## Step 6 — Grant Gmail Permission to Publish

Gmail uses a Google-managed service account to push to your topic. Grant it the Publisher role:

1. **Pub/Sub → Topics → gmail-notifications → Add Principal**
2. New principal: `gmail-api-push@system.gserviceaccount.com`
3. Role: `Pub/Sub Publisher`
4. Save

```bash
gcloud pubsub topics add-iam-policy-binding gmail-notifications \
  --member="serviceAccount:gmail-api-push@system.gserviceaccount.com" \
  --role="roles/pubsub.publisher"
```

> This is a Google-managed account — you grant it permission, you do not create it.

---

## Step 7 — Create a Push Subscription

1. **Pub/Sub → Subscriptions → Create Subscription**
2. Subscription ID: `gmail-push-sub`
3. Topic: `gmail-notifications`
4. Delivery type: `Push`
5. Endpoint URL:
   ```
   https://yourhost/api/gmail/push
   ```
6. Acknowledgement deadline: `30` seconds
7. *(Recommended for production)* Enable **Authentication**:
   - Check **Enable authentication**
   - Service account: create one (e.g. `pubsub-invoker`)
   - Audience: `https://yourhost/api/gmail/push`
8. Click **Create**

```bash
gcloud pubsub subscriptions create gmail-push-sub \
  --topic=gmail-notifications \
  --push-endpoint=https://yourhost/api/gmail/push \
  --ack-deadline=30
```

> **HTTP is rejected by Google.** The endpoint must be HTTPS. Use ngrok for local development.

---

## Step 8 — Set Environment Variables

Add to your `.env` file (picked up by `docker-compose.yml`) and export for `mvn spring-boot:run`:

```env
GMAIL_CLIENT_ID=your-client-id.apps.googleusercontent.com
GMAIL_CLIENT_SECRET=your-client-secret
GMAIL_PUBSUB_TOPIC=projects/YOUR_PROJECT_ID/topics/gmail-notifications
GMAIL_REDIRECT_URI=https://yourhost/api/gmail/oauth/callback

# Optional — set if you enabled OIDC auth on the subscription in Step 7
GMAIL_PUBSUB_AUDIENCE=https://yourhost/api/gmail/push
```

For local dev:
```bash
export GMAIL_CLIENT_ID=...
export GMAIL_CLIENT_SECRET=...
export GMAIL_PUBSUB_TOPIC=projects/YOUR_PROJECT_ID/topics/gmail-notifications
export GMAIL_REDIRECT_URI=http://localhost:8080/api/gmail/oauth/callback
mvn spring-boot:run
```

---

## Step 9 — Verify Everything Works

### 1. Connect a Gmail account
Open in a browser (with your JWT in the Authorization header, or use the frontend):
```
GET https://yourhost/api/gmail/oauth/authorize
Authorization: Bearer <your-JWT>
```
→ Google consent screen → authorize → redirected to `/inbox.html?gmail_connected=true`

### 2. Simulate a push notification
```bash
# data = base64('{"emailAddress":"you@gmail.com","historyId":12345}')
curl -X POST https://yourhost/api/gmail/push \
  -H "Content-Type: application/json" \
  -d '{
    "message": {
      "data": "eyJlbWFpbEFkZHJlc3MiOiJ5b3VAZ21haWwuY29tIiwiaGlzdG9yeUlkIjoxMjM0NX0=",
      "messageId": "test-1"
    }
  }'
# Expected response: HTTP 204
```

### 3. Check the watch registration in the database
```sql
SELECT a.email_address, w.history_id, w.watch_expiry
FROM gmail_watch_registrations w
JOIN email_accounts a ON a.id = w.account_id;
```

### 4. Send a real test email
Send an email to the connected Gmail address from another account.  
Within seconds the Pub/Sub push fires → message appears in the inbox via WebSocket.

---

## Common Issues

| Problem | Cause | Fix |
|---|---|---|
| `400 redirect_uri_mismatch` | Redirect URI not registered in Console | Add the exact URI in Step 4 |
| No refresh token returned | User already authorized; `prompt=consent` was bypassed | Revoke access at myaccount.google.com/permissions and re-authorize |
| Push notifications never arrive | Endpoint not HTTPS or not publicly reachable | Use ngrok; double-check the subscription's push endpoint URL |
| `403` on `users.watch()` | Gmail API not enabled or missing scope | Re-check Steps 2 and 3 |
| Watch stops delivering after 7 days | Renewal scheduler not running or account INACTIVE | Check app logs for `GmailWatchService.renewExpiringWatches` |
