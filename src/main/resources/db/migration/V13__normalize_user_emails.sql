-- V__normalize_user_emails.sql
UPDATE users SET email = LOWER(TRIM(email));
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));