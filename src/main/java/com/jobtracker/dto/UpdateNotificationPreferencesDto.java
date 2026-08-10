package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Partial update of notification preferences — null means "leave unchanged", matching the
 * PATCH semantics used for jobs.
 *
 * <p>No push field: there is no push transport, and a toggle that stores a value nothing reads
 * is worse than an absent one because it looks like it works.
 */
@Getter
@Setter
public class UpdateNotificationPreferencesDto {

    private Boolean emailNotifications;
}
