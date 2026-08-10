package com.jobtracker.repository;

import com.jobtracker.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Integer> {

    /**
     * Scoped by user id in the query itself — this table has no parent entity to hang an
     * ownership check off, same reasoning as findUpcomingByUser.
     *
     * <p>Pageable carries the row limit. Unlike job_applications this table only ever grows,
     * so an unbounded read is not an option.
     */
    Page<ActivityLog> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
}
