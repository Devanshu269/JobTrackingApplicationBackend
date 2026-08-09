package com.jobtracker.controller;

import com.jobtracker.dto.JobApplicationRequestDto;
import com.jobtracker.dto.JobApplicationResponseDto;
import com.jobtracker.dto.JobStatsResponseDto;
import com.jobtracker.enums.JobType;
import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import com.jobtracker.model.User;
import com.jobtracker.service.JobApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping
    public ResponseEntity<List<JobApplicationResponseDto>> listJobs(Authentication authentication,
                                                                    @RequestParam(required = false) Status status,
                                                                    @RequestParam(required = false) Priority priority,
                                                                    @RequestParam(required = false) JobType jobType,
                                                                    @RequestParam(required = false) String search) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(jobApplicationService.listJobs(user, status, priority, jobType, search));
    }

    @GetMapping("/stats")
    public ResponseEntity<JobStatsResponseDto> getStats(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(jobApplicationService.getStats(user));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobApplicationResponseDto> getJob(Authentication authentication, @PathVariable Integer jobId) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(jobApplicationService.getJob(user, jobId));
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponseDto> createJob(Authentication authentication,
                                                               @Valid @RequestBody JobApplicationRequestDto dto) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(jobApplicationService.createJob(user, dto));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<JobApplicationResponseDto> updateJob(Authentication authentication,
                                                               @PathVariable Integer jobId,
                                                               @Valid @RequestBody JobApplicationRequestDto dto) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(jobApplicationService.updateJob(user, jobId, dto));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> deleteJob(Authentication authentication, @PathVariable Integer jobId) {
        User user = (User) authentication.getPrincipal();
        jobApplicationService.deleteJob(user, jobId);
        return ResponseEntity.noContent().build();
    }
}
