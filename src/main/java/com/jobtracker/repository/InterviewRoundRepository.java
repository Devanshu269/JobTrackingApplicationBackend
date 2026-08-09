package com.jobtracker.repository;

import com.jobtracker.model.InterviewRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Integer>{

    List<InterviewRound> findByJobApplication_JobIdOrderByRoundNumberAsc(Integer jobId);

    /**
     * Scoped to the parent job, so a round id from someone else's job can't be reached
     * even if the caller owns *a* job. Ownership of the job itself is checked first.
     */
    Optional<InterviewRound> findByJobRoundIdAndJobApplication_JobId(Integer jobRoundId, Integer jobId);

    /**
     * Every scheduled round across all of the caller's jobs, soonest first.
     *
     * Scoped by user id rather than job id — this is the one round query with no parent job to
     * hang ownership off, so the WHERE clause carries it instead. Rounds with a null roundDate
     * are excluded by the comparison: unscheduled rounds aren't "upcoming".
     *
     * JOIN FETCH pulls the parent job in the same statement so mapping company/role doesn't
     * fire a lazy select per row.
     */
    @Query("""
            SELECT r FROM InterviewRound r
            JOIN FETCH r.jobApplication j
            WHERE j.user.userId = :userId AND r.roundDate >= :from
            ORDER BY r.roundDate ASC
            """)
    List<InterviewRound> findUpcomingByUser(@Param("userId") Integer userId,
                                            @Param("from") LocalDateTime from);
}
