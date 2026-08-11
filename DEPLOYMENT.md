# Deployment

Going-live checklist for the JobTracker backend.

**Placeholders used throughout:**
- Backend API: `https://jobtrackingapplicationbackend.onrender.com` (Render)
- Frontend is already live at `https://job-tracking-application-frontend.vercel.app`

---

## What deploys where

| Piece | Platform | Notes |
|---|---|---|
| Backend (this repo) | **Render** | Spring Boot fat jar, Java 17 |
| MySQL 8 | **Aiven** | TLS mandatory; schema owned by Flyway |
| Frontend | Vercel | Already deployed |
| File storage | Cloudinary | Already configured (`igmsrg7x`) |
| Outbound email | Gmail SMTP | App Password, not the account password |

The API is served under the context path **`/jobTracking`**, so every URL includes it —
including the OAuth2 callbacks. Forgetting it is the most common setup mistake here.

---

## 0. Decide the Render plan first — it changes what works

Render's **free** web services **sleep after ~15 minutes without traffic**. Two consequences that
are not obvious and are specific to this app:

1. **Scheduled jobs do not run while asleep.** A sleeping process fires no `@Scheduled` work, so
   follow-up reminder emails and the nightly token cleanup simply don't happen. A follow-up can
   pass its window entirely and never be emailed. This isn't a misconfiguration you can fix in
   `application-prod.yaml` — the process isn't running.
2. **The first request after idle takes ~30–60s** while the service cold-starts. To anyone using
   the site that reads as "the app is broken", and it will hit the Vercel frontend's very first
   API call.

| Option | Effect |
|---|---|
| **Render paid instance** (always on) | Everything works as designed. The straightforward fix. |
| **Free + external pinger** (cron-job.org etc. hitting the API every 10 min) | Keeps it awake, so schedulers mostly run and cold starts mostly disappear. A workaround, not a guarantee — and it burns free-tier hours. |
| **Free, accept it** | Reminders become unreliable and cleanup rarely runs. Fine for a demo, not for real users relying on reminder email. Set `REMINDERS_ENABLED=false` rather than shipping a half-working feature. |

Decide this before step 1 — it determines whether the reminder feature should be enabled at all.

## 1. Before the first deploy

- [ ] **Generate a production `JWT_SECRET`** — `openssl rand -base64 48`.
      Do **not** reuse the development secret. Rotating it later logs every user out, so do it now.
