package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One entry for the notification bell.
 *
 * <p>Exists so the bell doesn't have to pull the whole jobs list to work out what's due — which
 * stopped being viable at all once GET /api/jobs became paginated.
 */
@Getter
@Setter
public class NotificationDto {

    /** Stable within a response; suitable as a React key. */
    private String id;

    /** Currently only FOLLOW_UP_DUE. Kept as a field so more kinds can be added without a shape change. */
    private String type;

    private Integer jobId;
    private String companyName;
    private String jobRole;

    /** The follow-up date that made this due. */
    private LocalDateTime dueAt;

    /** Null if no reminder email has gone out — lets the UI say "due" vs "we emailed you". */
    private LocalDateTime reminderSentAt;
}
