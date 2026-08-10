--
-- V4: composite index for the jobs list query.
--
-- Every read of GET /api/jobs filters on user_id and sorts by created_at DESC. The only index
-- on the table was the single-column foreign key on user_id, so MySQL narrowed by user and then
-- filesorted the remainder. Mirrors idx_activity_user_created, which already has this shape for
-- the identical access pattern on activity_log.
--
-- created_at is indexed DESC-friendly by ordering alone; MySQL can scan a B-tree backwards, so a
-- plain ascending index serves ORDER BY created_at DESC without a separate descending index.
--

CREATE INDEX idx_job_applications_user_created
    ON job_applications (user_id, created_at);
