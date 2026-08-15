package com.jobtracker.service;

import com.jobtracker.dto.ActivityResponseDto;
import com.jobtracker.dto.PagedResponseDto;
import com.jobtracker.enums.ActivityAction;
import com.jobtracker.enums.Status;
import com.jobtracker.model.ActivityLog;
import com.jobtracker.model.JobApplication;
import com.jobtracker.model.User;
import com.jobtracker.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActivityService {

    /** Hard ceiling on a read — the table only grows, so an unbounded request is never valid. */
    public static final int MAX_LIMIT = 100;
    public static final int DEFAULT_LIMIT = 20;

    private final ActivityLogRepository activityLogRepository;

    public void recordJobCreated(User user, JobApplication job) {
        save(user, job, ActivityAction.JOB_CREATED, job.getStatus(), null);
    }

    public void recordJobDeleted(User user, JobApplication job) {
        save(user, job, ActivityAction.JOB_DELETED, job.getStatus(), null);
    }

    public void recordRoundScheduled(User user, JobApplication job) {
        save(user, job, ActivityAction.ROUND_SCHEDULED, job.getStatus(), null);
    }

    public void recordJobUpdated(User user, JobApplication job, Status previousStatus) {
        Status current = job.getStatus();
        if (Objects.equals(previousStatus, current)) {
            save(user, job, ActivityAction.JOB_UPDATED, current, null);
            return;
        }
        // Terminal states get their own action so the UI can celebrate/commiserate rather than
        // rendering a generic "moved to". Mirrors the frontend's editAction() semantics, except
        // here we actually know the status changed instead of inferring it.
        ActivityAction action = switch (current) {
            case OFFER -> ActivityAction.OFFER_RECEIVED;
            case REJECTED -> ActivityAction.REJECTED;
            default -> ActivityAction.STATUS_CHANGED;
        };
        save(user, job, action, current, previousStatus);
    }

    public PagedResponseDto<ActivityResponseDto> listRecent(User user, Integer page, Integer size) {
        // Math.clamp is Java 21; this project targets 17.
        int pageSize = size == null ? DEFAULT_LIMIT : Math.max(1, Math.min(size, MAX_LIMIT));
        int pageNumber = page == null ? 0 : Math.max(0, page);
        return PagedResponseDto.from(
                activityLogRepository.findByUserIdOrderByCreatedAtDesc(
                        user.getUserId(), PageRequest.of(pageNumber, pageSize)),
                this::toDto);
    }

    private void save(User user, JobApplication job, ActivityAction action, Status status, Status previousStatus) {
        ActivityLog log = new ActivityLog();
        log.setUserId(user.getUserId());
        log.setJobId(job.getJobId());
        // Snapshots, not lookups — the job row may be gone by the time this is read back.
        log.setCompanyName(job.getCompanyName());
        log.setJobRole(job.getJobRole());
        log.setAction(action);
        log.setStatus(status);
        log.setPreviousStatus(previousStatus);
        activityLogRepository.save(log);
    }

    private ActivityResponseDto toDto(ActivityLog log) {
        ActivityResponseDto dto = new ActivityResponseDto();
        dto.setId(log.getActivityId());
        dto.setAction(log.getAction());
        dto.setJobId(log.getJobId());
        dto.setCompanyName(log.getCompanyName());
        dto.setJobRole(log.getJobRole());
        dto.setStatus(log.getStatus());
        dto.setPreviousStatus(log.getPreviousStatus());
        dto.setTimestamp(log.getCreatedAt());
        return dto;
    }
}
