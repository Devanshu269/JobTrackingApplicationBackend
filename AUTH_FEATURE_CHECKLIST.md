# Auth Feature Checklist

Mark items `[x]` as you complete them (manual, not auto-synced). Scope: local signup/login, Google + GitHub OAuth2, JWT access tokens + DB-backed refresh tokens, profile lookup, change password, forgot/reset password. Base path `/jobTracking/api/auth`.

## Endpoints — full list
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

## DTOs — code-complete
- [x] SignupRequestDto — userFirstName, userLastName, email, password
- [x] LoginRequestDto — email, password
- [x] ChangePasswordRequestDto — currentPassword, newPassword (both 4-12 chars) — now wired to `POST /change-password`
- [x] ForgotPasswordRequestDto — email
- [x] ResetPasswordRequestDto — token, newPassword (4-12 chars)
- [x] OAuthExchangeRequestDto — code (for `POST /oauth/exchange`)
- [x] RefreshTokenRequestDto — refreshToken (for `POST /refresh` and `/logout`)
- [x] AuthResponseDto — `token` + `refreshToken` + `userId` (no tokenType, no embedded profile — frontend calls `/me` separately for that)
- [x] UserDto — userId, userFirstName, userLastName, email, avatarUrl, **provider** (added so frontend can gate "change password" UI to LOCAL-only accounts) — backs `GET /me`
- [x] ErrorResponseDto — timestamp, status, message, errors map
- [ ] UpdateProfileRequestDto, UpdateDefaultResumeRequestDto — exist, not yet wired to any endpoint (out of auth scope, belongs to profile/job features)

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

## Security — code-complete, runtime-tested
- [x] SecurityConfig: CSRF off, sessions STATELESS
- [x] `/api/auth/**` public EXCEPT `logout-all`, `me`, `change-password` explicitly carved out to require auth (see the `/me` note above — this list needs a new entry every time an authenticated endpoint is added under `/api/auth`)
- [x] `/oauth2/**`, `/login/oauth2/**` explicitly `permitAll()`
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
  - [x] MethodArgumentNotValidException → 400 with per-field messages

## Not done yet
- [ ] Scheduled cleanup job for expired `refresh_tokens` AND `password_reset_tokens` rows (`@Scheduled` + `@EnableScheduling`) — `deleteByExpiresAtBefore` exists on RefreshTokenRepository but is unused; no equivalent exists yet on PasswordResetTokenRepository
- [ ] Refresh token rotation — expiresAt fixed at creation, never extended; an actively-used session still force-logs-out exactly 7 days after login
- [ ] Roles/admin-moderator system — no UserDetailsService or role column yet
- [ ] Rate limiting on `/forgot-password` (currently nothing stops someone spamming reset emails at a victim's inbox)

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
