package com.jobtracker.dto;

import com.jobtracker.enums.Outcome;
import com.jobtracker.enums.RoundType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class InterviewRoundRequestDto {

    @NotNull(message = "Round number is required")
    @Min(value = 1, message = "Round number must be at least 1")
    private Integer roundNumber;

    @NotNull(message = "Round type is required")
    private RoundType roundType;

    private LocalDateTime roundDate;

    private String interviewerName;

    private String notes;

    private String feedback;

    private Outcome outcome;
}
