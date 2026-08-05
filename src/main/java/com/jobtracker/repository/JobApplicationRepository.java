package com.jobtracker.repository;

import com.jobtracker.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer>, JpaSpecificationExecutor<JobApplication> {

    List<JobApplication> findByUser_UserIdAndJobId(Integer userId, Integer jobId);
}
