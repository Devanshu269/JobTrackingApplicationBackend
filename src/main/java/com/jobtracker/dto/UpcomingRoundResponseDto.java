package com.jobtracker.dto;

import com.jobtracker.enums.Outcome;
import com.jobtracker.enums.RoundType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpcomingRoundResponseDto {

    private Integer jobRoundId;
    private Integer jobId;
    private String companyName;
    private String jobRole;
    private Integer roundNumber;
    private RoundType roundType;
    private LocalDateTime roundDate;
    private String interviewerName;
    private String notes;
    private Outcome outcome;
}
