# JobTracker — Backend

Spring Boot REST API for **Job Juggler**, a job-application tracker. Serves the React frontend in
the sibling repo `JobTrackingApplicationFrontend`.

**Stack:** Java 17 · Spring Boot 4.1 · Spring Security (JWT + OAuth2) · Spring Data JPA / Hibernate 7 ·
MySQL 8 · Flyway · Cloudinary · Gmail API · Maven

---

## Quickstart

**Prerequisites:** JDK 17, MySQL 8 running locally, and a `jobtracking` database (the JDBC URL
creates it if missing).

```bash
cp local-secrets.properties.example local-secrets.properties   # if present; otherwise see below
./mvnw spring-boot:run
```

The app starts on **`http://localhost:8080/jobTracking`** — note the `/jobTracking` context path,
which is part of every URL including the OAuth2 callbacks.

### Secrets

Real credentials live in **`local-secrets.properties`** at the project root. It is gitignored and
loaded automatically via `spring.config.import` in `application.yaml`. The tracked
`application.yaml` only ever holds safe placeholder fallbacks.

| Key | Needed for |
|---|---|
| `DB_PASSWORD` | MySQL |
| `JWT_SECRET` | signing access tokens |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google sign-in |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | GitHub sign-in |
| `GMAIL_OAUTH_CLIENT_ID` / `_CLIENT_SECRET` / `_REFRESH_TOKEN` | password-reset and reminder email |
| `CLOUDINARY_CLOUD_NAME` / `_API_KEY` / `_API_SECRET` | file upload |

**Email needs no configuration to run locally.** `EMAIL_PROVIDER` defaults to `log`, which prints
each message to the console instead of sending it — so a fresh clone cannot mail real people, and
you can copy a password-reset link straight out of the terminal to test the flow.

To send for real, set `EMAIL_PROVIDER=gmail` and the three `GMAIL_OAUTH_*` values. **Mail goes
over the Gmail REST API, not SMTP**, for two reasons: many hosts (Render included) block outbound
SMTP entirely, and Gmail/Yahoo/Outlook now enforce DMARC — no third-party relay can sign for
`gmail.com`, so relayed mail from a Gmail From address is spam-filed by design. Sending through
Google's own API is both unblocked and DMARC-aligned. See DEPLOYMENT.md §3b for how to mint the
refresh token.

### OAuth2 redirect URIs

Register these exactly, context path included, or the provider rejects the callback:

```
http://localhost:8080/jobTracking/login/oauth2/code/google
http://localhost:8080/jobTracking/login/oauth2/code/github
```

---

## API

The full contract — every endpoint with worked request/response examples, error shapes and enum
strings — lives in **[`../JobTrackingApplicationFrontend/BACKEND_INTEGRATION.md`](../JobTrackingApplicationFrontend/BACKEND_INTEGRATION.md)**.
That file is the source of truth; this section is only a map.

| Area | Base path |
|---|---|
| Auth (signup, login, OAuth2 exchange, refresh, logout, password reset) | `/api/auth` |
| Profile, default resume, notification preferences | `/api/users` |
| Job applications, stats, trend | `/api/jobs` |
| Interview rounds (nested) | `/api/jobs/{jobId}/rounds` |
| Upcoming rounds across all jobs | `/api/rounds/upcoming` |
| Activity log | `/api/activity` |
| Notification bell | `/api/notifications` |
| File upload / download | `/api/files` |

---

## Architecture

```
com.jobtracker/
├── config/      Cloudinary, JPA auditing, scheduling
├── controller/  HTTP only — no business logic
├── dto/         request/response shapes; entities are never serialised directly
├── enums/       Status, Priority, JobType, RoundType, Outcome, Provider, ActivityAction, FilePurpose
├── exception/   domain exceptions + GlobalExceptionHandler
├── model/       JPA entities
├── repository/  Spring Data interfaces
├── security/    JWT filter, SecurityConfig, OAuth2 handlers, rate limiter
└── service/     business logic
```

Four conventions worth knowing before changing anything:

**Ownership lives in the query, never a post-fetch check.** Every user-scoped lookup goes through
something like `findByJobIdAndUser_UserId(jobId, userId)`, so "not yours" and "doesn't exist" are
the same code path. Both return **404, never 403** — a 403 would confirm the id exists.

