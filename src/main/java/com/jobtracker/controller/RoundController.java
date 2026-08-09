package com.jobtracker.controller;

import com.jobtracker.dto.UpcomingRoundResponseDto;
import com.jobtracker.model.User;
import com.jobtracker.service.InterviewRoundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cross-job round reads.
 *
 * Separate from {@link InterviewRoundController}, which is mapped under
 * /api/jobs/{jobId}/rounds and therefore always has a parent job to scope by. These endpoints
 * span every job the caller owns, so they can't live under that path.
 */
@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
public class RoundController {

    private final InterviewRoundService interviewRoundService;

    @GetMapping("/upcoming")
    public ResponseEntity<List<UpcomingRoundResponseDto>> listUpcomingRounds(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(interviewRoundService.listUpcomingRounds(user));
    }
}
