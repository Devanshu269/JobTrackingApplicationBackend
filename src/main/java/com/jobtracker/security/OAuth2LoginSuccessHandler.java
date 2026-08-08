package com.jobtracker.security;

import com.jobtracker.dto.AuthResponseDto;
import com.jobtracker.enums.Provider;
import com.jobtracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuthExchangeCodeStore oAuthExchangeCodeStore;
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    @Value("${app.oauth2.redirect-uri}")
    private String frontendRedirectUri;

    public OAuth2LoginSuccessHandler(AuthService authService, OAuthExchangeCodeStore oAuthExchangeCodeStore) {
        this.authService = authService;
        this.oAuthExchangeCodeStore = oAuthExchangeCodeStore;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest  request, HttpServletResponse response, Authentication authentication) throws IOException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String registrationId = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();
            Provider provider = "github".equals(registrationId) ? Provider.GITHUB : Provider.GOOGLE;

            String email = oAuth2User.getAttribute("email");
            if (email == null) {
                response.sendRedirect(frontendRedirectUri + "?error=Unable+to+retrieve+email+from+" + registrationId);
                return;
            }

            String firstName;
            String lastName;
            String avatarUrl;
            if (provider == Provider.GITHUB) {
                String name = oAuth2User.getAttribute("name");
                String login = oAuth2User.getAttribute("login");
                firstName = (name != null) ? name : login;
                lastName = null;
                avatarUrl = oAuth2User.getAttribute("avatar_url");
            } else {
                firstName = oAuth2User.getAttribute("given_name");
                lastName = oAuth2User.getAttribute("family_name");
                avatarUrl = oAuth2User.getAttribute("picture");
            }

            AuthResponseDto authResponseDto = authService.handleOAuth2Login(email, firstName, lastName, avatarUrl, provider, request.getHeader("User-Agent"));

            // Hand the frontend a short-lived, single-use code instead of the real tokens,
            // so the JWT/refresh token never end up in the URL, browser history, or server logs.
            String code = oAuthExchangeCodeStore.store(authResponseDto);
            response.sendRedirect(frontendRedirectUri + "?code=" + code);
        } catch (Exception e) {
            log.error("OAuth2 login failed", e);
            response.sendRedirect(frontendRedirectUri + "?error=" + URLEncoder.encode("Authentication failed: " + e.getMessage(), StandardCharsets.UTF_8));
        }
    }
}
