package com.jobtracker.Utils;

import com.jobtracker.dto.InterviewRoundRequestDto;
import com.jobtracker.dto.InterviewRoundResponseDto;
import com.jobtracker.dto.JobApplicationRequestDto;
import com.jobtracker.dto.JobApplicationResponseDto;
import com.jobtracker.dto.UpcomingRoundResponseDto;
import com.jobtracker.model.InterviewRound;
import com.jobtracker.model.JobApplication;
import org.springframework.stereotype.Component;

@Component
public class JobUtils {

    /**
     * Copies request fields onto the entity. Shared by create and update so the two can't
     * drift apart when a new field is added.
     */
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
        job.setFollowUpDate(dto.getFollowUpDate());
        // reminderEnabled is NOT NULL in the DB; default it rather than letting a null through
        job.setReminderEnabled(dto.getReminderEnabled() != null ? dto.getReminderEnabled() : Boolean.FALSE);
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

    /** Flattens a round together with its parent job's company/role for the cross-job view. */
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
