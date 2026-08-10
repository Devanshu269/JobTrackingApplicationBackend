# Backend Feature Checklist

Mark items `[x]` as you complete them (manual, not auto-synced).

Scope has outgrown the original auth-only intent — it now covers local signup/login, Google + GitHub OAuth2, JWT + refresh tokens, password management, **and** the job-tracking domain (job applications, interview rounds, profile). Filename still says `AUTH_FEATURE_CHECKLIST.md`; rename it if that bothers you.

All paths below are relative to the `/jobTracking` context path. The frontend-facing version of this contract (request/response bodies, enum strings, filter params) lives in `JobTrackingApplicationFrontend/BACKEND_INTEGRATION.md` — keep the two in sync.

## Endpoints — full list

### Auth (`/api/auth`)
- [x] `POST /signup` — local signup, public
- [x] `POST /login` — local login, public
- [x] `GET /oauth2/authorization/google` — kicks off Google login (Spring Security built-in route, not a controller method)
- [x] `GET /oauth2/authorization/github` — kicks off GitHub login (same)
- [x] `GET /login/oauth2/code/google` — Google's callback (Spring Security built-in, public)
- [x] `GET /login/oauth2/code/github` — GitHub's callback (Spring Security built-in, public)
- [x] `POST /oauth/exchange` — swaps a one-time OAuth2 login code for real tokens, public
- [x] `GET /me` — current user's profile, **requires auth**
- [x] `POST /change-password` — current+new password, **requires auth**
- [x] `POST /forgot-password` — request a reset email, public
- [x] `POST /reset-password` — consume reset token + set new password, public
- [x] `POST /refresh` — new access token from a refresh token, public
- [x] `POST /logout` — deletes one refresh token row, public
- [x] `POST /logout-all` — deletes all refresh token rows for current user, **requires auth**

### Profile (`/api/users`) — all **require auth**
- [x] `PUT /me` — partial profile update (firstName/lastName/avatarUrl)
- [x] `PUT /me/default-resume` — set the reusable default resume URL
- [x] `DELETE /me/default-resume` — clear it (needs its own verb because the DTO is `@NotBlank`)

### Job applications (`/api/jobs`) — all **require auth**, all scoped to the caller
- [x] `GET /` — list, newest first, optional `?status=` `?priority=` `?jobType=` `?search=` filters
- [x] `GET /stats` — dashboard counts `{ total, byStatus }`, every Status key zero-filled
- [x] `GET /{jobId}` — one job
- [x] `POST /` — create, 201
- [x] `PUT /{jobId}` — full replace (not a patch)
- [x] `DELETE /{jobId}` — 204, cascades to rounds + AI results

### Files (`/api/files`) — all **require auth**
- [x] `POST /` — multipart upload (`file` + `purpose`), 201
- [x] `GET /{fileId}` — exchanges an opaque ref for a 5-minute signed download URL

### Activity log (`/api/activity`) — **requires auth**
- [x] `GET /` — recent audit events, newest first, `?limit=` defaults 20 / clamped 1–100

### Cross-job rounds (`/api/rounds`) — **requires auth**
- [x] `GET /upcoming` — every scheduled round across all the caller's jobs, soonest first, with company/role flattened in

### Interview rounds (`/api/jobs/{jobId}/rounds`) — all **require auth**, ownership via parent job
- [x] `GET /` — list, ordered by roundNumber asc
- [x] `GET /{roundId}` — one round
- [x] `POST /` — create, 201
- [x] `PUT /{roundId}` — full replace
- [x] `DELETE /{roundId}` — 204

## DTOs — code-complete

