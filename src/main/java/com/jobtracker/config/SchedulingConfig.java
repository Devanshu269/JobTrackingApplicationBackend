package com.jobtracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} support. Kept as its own config rather than annotating the main
 * application class, matching how JpaAuditingConfig is organised.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
