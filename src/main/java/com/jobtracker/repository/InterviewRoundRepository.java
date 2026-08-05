package com.jobtracker.repository;

import com.jobtracker.model.InterviewRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewRoundRepository extends JpaRepository<InterviewRound, Integer>{

    List<InterviewRound> findByJobApplication_JobIdOrderByRoundNumberAsc(Integer jobId);
}