### Auth
- [x] SignupRequestDto — userFirstName, userLastName (3–20 each), email, password (8–64)
- [x] LoginRequestDto — email, password
- [x] ChangePasswordRequestDto — currentPassword (**no `@Size`** — it's an existing password being verified, and anyone created under the old 4–12 rule would otherwise be locked out of changing it), newPassword (8–64)
- [x] ForgotPasswordRequestDto — email
- [x] ResetPasswordRequestDto — token, newPassword (8–64)
- [x] OAuthExchangeRequestDto — code (for `POST /oauth/exchange`)
- [x] RefreshTokenRequestDto — refreshToken (for `POST /refresh` and `/logout`)
- [x] AuthResponseDto — `token` + `refreshToken` + `userId` (no tokenType, no embedded profile — frontend calls `/me` separately for that)
- [x] UserDto — userId, userFirstName, userLastName, email, avatarUrl, **provider** (added so frontend can gate "change password" UI to LOCAL-only accounts) — backs `GET /me`
- [x] ErrorResponseDto — timestamp, status, message, errors map

### Job domain
- [x] UpdateProfileRequestDto — firstName/lastName/avatarUrl, all optional (no `@NotBlank`) — now wired to `PUT /api/users/me` as a genuine partial update
- [x] JobApplicationRequestDto — used by both POST and PUT. Required: companyName, jobRole, status. Optional: priority, **jobType**, jobUrl, location, salaryRange, recruiter{Name,Email,Phone}, resumeUrl, coverLetterUrl, notes, appliedDate, followUpDate, reminderEnabled
- [x] JobApplicationResponseDto — the request fields plus jobId, createdAt, updatedAt. **Deliberately never returns the entity** — serializing `JobApplication` directly would drag in `user`, which recurses back into `jobApplications` and exposes the password hash
- [x] InterviewRoundRequestDto — required roundNumber (min 1) + roundType; optional roundDate, interviewerName, notes, feedback, outcome
- [x] InterviewRoundResponseDto — plus jobRoundId, jobId, createdAt
- [x] UpcomingRoundResponseDto — round fields plus the parent job's `jobId`/`companyName`/`jobRole`. Separate from InterviewRoundResponseDto because this is read across *all* jobs at once, so the job context has to travel with each row
- [x] JobStatsResponseDto — total + `Map<Status, Long>`, zero-filled for every Status so the frontend never null-checks a bucket
- [x] UpdateDefaultResumeRequestDto — `resumeUrl`, `@NotBlank`. Now wired to `PUT /api/users/me/default-resume` after adding the missing `User.defaultResumeUrl` field it depended on

## Password policy — resolved 2026-08-09
- [x] Raised from `@Size(min = 4, max = 12)` to **`min = 8, max = 64`** on SignupRequestDto, ChangePasswordRequestDto and ResetPasswordRequestDto. The old 12-char ceiling blocked passphrases and password managers for no security benefit — the stored value is a fixed-length bcrypt hash, so a longer max costs nothing in the schema
- [x] `currentPassword` deliberately left with no `@Size` (see the DTO note above)
- [ ] Frontend `src/lib/validation.js` still has no upper bound, so a 65+ char password passes client validation then 400s. Frontend-side fix

## JobType — code-complete, runtime-tested
- [x] New `JobType` enum (`REMOTE`/`HYBRID`/`ONSITE`) + nullable `job_type` column on `JobApplication`. Added because the frontend already renders remote/hybrid/on-site in `JobTable` and `KanbanBoard` but no backend field existed — the alternative was inferring it from the free-text `location`, which is fragile (`"Remote (Canada)"` vs `"Austin, TX"`)
- [x] Wired through JobApplicationRequestDto, JobApplicationResponseDto, JobUtils (both directions), and added as a `?jobType=` list filter alongside status/priority
- [x] UPPERCASE to match Status/Priority/Outcome (RoundType's PascalCase remains the odd one out)
- [x] Runtime-tested: all three values persist and round-trip, filter works alone and combined with `status`, PUT changes it, omitting it stores null

## Upcoming rounds — code-complete, runtime-tested
- [x] `GET /api/rounds/upcoming` in a new `RoundController`. **Why a separate controller:** `InterviewRoundController` is mapped under `/api/jobs/{jobId}/rounds` and always has a parent job to scope by; this query deliberately spans every job the caller owns, so there's no `jobId` to supply
- [x] `findUpcomingByUser` uses a `JOIN FETCH` on the parent job so mapping company/role doesn't fire a lazy select per row — the whole point of the endpoint was to avoid the frontend doing one request per job
- [x] **Ownership lives in the WHERE clause** (`j.user.userId = :userId`) rather than a `findOwnedJob` call — this is the one round query with no parent job to hang the check off
- [x] `roundDate >= :from` naturally excludes both past rounds and unscheduled (null-date) ones
- [x] Runtime-tested: correct ordering across two jobs created out of order, past round excluded, null-date round excluded, cross-user isolation verified with a second account, empty case returns `[]` not 404, 401 without a token

## Activity log — code-complete, runtime-tested
- [x] `activity_log` table + `ActivityLog` entity, written from `ActivityService` on job create/update/delete and round create
- [x] **No JPA relationships — `userId`/`jobId` are plain columns.** An audit log must outlive what it audits: a deleted job's history still has to render. A real FK would either block the delete (cascade is declared per-association, so a new one wouldn't be covered) or, with an inverse cascade, erase the very history the table exists to keep
- [x] `companyName`/`jobRole` are **snapshots** taken at write time, not joins — after the job row is gone there's nothing to join to. Side effect: renaming a company doesn't rewrite history, which is correct for an audit trail
- [x] **Old status captured BEFORE `jobUtils.applyToEntity()`** in `updateJob` — that call mutates the managed entity in place, so reading after it logs every transition as `X -> X`. Same class of bug as the `followUpDate` re-arm below
- [x] Written from the service layer, **not a JPA `@EntityListener`** — a listener fires on the already-mutated entity and can't tell a status change from a notes edit without `@PostLoad` snapshotting or Envers. The service knows the intent
- [x] Terminal statuses get their own actions (`OFFER_RECEIVED`/`REJECTED`) rather than a generic `STATUS_CHANGED`; action names match the frontend's existing `ACTIVITY_ACTIONS` map so the swap is one line there
- [x] `?limit` clamped 1–100 rather than rejected. Unlike `job_applications` this table only ever grows, so unbounded reads are never valid
- [x] Composite index on `(user_id, created_at)` — every read is "this user's rows, newest first"
- [x] Runtime-tested: full lifecycle (create → status change → notes-only edit → round → offer → delete) logged correctly with accurate `previousStatus`; history survives the job delete with snapshots intact; cross-user isolation; limit clamping at both ends
- [x] `JOB_DELETED` added to the frontend's `ACTIVITY_ACTIONS` map (verified in src/lib/activity.js 2026-08-10)

## Follow-up reminders — code-complete, dry-run tested (no live email sent)
- [x] `@EnableScheduling` via `SchedulingConfig`; `ReminderService` runs on `app.reminders.cron` (hourly default), batch-capped, and can be disabled with `REMINDERS_ENABLED=false`
- [x] Query selects `reminderEnabled = true AND followUpDate <= now AND reminderSentAt IS NULL AND status <> REJECTED`, with `JOIN FETCH` on the user because the scheduler needs the email outside any request-bound persistence context. It is the **one deliberately un-user-scoped query** in the repository
- [x] New `reminderSentAt` column for idempotency — without it every tick after the date passes re-sends
- [x] **Changing `followUpDate` clears `reminderSentAt`** so a rescheduled follow-up re-arms; the comparison happens before the field is overwritten in `applyToEntity`. Editing other fields correctly leaves the marker alone
- [x] **Deliberately NOT `@Transactional` over the loop.** One transaction would roll back sent-markers for emails that already left the server, and those can't be un-sent — the next tick would deliver duplicates. Each save commits individually, right after its send succeeds
- [x] Per-job try/catch so one bad address can't abort the batch; a failure leaves `reminderSentAt` null so the row retries next tick
- [x] Dry-run tested: query selects exactly the due row and excludes future / opt-out / rejected; marking sent drops it out; re-arm and no-false-re-arm both verified
- [ ] Not tested: actual SMTP delivery of a reminder (deliberate — outward-facing). The transport itself is proven by the password-reset flow
- [ ] A permanently-failing address retries forever — no attempt counter or dead-letter handling
- [x] Backfill guard: `app.reminders.max-overdue-days` (default 7) bounds the query below as well as above, so enabling reminders — or recovering from downtime — can't blast a stale backlog

## File upload — code-complete, runtime-tested against real Cloudinary
- [x] `POST /api/files` + `GET /api/files/{id}`, backed by Cloudinary (already in pom.xml; credentials now in `local-secrets.properties`, cloud name `igmsrg7x`)
- [x] **Avatars public, documents private.** Avatars must work in a bare `<img src>`, which can't send an Authorization header. Resumes/cover letters are PII, so they're uploaded as Cloudinary `type=authenticated` and only reachable via a signed URL — a public object's URL *is* its credential, and it leaks through Referer headers, history and forwarded links, permanently, since files are never deleted
- [x] Private files return an opaque `/api/files/{id}` rather than a URL; `GET` exchanges it for a 5-min signed `downloadUrl`. **Returns JSON rather than a 302 or a byte stream** because the client's Bearer token rides on axios, and a browser following a redirect or an `<a href>` would not send it
- [x] Uses `cloudinary.privateDownload(...)` with an explicit `expires_at`, **not** `cloudinary.url().signed(true)` — the latter produces a signature that never expires, which for a PII document is barely better than public. (First draft had this bug: it computed an expiry and never used it)
- [x] `FileTypeDetector` validates by **magic bytes**, not the client-declared Content-Type: `%PDF`, PNG header, JPEG, RIFF/WEBP, `PK\x03\x04` (docx/OOXML), OLE2 (legacy doc). Dependency-free rather than pulling in Tika, since the accepted set is small and fixed
- [x] Per-purpose caps in `FilePurpose` (5 MB docs / 2 MB avatars) plus outer `spring.servlet.multipart` limits, with a `MaxUploadSizeExceededException` handler so an oversized upload is a 413 with a proper body instead of escaping to `/error`
- [x] `StoredFile` uses a plain `userId` column, not `@ManyToOne` — the row *is* the ownership check at download time. Fresh UUID key per upload, never overwritten, so a job keeps resolving the resume that was current when it was created
- [x] Original filename kept in the DB, **not** in the storage key — keeps user-supplied text out of the object path (encoding/traversal surface) at no cost, since it's returned on the exchange
- [x] `parsePurpose` converts an unknown value to `InvalidFileException` rather than letting `valueOf`'s `IllegalArgumentException` escape to `/error` and return a bodyless 401 — the same trap as the enum-binding bug earlier
- [x] Runtime-tested end-to-end: real PDF and PNG uploaded to Cloudinary; signed URL fetches the PDF (`%PDF` header verified); **unsigned public URL → 404 and unsigned authenticated URL → 401**, while the avatar's public URL → 200; `.txt` renamed `.pdf` → 400; PNG as resume → 400; bad purpose → 400; 7 MB → 413; no token → 401; another user's fileId → 404
- [ ] Signed-URL *expiry* not directly observed (would need a 5-minute wait) — the `expires_at` parameter is present and Cloudinary enforces it
- [ ] Nothing ever deletes Cloudinary objects. Deliberate (immutability), but means storage grows forever and orphans accumulate when a job is deleted

## Schema migrations (Flyway) — replaces ddl-auto, runtime-verified
- [x] `spring-boot-starter-flyway` + `flyway-mysql`; migrations in `src/main/resources/db/migration`
- [x] `V1__baseline_schema.sql` — the schema as it existed before Flyway, generated with `mysqldump --no-data`. Existing databases are stamped at V1 by `baseline-on-migrate` without running it; a brand-new database builds from it
- [x] `V2__refresh_token_rotation.sql` — codifies the family/rotation columns, including the nullable-add → backfill → NOT NULL ordering that `ddl-auto` cannot do
- [x] **`ddl-auto` changed from `update` to `validate`.** Flyway owns the schema; Hibernate now only checks that entities match and fails fast if they don't, instead of silently "fixing" things
- [x] Verified end-to-end by reverting the local schema to its pre-rotation state and letting Flyway do it for real: baselined at V1, applied V2, backfilled all 16 pre-existing rows into 16 distinct families with `family_created_at` reconstructed as `expires_at - 7 days`, then Hibernate validated clean and the app started
- [ ] Railway/production has never run Flyway. Its first deploy will baseline at V1 and apply V2 the same way — but that has not been observed, only reasoned about. Watch the first deploy's logs for `Successfully applied 1 migration`

## Refresh token rotation — code-complete, runtime-tested
- [x] Each refresh **consumes** the presented token and issues a successor in the same family. Fixes the original complaint (an actively-used session died at exactly 7 days) and shortens a stolen token's useful life to "until either party next refreshes"
- [x] `RefreshToken` gained `familyId` (one rotation chain = one login session), `familyCreatedAt` (carried forward unchanged), and `rotatedAt`
- [x] **Reuse detection.** A replayed already-consumed token means either a race or a theft, and the two are indistinguishable. Beyond a 30s grace window the conservative reading wins: `revokeFamily` kills every token in the chain, logging out all devices on that session
- [x] **30s grace window** for the benign case — two browser tabs refreshing at the same instant fails only that call. The frontend memoises its refresh promise so same-tab races are already impossible; this covers cross-tab and lost-response retries
- [x] **30-day absolute cap** (`jwt.refresh-absolute-expiration`) measured from the original login, so sliding expiry can't produce an immortal session. Reaching it revokes the family with a distinct message (*"Session expired. Please log in again."*)
- [x] **`@Transactional(noRollbackFor = InvalidRefreshTokenException.class)` is load-bearing.** Both revoke-then-throw paths were silently no-ops without it: the unchecked exception rolled back the very revocation just performed, so a replayed token was rejected while its family stayed alive. Caught by test — the first run showed the live token still working after a detected replay
- [x] Runtime-tested: rotation returns a new token each time; the old one 401s; replay inside grace leaves the session alive; replay outside grace revokes all rows including the live unconsumed one; 31-day-old session rejected, 29-day-old accepted
- [ ] **Breaking change for clients: `POST /api/auth/refresh` now returns a non-null `refreshToken` that MUST be stored.** The previous contract returned null and told clients to keep reusing the original. A client that ignores the new value will 401 on its *next* refresh, because the token it kept is now revoked

## Pre-launch hardening — code-complete, runtime-tested
- [x] **Reminder backfill guard.** `findDueReminders` now has a lower bound (`followUpDate >= now - max-overdue-days`, default 7). Verified: a 2-day-overdue follow-up sends, a 30-day-stale one doesn't — without the guard *both* did. This was the only failure mode here that reaches real inboxes
- [x] **Rate limiting on `/forgot-password`**, two independent layers:
  - Per-IP sliding window (`RateLimiter`, 5 per 15 min) → **429**. Keyed on IP rather than email deliberately: an email-keyed 429 would confirm which addresses are registered
  - Per-account cooldown (`app.password-reset.resend-cooldown-minutes`, default 2) enforced in `AuthService` and **silent** — returns the same generic 200, because surfacing it would be exactly the enumeration oracle the endpoint was designed to avoid. Verified: 3 requests from 3 different IPs → 3× 200, but only 1 token row created
  - Verified non-enumerating: registered and unregistered emails return byte-identical status and body
- [x] **`X-Forwarded-For` parsed from the RIGHT, not the left.** First implementation took the leftmost entry — the intuitive choice — which let anyone reset their own rate limit by sending a forged header (confirmed by test). Proxies *append*, so the rightmost value is the one your own proxy observed; everything left of it is client-supplied
- [x] `PasswordResetToken` gained `createdAt` (`@CreatedDate`) to back the cooldown query
- [x] **`TokenCleanupService`** — daily 03:30, purges expired `refresh_tokens` and `password_reset_tokens`, and evicts stale in-memory rate-limiter keys. `@Transactional` is load-bearing: both are derived `deleteBy` methods, the trap that has produced misleading empty 403s twice in this codebase. Verified live by temporarily running it every 30s — deleted all 3 expired rows, left both live ones, no exception
- [x] `RateLimiter` is process-local by design; two instances would double the effective allowance. Fine for a single deployment, swap for Redis/Bucket4j if it ever scales out

## JwtUtil — done
- [x] @Value-injected secret + expiration (access: 15 min via jwt.expiration)
- [x] SecretKey built once in @PostConstruct init()
- [x] generateToken(String email)
- [x] extractEmail(String token)
- [x] validateToken(String token) — catches parse/verify exceptions, returns false

## Refresh token design — code-complete, runtime-tested
- [x] RefreshToken entity/table (`refresh_tokens`): random UUID value, deviceInfo (from User-Agent), expiresAt, revoked
- [x] Chose per-session table over a single column on User — deliberately supports multi-device concurrent login
- [x] jwt.refresh-expiration = 7 days
- [x] RefreshTokenRepository — lookup by token, delete-all-by-user (logout-all + used again by reset-password to kill sessions)
- [x] `deleteByExpiresAtBefore` now called by `TokenCleanupService` (daily 03:30)

## AuthService — code-complete, runtime-tested
- [x] `signup(dto, deviceInfo)` — email uniqueness check, hash password, provider=LOCAL, issue tokens
- [x] `login(dto, deviceInfo)` — provider check before password check, generic InvalidCredentialsException on both email-not-found and wrong-password (no enumeration)
- [x] `handleOAuth2Login(email, firstName, lastName, avatarUrl, provider, deviceInfo)` — shared by Google + GitHub. Find-or-create by email; if found, blocks cross-provider login with `WrongAuthProviderException` (deliberate design decision — no silent account linking)
- [x] `changePassword(authentication, dto)` — blocks non-LOCAL providers (no password to check), verifies currentPassword, re-encodes new one
- [x] `forgotPassword(email)` — silently no-ops if email not found or provider != LOCAL (anti-enumeration); otherwise creates a `PasswordResetToken` and emails the link
- [x] `resetPassword(token, newPassword)` `@Transactional` — validates token not used/expired, updates password, marks token used, **deletes all refresh tokens for that user** (kills every existing session on reset)
- [x] `exchangeOAuthCode(code)` — consumes a one-time code from `OAuthExchangeCodeStore`, throws `InvalidExchangeCodeException` if missing/expired/reused
- [x] `refreshAccessToken(refreshTokenValue)` — rejects missing/expired/revoked tokens and deactivated users; refresh token itself NOT rotated
- [x] `createRefreshToken(user, deviceInfo)` — shared by every flow that issues tokens
- [x] `logoutUser(refreshTokenValue)` `@Transactional` — deletes the one matching row
- [x] `logoutFromAllDeviceOfUser(authentication)` `@Transactional` — deletes all rows for that user

## OAuth2 (Google + GitHub) — code-complete, runtime-tested end-to-end
- [x] Google Cloud Console OAuth client created (jobjugglerio@gmail.com), redirect URI `http://localhost:8080/jobTracking/login/oauth2/code/google`
- [x] GitHub OAuth App created, callback URL `http://localhost:8080/jobTracking/login/oauth2/code/github`
- [x] Client IDs/secrets in `local-secrets.properties` (gitignored), referenced via env-var placeholders in `application.yaml`
- [x] `OAuth2LoginSuccessHandler` (`security/`) — implements `AuthenticationSuccessHandler`
  - [x] Derives provider from `((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId()` — not hardcoded
  - [x] Google attributes: `given_name`/`family_name`/`picture`. GitHub attributes: single `name` field (falls back to `login` username if blank), `avatar_url`, `lastName` left null (GitHub has no last-name concept)
  - [x] Null-email guard before calling `handleOAuth2Login`
  - [x] try/catch around the whole body — exceptions (e.g. `WrongAuthProviderException` on provider conflict) redirect to frontend with `?error=...` (URL-encoded) instead of propagating into an unhandled 500/Whitelabel page
  - [x] Logs failures via SLF4J before redirecting, so real bugs aren't silently swallowed
- [x] `CustomOAuth2UserService` (`security/`) — extends `DefaultOAuth2UserService`. GitHub keeps email private by default, so if `email` comes back null on the `github` registration, it makes one extra call to `https://api.github.com/user/emails` (via `RestClient`, using the same access token) and merges the primary+verified email into the attributes map
- [x] `OAuthExchangeCodeStore` (`security/`) — in-memory `ConcurrentHashMap`, 60s TTL, single-use `consume()`. **Why it exists:** OAuth2 login is a full-page browser redirect, not a fetch/XHR call, so there's no response body to hand tokens back through — only the URL. Putting the real JWT/refresh token in a URL is a real risk (browser history, server/proxy access logs, `Referer` leakage to third parties). Instead the success handler hands back a random one-time code; the frontend immediately calls `POST /oauth/exchange` to swap it for the real tokens via a normal JSON response body, same as any other endpoint
- [x] `SecurityConfig`: `.oauth2Login(...)` wired with `.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))` and `.successHandler(oAuth2LoginSuccessHandler)`; `/oauth2/**` and `/login/oauth2/**` explicitly `permitAll()`

## `GET /me` — code-complete, runtime-tested
- [x] `AuthUtils.toUserDto(User)` maps entity → `UserDto` (including `provider`)
- [x] Controller reads `(User) authentication.getPrincipal()` — set by the existing `JwtAuthenticationFilter`, no new auth machinery needed
- [x] Explicitly `.authenticated()` in `SecurityConfig` (falls under `/api/auth/**`, which is `permitAll()` by default — every new endpoint that needs auth must be listed there, easy to forget)

## Change password — code-complete, runtime-tested
- [x] `POST /change-password`, authenticated, current+new password only (no confirm-password field — that check belongs on the frontend form, backend only needs the final value)
- [x] Blocks OAuth-provider accounts (no password to verify)
- [x] Verified via curl: wrong current password → 401; correct change → 204; old password stops working; new password works

## Forgot / reset password — code-complete, runtime-tested (including real email delivery)
- [x] Gmail SMTP configured: App Password for `jobjugglerio@gmail.com` in `local-secrets.properties` (`GMAIL_USER`/`GMAIL_APP_PASSWORD`) — **not** the Google account password, a separate SMTP-only credential from myaccount.google.com/apppasswords
- [x] `PasswordResetToken` entity/table (`password_reset_tokens`): random UUID token, user FK, expiresAt (30 min, `app.password-reset.token-expiration-minutes`), used flag
- [x] `EmailService` (`service/`) — thin wrapper around `JavaMailSender`, `sendPasswordResetEmail(to, resetLink)`
- [x] `forgotPassword`: **generic response regardless of outcome** — same 200 whether the email exists, doesn't exist, or belongs to an OAuth-provider account. Deliberate anti-enumeration design (discussed explicitly — don't let the endpoint reveal which emails have accounts)
- [x] `resetPassword` is `@Transactional` (calls derived `deleteByUser`, which needs it) — single-use token, and a successful reset deletes all that user's refresh tokens (kills every existing session)
- [x] Verified via curl + direct DB checks: non-existent email → same generic 200, no row created; real LOCAL user → row created, real email sent (no SMTP exception thrown); garbage token → 401; real token → 204; token reused → 401; login with new password → 200; old refresh tokens confirmed gone from DB after reset
- [x] Frontend `/reset-password` page — built (src/pages/ResetPasswordPage.jsx). Original spec, kept for reference: reads `token` off the query string (the email link points to `app.password-reset.redirect-uri` + `?token=...`), shows new-password + confirm-password (confirm checked client-side only, same as change-password), submits `{token, newPassword}` to `POST /reset-password`, redirects to login on 204. Should also handle someone landing on `/reset-password` with no `token` in the URL at all (direct navigation, not via the email link) — show a message pointing back to forgot-password instead of a broken form.

