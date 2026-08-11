# Deployment

Going-live checklist for the JobTracker backend.

**Placeholders used throughout:**
- `<API-DOMAIN>` — the Railway domain for this service, e.g. `jobtracker-api.up.railway.app`
- Frontend is already live at `https://job-tracking-application-frontend.vercel.app`

---

## What deploys where

| Piece | Platform | Notes |
|---|---|---|
| Backend (this repo) | Railway | Spring Boot fat jar, Java 17 |
| MySQL 8 | Railway | Schema owned by Flyway |
| Frontend | Vercel | Already deployed |
| File storage | Cloudinary | Already configured (`igmsrg7x`) |
| Outbound email | Gmail SMTP | App Password, not the account password |

The API is served under the context path **`/jobTracking`**, so every URL includes it —
including the OAuth2 callbacks. Forgetting it is the most common setup mistake here.

---

## 1. Before the first deploy

- [ ] **Generate a production `JWT_SECRET`** — `openssl rand -base64 48`.
      Do **not** reuse the development secret. Rotating it later logs every user out, so do it now.
- [ ] Provision MySQL on Railway and note the connection details.
- [ ] Confirm `local-secrets.properties` is gitignored (it is) — production reads env vars only.
- [ ] Decide instance count: **start at 1**. See [Known constraints](#known-constraints).

## 2. Environment variables (Railway)

Set **all** of these. Every secret is deliberately declared without a fallback, so a missing one
**fails the app at startup** rather than silently booting with a placeholder key.

### Required — app will not start without them

| Variable | Value / where it comes from |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Railway MySQL, as a JDBC URL — see the note below |
| `DATABASE_USERNAME` | Railway MySQL |
| `DATABASE_PASSWORD` | Railway MySQL |
| `JWT_SECRET` | the value generated in step 1 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google Cloud Console |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth App |
| `GMAIL_USER` | `jobjugglerio@gmail.com` |
| `GMAIL_APP_PASSWORD` | Google App Password (16 letters, spaces stripped) |
| `CLOUDINARY_CLOUD_NAME` | `igmsrg7x` |
| `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | Cloudinary dashboard |

> **`DATABASE_URL` must be a JDBC URL.** Railway's `MYSQL_URL` is
> `mysql://user:pass@host:port/db`, which the driver will not accept. Convert it to:
> `jdbc:mysql://<host>:<port>/<database>?useSSL=true&serverTimezone=UTC`
> and put the credentials in `DATABASE_USERNAME` / `DATABASE_PASSWORD` separately.

### Optional — sensible defaults already applied

| Variable | Default | When to override |
|---|---|---|
| `PORT` | injected by Railway | never set manually |
| `CORS_ALLOWED_ORIGIN` | the Vercel production URL | to add preview URLs — comma-separated |
| `OAUTH2_REDIRECT_URI` | `…vercel.app/oauth2/redirect` | custom domain |
| `PASSWORD_RESET_REDIRECT_URI` | `…vercel.app/reset-password` | custom domain |
| `REMINDERS_ENABLED` | `true` | `false` to silence reminder email |
| `REMINDERS_MAX_OVERDUE_DAYS` | `7` | backfill guard window |
| `CLEANUP_ENABLED` | `true` | `false` to pause token cleanup |
| `DB_POOL_SIZE` | `8` | if Railway's connection cap bites |

## 3. OAuth2 provider setup

**Do this before announcing the launch** — social login fails immediately without it, because
both providers currently only know the localhost callbacks.

- [ ] **Google** — [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services →
      Credentials → your OAuth client → **Authorised redirect URIs**, add:
      ```
      https://<API-DOMAIN>/jobTracking/login/oauth2/code/google
      ```
- [ ] **GitHub** — [github.com/settings/developers](https://github.com/settings/developers) → your
      OAuth App → **Authorization callback URL**:
      ```
      https://<API-DOMAIN>/jobTracking/login/oauth2/code/github
      ```
      GitHub allows only one callback URL per app. If you want localhost to keep working,
      register a second OAuth App for development.
- [ ] **Google OAuth consent screen** is likely still in *Testing*, which limits sign-in to
      explicitly listed test users. **Publish it** before real users arrive, or everyone else sees
      Google's "access blocked" screen — which looks like a bug in your app but isn't.

Note these are the **backend** domain with `/jobTracking`, not the Vercel one.

## 4. Frontend (Vercel)

- [ ] Set `VITE_API_BASE_URL` = `https://<API-DOMAIN>/jobTracking` and redeploy.
      Without it the deployed frontend still calls `localhost:8080` and every request fails.
- [ ] Confirm the frontend has applied the two breaking backend changes:
      rotated refresh tokens must be persisted, and `/api/jobs` + `/api/activity` now return a
      page object rather than an array. See `BACKEND_INTEGRATION.md` in the frontend repo.

## 5. Deploy

No `Dockerfile` or `railway.json` exists — Railway auto-detects Maven + Java and runs
`./mvnw package` then the resulting fat jar. `spring-boot-maven-plugin` is present, so the jar is
executable. If you'd rather pin the build, add a `Dockerfile`; auto-detection is fine to start.

```bash
git push          # Railway builds and deploys from the connected branch
```

## 6. Verify the first deploy

**Watch the startup logs for Flyway.** This is the step most likely to go wrong, because the
production database predates Flyway:

```
Schema history table ... does not exist yet
Creating Schema History table ... with baseline
Successfully baselined schema with version: 1
Migrating schema to version "2 - refresh token rotation"
Migrating schema to version "3 - user notification preferences"
Migrating schema to version "4 - job applications user created index"
Successfully applied 3 migrations
```

- ✅ *baselined at version 1* then *applied 3 migrations* — correct.
- ❌ If it tries to **run** V1 against a populated database, **stop**. It means baselining didn't
  happen and the schema is being rebuilt over live data.
- ❌ *Schema validation: missing column …* means a migration didn't apply. `ddl-auto: validate` is
  doing its job — fix the migration rather than switching back to `update`.

Then smoke-test against the live API:

```bash
API=https://<API-DOMAIN>/jobTracking

# 1. Reachable, and rejecting unauthenticated calls
curl -s -o /dev/null -w "%{http_code}\n" $API/api/jobs                    # expect 401

# 2. Signup works (writes to the real database)
curl -s -X POST $API/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"userFirstName":"Smoke","userLastName":"Test","email":"smoke@example.com","password":"SmokeTest1!"}'

# 3. Authenticated read with the returned token
curl -s $API/api/auth/me -H "Authorization: Bearer <token>"

# 4. CORS preflight from the real frontend origin
curl -s -i -X OPTIONS $API/api/jobs \
  -H "Origin: https://job-tracking-application-frontend.vercel.app" \
  -H "Access-Control-Request-Method: GET" | grep -i access-control-allow-origin
```

- [ ] Both OAuth2 flows in a real browser — the interactive consent screens have **never** been
      tested end to end, in any environment.
- [ ] One password reset, confirming the emailed link points at the Vercel domain (not localhost).
- [ ] Delete the smoke-test user afterwards.

---

## Known constraints

### Run exactly one instance

Two pieces of state live in memory:

- **`OAuthExchangeCodeStore`** — the OAuth2 callback stores a one-time code on whichever instance
  handled it; the frontend's `POST /api/auth/oauth/exchange` may then hit a *different* instance
  that has never seen it. With two instances roughly **half of all social logins fail**,
  intermittently and unreproducibly.
- **`RateLimiter`** — degrades gently: limits become per-instance rather than global.

Moving the exchange store to the database is a small change and the precondition for scaling out.

### No automated tests

Everything has been verified manually against a running server and real MySQL. There is no test
suite to catch a regression before deploy — review diffs accordingly.

### First reminder run

`REMINDERS_MAX_OVERDUE_DAYS` (default 7) stops the scheduler emailing a backlog of long-past
follow-ups on its first production run. If you seed or import historical data with old
`followUpDate` values and `reminderEnabled = true`, either keep this low or set
`REMINDERS_ENABLED=false` for the first hour.

---

## Rollback

- **Application** — redeploy the previous commit from Railway's deployment history.
- **Database** — Flyway migrations are **not** reversible here; there are no `undo` scripts. A bad
  migration needs a new forward migration, or a restore from a Railway backup. Take a backup
  before deploying any release that includes one.
- Rolling the app back while leaving a newer schema in place is usually fine — the migrations so
  far are additive — but `ddl-auto: validate` will refuse to start if an older build's entities no
  longer match. That is a deliberate stop, not a bug.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Deploy marked failed, health check times out | `PORT` not honoured. It's `${PORT:8080}` — don't override it. |
| OAuth2 redirects to `http://` and the provider rejects it | `forward-headers-strategy: framework` missing. It's set — check it wasn't overridden. |
| `redirect_uri_mismatch` from Google/GitHub | Callback not registered, or registered without `/jobTracking`. |
| CORS error in browser, works in curl | Origin mismatch: trailing slash, `http` vs `https`, or a Vercel preview hostname not in `CORS_ALLOWED_ORIGIN`. |
| Frontend calls `localhost:8080` | `VITE_API_BASE_URL` not set on Vercel. |
| Users logged out after ~15 minutes | Frontend not persisting the rotated `refreshToken`. |
| App won't start, complains about a placeholder | A required env var is unset — that's the intended fail-fast. |
| Empty 401/403 with no body on a valid route | An unhandled exception forwarded to `/error`, which sits behind the security filter. Look upstream in the logs. |
| No reminder emails | `REMINDERS_ENABLED`, the user's `emailNotifications` flag, or follow-ups older than the backfill window. |
