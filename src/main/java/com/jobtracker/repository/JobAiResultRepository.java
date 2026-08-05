package com.jobtracker.repository;

import com.jobtracker.model.JobAiResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobAiResultRepository extends JpaRepository<JobAiResult, Integer> {

    List<JobAiResult> findByJobApplication_JobIdOrderByGeneratedAtDesc(Integer jobId);
}
