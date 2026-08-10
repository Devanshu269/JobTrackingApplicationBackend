package com.jobtracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendFollowUpReminder(String to, String companyName, String jobRole, String statusLabel) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Follow up on " + companyName);
        message.setText("A follow-up you scheduled is due.\n\n"
                + jobRole + " at " + companyName + "\n"
                + "Current status: " + statusLabel + "\n\n"
                + "Open JobTracker to update the application or reschedule the follow-up.\n\n"
                + "You're getting this because reminders are switched on for this application.");
        mailSender.send(message);
    }

    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Reset your JobTracker password");
        message.setText("We received a request to reset your JobTracker password.\n\n"
                + "Click the link below to set a new password. This link expires in 30 minutes:\n\n"
                + resetLink
                + "\n\nIf you didn't request this, you can safely ignore this email.");
        mailSender.send(message);
    }
}
