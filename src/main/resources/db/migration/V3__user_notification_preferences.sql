--
-- V3: per-user notification preferences.
--
-- Only email exists. A `push_notifications` column was deliberately NOT added: there is no push
-- infrastructure anywhere in either repo (no service worker, no FCM, no web-push keys), so the
-- toggle would store a value nothing ever reads — the same "shipping fiction" that got the
-- notification toggles removed from the Settings page in the first place. Add it alongside a
-- real transport, not before.
--
-- Defaults to TRUE so existing users keep the behaviour they have today (reminders were
-- unconditional before this), rather than silently opting everyone out.
--

ALTER TABLE users ADD COLUMN email_notifications TINYINT(1) NOT NULL DEFAULT 1;
