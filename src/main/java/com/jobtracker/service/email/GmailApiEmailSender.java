package com.jobtracker.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Sends mail through the Gmail REST API as the project's own Gmail account.
 *
 * <p><b>Why this exists rather than Brevo.</b> Gmail, Yahoo and Outlook now enforce DMARC on
 * bulk senders, and Google publishes a policy telling receivers to quarantine mail claiming to be
 * from {@code @gmail.com} that Google did not send. No third-party relay can satisfy that — only
 * Google can sign for {@code gmail.com} — so Brevo-relayed mail from a Gmail From address is
 * spam-filed by design, not by reputation. Sending through Google's own API makes SPF, DKIM and
 * DMARC align, and the mail reaches the inbox from a free account with no domain to buy.
 *
 * <p>It also sidesteps the original production bug for the same reason Brevo did: this is HTTPS
 * to {@code googleapis.com}, and Render blocks outbound SMTP, not port 443.
 *
 * <p>Limits: a free Gmail account allows roughly 500 recipients/day, far above this app's needs.
 *
 * <p><b>These credentials are deliberately separate from {@code GOOGLE_CLIENT_ID} / the login
 * OAuth app.</b> That consent screen is published and working for user sign-in; adding a
 * restricted scope to it risks disturbing something already in production. This uses its own
 * Cloud project and its own client.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "gmail")
public class GmailApiEmailSender implements EmailSender {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_URL =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    /** Refresh this long before actual expiry, so a token can't die mid-request. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String fromName;
    private final String fromAddress;
    private final String replyTo;

    private String cachedAccessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    public GmailApiEmailSender(
            @Value("${app.email.gmail.client-id:}") String clientId,
            @Value("${app.email.gmail.client-secret:}") String clientSecret,
            @Value("${app.email.gmail.refresh-token:}") String refreshToken,
            @Value("${app.email.from-name}") String fromName,
            @Value("${app.email.from-address}") String fromAddress,
            @Value("${app.email.reply-to}") String replyTo) {

        // Fail at startup rather than boot healthy and discard every email. An empty @Value
        // default is not enough on its own — an empty string satisfies the placeholder happily.
        requireConfigured(clientId, "app.email.gmail.client-id (GMAIL_OAUTH_CLIENT_ID)");
        requireConfigured(clientSecret, "app.email.gmail.client-secret (GMAIL_OAUTH_CLIENT_SECRET)");
        requireConfigured(refreshToken, "app.email.gmail.refresh-token (GMAIL_OAUTH_REFRESH_TOKEN)");

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.fromName = fromName;
        this.fromAddress = fromAddress;
        this.replyTo = replyTo;

        // Explicit timeouts: without them a hung endpoint pins an executor thread forever, and
        // the email pool is only one or two threads wide.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    private void requireConfigured(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "app.email.provider=gmail but " + what + " is empty. "
                            + "See DEPLOYMENT.md for how to mint a refresh token, "
                            + "or use app.email.provider=log for local development.");
        }
    }

    /**
     * Runs on the {@code emailExecutor} pool. Nothing is rethrown: an {@code @Async void}
     * exception has no caller to reach, so an uncaught one would vanish silently — and "we don't
     * know whether the password reset sent" is the worst available outcome.
     */
    @Async("emailExecutor")
    @Override
    public void send(String to, String subject, String textBody) {
        try {
            String raw = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(buildMimeMessage(to, subject, textBody)
                            .getBytes(StandardCharsets.UTF_8));
            try {
                dispatch(raw, accessToken(false));
            } catch (RestClientResponseException e) {
                // A token can be revoked or invalidated server-side before its stated expiry.
                // One forced refresh distinguishes that from genuinely bad credentials.
                if (e.getStatusCode().value() != 401) {
                    throw e;
                }
                log.warn("Gmail API returned 401; refreshing access token and retrying once");
                dispatch(raw, accessToken(true));
            }
            log.info("Email sent via Gmail API to {} (subject: {})", mask(to), subject);
        } catch (RestClientResponseException e) {
            // Google's error body names the cause (invalid_grant, insufficient scope, quota).
            // Subject and masked recipient only — the body can carry a live reset link, which in
            // a retained log is a working account takeover.
            log.error("Gmail API rejected email to {} (subject: {}): {} {}",
                    mask(to), subject, e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to send email via Gmail API to {} (subject: {}): {}",
                    mask(to), subject, e.getMessage(), e);
        }
    }

    private void dispatch(String rawMessage, String accessToken) {
        restClient.post()
                .uri(SEND_URL)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("raw", rawMessage))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Exchanges the long-lived refresh token for a short-lived access token, caching until just
     * before expiry. Synchronized because the executor can run two sends concurrently and both
     * would otherwise mint their own token.
     *
     * <p>The refresh token itself does not expire — <b>provided the OAuth consent screen is
     * published ("In production")</b>. While it is in "Testing", Google expires refresh tokens
     * after 7 days, which shows up as mail working for a week and then silently stopping with
     * {@code invalid_grant}.
     */
    private synchronized String accessToken(boolean forceRefresh) {
        if (!forceRefresh
                && cachedAccessToken != null
                && Instant.now().isBefore(accessTokenExpiry)) {
            return cachedAccessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        Map<?, ?> response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Google token endpoint returned no access_token");
        }

        cachedAccessToken = response.get("access_token").toString();
        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
        accessTokenExpiry = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        return cachedAccessToken;
    }

    /**
     * Builds an RFC 5322 message. The body is base64 with an explicit transfer encoding rather
     * than 8bit, so any non-ASCII a user typed (a company name, say) survives intact.
     */
    private String buildMimeMessage(String to, String subject, String textBody) {
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(textBody.getBytes(StandardCharsets.UTF_8));

        StringBuilder message = new StringBuilder()
                .append("From: ").append(encodeHeader(fromName))
                .append(" <").append(fromAddress).append(">\r\n")
                .append("To: ").append(to).append("\r\n");

        if (replyTo != null && !replyTo.isBlank()) {
            message.append("Reply-To: ").append(replyTo).append("\r\n");
        }

        return message
                .append("Subject: ").append(encodeHeader(subject)).append("\r\n")
                .append("MIME-Version: 1.0\r\n")
                .append("Content-Type: text/plain; charset=\"UTF-8\"\r\n")
                .append("Content-Transfer-Encoding: base64\r\n")
                .append("\r\n")
                .append(encodedBody)
                .toString();
    }

    /**
     * RFC 2047 encoding for header values. Headers are ASCII-only on the wire, so a non-ASCII
     * subject must be wrapped or it arrives as mojibake.
     */
    private String encodeHeader(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.chars().allMatch(c -> c < 128)) {
            return value;
        }
        return "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                + "?=";
    }

    /** {@code devanshu@gmail.com -> d*******@gmail.com}. Enough to correlate, not enough to harvest. */
    private String mask(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "*".repeat(at - 1) + email.substring(at);
    }
}
