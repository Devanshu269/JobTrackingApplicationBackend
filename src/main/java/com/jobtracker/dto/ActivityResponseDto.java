package com.jobtracker.dto;

import com.jobtracker.enums.ActivityAction;
import com.jobtracker.enums.Status;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Field names match the event shape the frontend's buildActivityFeed() already produces
 * ({@code id, action, jobId, companyName, jobRole, status, timestamp}), plus
 * {@code previousStatus} which the derived feed could never supply.
 */
@Getter
@Setter
public class ActivityResponseDto {

    private Integer id;
    private ActivityAction action;
    private Integer jobId;
    private String companyName;
    private String jobRole;
    private Status status;
    private Status previousStatus;
    private LocalDateTime timestamp;
}
