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
     * Jobs whose follow-up is due and whose reminder hasn't gone out yet.
     *
     * <p>Not user-scoped — the only caller is the scheduler, which legitimately runs across all
     * users. Every other query in this repository is scoped; this is the deliberate exception.
     *
     * <p>JOIN FETCH on the user because the scheduler needs the email address and runs outside
     * any request-bound persistence context. Pageable caps the batch so one tick can't try to
     * send thousands of emails.
     */
    @Query("""
            SELECT j FROM JobApplication j
            JOIN FETCH j.user
            WHERE j.reminderEnabled = true
              AND j.followUpDate IS NOT NULL
              AND j.followUpDate <= :now
              AND j.reminderSentAt IS NULL
              AND j.status <> :excludedStatus
            ORDER BY j.followUpDate ASC
            """)
    List<JobApplication> findDueReminders(@Param("now") LocalDateTime now,
                                          @Param("excludedStatus") Status excludedStatus,
                                          Pageable pageable);

    interface StatusCount {
        Status getStatus();
        Long getCount();
    }
}
