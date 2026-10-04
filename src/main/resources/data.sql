-- Idempotent seed data (INSERT IGNORE skips if unique key exists)
-- Default admin: login=admin / password=admin123 — change after first login.
-- Only on an empty ADMIN_USER table: the login ID is editable on 権限設定 (総長), so keying on
-- LOGIN_ID ('admin') would re-create admin/admin123 on the next start after a rename.

INSERT INTO ADMIN_USER (NAME, LOGIN_ID, LOGIN_PASSWORD, ROLE, IS_ACTIVE, CREATED_AT, UPDATED_AT)
SELECT '管理者', 'admin',
       '$2a$12$KM0KkUiDmOcl7LtYXpWdfeLLbxmnjMJhLfjwkRdLoNy/Efas8A0Cu',
       'ADMIN', 1, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ADMIN_USER);

INSERT INTO REPLY_PAGE_SETTING (ID, DEFAULT_HEADER_HTML, DEFAULT_CSS, FOOTER_HTML, REQUIRE_LOGIN, UPDATED_AT)
SELECT 1, NULL, NULL, NULL, 0, NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM REPLY_PAGE_SETTING WHERE ID = 1);
