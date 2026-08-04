package com.jobtracker.model;

import com.jobtracker.enums.Priority;
import com.jobtracker.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
