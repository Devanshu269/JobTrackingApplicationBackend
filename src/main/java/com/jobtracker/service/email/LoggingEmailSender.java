package com.jobtracker.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes the email to the application log instead of sending it. The default transport.
 *
 * <p>{@code matchIfMissing = true} makes this the fallback, so a fresh clone with no configuration
 * cannot send real mail to real people — and local development doesn't consume the 300/day free
 * tier. Printing the body in full is the point: it's how you copy a password-reset link out of the
 * console to test the flow without an inbox.
 *
 * <p><b>That same behaviour is why this must never be the production transport.</b> Reset links
 * are live credentials; in a retained, searchable production log they are an account takeover.
 * Production sets {@code app.email.provider=brevo} — see {@code application-prod.yaml}.
 *
 * <p>Left synchronous, unlike {@link BrevoEmailSender}. There is no network call to get off the
 * request thread, and logging in-line keeps the output in causal order while debugging.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("""

                        ──────── EMAIL (not sent — provider is 'log') ────────
                        To:      {}
                        Subject: {}

                        {}
                        ─────────────────────────────────────────────────────""",
                to, subject, textBody);
    }
}
