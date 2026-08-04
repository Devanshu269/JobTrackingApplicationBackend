package com.jobtracker.model;

import com.jobtracker.enums.Outcome;
import com.jobtracker.enums.RoundType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@EntityListeners(AuditingEntityListener.class)
@Table(name = "interview_rounds")
@Entity
@Getter
@Setter
public class InterviewRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_round_id", nullable = false)
    private Integer jobRoundId;

    @ManyToOne
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = false)
    private JobApplication jobApplication;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "round_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RoundType roundType;

    @Column(name = "round_date")
    private LocalDateTime roundDate;

    @Column(name = "interviewer_name")
    private String interviewerName;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name="outcome")
    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
