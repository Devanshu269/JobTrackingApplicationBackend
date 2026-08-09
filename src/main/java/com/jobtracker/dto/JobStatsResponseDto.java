package com.jobtracker.dto;

import com.jobtracker.enums.Status;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class JobStatsResponseDto {

    private long total;

    /** Every Status key is always present, zero-filled, so the frontend never has to null-check. */
    private Map<Status, Long> byStatus;
}
