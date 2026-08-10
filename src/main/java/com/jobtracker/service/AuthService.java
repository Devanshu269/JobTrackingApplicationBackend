package com.jobtracker.service;

import com.jobtracker.Constants.AuthConstants;
import com.jobtracker.Utils.AuthUtils;
import com.jobtracker.dto.AuthResponseDto;
import com.jobtracker.dto.ChangePasswordRequestDto;
import com.jobtracker.dto.LoginRequestDto;
import com.jobtracker.dto.SignupRequestDto;
import com.jobtracker.enums.Provider;
import com.jobtracker.exception.EmailAlreadyExistsException;
import com.jobtracker.exception.InvalidCredentialsException;
import com.jobtracker.exception.InvalidExchangeCodeException;
import com.jobtracker.exception.InvalidRefreshTokenException;
import com.jobtracker.exception.InvalidResetTokenException;
import com.jobtracker.exception.WrongAuthProviderException;
import com.jobtracker.model.PasswordResetToken;
import com.jobtracker.model.RefreshToken;
import com.jobtracker.model.User;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.security.JwtUtil;
import com.jobtracker.security.OAuthExchangeCodeStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthUtils authUtils;
    private final JwtUtil jwtUtil;
    private final OAuthExchangeCodeStore oAuthExchangeCodeStore;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * Hard ceiling on a session's total life, measured from the original login regardless of how
     * many rotations have happened since. Without it, sliding expiry means a session that's used
     * weekly never ends.
     */
    @Value("${jwt.refresh-absolute-expiration}")
    private long refreshAbsoluteExpiration;

    /** Window in which a replayed token is treated as a benign race rather than a theft. */
    @Value("${jwt.refresh-rotation-grace-seconds:30}")
    private long rotationGraceSeconds;

    @Value("${app.password-reset.redirect-uri}")
    private String passwordResetRedirectUri;

    @Value("${app.password-reset.token-expiration-minutes}")
    private long passwordResetTokenExpirationMinutes;

    @Value("${app.password-reset.resend-cooldown-minutes:2}")
    private long resendCooldownMinutes;

    public AuthResponseDto signup(SignupRequestDto dto, String deviceInfo) {
        Optional<User> userOptional = userRepository.findByEmail(dto.getEmail());
        if (userOptional.isPresent()) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        User user = authUtils.buildUser(dto.getEmail(), dto.getUserFirstName(), dto.getUserLastName(), AuthConstants.DEFAULT_AVATAR_URL, Provider.LOCAL, true);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
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

    public AuthResponseDto handleOAuth2Login(String email, String firstName, String lastName, String avatarUrl, Provider provider, String deviceInfo) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            User newUser = authUtils.buildUser(email, firstName, lastName, avatarUrl, provider, true);
            userRepository.save(newUser);

            String token = jwtUtil.generateToken(newUser.getEmail());
            RefreshToken refreshToken = createRefreshToken(newUser, deviceInfo);

            AuthResponseDto responseDto = new AuthResponseDto();
            responseDto.setToken(token);
            responseDto.setRefreshToken(refreshToken.getToken());
            responseDto.setUserId(newUser.getUserId());
            return responseDto;
        }
        User user = userOptional.get();

        if(user.getIsActive() == null || !user.getIsActive()) {
            throw new InvalidCredentialsException("User account has been De-Activated. Please contact support for assistance.");
        }

        if(!provider.equals(user.getProvider())) {
            throw new WrongAuthProviderException("Invalid authentication provider. Please use " + user.getProvider() + " to login.");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        RefreshToken refreshToken = createRefreshToken(user, deviceInfo);

        AuthResponseDto responseDto = new AuthResponseDto();
        responseDto.setToken(token);
        responseDto.setRefreshToken(refreshToken.getToken());
        responseDto.setUserId(user.getUserId());
        return responseDto;
    }

    @Transactional
    public void changePassword(Authentication authentication, ChangePasswordRequestDto dto) {
        User user = (User) authentication.getPrincipal();

        if (!Provider.LOCAL.equals(user.getProvider())) {
            throw new WrongAuthProviderException("Password change is not available for " + user.getProvider() + " accounts.");
        }

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        // Same reasoning as resetPassword, which has always done this: a password change is
        // very often a response to suspected compromise, and leaving existing refresh tokens
        // alive would let an attacker's session keep rotating itself for another 7 days.
        // The caller's own session dies too — the client must re-authenticate after this.
        refreshTokenRepository.deleteByUser(user);
    }

    public void forgotPassword(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty() || !Provider.LOCAL.equals(userOptional.get().getProvider())) {
            // Deliberately do nothing further — the controller always returns the same
            // generic response regardless, so we never reveal whether this email is registered.
            return;
        }

        User user = userOptional.get();

        // Per-account cooldown, so repeated submissions can't be used to flood someone's inbox.
        // Silently returns — it must NOT surface as an error or a different status code, or the
        // rate limit itself becomes an oracle telling an attacker the address is registered.
        LocalDateTime cooldownStart = LocalDateTime.now().minusMinutes(resendCooldownMinutes);
        if (passwordResetTokenRepository.existsByUser_UserIdAndUsedFalseAndCreatedAtAfter(user.getUserId(), cooldownStart)) {
            return;
        }

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenExpirationMinutes));
        resetToken.setUsed(false);
        passwordResetTokenRepository.save(resetToken);

        String resetLink = passwordResetRedirectUri + "?token=" + resetToken.getToken();
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(InvalidResetTokenException::new);

        if (Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException();
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Reset should kill all existing sessions, same reasoning as logout-all:
        // if an attacker reset it, the real owner's stale sessions die too;
        // if the real owner reset it, any attacker session dies immediately.
        refreshTokenRepository.deleteByUser(user);
    }

    public AuthResponseDto exchangeOAuthCode(String code) {
        return oAuthExchangeCodeStore.consume(code)
                .orElseThrow(InvalidExchangeCodeException::new);
    }

    /**
     * Rotating refresh: the presented token is consumed and a replacement issued.
     *
     * <p>Two things this buys over the previous non-rotating version:
     * <ul>
     *   <li><b>A stolen token has a short useful life</b> — whoever refreshes first invalidates
     *       it for the other party, and the loser's replay trips reuse detection.</li>
     *   <li><b>An active session no longer dies at exactly 7 days</b>, because each rotation
     *       issues a fresh 7-day window — bounded by the absolute cap below.</li>
     * </ul>
     */
    /**
     * {@code noRollbackFor} is essential, not cosmetic. Both the reuse-detection and
     * absolute-cap paths revoke the token family and then throw — and an unchecked exception
     * would normally roll the transaction back, silently undoing the revocation that had just
     * been performed. Confirmed by test: without this, a replayed token was rejected but its
     * family stayed alive, so the security control was a no-op.
     *
     * <p>Safe to apply method-wide because every other throw site does no writes beforehand.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponseDto refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(InvalidRefreshTokenException::new);

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            handleReplayedToken(refreshToken, now);
        }
        if (refreshToken.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException();
        }
        // Sliding expiry must not mean an immortal session: the chain can't outlive this window
        // measured from the ORIGINAL login, however many times it has been rotated since.
        if (refreshToken.getFamilyCreatedAt().plusSeconds(refreshAbsoluteExpiration / 1000).isBefore(now)) {
            refreshTokenRepository.revokeFamily(refreshToken.getFamilyId());
            throw new InvalidRefreshTokenException("Session expired. Please log in again.");
        }

        User user = refreshToken.getUser();
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new InvalidRefreshTokenException("User account has been De-Activated. Please contact support for assistance.");
        }

        // Consume the presented token, then issue its successor in the same family.
        refreshToken.setRevoked(true);
        refreshToken.setRotatedAt(now);
        refreshTokenRepository.save(refreshToken);

        RefreshToken rotated = new RefreshToken();
        rotated.setToken(UUID.randomUUID().toString());
        rotated.setUser(user);
        rotated.setExpiresAt(now.plusSeconds(refreshExpiration / 1000));
        rotated.setDeviceInfo(refreshToken.getDeviceInfo());
        rotated.setRevoked(false);
        rotated.setFamilyId(refreshToken.getFamilyId());
        rotated.setFamilyCreatedAt(refreshToken.getFamilyCreatedAt());
        refreshTokenRepository.save(rotated);

        AuthResponseDto responseDto = new AuthResponseDto();
        responseDto.setToken(jwtUtil.generateToken(user.getEmail()));
        // NOTE: unlike the previous implementation this is NOT null. The client must store it —
        // the token it sent is now dead.
        responseDto.setRefreshToken(rotated.getToken());
        responseDto.setUserId(user.getUserId());
        return responseDto;
    }

    /**
     * An already-consumed token came back. Either two clients raced, or one copy was stolen —
     * and from here the two are indistinguishable.
     *
     * <p>Within a short grace window it is treated as a race (two tabs refreshing at the same
     * instant) and only that call fails. Beyond it, the conservative reading wins: the chain is
     * assumed compromised and the whole session is revoked, forcing a fresh login on every
     * device using it.
     */
    private void handleReplayedToken(RefreshToken refreshToken, LocalDateTime now) {
        LocalDateTime rotatedAt = refreshToken.getRotatedAt();
        boolean withinGrace = rotatedAt != null
                && rotatedAt.plusSeconds(rotationGraceSeconds).isAfter(now);

        if (!withinGrace) {
            int revoked = refreshTokenRepository.revokeFamily(refreshToken.getFamilyId());
            log.warn("Refresh token reuse detected for user {} — revoked {} token(s) in family {}",
                    refreshToken.getUser().getUserId(), revoked, refreshToken.getFamilyId());
        }
        throw new InvalidRefreshTokenException();
    }

    /**
     * Starts a <b>new</b> session family — used by signup, login and OAuth. Rotation of an
     * existing session happens inside {@link #refreshAccessToken}, which carries the family
     * forward instead of minting a new one.
     */
    public RefreshToken createRefreshToken(User user, String deviceInfo) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(now.plusSeconds(refreshExpiration / 1000));
        refreshToken.setDeviceInfo(deviceInfo != null ? deviceInfo : "Unknown device");
        refreshToken.setRevoked(false);
        refreshToken.setFamilyId(UUID.randomUUID().toString());
        refreshToken.setFamilyCreatedAt(now);
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void logoutUser(String refreshTokenValue) {

        refreshTokenRepository.deleteByToken(refreshTokenValue);
    }

    @Transactional
    public void logoutFromAllDeviceOfUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        refreshTokenRepository.deleteByUser(user);
    }

}