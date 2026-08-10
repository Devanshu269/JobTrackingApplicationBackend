package com.jobtracker.service;

import com.jobtracker.Utils.JobUtils;
import com.jobtracker.dto.JobApplicationRequestDto;
import com.jobtracker.dto.JobApplicationResponseDto;
import com.jobtracker.dto.JobStatsResponseDto;
import com.jobtracker.enums.JobType;
import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.model.JobApplication;
import com.jobtracker.model.User;
import com.jobtracker.repository.JobApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobUtils jobUtils;
    private final ActivityService activityService;

    public List<JobApplicationResponseDto> listJobs(User user, Status status, Priority priority, JobType jobType, String search) {
        Specification<JobApplication> spec = buildSpec(user.getUserId(), status, priority, jobType, search);
        return jobApplicationRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(jobUtils::toJobResponseDto)
                .toList();
    }

    public JobApplicationResponseDto getJob(User user, Integer jobId) {
        return jobUtils.toJobResponseDto(findOwnedJob(user, jobId));
    }

    @Transactional
    public JobApplicationResponseDto createJob(User user, JobApplicationRequestDto dto) {
        JobApplication job = new JobApplication();
        job.setUser(user);
        jobUtils.applyToEntity(dto, job);
        JobApplication saved = jobApplicationRepository.save(job);
        // After save, so the activity row gets a real jobId rather than null.
        activityService.recordJobCreated(user, saved);
        return jobUtils.toJobResponseDto(saved);
    }

    @Transactional
    public JobApplicationResponseDto updateJob(User user, Integer jobId, JobApplicationRequestDto dto) {
        JobApplication job = findOwnedJob(user, jobId);
        // Must be read BEFORE applyToEntity: that call mutates the managed entity in place, so
        // reading the status afterwards returns the new value and every change logs "X -> X".
        Status previousStatus = job.getStatus();
        jobUtils.applyToEntity(dto, job);
        JobApplication saved = jobApplicationRepository.save(job);
        activityService.recordJobUpdated(user, saved, previousStatus);
        return jobUtils.toJobResponseDto(saved);
    }

    @Transactional
    public void deleteJob(User user, Integer jobId) {
        JobApplication job = findOwnedJob(user, jobId);
        // Logged before the delete, while companyName/jobRole are still readable to snapshot.
        activityService.recordJobDeleted(user, job);
        jobApplicationRepository.delete(job);
    }

    public JobStatsResponseDto getStats(User user) {
        Map<Status, Long> byStatus = new EnumMap<>(Status.class);
        // Zero-fill every status so the frontend can render all buckets without null checks
        for (Status status : Status.values()) {
            byStatus.put(status, 0L);
        }
        for (JobApplicationRepository.StatusCount row : jobApplicationRepository.countGroupedByStatus(user.getUserId())) {
            byStatus.put(row.getStatus(), row.getCount());
        }

        JobStatsResponseDto dto = new JobStatsResponseDto();
        dto.setTotal(jobApplicationRepository.countByUser_UserId(user.getUserId()));
        dto.setByStatus(byStatus);
        return dto;
    }

    /**
     * Single choke point for ownership. Every read/write path goes through here, so another
     * user's job id is indistinguishable from a non-existent one (404, never 403 — a 403
     * would confirm the id exists).
     */
    public JobApplication findOwnedJob(User user, Integer jobId) {
        return jobApplicationRepository.findByJobIdAndUser_UserId(jobId, user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Job application not found"));
    }

    private Specification<JobApplication> buildSpec(Integer userId, Status status, Priority priority, JobType jobType, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Always scoped to the caller — never optional, regardless of other filters
            predicates.add(cb.equal(root.get("user").get("userId"), userId));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (jobType != null) {
                predicates.add(cb.equal(root.get("jobType"), jobType));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("companyName")), like),
                        cb.like(cb.lower(root.get("jobRole")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
