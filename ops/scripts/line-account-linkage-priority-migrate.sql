-- Adds a per-account priority used to pick ONE account when a customer is friended with more
-- than one LINE account and a "紐づきアカ" (dynamic account) send/step needs to choose exactly
-- one (client request 2026-09-27: only one message should ever be delivered, deterministically,
-- not one per linked account). Lower value = higher priority. Existing rows default to 100
-- (lowest priority) so nothing changes in effective behavior until an admin explicitly ranks
-- specific accounts higher.

ALTER TABLE LINE_ACCOUNT
  ADD COLUMN LINKAGE_PRIORITY INT NOT NULL DEFAULT 100
    COMMENT '複数アカウントに紐づく顧客がいる場合の優先順位。小さいほど優先'
    AFTER IS_GROUP_CHAT_MODE;
