package com.jobtracker.controller;

import com.jobtracker.dto.AuthResponseDto;
import com.jobtracker.dto.LoginRequestDto;
import com.jobtracker.dto.RefreshTokenRequestDto;
import com.jobtracker.dto.SignupRequestDto;
import com.jobtracker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
