# Auth Feature Checklist

Mark items `[x]` as you complete them (manual, not auto-synced). Scope: local signup/login with JWT access tokens + DB-backed refresh tokens. Base path `/jobTracking/api/auth`.

## DTOs — code-complete
- [x] SignupRequestDto
- [x] LoginRequestDto
- [x] UpdateProfileRequestDto (partial-update friendly, no @NotBlank on optional fields)
- [x] ChangePasswordRequestDto
- [x] UpdateDefaultResumeRequestDto
- [x] AuthResponseDto — `token` + `refreshToken` + `userId` (no tokenType, no embedded profile)
- [x] UserDto — userId, username, email, avatarUrl (for future GET /api/auth/me, not the auth response)
- [x] RefreshTokenRequestDto (for POST /refresh)
- [x] ErrorResponseDto — timestamp, status, message, errors map

## JwtUtil — done
- [x] @Value-injected secret + expiration (access: 15 min via jwt.expiration)
- [x] SecretKey built once in @PostConstruct init()
- [x] generateToken(String email)
- [x] extractEmail(String token)
- [x] validateToken(String token) — catches parse/verify exceptions, returns false

## Refresh token design — code-complete
- [x] RefreshToken entity/table (`refresh_tokens`): random UUID value, deviceInfo (from User-Agent), expiresAt, revoked
- [x] Chose per-session table over a single column on User — deliberately supports multi-device concurrent login
- [x] jwt.refresh-expiration = 7 days
- [x] RefreshTokenRepository
  - [x] lookup by token value
  - [x] delete-all-by-user (for logout-all)
  - [ ] deleteByExpiresAtBefore actually *called* anywhere (method exists, currently unused — see cleanup job below)

## AuthService — code-complete, not runtime-tested
- [x] signup(SignupRequestDto dto)
  - [x] Email uniqueness check → EmailAlreadyExistsException on duplicate
  - [x] Hash password via injected PasswordEncoder
  - [x] Build User entity, provider = Provider.LOCAL
  - [x] Save via UserRepository
  - [x] Generate access token + create RefreshToken row
  - [x] Build and return AuthResponseDto
- [x] login(LoginRequestDto dto)
  - [x] Look up user by email
  - [x] WrongAuthProviderException checked *before* password check
  - [x] Email-not-found and wrong-password branches throw the identical InvalidCredentialsException message (no leak)
  - [x] Verify password via passwordEncoder.matches
  - [x] Generate access token + create RefreshToken row
  - [x] Build and return AuthResponseDto
- [x] refreshAccessToken(RefreshTokenRequestDto dto)
  - [x] Look up refresh token row, reject if missing/expired/revoked → InvalidRefreshTokenException
  - [x] Check user.isActive (deactivated accounts can't refresh — mirrors login's check)
  - [x] Issue new access token (refresh token itself is NOT rotated)
- [x] logout(String refreshToken) — deletes the one matching row
- [x] logoutAll(User authenticatedUser) — deletes all refresh token rows for that user

## AuthController — code-complete, not runtime-tested
- [x] POST /signup → 201 + AuthResponseDto
- [x] POST /login → 200 + AuthResponseDto
- [x] POST /refresh → 200 + new access token
- [x] POST /logout → deletes one refresh token row
- [x] POST /logout-all → requires auth, deletes all rows for current user

