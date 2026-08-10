package com.jobtracker.dto;

import com.jobtracker.enums.JobType;
import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobApplicationResponseDto {

    private Integer jobId;
    private String companyName;
    private String jobRole;
    private Status status;
    private Priority priority;
    private String jobUrl;
    private String location;
    private JobType jobType;
    private String salaryRange;
    private String recruiterName;
    private String recruiterEmail;
    private String recruiterPhone;
    private String resumeUrl;
    private String coverLetterUrl;
    private String notes;
    private LocalDateTime appliedDate;
    private LocalDateTime followUpDate;
    private Boolean reminderEnabled;
    /** Null means the follow-up reminder hasn't gone out yet — lets the UI tell pending from sent. */
    private LocalDateTime reminderSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
