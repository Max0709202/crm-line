-- Phase 2 of the LINE Messaging API integration. LINE_USER was created empty in Phase 1
-- (schema.sql's CREATE TABLE IF NOT EXISTS ran once on that deploy) — this adds two columns
-- discovered to be necessary once inbound-message handling was actually implemented: an
-- unlinked LineUser (no CRM_USER match yet) has nowhere else to show "here's what they
-- said", since MESSAGE.USER_ID is NOT NULL with a real FK and can't reference a customer
-- that doesn't exist yet. Table is empty in production, so this is a trivial, instant,
-- zero-risk ALTER — no backup step needed the way the Phase 1 migration's MESSAGE/ADMIN_USER
-- alters did (those had real rows).

ALTER TABLE LINE_USER
  ADD COLUMN LAST_MESSAGE_PREVIEW TEXT DEFAULT NULL
    COMMENT 'most recent inbound message body, kept here since MESSAGE.USER_ID is NOT NULL and this row may still be unlinked'
    AFTER LINE_PICTURE_URL,
  ADD COLUMN LAST_MESSAGE_AT DATETIME DEFAULT NULL
    AFTER LAST_MESSAGE_PREVIEW;
