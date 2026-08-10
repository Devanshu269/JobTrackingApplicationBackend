package com.jobtracker.controller;

import com.jobtracker.dto.NotificationDto;
import com.jobtracker.model.User;
import com.jobtracker.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** Capped server-side; a bell doesn't need more than a screenful. */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.listActive(user));
    }
}
