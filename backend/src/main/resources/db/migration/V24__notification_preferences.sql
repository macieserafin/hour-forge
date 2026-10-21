-- Which optional e-mails a person gets. Security mails (sign-in, password, address) are always sent.
ALTER TABLE user_preferences ADD COLUMN team_event_emails boolean NOT NULL DEFAULT true;
ALTER TABLE user_preferences ADD COLUMN weekly_report_email boolean NOT NULL DEFAULT true;
-- Lets a person switch an e-mail kind off from the link in the message without signing in; it can do nothing else.
ALTER TABLE user_preferences ADD COLUMN unsubscribe_token varchar(64);
CREATE UNIQUE INDEX uq_user_preferences_unsubscribe_token ON user_preferences (unsubscribe_token) WHERE unsubscribe_token IS NOT NULL;