**Controllers never return entities.** Serialising `JobApplication` would drag in `user`, recurse
back into `jobApplications`, and expose the password hash. Everything maps to a `*ResponseDto`.

**The audit log holds no JPA relationships.** `activity_log.user_id` and `job_id` are plain columns
and company/role are snapshots, because an audit trail must outlive what it audits — a deleted
job's history still has to render.

**Schema changes go through Flyway, not `ddl-auto`.** See below.

---

## Database & migrations

Flyway owns the schema. `spring.jpa.hibernate.ddl-auto` is **`validate`** — Hibernate checks that
entities match and fails startup on a mismatch, rather than silently altering the database.

```
src/main/resources/db/migration/
├── V1__baseline_schema.sql                    pre-Flyway snapshot (existing DBs are stamped, not run)
├── V2__refresh_token_rotation.sql
├── V3__user_notification_preferences.sql
└── V4__job_applications_user_created_index.sql
```

To change the schema: **add a new `V{n}__description.sql`**, never edit an applied one, and never
rely on `ddl-auto`. Existing databases were baselined at V1 via `baseline-on-migrate`.

This replaced `ddl-auto: update` after it produced four distinct silent-drift incidents — most
sharply, adding a `NOT NULL DATETIME` column to a populated table fails outright (MySQL backfills
`'0000-00-00'`, strict mode rejects it) while the app starts anyway, and a `NOT NULL VARCHAR`
*succeeds* by backfilling every row with the empty string. `V2` documents both.

---

## Scheduled jobs

| Job | Default schedule | Disable with |
|---|---|---|
| Follow-up reminder email | hourly | `REMINDERS_ENABLED=false` |
| Expired token cleanup | daily 03:30 | `CLEANUP_ENABLED=false` |

Reminders honour each user's `emailNotifications` flag and skip follow-ups older than
`app.reminders.max-overdue-days` (default 7), so enabling the job can't mass-email a stale backlog.

---

## Deploying

See **[DEPLOYMENT.md](DEPLOYMENT.md)** — env vars, OAuth2 callback registration, first-deploy
Flyway expectations, smoke tests and rollback.

Production config lives in `application-prod.yaml`, activated with `SPRING_PROFILES_ACTIVE=prod`.
Every secret there is declared **without a fallback**, so a missing variable fails startup rather
than booting with a placeholder signing key.

## Testing

There is **no automated test suite**. Everything has been verified manually against a running
server and a real MySQL database — including cross-user isolation checks with a second account.
`AUTH_FEATURE_CHECKLIST.md` records what was actually exercised versus what was only reasoned
about; the distinction is kept deliberately honest there.

Adding tests is the most valuable unclaimed work in this repo.

---

## Gotchas

Full list with reproductions in [`AUTH_FEATURE_CHECKLIST.md`](AUTH_FEATURE_CHECKLIST.md). The ones
that cost the most time:

- **An unhandled exception surfaces as a bodyless 401/403, not a 500.** Spring forwards failures to
  `/error`, which sits behind the security filter chain. If you see an empty error on a route that
  should work, suspect an unhandled exception upstream — not an auth misconfiguration.
- **Derived `deleteBy...` repository methods need `@Transactional` on the caller.** Unlike
  `save()`/`findById()` they get no automatic transaction and throw `TransactionRequiredException`.
- **Revoke-then-throw inside `@Transactional` silently undoes the revoke** unless you add
  `noRollbackFor`. This made refresh-token reuse detection a no-op while still returning a
  correct-looking 401.
- **`LocalDateTime` is stored UTC-shifted** (`serverTimezone=UTC`). It round-trips correctly through
  the API, but hand-written SQL comparing against `NOW()` is off by the UTC offset — use
  `UTC_TIMESTAMP()`.
- **Spring Boot 4 splits autoconfiguration into per-technology modules.** A bare third-party jar
  (e.g. `flyway-core`) puts the library on the classpath with nothing that runs it — no error, it
  just silently doesn't happen. Use the matching `spring-boot-starter-*`.
- **IntelliJ won't pick up a new Maven dependency on restart.** Reload the Maven project, then Stop
  and Run — the ↻ button reuses the cached classpath. When a dependency seems to have no effect,
  confirm from the CLI first.