## AuthController — code-complete, runtime-tested
- [x] All endpoints listed at top of this file are wired to `AuthService`, no business logic in the controller layer itself

## Job applications — code-complete, runtime-tested
- [x] `JobApplicationService` — list/get/create/update/delete/stats, plus the shared `findOwnedJob(user, jobId)` helper
- [x] **Ownership model (the important bit):** every read and write path funnels through `findOwnedJob`, which queries `findByJobIdAndUser_UserId(jobId, userId)` — the two conditions together, never "fetch by id then check owner". Misses throw `ResourceNotFoundException` → **404, never 403**, because a 403 would confirm the id exists
- [x] List filtering via `JpaSpecificationExecutor` — optional `status`, `priority`, and `search` (case-insensitive `LIKE` on companyName OR jobRole). The user-id predicate is added unconditionally, before any optional filter, so no combination of query params can widen the result set beyond the caller's own rows
- [x] Sorted newest-first (`createdAt DESC`)
- [x] `GET /api/jobs/stats` — one grouped `COUNT` query via an interface projection (`StatusCount`), then zero-filled across `Status.values()` in an `EnumMap`
- [x] `JobUtils` (`Utils/`) — shared `applyToEntity` used by BOTH create and update so the two can't drift when a field is added; plus entity→ResponseDto mapping
- [x] `reminderEnabled` defaulted to `false` in the mapper — the column is `NOT NULL` and the field is optional in the request, so a null would blow up the insert
- [x] Added `@OneToMany(cascade = ALL, orphanRemoval = true)` from `JobApplication` to `interviewRounds` and `aiResults`. **Why:** without it, deleting a job that has rounds fails on the child FK — mirrors the existing `User -> jobApplications` cascade
- [x] Runtime-tested: create/list/get/update/delete, all three filters, combined filters, stats zero-fill, 400 validation, 404 on unknown id, 401 without token, cascade delete (job with 2 rounds → both gone)

