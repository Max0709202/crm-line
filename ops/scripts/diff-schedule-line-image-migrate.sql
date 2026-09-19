-- Adds LINE and image-insertion support to the diff-schedule feature (差分予約).
-- DIFF_STEP/DIFF_SCHEDULE_STEP were created empty-safe by schema.sql's CREATE TABLE IF NOT
-- EXISTS on an earlier deploy; both tables currently have 0 rows in production (checked
-- 2026-09-19), so this is a trivial, instant, zero-risk ALTER — no backup step needed, same
-- as the LINE_USER Phase 2 migration.

ALTER TABLE DIFF_STEP
  ADD COLUMN LINE_ACCOUNT_ID BIGINT DEFAULT NULL
    COMMENT 'required when CHANNEL=LINE — which registered LINE account to send from'
    AFTER MEMO_SLOT,
  ADD COLUMN IMAGE_ID BIGINT DEFAULT NULL
    COMMENT 'required when STEP_TYPE=MESSAGE_IMAGE — an existing HTML_IMAGE row inserted into the body'
    AFTER LINE_ACCOUNT_ID;

ALTER TABLE DIFF_SCHEDULE_STEP
  ADD COLUMN LINE_ACCOUNT_ID BIGINT DEFAULT NULL
    COMMENT 'copy of DIFF_STEP.LINE_ACCOUNT_ID at SET time'
    AFTER MEMO_SLOT_SNAPSHOT,
  ADD COLUMN IMAGE_ID_SNAPSHOT BIGINT DEFAULT NULL
    COMMENT 'copy of DIFF_STEP.IMAGE_ID at SET time'
    AFTER LINE_ACCOUNT_ID;
