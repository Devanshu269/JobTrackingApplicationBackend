package com.jobtracker.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends mail over Brevo's HTTPS API.
 *
 * <p><b>HTTPS, not SMTP — that is the entire point.</b> Render blocks outbound SMTP, so the
 * previous {@code JavaMailSender} transport hung ~45s per send and delivered nothing in
 * production. Port 443 is not blocked.
 *
 * <p>Brevo was chosen over Resend because this project owns no domain: the frontend is on
 * {@code vercel.app} and the backend on {@code onrender.com}. Brevo verifies a single sender
 * address by email confirmation, whereas Resend's free tier only delivers to your own address
 * until you verify a domain you control.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "brevo")
public class BrevoEmailSender implements EmailSender {

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String fromName;
    private final String fromAddress;
    private final String replyTo;

    public BrevoEmailSender(
            // No default value: if the provider is set to brevo and the key is missing, the
            // application must fail at startup rather than boot and silently drop every email.
            @Value("${app.email.brevo.api-key}") String apiKey,
            @Value("${app.email.from-name}") String fromName,
            @Value("${app.email.from-address}") String fromAddress,
            @Value("${app.email.reply-to}") String replyTo) {

        // The @Value above is not enough on its own: application.yaml supplies an empty default
        // (${BREVO_API_KEY:}) so that provider=log works with nothing configured, and an empty
        // string satisfies the placeholder happily. Without this check, asking for the Brevo
        // transport without a key starts a perfectly healthy-looking app that 401s on every
        // single send. Fail here instead — confirmed by test that it otherwise boots fine.
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "app.email.provider=brevo but app.email.brevo.api-key is empty. "
                            + "Set BREVO_API_KEY, or use app.email.provider=log for local development.");
        }

        this.fromName = fromName;
        this.fromAddress = fromAddress;
        this.replyTo = replyTo;

        // Explicit timeouts. Without them a hung provider would pin an executor thread
        // indefinitely, and with a pool of two that is the whole pool.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(BREVO_SEND_URL)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Runs on the {@code emailExecutor} pool, not the caller's thread.
     *
     * <p>This is why {@code @Async} sits on the transport rather than on {@code EmailService}:
     * Spring's async support is proxy-based, so a method calling its own {@code @Async} method
     * bypasses the proxy and runs synchronously. The boundary has to be a call from one bean
     * into a different bean, which is exactly what {@code EmailService -> EmailSender} is.
     *
     * <p>Nothing is rethrown. An {@code @Async void} method's exception has nowhere to go — no
     * caller is waiting on it — so an uncaught one would vanish without a trace. "We don't know
     * whether the password reset sent" is the worst available outcome, so every failure
     * is logged loudly instead.
     */
    @Async("emailExecutor")
    @Override
    public void send(String to, String subject, String textBody) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", fromName, "email", fromAddress));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", subject);
        payload.put("textContent", textBody);
        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("replyTo", Map.of("email", replyTo));
        }

        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email sent via Brevo to {} (subject: {})", mask(to), subject);
        } catch (Exception e) {
            // Subject and masked recipient only. The body is deliberately never logged: it can
            // carry a live password-reset link, which in a log file is a working account takeover.
            log.error("Failed to send email via Brevo to {} (subject: {}): {}",
                    mask(to), subject, e.getMessage(), e);
        }
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
