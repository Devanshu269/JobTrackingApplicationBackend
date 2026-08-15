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

    Optional<JobApplication> findByJobIdAndUser_UserId(Integer jobId, Integer userId);

    long countByUser_UserId(Integer userId);

    @Query("SELECT j.status AS status, COUNT(j) AS count FROM JobApplication j WHERE j.user.userId = :userId GROUP BY j.status")
    List<StatusCount> countGroupedByStatus(@Param("userId") Integer userId);

    @Query("""
            SELECT COALESCE(j.appliedDate, j.createdAt) FROM JobApplication j
            WHERE j.user.userId = :userId
              AND COALESCE(j.appliedDate, j.createdAt) >= :from
            """)
    List<LocalDateTime> findTrendTimestamps(@Param("userId") Integer userId,
                                            @Param("from") LocalDateTime from);

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
