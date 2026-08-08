package com.jobtracker.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"github".equals(registrationId) || oAuth2User.getAttribute("email") != null) {
            return oAuth2User;
        }

        String email = fetchPrimaryVerifiedEmail(userRequest.getAccessToken().getTokenValue());
        if (email == null) {
            return oAuth2User;
        }

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());
        attributes.put("email", email);

        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        return new DefaultOAuth2User(oAuth2User.getAuthorities(), attributes, nameAttributeKey);
    }

    @SuppressWarnings("unchecked")
    private String fetchPrimaryVerifiedEmail(String accessToken) {
        List<Map<String, Object>> emails = restClient.get()
                .uri(GITHUB_EMAILS_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(List.class);

        if (emails == null) {
            return null;
        }

        return emails.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("primary")) && Boolean.TRUE.equals(entry.get("verified")))
                .map(entry -> (String) entry.get("email"))
                .findFirst()
                .or(() -> emails.stream()
                        .filter(entry -> Boolean.TRUE.equals(entry.get("verified")))
                        .map(entry -> (String) entry.get("email"))
                        .findFirst())
                .orElse(null);
    }
}