## Interview rounds — code-complete, runtime-tested
- [x] `InterviewRoundService` — nested under a job at `/api/jobs/{jobId}/rounds`
- [x] Ownership reuses `JobApplicationService.findOwnedJob` first, so a round under someone else's job 404s before any round row is read; the round lookup itself is then also scoped by `jobId`, so a round id can't be reached through a job you happen to own
- [x] Runtime-tested: create/list (ordered by roundNumber)/update/delete, `roundNumber` min-1 validation, and cross-user 404 on both list and create

## Profile — code-complete, runtime-tested
- [x] `UserService.updateProfile` — genuine partial update: each field only applied when non-null, so the frontend can send just the one thing that changed
- [x] Returns the same `UserDto` shape as `GET /api/auth/me`, so the frontend can reuse one response handler
- [x] Runtime-tested: sending only `firstName` updated it and left `lastName`/`avatarUrl` untouched
- [x] Added `User.defaultResumeUrl` (nullable) — the field `UpdateDefaultResumeRequestDto` had always assumed existed but which was never actually on the entity. Exposed on `UserDto` so the frontend can prefill a new job application's `resumeUrl`
- [x] `PUT /me/default-resume` to set it, `DELETE /me/default-resume` to clear. **Why two endpoints:** the DTO's `resumeUrl` is `@NotBlank`, so there's no way to express "remove it" through the PUT without weakening validation — removal gets its own verb rather than accepting empty strings. The DELETE returns the updated `UserDto` rather than 204, so the client can refresh local state from the response
- [x] Runtime-tested: null before set → persists after PUT → visible on `/me` → blank value 400s → DELETE nulls it → 401 without a token
- [x] Confirmed `ddl-auto: update` DID add the brand-new `default_resume_url` column on restart — consistent with the gotcha that it reliably *adds* but not *alters*

