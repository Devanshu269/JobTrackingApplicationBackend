package com.jobtracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

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
