package com.jobtracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Table(name = "job_ai_results")
@Entity
@Getter
@Setter
public class JobAiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_result_id", nullable = false)
    private Integer aiResultId;

    @ManyToOne
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = false)
    private JobApplication jobApplication;

    @Column(name = "job_description", columnDefinition = "TEXT", nullable = false)
    private String jobDescription;

    @Column(name="ai_resume_analysis", columnDefinition = "TEXT")
    private String aiResumeAnalysis;

    @Column(name="ai_cover_letter", columnDefinition = "TEXT")
    private String aiCoverLetter;

    @Column(name="jd_match_score")
    private Double jdMatchScore;

    @CreatedDate
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
