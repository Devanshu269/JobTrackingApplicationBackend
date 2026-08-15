package com.jobtracker.service;

import com.jobtracker.service.email.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owns what emails <i>say</i>. How they are delivered is {@link EmailSender}'s job.
 *
 * <p>The public method signatures are unchanged from the {@code JavaMailSender} version, so
 * {@code AuthService} and {@code ReminderService} needed no edit when the transport was replaced.
 * That is the whole benefit of the split.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailSender emailSender;

    public void sendFollowUpReminder(String to, String companyName, String jobRole, String statusLabel) {
        String subject = "Follow up on " + companyName;
        String body = "A follow-up you scheduled is due.\n\n"
                + jobRole + " at " + companyName + "\n"
                + "Current status: " + statusLabel + "\n\n"
                + "Open JobTracker to update the application or reschedule the follow-up.\n\n"
                + "You're getting this because reminders are switched on for this application.";
        emailSender.send(to, subject, body);
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        String subject = "Reset your JobTracker password";
        String body = "We received a request to reset your JobTracker password.\n\n"
                + "Click the link below to set a new password. This link expires in 30 minutes:\n\n"
                + resetLink
                + "\n\nIf you didn't request this, you can safely ignore this email.";
        emailSender.send(to, subject, body);
    }
}
