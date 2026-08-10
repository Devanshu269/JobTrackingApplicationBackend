--
-- V2: refresh token rotation.
--
-- Adds the family tracking that rotation needs: familyId groups one login session's
-- rotation chain, familyCreatedAt backs the 30-day absolute cap, rotatedAt records when
-- a token was consumed (and drives the replay grace window).
--
-- The ordering here is not stylistic. Letting Hibernate's ddl-auto add these produced two
-- distinct failures on a table that already had rows:
--
--   * `family_created_at DATETIME NOT NULL` FAILS outright — MySQL backfills existing rows
--     with '0000-00-00 00:00:00', which strict mode rejects. The ALTER aborts, the column is
--     never created, and the application starts anyway because DDL errors are only warnings.
--
--   * `family_id VARCHAR(64) NOT NULL` SUCCEEDS, which is worse. Every pre-existing row is
--     backfilled with the empty string, putting every live session into one shared "family".
--     A single replayed token would then revoke every logged-in user at once.
--
-- Hence: add nullable, backfill deliberately, then apply the constraint.
--

ALTER TABLE refresh_tokens ADD COLUMN family_id VARCHAR(64) NULL;
ALTER TABLE refresh_tokens ADD COLUMN family_created_at DATETIME(6) NULL;
ALTER TABLE refresh_tokens ADD COLUMN rotated_at DATETIME(6) NULL;

-- UUID() is evaluated per row, so each pre-existing token becomes its own family. Sessions
-- that predate rotation are unrelated to each other and must not share a blast radius.
UPDATE refresh_tokens
   SET family_id = UUID()
 WHERE family_id IS NULL OR family_id = '';

-- Reconstruct the original login time. Pre-rotation, expires_at was always set to
-- login + jwt.refresh-expiration (7 days), so subtracting it recovers the session start —
-- which is what the absolute cap measures from.
UPDATE refresh_tokens
   SET family_created_at = expires_at - INTERVAL 7 DAY
 WHERE family_created_at IS NULL;

ALTER TABLE refresh_tokens MODIFY COLUMN family_id VARCHAR(64) NOT NULL;
ALTER TABLE refresh_tokens MODIFY COLUMN family_created_at DATETIME(6) NOT NULL;
