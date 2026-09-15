-- Phase 1 of the LINE Messaging API integration: manual ALTER TABLE for the two EXISTING
-- tables this feature extends. schema.sql only ever runs CREATE TABLE IF NOT EXISTS on
-- every boot (spring.sql.init.mode=always) — it does NOT retroactively alter a table that
-- already exists, so the new LINE_ACCOUNT_ID/DISPLAY_NAME/AVATAR_URL columns must be added
-- here, by hand, once, against the live database. The two brand-new tables (LINE_ACCOUNT,
-- LINE_USER) do NOT need this file — they're created automatically on the next app boot.
--
-- Before running: back up first.
--   mysqldump --single-transaction crm_v2 MESSAGE ADMIN_USER > crm-backups/crm_pre_line_integration_$(date +%Y%m%d_%H%M%S).sql
--
-- Then run this file against the live database, e.g.:
--   podman exec -i crm-mysql mysql -ucrm_user -p<password> crm_v2 < ops/scripts/line-integration-migrate.sql
--
-- Both statements are additive (new nullable columns only) and safe to run while the app
-- is up — no table lock beyond the brief metadata change, no existing data touched.

ALTER TABLE MESSAGE
  ADD COLUMN LINE_ACCOUNT_ID BIGINT DEFAULT NULL
    COMMENT 'which LINE Official Account this message went through — NULL for non-LINE channels'
    AFTER MESSAGE_ID_HEADER,
  ADD KEY IDX_MSG_LINE_ACCOUNT (LINE_ACCOUNT_ID);

ALTER TABLE ADMIN_USER
  ADD COLUMN DISPLAY_NAME VARCHAR(255) DEFAULT NULL
    COMMENT 'LINE persona shown to customers via sender-name override; NULL falls back to NAME'
    AFTER IS_ACTIVE,
  ADD COLUMN AVATAR_URL VARCHAR(500) DEFAULT NULL
    COMMENT 'LINE persona icon URL (sender.iconUrl) — must be a public HTTPS URL'
    AFTER DISPLAY_NAME;
