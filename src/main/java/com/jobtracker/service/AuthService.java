package com.jobtracker.service;

import com.jobtracker.dto.AuthResponseDto;
import com.jobtracker.dto.LoginRequestDto;
import com.jobtracker.dto.SignupRequestDto;
import com.jobtracker.enums.Provider;
import com.jobtracker.exception.EmailAlreadyExistsException;
import com.jobtracker.exception.InvalidCredentialsException;
import com.jobtracker.exception.InvalidRefreshTokenException;
import com.jobtracker.exception.WrongAuthProviderException;
import com.jobtracker.model.RefreshToken;
import com.jobtracker.model.User;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public AuthResponseDto signup(SignupRequestDto dto, String deviceInfo) {
        Optional<User> userOptional = userRepository.findByEmail(dto.getEmail());
        if (userOptional.isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        User user = new User();
        user.setUserFirstName(dto.getUserFirstName());
        user.setUserLastName(dto.getUserLastName());
        user.setEmail(dto.getEmail());
        user.setAvatarUrl("https://cdn-icons-png.flaticon.com/512/149/149071.png"); //for now random url, we can set a default avatar URL later
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setProvider(Provider.LOCAL);
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        RefreshToken refreshToken = createRefreshToken(user, deviceInfo);

        AuthResponseDto responseDto = new AuthResponseDto();
        responseDto.setToken(token);
        responseDto.setRefreshToken(refreshToken.getToken());
        responseDto.setUserId(user.getUserId());
        return responseDto;

    }

    public AuthResponseDto login(LoginRequestDto loginRequestDto, String deviceInfo) {
        Optional<User> userOptional = userRepository.findByEmail(loginRequestDto.getEmail());
        if (userOptional.isEmpty()) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        User user = userOptional.get();

        if(user.getIsActive() == null || !user.getIsActive()) {
            throw new InvalidCredentialsException("User account has been De-Activated. Please contact support for assistance.");
        }

        if(!Provider.LOCAL.equals(user.getProvider())) {
            throw new WrongAuthProviderException("Invalid authentication provider. Please use " + user.getProvider() + " to login.");
        }

        if (!passwordEncoder.matches(loginRequestDto.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        RefreshToken refreshToken = createRefreshToken(user, deviceInfo);

        AuthResponseDto responseDto = new AuthResponseDto();
        responseDto.setToken(token);
        responseDto.setRefreshToken(refreshToken.getToken());
        responseDto.setUserId(user.getUserId());
        return responseDto;
    }

    public AuthResponseDto refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = refreshToken.getUser();
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new InvalidRefreshTokenException("User account has been De-Activated. Please contact support for assistance.");
        }
        String newAccessToken = jwtUtil.generateToken(user.getEmail());

        AuthResponseDto responseDto = new AuthResponseDto();
        responseDto.setToken(newAccessToken);
        responseDto.setUserId(user.getUserId());
        return responseDto;
    }

    private RefreshToken createRefreshToken(User user, String deviceInfo) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000));
        refreshToken.setDeviceInfo(deviceInfo != null ? deviceInfo : "Unknown device");
        refreshToken.setRevoked(false);
        return refreshTokenRepository.save(refreshToken);
    }

    public void logoutUser(String refreshTokenValue) {

        refreshTokenRepository.deleteByToken(refreshTokenValue);
    }

    public void logoutFromAllDeviceOfUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        refreshTokenRepository.deleteByUser(user);
    }

}