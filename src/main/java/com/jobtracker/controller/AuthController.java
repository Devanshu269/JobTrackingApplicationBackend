package com.jobtracker.controller;

import com.jobtracker.Utils.AuthUtils;
import com.jobtracker.dto.AuthResponseDto;
import com.jobtracker.dto.ChangePasswordRequestDto;
import com.jobtracker.dto.ForgotPasswordRequestDto;
import com.jobtracker.dto.LoginRequestDto;
import com.jobtracker.dto.OAuthExchangeRequestDto;
import com.jobtracker.dto.RefreshTokenRequestDto;
import com.jobtracker.dto.ResetPasswordRequestDto;
import com.jobtracker.dto.SignupRequestDto;
import com.jobtracker.dto.UserDto;
import com.jobtracker.exception.RateLimitExceededException;
import com.jobtracker.model.User;
import com.jobtracker.security.RateLimiter;
import com.jobtracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 5 attempts per 15 minutes per IP — generous for a human, useless for a script. */
    private static final int FORGOT_PASSWORD_MAX = 5;
    private static final long FORGOT_PASSWORD_WINDOW_SECONDS = 900;

    private final AuthService authService;
    private final AuthUtils authUtils;
    private final RateLimiter rateLimiter;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto> signup(@Valid @RequestBody SignupRequestDto signupRequestDto,
                                                   @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        AuthResponseDto response =  authService.signup(signupRequestDto, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto>  login(@Valid @RequestBody LoginRequestDto loginRequestDto,
                                                   @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        AuthResponseDto response =  authService.login(loginRequestDto, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<AuthResponseDto> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequestDto oAuthExchangeRequestDto) {
        AuthResponseDto response = authService.exchangeOAuthCode(oAuthExchangeRequestDto.getCode());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(authUtils.toUserDto(user));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequestDto changePasswordRequestDto) {
        authService.changePassword(authentication, changePasswordRequestDto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto,
                                               HttpServletRequest request) {
        // Per-IP guard against someone hammering this to spam inboxes or burn the mail quota.
        // Keyed on IP rather than email on purpose: an email-keyed 429 would confirm which
        // addresses are registered. The per-account cooldown lives in the service and is silent.
        if (!rateLimiter.tryAcquire("forgot-password:" + clientIp(request), FORGOT_PASSWORD_MAX, FORGOT_PASSWORD_WINDOW_SECONDS)) {
            throw new RateLimitExceededException("Too many password reset requests. Please try again later.");
        }
        authService.forgotPassword(forgotPasswordRequestDto.getEmail());
        // Always the same response whether or not the email is registered — avoids leaking
        // which emails have accounts (user enumeration).
        return ResponseEntity.ok().build();
    }

    /**
     * Resolves the client IP for rate-limiting behind a proxy (Railway in production).
     *
     * <p><b>Takes the LAST entry of X-Forwarded-For, not the first.</b> Proxies <i>append</i> to
     * this header, so the rightmost value is the address your own proxy actually observed, while
     * everything to its left is client-supplied and therefore forgeable. Reading the first entry
     * — the intuitive choice, and what this originally did — lets anyone reset their own rate
     * limit by sending a made-up {@code X-Forwarded-For}, which was confirmed by test.
     *
     * <p>With no proxy in front (local dev) the header is absent and the socket address is used.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto resetPasswordRequestDto) {
        authService.resetPassword(resetPasswordRequestDto.getToken(), resetPasswordRequestDto.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
        AuthResponseDto response = authService.refreshAccessToken(refreshTokenRequestDto.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
        authService.logoutUser(refreshTokenRequestDto.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        authService.logoutFromAllDeviceOfUser(authentication);
        return ResponseEntity.noContent().build();
    }

}
