package com.jobtracker.Utils;

import com.jobtracker.dto.InterviewRoundRequestDto;
import com.jobtracker.dto.InterviewRoundResponseDto;
import com.jobtracker.dto.JobApplicationPatchDto;
import com.jobtracker.dto.JobApplicationRequestDto;
import com.jobtracker.dto.JobApplicationResponseDto;
import com.jobtracker.dto.UpcomingRoundResponseDto;
import com.jobtracker.model.InterviewRound;
import com.jobtracker.model.JobApplication;
import org.springframework.stereotype.Component;

@Component
public class JobUtils {

    public void applyToEntity(JobApplicationRequestDto dto, JobApplication job) {
        job.setCompanyName(dto.getCompanyName());
        job.setJobRole(dto.getJobRole());
        job.setStatus(dto.getStatus());
        job.setPriority(dto.getPriority());
        job.setJobUrl(dto.getJobUrl());
        job.setLocation(dto.getLocation());
        job.setJobType(dto.getJobType());
        job.setSalaryRange(dto.getSalaryRange());
        job.setRecruiterName(dto.getRecruiterName());
        job.setRecruiterEmail(dto.getRecruiterEmail());
        job.setRecruiterPhone(dto.getRecruiterPhone());
        job.setResumeUrl(dto.getResumeUrl());
        job.setCoverLetterUrl(dto.getCoverLetterUrl());
        job.setNotes(dto.getNotes());
        job.setAppliedDate(dto.getAppliedDate());
        // Compare BEFORE overwriting: if the follow-up moved, clear the sent-marker so the
        // reminder re-arms. Without this a rescheduled follow-up would never fire again,
        // because reminderSentAt still holds the send from the *previous* date.
        if (!java.util.Objects.equals(job.getFollowUpDate(), dto.getFollowUpDate())) {
            job.setReminderSentAt(null);
        }
        job.setFollowUpDate(dto.getFollowUpDate());
        // reminderEnabled is NOT NULL in the DB; default it rather than letting a null through
        job.setReminderEnabled(dto.getReminderEnabled() != null ? dto.getReminderEnabled() : Boolean.FALSE);
    }

    public void applyPatchToEntity(JobApplicationPatchDto dto, JobApplication job) {
        if (dto.getCompanyName() != null) job.setCompanyName(dto.getCompanyName());
        if (dto.getJobRole() != null) job.setJobRole(dto.getJobRole());
        if (dto.getStatus() != null) job.setStatus(dto.getStatus());
        if (dto.getPriority() != null) job.setPriority(dto.getPriority());
        if (dto.getJobType() != null) job.setJobType(dto.getJobType());
        if (dto.getJobUrl() != null) job.setJobUrl(dto.getJobUrl());
        if (dto.getLocation() != null) job.setLocation(dto.getLocation());
        if (dto.getSalaryRange() != null) job.setSalaryRange(dto.getSalaryRange());
        if (dto.getRecruiterName() != null) job.setRecruiterName(dto.getRecruiterName());
        if (dto.getRecruiterEmail() != null) job.setRecruiterEmail(dto.getRecruiterEmail());
        if (dto.getRecruiterPhone() != null) job.setRecruiterPhone(dto.getRecruiterPhone());
        if (dto.getResumeUrl() != null) job.setResumeUrl(dto.getResumeUrl());
        if (dto.getCoverLetterUrl() != null) job.setCoverLetterUrl(dto.getCoverLetterUrl());
        if (dto.getNotes() != null) job.setNotes(dto.getNotes());
        if (dto.getAppliedDate() != null) job.setAppliedDate(dto.getAppliedDate());
        if (dto.getFollowUpDate() != null && !java.util.Objects.equals(job.getFollowUpDate(), dto.getFollowUpDate())) {
            job.setReminderSentAt(null);
            job.setFollowUpDate(dto.getFollowUpDate());
        }
        if (dto.getReminderEnabled() != null) job.setReminderEnabled(dto.getReminderEnabled());
    }

    public JobApplicationResponseDto toJobResponseDto(JobApplication job) {
        JobApplicationResponseDto dto = new JobApplicationResponseDto();
        dto.setJobId(job.getJobId());
        dto.setCompanyName(job.getCompanyName());
        dto.setJobRole(job.getJobRole());
        dto.setStatus(job.getStatus());
        dto.setPriority(job.getPriority());
        dto.setJobUrl(job.getJobUrl());
        dto.setLocation(job.getLocation());
        dto.setJobType(job.getJobType());
        dto.setSalaryRange(job.getSalaryRange());
        dto.setRecruiterName(job.getRecruiterName());
        dto.setRecruiterEmail(job.getRecruiterEmail());
        dto.setRecruiterPhone(job.getRecruiterPhone());
        dto.setResumeUrl(job.getResumeUrl());
        dto.setCoverLetterUrl(job.getCoverLetterUrl());
        dto.setNotes(job.getNotes());
        dto.setAppliedDate(job.getAppliedDate());
        dto.setFollowUpDate(job.getFollowUpDate());
        dto.setReminderEnabled(job.getReminderEnabled());
        dto.setReminderSentAt(job.getReminderSentAt());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUpdatedAt(job.getUpdatedAt());
        return dto;
    }

    public void applyToEntity(InterviewRoundRequestDto dto, InterviewRound round) {
        round.setRoundNumber(dto.getRoundNumber());
        round.setRoundType(dto.getRoundType());
        round.setRoundDate(dto.getRoundDate());
        round.setInterviewerName(dto.getInterviewerName());
        round.setNotes(dto.getNotes());
        round.setFeedback(dto.getFeedback());
        round.setOutcome(dto.getOutcome());
    }

    public InterviewRoundResponseDto toRoundResponseDto(InterviewRound round) {
        InterviewRoundResponseDto dto = new InterviewRoundResponseDto();
        dto.setJobRoundId(round.getJobRoundId());
        dto.setJobId(round.getJobApplication().getJobId());
        dto.setRoundNumber(round.getRoundNumber());
        dto.setRoundType(round.getRoundType());
        dto.setRoundDate(round.getRoundDate());
        dto.setInterviewerName(round.getInterviewerName());
        dto.setNotes(round.getNotes());
        dto.setFeedback(round.getFeedback());
        dto.setOutcome(round.getOutcome());
        dto.setCreatedAt(round.getCreatedAt());
        return dto;
    }

    public UpcomingRoundResponseDto toUpcomingRoundDto(InterviewRound round) {
        JobApplication job = round.getJobApplication();
        UpcomingRoundResponseDto dto = new UpcomingRoundResponseDto();
        dto.setJobRoundId(round.getJobRoundId());
        dto.setJobId(job.getJobId());
        dto.setCompanyName(job.getCompanyName());
        dto.setJobRole(job.getJobRole());
        dto.setRoundNumber(round.getRoundNumber());
        dto.setRoundType(round.getRoundType());
        dto.setRoundDate(round.getRoundDate());
        dto.setInterviewerName(round.getInterviewerName());
        dto.setNotes(round.getNotes());
        dto.setOutcome(round.getOutcome());
        return dto;
    }
}
