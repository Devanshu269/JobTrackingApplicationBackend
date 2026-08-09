package com.jobtracker.controller;

import com.jobtracker.dto.InterviewRoundRequestDto;
import com.jobtracker.dto.InterviewRoundResponseDto;
import com.jobtracker.model.User;
import com.jobtracker.service.InterviewRoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs/{jobId}/rounds")
@RequiredArgsConstructor
public class InterviewRoundController {

    private final InterviewRoundService interviewRoundService;

    @GetMapping
    public ResponseEntity<List<InterviewRoundResponseDto>> listRounds(Authentication authentication,
                                                                      @PathVariable Integer jobId) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(interviewRoundService.listRounds(user, jobId));
    }

    @GetMapping("/{roundId}")
    public ResponseEntity<InterviewRoundResponseDto> getRound(Authentication authentication,
                                                              @PathVariable Integer jobId,
                                                              @PathVariable Integer roundId) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(interviewRoundService.getRound(user, jobId, roundId));
    }

    @PostMapping
    public ResponseEntity<InterviewRoundResponseDto> createRound(Authentication authentication,
                                                                 @PathVariable Integer jobId,
                                                                 @Valid @RequestBody InterviewRoundRequestDto dto) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(interviewRoundService.createRound(user, jobId, dto));
    }

    @PutMapping("/{roundId}")
    public ResponseEntity<InterviewRoundResponseDto> updateRound(Authentication authentication,
                                                                 @PathVariable Integer jobId,
                                                                 @PathVariable Integer roundId,
                                                                 @Valid @RequestBody InterviewRoundRequestDto dto) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(interviewRoundService.updateRound(user, jobId, roundId, dto));
    }

    @DeleteMapping("/{roundId}")
    public ResponseEntity<Void> deleteRound(Authentication authentication,
                                            @PathVariable Integer jobId,
                                            @PathVariable Integer roundId) {
        User user = (User) authentication.getPrincipal();
        interviewRoundService.deleteRound(user, jobId, roundId);
        return ResponseEntity.noContent().build();
    }
}
