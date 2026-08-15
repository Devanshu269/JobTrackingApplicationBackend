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

    Optional<InterviewRound> findByJobRoundIdAndJobApplication_JobId(Integer jobRoundId, Integer jobId);

    @Query("""
            SELECT r FROM InterviewRound r
            JOIN FETCH r.jobApplication j
            WHERE j.user.userId = :userId AND r.roundDate >= :from
            ORDER BY r.roundDate ASC
            """)
    List<InterviewRound> findUpcomingByUser(@Param("userId") Integer userId,
                                            @Param("from") LocalDateTime from);
}
