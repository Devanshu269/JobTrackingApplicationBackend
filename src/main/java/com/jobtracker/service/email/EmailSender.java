package com.jobtracker.service.email;

/**
 * How an email physically leaves the building.
 *
 * <p>Deliberately separate from {@link com.jobtracker.service.EmailService}, which decides what
 * an email <i>says</i>. Only the transport was ever broken: Render blocks outbound SMTP, so
 * {@code JavaMailSender} hung for ~45s on a connection that never completed and no mail was ever
 * delivered in production. Swapping the transport behind this interface leaves every caller and
 * every piece of copy untouched.
 *
 * <p>Implementations are selected by {@code app.email.provider} — see {@link GmailApiEmailSender},
 * {@link BrevoEmailSender} and {@link LoggingEmailSender}. Exactly one is ever active.
 */
public interface EmailSender {

    /**
     * Sends a plain-text email. Implementations must not throw: callers are business flows
     * (a password reset, a follow-up reminder) whose own work is already committed by this point,
     * and a mail outage must not roll them back or surface as a 500.
     */
    void send(String to, String subject, String textBody);
}
