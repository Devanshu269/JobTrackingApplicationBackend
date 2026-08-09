package com.jobtracker.repository;

import com.jobtracker.enums.Status;
import com.jobtracker.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    interface StatusCount {
        Status getStatus();
        Long getCount();
    }
}
