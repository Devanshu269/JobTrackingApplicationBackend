package com.jobtracker.repository;

import com.jobtracker.enums.Status;
import com.jobtracker.model.JobApplication;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer>, JpaSpecificationExecutor<JobApplication> {

    List<JobApplication> findByUser_UserIdAndJobId(Integer userId, Integer jobId);

    /**
     * Ownership-scoped lookup: returns empty both when the job doesn't exist AND when it
     * belongs to a different user, so callers can 404 without revealing which case it was.
     */
    Optional<JobApplication> findByJobIdAndUser_UserId(Integer jobId, Integer userId);

    long countByUser_UserId(Integer userId);

    @Query("SELECT j.status AS status, COUNT(j) AS count FROM JobApplication j WHERE j.user.userId = :userId GROUP BY j.status")
    List<StatusCount> countGroupedByStatus(@Param("userId") Integer userId);

    /**
     * Raw timestamps for the applications-per-day chart, newest bound applied by the caller.
     *
     * <p>Returns the timestamps rather than a SQL {@code GROUP BY DATE(...)} on purpose. The
     * column stores a UTC-shifted value (serverTimezone=UTC), so grouping in SQL buckets by the
     * <i>UTC</i> date and silently moves late-evening or early-morning entries into the wrong
     * day. Hibernate reads a LocalDateTime back as the value the user originally submitted, so
     * bucketing in Java on {@code toLocalDate()} matches what they'd expect to see — and matches
     * what the frontend's own client-side version already computes.
     *
     * <p>Volumes here are one user's applications over a few weeks, so loading the timestamps is
     * cheap; revisit if that ever stops being true.
     */
    @Query("""
            SELECT COALESCE(j.appliedDate, j.createdAt) FROM JobApplication j
            WHERE j.user.userId = :userId
              AND COALESCE(j.appliedDate, j.createdAt) >= :from
            """)
    List<LocalDateTime> findTrendTimestamps(@Param("userId") Integer userId,
                                            @Param("from") LocalDateTime from);

    /**
     * Everything currently worth surfacing in the notification bell: follow-ups that are due and
     * not in a terminal state. Unlike findDueReminders this ignores reminderSentAt — an already
     * emailed follow-up is still outstanding until the user acts on it — and has no lower bound,
     * because an old overdue follow-up is exactly what a user would want reminding about in-app
     * even though emailing it would be noise.
     */
    @Query("""
            SELECT j FROM JobApplication j
            WHERE j.user.userId = :userId
              AND j.followUpDate IS NOT NULL
              AND j.followUpDate <= :now
              AND j.status <> :excludedStatus
            ORDER BY j.followUpDate ASC
            """)
    List<JobApplication> findDueFollowUps(@Param("userId") Integer userId,
                                          @Param("now") LocalDateTime now,
                                          @Param("excludedStatus") Status excludedStatus,
                                          Pageable pageable);

    /**
     * Jobs whose follow-up is due and whose reminder hasn't gone out yet.
     *
     * <p>Not user-scoped — the only caller is the scheduler, which legitimately runs across all
     * users. Every other query in this repository is scoped; this is the deliberate exception.
     *
     * <p>JOIN FETCH on the user because the scheduler needs the email address and runs outside
     * any request-bound persistence context. Pageable caps the batch so one tick can't try to
     * send thousands of emails.
     *
     * <p><b>{@code notBefore} is the backfill guard.</b> Without a lower bound, the first run
     * after enabling reminders — or after any extended downtime — would email every follow-up
     * that has ever come due, including ones months stale. A follow-up that old is no longer
     * actionable, and mass-sending it is the one failure mode here that reaches real inboxes.
     */
    @Query("""
            SELECT j FROM JobApplication j
            JOIN FETCH j.user
            WHERE j.reminderEnabled = true
              AND j.followUpDate IS NOT NULL
              AND j.followUpDate <= :now
              AND j.followUpDate >= :notBefore
              AND j.reminderSentAt IS NULL
              AND j.status <> :excludedStatus
            ORDER BY j.followUpDate ASC
            """)
    List<JobApplication> findDueReminders(@Param("now") LocalDateTime now,
                                          @Param("notBefore") LocalDateTime notBefore,
                                          @Param("excludedStatus") Status excludedStatus,
                                          Pageable pageable);

    interface StatusCount {
        Status getStatus();
        Long getCount();
    }
}
