package com.jobtracker.dto;

import com.jobtracker.enums.Outcome;
import com.jobtracker.enums.RoundType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InterviewRoundResponseDto {

    private Integer jobRoundId;
    private Integer jobId;
    private Integer roundNumber;
    private RoundType roundType;
    private LocalDateTime roundDate;
    private String interviewerName;
    private String notes;
    private String feedback;
    private Outcome outcome;
    private LocalDateTime createdAt;
}
