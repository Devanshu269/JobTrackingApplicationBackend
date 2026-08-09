package com.jobtracker.dto;

import com.jobtracker.enums.Outcome;
import com.jobtracker.enums.RoundType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A scheduled interview round flattened together with the company/role it belongs to.
 *
 * Distinct from {@link InterviewRoundResponseDto} because this one is read across *all* of a
 * user's jobs at once — the caller has no parent job in hand, so the job context has to travel
 * with each row. Without it a dashboard widget would need one extra fetch per round.
 */
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
