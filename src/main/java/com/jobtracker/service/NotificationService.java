package com.jobtracker.service;

import com.jobtracker.dto.NotificationDto;
import com.jobtracker.enums.Status;
import com.jobtracker.model.JobApplication;
import com.jobtracker.model.User;
import com.jobtracker.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /** A bell shows a short list; more than this is a page, not a dropdown. */
    private static final int MAX_NOTIFICATIONS = 20;

    private final JobApplicationRepository jobApplicationRepository;

    public List<NotificationDto> listActive(User user) {
        List<JobApplication> due = jobApplicationRepository.findDueFollowUps(
                user.getUserId(), LocalDateTime.now(), Status.REJECTED, PageRequest.of(0, MAX_NOTIFICATIONS));

        return due.stream().map(job -> {
            NotificationDto dto = new NotificationDto();
            dto.setId("follow-up-" + job.getJobId());
            dto.setType("FOLLOW_UP_DUE");
            dto.setJobId(job.getJobId());
            dto.setCompanyName(job.getCompanyName());
            dto.setJobRole(job.getJobRole());
            dto.setDueAt(job.getFollowUpDate());
            dto.setReminderSentAt(job.getReminderSentAt());
            return dto;
        }).toList();
    }
}