## Security — code-complete, not runtime-tested
- [x] SecurityConfig: CSRF off, sessions STATELESS
- [x] /api/auth/** public EXCEPT /logout-all carved out to require auth
- [x] CORSConfig.java — origin from CORS_ALLOWED_ORIGIN env var (not hardcoded)
- [x] JwtAuthenticationFilter
  - [x] Reads `Authorization: Bearer <token>`
  - [x] Validates via JwtUtil
  - [x] Sets the **User entity itself** as the Authentication principal
  - [x] Deliberately no UserDetailsService / AuthenticationManager (no role system yet — noted as a clean seam for later)

## Error handling — code-complete
- [x] GlobalExceptionHandler (@RestControllerAdvice)
  - [x] EmailAlreadyExistsException → 409
  - [x] InvalidCredentialsException → 401
  - [x] WrongAuthProviderException → 401
  - [x] InvalidRefreshTokenException → 401
  - [x] MethodArgumentNotValidException → 400 with per-field messages
  - [x] All responses use consistent ErrorResponseDto shape

## Runtime testing — app runs, core flows verified against real MySQL
- [x] Start the app successfully
- [x] Signup: success (201, token + refreshToken + userId returned)
- [x] Signup: duplicate email → 409, correct ErrorResponseDto shape
- [ ] Signup: validation failure → 400 with field errors (not yet tried)
- [x] Login: success (200, tokens returned)
- [x] Login: wrong password → 401, message "Invalid email or password"
- [ ] Login: email not found → 401 same message (not yet tried — should behave same as wrong password per the code, worth confirming)
- [ ] Login: correct creds but provider != LOCAL → 401 (can't test yet — no OAuth signup path exists to create such a user)
- [x] Refresh: valid refresh token → 200, new access token, refreshToken null (not rotated)
- [x] Refresh: invalid/unknown refresh token → 401 "Invalid or expired refresh token"
- [x] Refresh: after logout, same token → 401 (confirms logout actually revokes, not just fake-succeeds)
- [ ] Refresh: user.isActive = false → rejected (not yet tried — no way yet to flip isActive without direct DB edit)
- [x] Logout: deletes the specific refresh token row → 204, verified via subsequent refresh returning 401
- [ ] Logout-all: without auth header → 401 (not yet explicitly tried)
- [x] Logout-all: with auth header → 204, revokes session(s) for that user
- [ ] Multi-device logout-all test (2 logins, 1 logout-all, confirm BOTH refresh tokens die) — logout-all itself confirmed working, full multi-device proof not yet run
- [ ] Protected endpoint (not logout-all), no/malformed/expired token → 401 — blocked: no other authenticated business endpoint exists yet to test against (JobApplication CRUD not built)

## Bugs found & fixed during tonight's runtime testing
- [x] **Stale `username` DB column** — `User.username` was renamed to `userFirstName`/`userLastName` earlier, but `ddl-auto: update` never drops/renames old columns, so the live `users` table still had a `NOT NULL username` column with nothing populating it. Every signup insert failed with `DataIntegrityViolationException`, which Spring forwarded to `/error` — itself behind the security filter chain — surfacing as a confusing empty `403` instead of a `500`. Fixed by dropping the column (later, the whole schema was dropped and rebuilt clean via `ddl-auto: update` after further entity changes).
- [x] **`is_active`/`revoked` stored as `BIT(1)`** — Hibernate's default MySQL mapping for `Boolean`. Fixed with explicit `columnDefinition = "BOOL"` on both `User.isActive` and `RefreshToken.revoked` (MySQL has no real boolean type — `BOOL`/`BOOLEAN` are just aliases for `TINYINT(1)`, confirmed by direct test; this only changes how the type is declared/read, not the underlying `0`/`1` storage).
- [x] **Missing `@Transactional` on logout methods** — `AuthService.logoutUser()` and `logoutFromAllDeviceOfUser()` call derived `deleteByToken`/`deleteByUser` repository methods, which (unlike `.save()`/`.findById()`) don't get automatic transaction handling. Without an active transaction, JPA threw `TransactionRequiredException` ("cannot reliably process 'remove' call"), again surfacing as an empty `403` via the protected `/error` page. Fixed by adding `@Transactional` to both methods.

## Not done yet
- [ ] Scheduled cleanup job for expired refresh_tokens rows (@Scheduled + @EnableScheduling), calling the existing-but-unused `deleteByExpiresAtBefore`
- [ ] Remaining untested cases above (validation failures, email-not-found on login, isActive=false paths, logout-all without auth header, full multi-device proof)
- [ ] This checklist kept in sync as work continues (you're doing this manually — that's fine)

## Later / deferred on purpose
- [ ] OAuth2 Google login (find-or-create by email, provider = GOOGLE, issue our own JWT not Google's)
- [ ] OAuth2 GitHub login (same pattern, provider = GITHUB)
- [ ] GET /api/auth/me → returns UserDto
- [ ] Password reset flow (request-reset email + confirm-reset endpoint)
- [ ] Roles/admin-moderator system — no UserDetailsService or role column yet; noted as a clean seam, won't require reworking the JWT filter
- [ ] Refresh token rotation — currently expiresAt is fixed at creation and never extended, so an actively-used session still gets force-logged-out exactly 7 days after login, even with continuous activity. Rotation would either extend the same row's expiresAt or issue a new refresh token (deleting the old) on every successful /refresh call, so only a truly idle session (no refresh calls for 7 days) expires.

## Known housekeeping (low priority)
- [ ] Rotate local MySQL root password (old value is in earlier git history; local dev DB only, not internet-exposed)

## Gotchas hit already (avoid re-tripping on these)
- `UrlBasedCorsConfigurationSource` in this Spring version uses `registerCorsConfiguration(String, CorsConfiguration)`, not `registerCorsConfigurationFor`.
- Watch IDE auto-import pulling in `org.apache.tomcat.util.net.openssl.ciphers.Authentication` instead of `org.springframework.security.core.Authentication` — same simple class name, wrong package, compiles-looking but wrong.
- Servlet stack (`spring-boot-starter-web`), not reactive — never import from `org.springframework.web.cors.reactive.*`.
- `ddl-auto: update` never drops/renames columns — renaming an entity field leaves the old DB column behind (NOT NULL and unpopulated = broken inserts). After any entity field rename, check the actual table via DBeaver/mysql, don't assume Hibernate cleaned it up.
- Any custom derived repository method starting with `deleteBy`/`removeBy` needs the calling service method annotated `@Transactional` — unlike `.save()`/`.findById()`, these don't get automatic transaction handling. Relevant for the still-unbuilt scheduled cleanup job (`deleteByExpiresAtBefore`) too — don't forget `@Transactional` there either.
- A thrown-but-unhandled exception anywhere in the request path can surface as a confusing empty `403` instead of `500` — Spring forwards failures to `/error`, which itself sits behind the security filter chain and gets denied by `Http403ForbiddenEntryPoint` if the request is unauthenticated. If you ever see an empty 403 with no message on a route that should be public, suspect an unhandled exception upstream, not an auth/CORS misconfiguration first.
