package com.jobtracker.dto;

import com.jobtracker.enums.JobType;
import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Partial update. Every field is optional.
 *
 * <p><b>Null means "leave unchanged", not "clear this field."</b> A plain POJO cannot distinguish
 * an omitted key from an explicit {@code null} — Jackson deserialises both to null — so treating
 * null as a clear would make {@code {"status":"INTERVIEW"}} silently wipe every other column,
 * which is the exact bug PATCH exists to avoid. To clear a field, use {@code PUT} with the full
 * object.
 *
 * <p>Validation annotations here fire only when a value is actually present, so a patch that omits
 * a field can never fail its constraint.
 */
@Getter
@Setter
public class JobApplicationPatchDto {

    @Size(max = 100, message = "Company name must be at most 100 characters")
    private String companyName;

    @Size(max = 100, message = "Job role must be at most 100 characters")
    private String jobRole;

    private Status status;
    private Priority priority;
    private JobType jobType;
    private String jobUrl;
    private String location;
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
