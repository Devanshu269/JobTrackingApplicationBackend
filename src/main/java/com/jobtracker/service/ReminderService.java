package com.jobtracker.service;

import com.jobtracker.enums.Status;
import com.jobtracker.model.JobApplication;
import com.jobtracker.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final JobApplicationRepository jobApplicationRepository;
    private final EmailService emailService;

    @Value("${app.reminders.enabled:true}")
    private boolean enabled;

    @Value("${app.reminders.batch-size:50}")
    private int batchSize;

    @Value("${app.reminders.max-overdue-days:7}")
    private int maxOverdueDays;

    @Scheduled(cron = "${app.reminders.cron:0 0 * * * *}")
    public void sendDueReminders() {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<JobApplication> due = jobApplicationRepository.findDueReminders(
                now, now.minusDays(maxOverdueDays), Status.REJECTED, PageRequest.of(0, batchSize));

        if (due.isEmpty()) {
            return;
        }
        log.info("Follow-up reminders: {} due", due.size());

        int sent = 0;
        for (JobApplication job : due) {
            try {
                // Global per-user opt-out. Checked here rather than in the query so the row is
                // still marked as handled — otherwise an opted-out user's overdue jobs would be
                // re-selected on every tick forever.
                if (!Boolean.TRUE.equals(job.getUser().getEmailNotifications())) {
                    job.setReminderSentAt(LocalDateTime.now());
                    jobApplicationRepository.save(job);
                    continue;
                }
                emailService.sendFollowUpReminder(
                        job.getUser().getEmail(), job.getCompanyName(), job.getJobRole(), job.getStatus().name());
                // Marked only after the send succeeds. On failure the row keeps a null
                // reminderSentAt and is simply picked up again next tick.
                job.setReminderSentAt(LocalDateTime.now());
                jobApplicationRepository.save(job);
                sent++;
            } catch (Exception e) {
                // Per-job catch so one bad address can't abort the rest of the batch.
                log.error("Follow-up reminder failed for job {}", job.getJobId(), e);
            }
        }
        log.info("Follow-up reminders: {}/{} sent", sent, due.size());
    }
}
