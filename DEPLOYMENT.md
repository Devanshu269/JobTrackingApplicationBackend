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
| **Free + external pinger** | Keeps it awake, so schedulers run and cold starts disappear. Setup below. |
| **Free, accept it** | Reminders become unreliable and cleanup rarely runs. Set `REMINDERS_ENABLED=false` rather than shipping a half-working feature. |

### Keeping a free instance awake (external pinger)

**This is configured outside the repo** — nothing in the codebase can prevent the sleep, because
Render's idle timer responds to *inbound* traffic. A self-ping from inside the app does not solve
it either: once the service is asleep its scheduler is asleep too, so it can never wake itself.
The ping has to come from somewhere else.

Use any free uptime monitor — [UptimeRobot](https://uptimerobot.com) or
[cron-job.org](https://cron-job.org):

| Setting | Value |
|---|---|
| URL | `https://jobtrackingapplicationbackend.onrender.com/jobTracking/actuator/health/liveness` |
| Method | `GET` |
| Interval | **every 10 minutes** (must be under Render's ~15 min idle window) |
| Expected status | `200` |

**Use `/health/liveness`, not `/health`.** The aggregate endpoint was measured on Render at
**503 DOWN after 135 seconds** — with the mail indicator already disabled, so blocked SMTP was not
the cause and that hypothesis is dead. Any monitor with a normal 30s timeout would record it as
permanently down and alert continuously. `liveness` checks no external dependency and answers in
milliseconds.

The honest trade: liveness reports only "the process is running". It cannot tell you the database
is reachable, and given the aggregate endpoint currently says DOWN, there is a real unresolved
problem here that a green liveness check will hide. Keeping the service awake and knowing it is
healthy are two different jobs; this does the first.

**Two caveats, worth knowing before relying on it:**

- **Free tier includes 750 instance-hours/month and a month is ~730 hours.** One always-awake
  service just fits, with no room for a second on the same account.
- It keeps the service warm; it does not make it resilient. A crash or a deploy still causes a
  cold start, and the pinger only shortens how long that lasts.

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

## 3b. Email (Gmail API)

Email does **not** go over SMTP. Render blocks outbound SMTP, so `JavaMailSender` hung ~45s per
send and delivered nothing — `forgot-password` timed out and reminder mail never arrived.

The replacement sends through Gmail's REST API over HTTPS. That fixes a second problem at the
same time: Gmail, Yahoo and Outlook now enforce DMARC, and Google publishes a policy telling
receivers to quarantine mail claiming to be from `@gmail.com` that Google did not send. No
third-party relay (Brevo, Resend, SendGrid) can sign for `gmail.com`, so relayed mail from a
Gmail From address is spam-filed by design. Sending through Google's own API is DMARC-aligned
and reaches the inbox — from a free account, with no domain to buy.

### One-time setup

1. **New Google Cloud project** — console.cloud.google.com → New Project. This project is named
   **`JobJugglerEmailService`**. Keep it **separate from the login OAuth project**: that consent
   screen is published and serving user sign-in, and `gmail.send` is a restricted scope that does
   not belong on it.

   > The console now calls this area **Google Auth Platform**. Test users and *Publish app* are
   > under **Audience**, scopes under **Data Access**, and the OAuth client under **Clients**.
   >
   > On **Branding**, leave *Application home page*, *Privacy policy* and *Terms of service*
   > empty. Filling them makes Google demand a matching entry under Authorised domains, which
   > takes a bare domain (no scheme, no path) that you must own and verify in Search Console —
   > impossible for a `*.vercel.app` subdomain, since Vercel owns that domain. Nobody but you
   > ever sees this consent screen, so the links serve no purpose here.
2. **Enable the API** — APIs & Services → Library → "Gmail API" → Enable.
3. **OAuth consent screen** — External. Add the scope
   `https://www.googleapis.com/auth/gmail.send`, and add the sending account as a test user.
   > **Then click Publish app, so the status reads "In production".** While it stays in
   > "Testing", Google expires refresh tokens after **7 days** — mail works for a week and then
   > stops with `invalid_grant`. Published-but-unverified is fine here: you are the only person
   > who ever grants consent, and the cost is one "Google hasn't verified this app" warning
   > (Advanced → Go to … ) during step 5.
4. **Create credentials** — Credentials → Create Credentials → OAuth client ID →
   **Web application**. Add `https://developers.google.com/oauthplayground` as an authorized
   redirect URI. Copy the client ID and secret.
5. **Mint the refresh token** — open
   [OAuth 2.0 Playground](https://developers.google.com/oauthplayground):
   - Gear icon → tick **Use your own OAuth credentials** → paste the client ID and secret
   - Select scope `https://www.googleapis.com/auth/gmail.send`
   - **Authorize APIs**, sign in as the sending account, accept the unverified-app warning
   - **Exchange authorization code for tokens** → copy the **refresh token**

   The refresh token is shown once. It does not expire (given step 3), but it is a credential:
   store it, don't paste it into a chat or a commit.

### Environment variables

| Key | Notes |
|---|---|
| `GMAIL_OAUTH_CLIENT_ID` | From step 4. Distinct from `GOOGLE_CLIENT_ID`, which is user login. |
| `GMAIL_OAUTH_CLIENT_SECRET` | From step 4. |
| `GMAIL_OAUTH_REFRESH_TOKEN` | From step 5. |
| `EMAIL_FROM_ADDRESS` | Must be the account that granted consent. |
| `EMAIL_REPLY_TO` | Where replies land. |
| `EMAIL_PROVIDER` | Defaults to `gmail` in prod, `log` locally. Optional. |

`GMAIL_USER` and `GMAIL_APP_PASSWORD` are obsolete — delete them. Nothing reads them, and the
Gmail App Password should be revoked in the Google account.

**A missing value fails the deploy at startup**, by design: `GmailApiEmailSender` validates its
config in its constructor and names the variable. The alternative is an app that boots healthy
and silently discards every password reset.

### Providers

Selected by `app.email.provider`:

| Value | Behaviour |
|---|---|
| `gmail` | Gmail REST API. The production transport. |
| `log` | Prints the email to the console, sends nothing. **The default**, so a fresh clone can't mail real people and local dev doesn't need credentials. Also how you copy a reset link out of the console while testing. |
| `brevo` | Brevo's HTTPS API. Kept for the day a real domain is bought — authenticate the domain in Brevo and send from `noreply@`, which removes the 500/day Gmail ceiling. Not usable with a `@gmail.com` From address, per the DMARC note above. |

Local development needs no email configuration at all. Set `EMAIL_PROVIDER=gmail` in
`local-secrets.properties` only when testing a real send.

### Verifying

After deploy, trigger a reset for an account you control and check the logs:

```
Email sent via Gmail API to d*******@gmail.com (subject: Reset your JobTracker password)
```

Failures are logged, never thrown — a mail outage must not roll back the business flow that
triggered it. So a silent absence of that line means look at the log, not at the response:
`invalid_grant` means the refresh token died (consent screen back in "Testing"?), `403` usually
means the scope is missing from the token.

Sending happens on a background executor, so the HTTP response returns immediately and the log
line appears a moment later on an `email-*` thread.

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
| Health check path | `/jobTracking/actuator/health/liveness` |
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

> **Use `/health/liveness`, not `/health`.** The aggregate `/actuator/health` endpoint hangs on
> Render — cause not established; it is not the database (connections are idle and healthy) and
> disabling the mail indicator did not fix it. `liveness` reports only whether the process is
> running, checks no external dependency, and responds in milliseconds, which is exactly what a
> platform health check should ask.
>
> The trade: you lose the database-connectivity signal from the platform probe. If you want that
> back, the external uptime pinger can watch a richer endpoint and alert separately.
>
> To diagnose the aggregate endpoint later, set `HEALTH_SHOW_DETAILS=always` in Render and call
> `/actuator/health` — it will name the failing component. Set it back to `never` afterwards.
>
> **Note the `/jobTracking` prefix** — the context path applies to actuator too. Without it the
> path 404s and every check fails.
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

### Free-tier sleep breaks scheduled jobs

Covered in step 0. In short: a sleeping process runs no `@Scheduled` work, so reminders and token
cleanup only fire when the app happens to be awake. The external pinger above keeps the scheduler
running.

> **The pinger fixes the scheduler, not email.** Render blocks outbound SMTP, so
> `JavaMailSender` times out and reminder mail is not delivered even on a fully awake instance —
> `forgot-password` shows the same 45s timeout. Until `EmailService` moves to an HTTP email
> provider (Resend, Brevo, SendGrid), an awake instance produces reminder attempts that fail
> rather than reminder emails. Token cleanup, which touches no mail, does start working.

### First reminder run

`REMINDERS_MAX_OVERDUE_DAYS` (default 7) stops the scheduler emailing a backlog of long-past
follow-ups on its first production run. If you seed or import historical data with old
`followUpDate` values and `reminderEnabled = true`, either keep this low or set
`REMINDERS_ENABLED=false` for the first hour.

---

## Database disappeared (`UnknownHostException`)

Symptom: nothing responds at all — not the API, not `/actuator/health/liveness` — and Render's
logs show the same stack trace repeating, ending in:

```
java.net.UnknownHostException: mysql-xxxx.aivencloud.com: Name or service not known
```

**This is DNS, not the database refusing the connection.** Aiven withdraws the hostname's DNS
record when a service is powered off or deleted. Confirm from anywhere:

```bash
dig +short mysql-xxxx.aivencloud.com   # empty  -> service is off/gone
dig +short aivencloud.com                            # answers -> Aiven's DNS is fine
```

### Why the whole app dies, not just database features

Flyway runs during startup. It cannot resolve the host, `flywayInitializer` fails, the Spring
context fails, the process exits, Render restarts it, and it fails again — a crash loop. There is
no live process to answer a health check, which is why even liveness goes dark.

That is correct behaviour, not a bug to fix. With `ddl-auto: validate` and migrations owning the
schema, booting without a database would only serve 500s while looking healthy. **It does mean an
uptime pinger cannot help here** — the pinger's value in this scenario is telling you it happened.

### Fixing it

Aiven console → find the service:

| State | Action |
|---|---|
| Powered off | Power on. Data is retained and the hostname returns, so Render's existing env vars keep working — nothing to change. |
| Deleted | Check Aiven's recovery window. Past it, the data is gone and the database must be recreated. |
| Trial expired | Powering on buys days, not a fix: expired trials get deleted after a grace period. Move to a free plan or add billing. |

**Check which plan it is.** A free plan (single node, ~5 GB) stays on. A 30-day trial ends, and
this recurs.

If it must be recreated, the host, port and password all change — update `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` in Render. Flyway then applies
V1–V4 to the empty database, so the schema rebuilds itself completely. Only the data is lost.
Cloudinary still holds uploaded files, but the rows pointing at them would be gone.

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
| **Service totally unreachable — even `/liveness` — and logs show `UnknownHostException: …aivencloud.com`** | **The Aiven service is powered off or deleted.** See "Database disappeared" below. Not a Render problem, and no pinger prevents it. |
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
