package com.jobtracker.dto;

import com.jobtracker.enums.JobType;
import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class JobApplicationRequestDto {

    @NotBlank(message = "Company name cannot be blank")
    @Size(max = 100, message = "Company name must be at most 100 characters")
    private String companyName;

    @NotBlank(message = "Job role cannot be blank")
    @Size(max = 100, message = "Job role must be at most 100 characters")
    private String jobRole;

    @NotNull(message = "Status is required")
    private Status status;

    private Priority priority;

    private String jobUrl;

    private String location;

    private JobType jobType;

    private String salaryRange;

    private String recruiterName;

    @Email(message = "Invalid recruiter email format")
    private String recruiterEmail;

    private String recruiterPhone;

    private String resumeUrl;

    private String coverLetterUrl;

    private String notes;

    private LocalDateTime appliedDate;

    private LocalDateTime followUpDate;

    private Boolean reminderEnabled;
}
