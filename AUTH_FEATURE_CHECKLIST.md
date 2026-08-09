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
- [ ] `deleteByExpiresAtBefore` actually *called* anywhere (method exists, unused — see cleanup job below)

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
- [ ] Frontend `/reset-password` page (not built yet — backend-only checklist, noted here since it's the missing link): reads `token` off the query string (the email link points to `app.password-reset.redirect-uri` + `?token=...`), shows new-password + confirm-password (confirm checked client-side only, same as change-password), submits `{token, newPassword}` to `POST /reset-password`, redirects to login on 204. Should also handle someone landing on `/reset-password` with no `token` in the URL at all (direct navigation, not via the email link) — show a message pointing back to forgot-password instead of a broken form.

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
- [ ] Scheduled cleanup job for expired `refresh_tokens` AND `password_reset_tokens` rows (`@Scheduled` + `@EnableScheduling`) — `deleteByExpiresAtBefore` exists on RefreshTokenRepository but is unused; no equivalent exists yet on PasswordResetTokenRepository
- [ ] Refresh token rotation — expiresAt fixed at creation, never extended; an actively-used session still force-logs-out exactly 7 days after login
- [ ] Rate limiting on `/forgot-password` (currently nothing stops someone spamming reset emails at a victim's inbox)
- [ ] Pagination on `GET /api/jobs` — currently returns every matching row. Fine at current scale, will need `Pageable` once a user has hundreds of applications
- [ ] `PUT` endpoints are full-replace, not `PATCH` — omitting a field nulls it. Fine if the frontend always sends the whole object back (typical for an edit form), but a real `PATCH` would be friendlier for one-field updates like a drag-and-drop status change on a kanban board
- [ ] AI features — `job_ai_results` table and entity exist, no endpoints and no Gemini integration (`GEMINI_API_KEY` still a placeholder)
- [ ] File upload — Cloudinary not integrated (`CLOUDINARY_*` still placeholders). `resumeUrl`/`coverLetterUrl` are plain strings the client sets itself
- [ ] `JobApplicationRepository.findByUser_UserIdAndJobId` — old method returning `List` where at most one row can match (jobId is the PK). Superseded by `findByJobIdAndUser_UserId` returning `Optional`; the old one is now unused and can be deleted
- [ ] Client-side password max — backend now allows up to 64 chars but `src/lib/validation.js` has no upper bound, so a 65+ char passphrase still 400s. Small gap, frontend-side fix

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
- Ownership checks must be part of the **query**, not a post-fetch `if`. `findByJobIdAndUser_UserId(jobId, userId)` makes "not yours" and "doesn't exist" the same code path, which is what lets every miss return an indistinguishable 404.
