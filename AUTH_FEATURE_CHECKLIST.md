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

## Runtime testing — NOT started (only `mvn compile` has been run)
- [ ] Start the app successfully (watch the CORS + Authentication-import gotchas below)
- [ ] Signup: success (201, token + refreshToken + userId returned)
- [ ] Signup: duplicate email → 409
- [ ] Signup: validation failure → 400 with field errors
- [ ] Login: success (200, tokens returned)
- [ ] Login: wrong password → 401
- [ ] Login: email not found → 401 (same message as wrong password)
- [ ] Login: correct creds but provider != LOCAL → 401 with clear rejection message
- [ ] Refresh: valid refresh token → new access token
- [ ] Refresh: expired/revoked/unknown refresh token → 401
- [ ] Refresh: user.isActive = false → rejected
- [ ] Logout: deletes the specific refresh token row (verify in DB)
- [ ] Logout-all: without auth header → 401
- [ ] Logout-all: with auth header → deletes all rows for that user (verify with multi-device test: 2 logins, 1 logout-all, both refresh tokens dead)
- [ ] Protected endpoint, no Authorization header → 401
- [ ] Protected endpoint, malformed/invalid token → 401
- [ ] Protected endpoint, expired access token → 401
- [ ] Protected endpoint, valid token → 200, principal is the right User

## Not done yet
- [ ] Scheduled cleanup job for expired refresh_tokens rows (@Scheduled + @EnableScheduling), calling the existing-but-unused `deleteByExpiresAtBefore`
- [ ] This checklist kept in sync as work continues (you're doing this manually — that's fine)

## Later / deferred on purpose
- [ ] OAuth2 Google login (find-or-create by email, provider = GOOGLE, issue our own JWT not Google's)
- [ ] OAuth2 GitHub login (same pattern, provider = GITHUB)
- [ ] GET /api/auth/me → returns UserDto
- [ ] Password reset flow (request-reset email + confirm-reset endpoint)
- [ ] Roles/admin-moderator system — no UserDetailsService or role column yet; noted as a clean seam, won't require reworking the JWT filter

## Known housekeeping (low priority)
- [ ] Rotate local MySQL root password (old value is in earlier git history; local dev DB only, not internet-exposed)

## Gotchas hit already (avoid re-tripping on these)
- `UrlBasedCorsConfigurationSource` in this Spring version uses `registerCorsConfiguration(String, CorsConfiguration)`, not `registerCorsConfigurationFor`.
- Watch IDE auto-import pulling in `org.apache.tomcat.util.net.openssl.ciphers.Authentication` instead of `org.springframework.security.core.Authentication` — same simple class name, wrong package, compiles-looking but wrong.
- Servlet stack (`spring-boot-starter-web`), not reactive — never import from `org.springframework.web.cors.reactive.*`.