## Cross-user isolation — explicitly tested with a second account
- [x] Created a separate "attacker" user and confirmed against the victim's job: list returns `[]`, GET/PUT/DELETE all 404, `/stats` reads all-zero, and the victim's row was verified intact afterward
- [x] Same for rounds: listing and creating under the victim's job both 404

## Security — code-complete, runtime-tested
- [x] SecurityConfig: CSRF off, sessions STATELESS
- [x] `/api/auth/**` public EXCEPT `logout-all`, `me`, `change-password` explicitly carved out to require auth (see the `/me` note above — this list needs a new entry every time an authenticated endpoint is added under `/api/auth`)
- [x] `/oauth2/**`, `/login/oauth2/**` explicitly `permitAll()`
- [x] `/api/jobs/**` and `/api/users/**` both `.authenticated()`
- [x] Custom `authenticationEntryPoint` returns a clean `401` for unauthenticated requests to protected endpoints. **Why it was needed:** with `oauth2Login()` configured, Spring Security's default behavior redirects unauthenticated requests to `/login` (302) instead of 401 — fine for a traditional server-rendered login page, wrong for a JSON API where a frontend `fetch()` call would instead get redirected into trying to parse an HTML login page as JSON
- [x] CORSConfig.java — origin from CORS_ALLOWED_ORIGIN env var (not hardcoded)
- [x] JwtAuthenticationFilter — reads `Authorization: Bearer <token>`, validates via JwtUtil, sets the **User entity itself** as the Authentication principal, always calls `filterChain.doFilter(...)` regardless (doesn't block unauthenticated OAuth2/public requests)
- [x] Deliberately no UserDetailsService/AuthenticationManager — no role system yet, noted as a clean seam for later

## Error handling — code-complete
- [x] GlobalExceptionHandler (@RestControllerAdvice), all mapped to consistent ErrorResponseDto shape:
  - [x] EmailAlreadyExistsException → 409
  - [x] InvalidCredentialsException → 401
  - [x] WrongAuthProviderException → 401
  - [x] InvalidRefreshTokenException → 401
  - [x] InvalidExchangeCodeException → 401
  - [x] InvalidResetTokenException → 401
  - [x] ResourceNotFoundException → 404 (used by every job/round ownership miss)
  - [x] MethodArgumentNotValidException → 400 with per-field messages
  - [x] HttpMessageNotReadableException → 400 (bad enum string or malformed JSON in a body)
  - [x] MethodArgumentTypeMismatchException → 400 (bad enum/type in a query param or path variable)
- [x] **Fixed a bodyless-401 bug on bad input.** An invalid enum (`"status": "applied"`, `?jobType=BOGUS`) or a non-numeric path variable (`/api/jobs/abc`) used to escape to Spring's `/error` page, which sits behind the security filter chain, and came back as an empty **401** — the same `/error`-forwarding trap that produced misleading 403s earlier, just wearing a different status code now that a custom `authenticationEntryPoint` exists. Actively harmful because the frontend's axios interceptor treats 401 as "token expired" and fires a refresh, so a typo'd enum would trigger a spurious refresh. Both handlers now return a 400 naming the field and listing the accepted enum values

## Deferred to the Administration board (decided 2026-08-09)

These are the remaining gaps in User read/update coverage. Deliberately parked — none of them block the frontend, and each is entangled with the admin/roles system that doesn't exist yet.

- [ ] **Change email** — no endpoint today. Three things make this more than a simple `PUT`:
  1. **OAuth landmine:** `handleOAuth2Login` finds existing users *by email*. If a `GOOGLE` user changes their stored email, their next Google login matches nothing and **silently creates a second account**, orphaning the original and all its job applications. Email change must be blocked for non-`LOCAL` providers, exactly like change-password already is.
  2. Uniqueness check → 409, reusing `EmailAlreadyExistsException`.
  3. Ideally a verification email to the *new* address before committing the change (reuse the `PasswordResetToken` pattern — random token, short TTL, single-use).
- [ ] **`isActive` is half-built** — `login()` and `refreshAccessToken()` both already check it and reject deactivated users, but **nothing can set it to false** except a manual DB edit. Decide what it's for before building: self-service "deactivate my account", or an admin moderation tool? If self-service, note reactivation can't go through login (a deactivated user is rejected before ever getting a token), so it needs either a time-window auto-reactivate or an admin action. Also not currently exposed on `UserDto`.
- [ ] **Delete account** — no endpoint, and it would fail today: `User` cascades only to `jobApplications`, but `refresh_tokens` and `password_reset_tokens` also hold FKs to `users` with no JPA cascade and no DB-level `ON DELETE CASCADE`, so `userRepository.delete(user)` hits `ERROR 1451`. Confirmed by hitting exactly that when cleaning up test users via raw SQL. Fix by adding `@OneToMany(cascade = ALL, orphanRemoval = true)` for both collections on `User` first.
- [ ] **Roles/admin-moderator system** — no `UserDetailsService` or role column yet. Previously noted as a clean seam; it's now the prerequisite for the three items above.
- [ ] `createdAt` not exposed on `UserDto` — trivial to add if a profile page wants "Member since March 2026".

## Not done yet
- [ ] Pagination on `GET /api/jobs` — currently returns every matching row. Fine at current scale, will need `Pageable` once a user has hundreds of applications
- [ ] `PUT` endpoints are full-replace, not `PATCH` — omitting a field nulls it. Fine if the frontend always sends the whole object back (typical for an edit form), but a real `PATCH` would be friendlier for one-field updates like a drag-and-drop status change on a kanban board
- [ ] AI features — `job_ai_results` table and entity exist, no endpoints and no Gemini integration (`GEMINI_API_KEY` still a placeholder)
- [x] File upload — built, Cloudinary integrated (cloud `igmsrg7x`). See the File upload section above
- [ ] `JobApplicationRepository.findByUser_UserIdAndJobId` — old method returning `List` where at most one row can match (jobId is the PK). Superseded by `findByJobIdAndUser_UserId` returning `Optional`; the old one is now unused and can be deleted

## Known housekeeping (low priority)
- [ ] Rotate local MySQL root password (old value is in earlier git history; local dev DB only, not internet-exposed)
- [ ] This checklist kept in sync as work continues (manual, on your own cadence)

## Gotchas hit already (avoid re-tripping on these)
- `UrlBasedCorsConfigurationSource` in this Spring version uses `registerCorsConfiguration(String, CorsConfiguration)`, not `registerCorsConfigurationFor`.
- Watch IDE auto-import pulling in `org.apache.tomcat.util.net.openssl.ciphers.Authentication` instead of `org.springframework.security.core.Authentication` — same simple class name, wrong package, compiles-looking but wrong.
- Servlet stack (`spring-boot-starter-web`), not reactive — never import from `org.springframework.web.cors.reactive.*`.
- `ddl-auto: update` never drops/renames columns, and is also unreliable at *altering* an existing column's constraints (e.g. flipping `nullable=false` → `nullable=true` on an already-existing column did nothing on restart — had to run a manual `ALTER TABLE ... MODIFY COLUMN ... NULL` directly). It only reliably adds brand-new tables/columns.
- Any custom derived repository method starting with `deleteBy`/`removeBy` needs the calling service method annotated `@Transactional` — unlike `.save()`/`.findById()`, these don't get automatic transaction handling. Bit us again with `resetPassword()` calling `deleteByUser`.
- A thrown-but-unhandled exception anywhere in the request path can surface as a confusing empty `403` instead of `500` — Spring forwards failures to `/error`, which itself sits behind the security filter chain. If you see an empty 403 on a route that should be public, suspect an unhandled exception upstream, not an auth/CORS misconfiguration first.
- Google/GitHub's OAuth2 callback path must match `server.servlet.context-path` exactly — with `context-path: /jobTracking`, the registered redirect URI has to be `.../jobTracking/login/oauth2/code/{registrationId}`, not the bare path.
- With `oauth2Login()` configured, unauthenticated requests to protected endpoints get redirected to `/login` (302) by default instead of returning 401 — needed a custom `exceptionHandling().authenticationEntryPoint(...)` in `SecurityConfig` to get clean JSON-API-appropriate 401s.
- GitHub's `/user` endpoint often has `email: null` (private by default) — Spring Security's default `DefaultOAuth2UserService` does NOT automatically fall back to `/user/emails`; needed a custom `OAuth2UserService` to handle it.
- A Gmail **App Password** (myaccount.google.com/apppasswords) is a separate SMTP-only credential from the actual Google account password — generating one doesn't touch real login security, and it's unrelated to anything about GitHub.
- **JPA cascade is application-level, not database-level.** `@OneToMany(cascade = ALL)` makes `repository.delete(parent)` clean up children, but the actual MySQL FKs have no `ON DELETE CASCADE` — so a raw SQL `DELETE FROM users ...` in DBeaver/CLI still fails with `ERROR 1451` and you must delete children in FK order by hand. Also means a cascade only covers collections the entity actually declares (see the delete-my-account gap above).
- `ddl-auto: update` also never updates an existing **MySQL ENUM column's** allowed-value list when the Java enum changes. Renaming/adding an enum constant needs a manual `ALTER TABLE ... MODIFY COLUMN x ENUM(...)`. Hit this fixing the `Priority.HiGH` → `HIGH` typo on 2026-08-09 (table was empty, so no data migration was needed — would have been much worse later).
- Enum constant names are part of the public API — they're the literal strings sent over JSON. `RoundType` is PascalCase (`Technical`, `SystemDesign`) while `Status`/`Priority`/`Outcome` are UPPERCASE; easy to get wrong from the frontend.
- Never return JPA entities from a controller. Returning `JobApplication` directly serializes its `user`, which recurses back into `user.jobApplications` and also exposes the password hash. Every endpoint maps to a `*ResponseDto` instead.
- **Spring Boot 4 split autoconfiguration into per-technology modules.** Adding `flyway-core` alone puts the library on the classpath with nothing that runs it: no error, no log line, migrations simply never applied. You need `spring-boot-starter-flyway` (which pulls `spring-boot-flyway`). The same applies to `spring-boot-jackson`, `spring-boot-security`, `spring-boot-hibernate` and friends — a bare third-party jar is not enough in Boot 4. Symptom is "the feature just isn't there", not a failure.
- **IntelliJ does not pick up a new Maven dependency on restart.** The ↻ restart button reuses the cached module classpath, so a newly added dependency appears absent no matter how many times you restart — three identical startup failures in a row. Maven tool window → *Reload All Maven Projects*, then Stop and Run fresh. When a dependency "isn't taking effect", confirm from the CLI (`./mvnw spring-boot:run`) before debugging the config: that separates a wrong configuration from a stale IDE classpath in one step.
- **Revoke-then-throw inside `@Transactional` silently undoes the revoke.** An unchecked exception triggers rollback, so any "mark this compromised, then reject the request" logic loses the mark. Needs `noRollbackFor` (or a `REQUIRES_NEW` helper). This made refresh-token reuse detection a no-op until it was caught by testing the *effect* rather than the status code — the 401 looked correct the whole time.
- **`ddl-auto: update` cannot add a `NOT NULL` datetime column to a populated table.** MySQL backfills with `'0000-00-00'`, which strict mode rejects, so the ALTER fails and the column simply never appears — while the app starts anyway. Add it nullable, backfill, then `MODIFY ... NOT NULL`. Note a `NOT NULL` *varchar* column silently succeeds with empty-string backfill, which is arguably worse: `family_id` ended up identical (`''`) across every pre-existing row, putting them all in one "family".
- **`X-Forwarded-For` must be read from the right-hand end.** Proxies append, so the leftmost entry is whatever the client sent and is trivially forged; the rightmost is what your own proxy actually saw. Taking `split(',')[0]` — which looks correct — makes any IP-based rate limit bypassable with a made-up header.
- **`LocalDateTime` is stored shifted, and raw SQL against it is a trap.** The JDBC URL sets `serverTimezone=UTC`, so a `LocalDateTime` of `09:00` written from an IST machine lands in MySQL as `03:30`. It round-trips correctly through the API (write 09:00, read 09:00) and Hibernate converts both sides of a query consistently, so application code is fine. But hand-written SQL is not: `follow_up_date <= NOW()` compares a UTC column against a SYSTEM-zone `NOW()` and is off by the UTC offset — verified by a row 2 minutes in the *future* reporting as due. Use `UTC_TIMESTAMP()` in ad-hoc SQL, and expect DBeaver to display shifted times.
- Ownership checks must be part of the **query**, not a post-fetch `if`. `findByJobIdAndUser_UserId(jobId, userId)` makes "not yours" and "doesn't exist" the same code path, which is what lets every miss return an indistinguishable 404.
