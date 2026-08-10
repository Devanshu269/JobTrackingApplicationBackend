package com.jobtracker.model;

import com.jobtracker.enums.ActivityAction;
import com.jobtracker.enums.Status;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Append-only audit trail of job/round mutations.
 *
 * <p><b>Deliberately holds no JPA relationships.</b> {@code userId} and {@code jobId} are plain
 * columns, not {@code @ManyToOne} associations, because an audit log has to outlive the things
 * it audits — a deleted job's history must still render. A real FK would either block the
 * delete outright (FK constraint violation, since cascade is declared per-association and this
 * one wouldn't be covered) or, if an inverse cascade were added, silently erase the very
 * history the table exists to keep.
 *
 * <p>For the same reason {@code companyName}/{@code jobRole} are <b>snapshots</b> taken at write
 * time rather than looked up on read: after the job row is gone there is nothing left to join to.
 */
@EntityListeners(AuditingEntityListener.class)
@Table(name = "activity_log", indexes = {
        @Index(name = "idx_activity_user_created", columnList = "user_id, created_at")
})
@Entity
@Getter
@Setter
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id", nullable = false)
    private Integer activityId;

    /** Plain column, not an FK — see class javadoc. Every query is scoped by this. */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** Plain column, not an FK. Stays populated after the job is deleted. */
    @Column(name = "job_id", nullable = false)
    private Integer jobId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "job_role", nullable = false)
    private String jobRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ActivityAction action;

    /** The job's status after the event. Null only where a status isn't meaningful. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    /** Only set on STATUS_CHANGED/OFFER_RECEIVED/REJECTED, so the UI can say "X → Y". */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private Status previousStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
