package com.jobtracker.model;

import com.jobtracker.enums.JobType;
import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@EntityListeners(AuditingEntityListener.class)
@Table(name = "job_applications")
@Entity
@Getter
@Setter
public class JobApplication {

    @Id
    @Column(name = "job_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer jobId;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    private User user;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "job_role", nullable = false)
    private String jobRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "job_url")
    private String jobUrl;

    @Column(name = "location")
    private String location;

    /** Remote / hybrid / on-site. Nullable — pre-existing rows have no value and it's optional on create. */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type")
    private JobType jobType;

    @Column(name = "salary_range")
    private String salaryRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private Priority priority;

    @Column(name="recruiter_name")
    private String recruiterName;


    @Column(name="recruiter_email")
    private String recruiterEmail;

    @Column(name="recruiter_phone")
    private String recruiterPhone;

    @Column(name="resume_url")
    private String resumeUrl;

    @Column(name="cover_letter_url")
    private String coverLetterUrl;

    @Column(name="notes")
    private String notes;

    @Column(name="applied_date")
    private LocalDateTime appliedDate;

    @Column(name="follow_up_date")
    private LocalDateTime followUpDate;

    @Column(name="reminder_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean reminderEnabled = false;

    /**
     * When the follow-up reminder email actually went out. Null means "not sent yet".
     *
     * <p>Exists purely for idempotency: the scheduler runs on a fixed cadence, so without a
     * marker every tick after followUpDate passes would re-send. Cleared automatically when
     * followUpDate is changed (see JobUtils.applyToEntity) so a rescheduled follow-up re-arms.
     */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Cascade so deleting a job also removes its rounds/AI results — without this the child
    // rows' FK to job_id blocks the delete (mirrors User -> jobApplications).
    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InterviewRound> interviewRounds;

    @OneToMany(mappedBy = "jobApplication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobAiResult> aiResults;
}
