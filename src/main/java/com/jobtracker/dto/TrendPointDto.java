package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One day of the applications-per-day chart. */
@Getter
@Setter
public class TrendPointDto {

    /** Serialises as "2026-08-01" — a plain date, no time component, no timezone. */
    private LocalDate date;

    private long count;
}