- [ ] Create the MySQL service on **Aiven** and note the Service URI.
- [ ] Confirm `local-secrets.properties` is gitignored (it is) — production reads env vars only.
- [ ] Decide instance count: **start at 1**. See [Known constraints](#known-constraints).

## 2. Environment variables (Render)

Set **all** of these. Every secret is deliberately declared without a fallback, so a missing one
**fails the app at startup** rather than silently booting with a placeholder key.

### Required — app will not start without them

| Variable | Value / where it comes from |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | Aiven MySQL, as a JDBC URL — see the note below |
| `DATABASE_USERNAME` | Aiven (`avnadmin` by default) |
| `DATABASE_PASSWORD` | Aiven |
| `JWT_SECRET` | the value generated in step 1 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google Cloud Console |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub OAuth App |
| `GMAIL_USER` | `jobjugglerio@gmail.com` |
| `GMAIL_APP_PASSWORD` | Google App Password (16 letters, spaces stripped) |
| `CLOUDINARY_CLOUD_NAME` | `igmsrg7x` |
| `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | Cloudinary dashboard |

> **`DATABASE_URL` must be a JDBC URL, and Aiven requires TLS.**
>
> Aiven shows a *Service URI* like
> `mysql://avnadmin:PASSWORD@mysql-xxxx.aivencloud.com:12345/defaultdb?ssl-mode=REQUIRED`.
> The driver will not accept that form. Convert it to:
>
> ```
> jdbc:mysql://mysql-xxxx.aivencloud.com:12345/defaultdb?sslMode=REQUIRED&serverTimezone=UTC
> ```
>
> and put the credentials in `DATABASE_USERNAME` / `DATABASE_PASSWORD` separately.
>
> Three things that catch people out:
> - **The port is not 3306.** Aiven assigns one per service.
> - **The database is `defaultdb`**, not `jobtracking`, unless you created another.
> - **`sslMode=REQUIRED`, not `useSSL=true`.** Aiven refuses plaintext connections outright, and
>   `useSSL` is the deprecated spelling. `REQUIRED` encrypts without verifying the server
>   certificate; for verification use `VERIFY_CA` and ship Aiven's CA cert, which needs a
>   truststore on the Render instance — not worth it for launch.
> - `serverTimezone=UTC` must stay. The whole codebase assumes it; changing it shifts every
>   stored `LocalDateTime`.

### Optional — sensible defaults already applied

| Variable | Default | When to override |
|---|---|---|
| `PORT` | injected by Render | never set manually |
| `CORS_ALLOWED_ORIGIN` | the Vercel production URL | to add preview URLs — comma-separated |
| `OAUTH2_REDIRECT_URI` | `…vercel.app/oauth2/redirect` | custom domain |
| `PASSWORD_RESET_REDIRECT_URI` | `…vercel.app/reset-password` | custom domain |
| `REMINDERS_ENABLED` | `true` | `false` to silence reminder email |
| `REMINDERS_MAX_OVERDUE_DAYS` | `7` | backfill guard window |
| `CLEANUP_ENABLED` | `true` | `false` to pause token cleanup |
| `DB_POOL_SIZE` | `5` | raise only if Aiven's plan allows; the free tier caps total connections at ~20 including your own DBeaver session |

## 3. OAuth2 provider setup

**Do this before announcing the launch** — social login fails immediately without it, because
both providers currently only know the localhost callbacks.

- [ ] **Google** — [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services →
      Credentials → your OAuth client → **Authorised redirect URIs**, add:
      ```
      https://jobtrackingapplicationbackend.onrender.com/jobTracking/login/oauth2/code/google
      ```
- [ ] **GitHub** — [github.com/settings/developers](https://github.com/settings/developers) → your
      OAuth App → **Authorization callback URL**:
      ```
      https://jobtrackingapplicationbackend.onrender.com/jobTracking/login/oauth2/code/github
      ```
      GitHub allows only one callback URL per app. If you want localhost to keep working,
      register a second OAuth App for development.
- [ ] **Google OAuth consent screen** is likely still in *Testing*, which limits sign-in to
      explicitly listed test users. **Publish it** before real users arrive, or everyone else sees
      Google's "access blocked" screen — which looks like a bug in your app but isn't.

Note these are the **backend** domain with `/jobTracking`, not the Vercel one.

## 4. Frontend (Vercel)

- [ ] Set `VITE_API_BASE_URL` = `https://jobtrackingapplicationbackend.onrender.com/jobTracking` and redeploy.
      Without it the deployed frontend still calls `localhost:8080` and every request fails.
- [ ] Confirm the frontend has applied the two breaking backend changes:
      rotated refresh tokens must be persisted, and `/api/jobs` + `/api/activity` now return a
      page object rather than an array. See `BACKEND_INTEGRATION.md` in the frontend repo.

## 5. Deploy to Render

Create a **Web Service** pointed at this repo.

**Render has no native Java runtime**, so it builds from the `Dockerfile` in this repo. There are
no build or start commands to configure — the Dockerfile owns both.

| Setting | Value |
|---|---|
| Runtime / Environment | **Docker** |
| Build & start commands | leave blank — the Dockerfile defines them |
| Health check path | `/jobTracking/actuator/health` |
| Instances | **1** (see Known constraints) |

> If the service was created before the Dockerfile existed, the first build fails with
> `failed to read dockerfile: open Dockerfile: no such file or directory`. Push the Dockerfile and
> redeploy — no service setting needs changing.

The build is multi-stage: Maven + JDK 17 compiles the fat jar, then a JRE-only image runs it as a
non-root user. Two details worth knowing:

- **Dependencies resolve in their own layer**, keyed on `pom.xml` alone, so source-only changes
  redeploy in well under a minute rather than re-downloading every dependency.
- **Heap is sized with `-XX:MaxRAMPercentage=75`** rather than a fixed `-Xmx`, so the same image is
  correct on Render's 512 MB free tier and on a larger paid plan with no edit.

`.dockerignore` excludes `local-secrets.properties` and `prod-secrets.properties`. **Docker does not
read `.gitignore`** — without that exclusion, `COPY` would bake real credentials into an image
layer, where they persist even if a later layer deletes the file.

`-DskipTests` is honest today only because there is no test suite; remove it from the Dockerfile
the moment one exists.

> **Note the `/jobTracking` prefix on the health path** — the context path applies to actuator too.
> `/actuator/health` without it returns 404 and every check fails.
>
> `/actuator/health` is the only actuator endpoint exposed and the only one permitted
> unauthenticated; `env`, `beans`, `mappings`, `heapdump` and the rest all return 401. It must be
> public because Render probes it before any token exists, and Render treats a non-2xx as
> unhealthy — requiring auth there would restart the service in a loop.
>
> Verified behaviour: `200 {"status":"UP"}` normally, and **`503 {"status":"DOWN"}` when the
> database is unreachable** even though the process is running. That second case is the reason to
> use it instead of a port check — Render restarts a service that is up but cannot reach Aiven,
> rather than leaving it serving errors. Details are suppressed (`show-details: never`) so the
> response never names the database or its state to an anonymous caller.

The JDK is pinned to 17 in the Dockerfile to match `<java.version>` in `pom.xml`, so no
`JAVA_VERSION` variable is needed and a base-image change cannot silently move it.

```bash
git push          # Render builds and deploys from the connected branch
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
API=https://jobtrackingapplicationbackend.onrender.com/jobTracking

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

- **Application** — redeploy the previous commit from Render's deploy history (Manual Deploy →
  pick an earlier commit).
- **Database** — Flyway migrations are **not** reversible here; there are no `undo` scripts. A bad
  migration needs a new forward migration, or a restore from an Aiven backup. **Confirm your Aiven
  plan actually retains backups before deploying any release containing a migration.**
- Rolling the app back while leaving a newer schema in place is usually fine — the migrations so
  far are additive — but `ddl-auto: validate` will refuse to start if an older build's entities no
  longer match. That is a deliberate stop, not a bug.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Build fails: `failed to read dockerfile` | Service predates the Dockerfile, or Runtime isn't set to Docker. |
| Deploy marked failed, health check times out | `PORT` not honoured. It's `${PORT:8080}` — don't override it. Also check the health check path includes `/jobTracking`. |
| First request of the day takes ~50s, then everything is fine | Render free tier cold start. See step 0. |
| Reminder emails never arrive in production | Render free tier sleeping — no process, no scheduler. See step 0. |
| `Communications link failure` / SSL errors from MySQL | `sslMode=REQUIRED` missing from the JDBC URL. Aiven refuses plaintext. |
| Intermittent `too many connections` | `DB_POOL_SIZE` too high for the Aiven plan, or a DBeaver session holding connections. |
| OAuth2 redirects to `http://` and the provider rejects it | `forward-headers-strategy: framework` missing. It's set — check it wasn't overridden. |
| `redirect_uri_mismatch` from Google/GitHub | Callback not registered, or registered without `/jobTracking`. |
| CORS error in browser, works in curl | Origin mismatch: trailing slash, `http` vs `https`, or a Vercel preview hostname not in `CORS_ALLOWED_ORIGIN`. |
| Frontend calls `localhost:8080` | `VITE_API_BASE_URL` not set on Vercel. |
| Users logged out after ~15 minutes | Frontend not persisting the rotated `refreshToken`. |
| App won't start, complains about a placeholder | A required env var is unset — that's the intended fail-fast. |
| Empty 401/403 with no body on a valid route | An unhandled exception forwarded to `/error`, which sits behind the security filter. Look upstream in the logs. |
| No reminder emails | `REMINDERS_ENABLED`, the user's `emailNotifications` flag, or follow-ups older than the backfill window. |
