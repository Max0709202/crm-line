-- Adds a reply-delay setting to LINE auto-reply rules ("何分後に返信", client request 2026-09-23).
-- LINE_AUTO_REPLY_RULE already exists in production; this is a single additive column with a
-- DEFAULT, so existing rows keep sending immediately (0 = no delay) with no backfill needed.

ALTER TABLE LINE_AUTO_REPLY_RULE
  ADD COLUMN DELAY_MINUTES INT NOT NULL DEFAULT 0
    COMMENT '返信までの遅延（分）。0=即時。実際の送信は既存のQUEUEDディスパッチャーが処理する'
    AFTER REPLY_BODY;
