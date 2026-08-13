ALTER TABLE users
  ADD CONSTRAINT users_email_normalized CHECK (email = lower(email));

ALTER TABLE profiles
  ADD CONSTRAINT profiles_latitude_range CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  ADD CONSTRAINT profiles_longitude_range CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  ADD CONSTRAINT profiles_dating_goal CHECK (dating_goal IN ('relationship','communication','dating','friendship','unknown'));

ALTER TABLE photos
  ADD CONSTRAINT photos_object_key_unique UNIQUE (object_key);

ALTER TABLE preferences
  ADD CONSTRAINT preferences_show_genders_nonempty CHECK (cardinality(show_genders) > 0),
  ADD CONSTRAINT preferences_max_age_range CHECK (max_age <= 100);

ALTER TABLE discover_actions
  ADD CONSTRAINT discover_actions_not_self CHECK (user_id <> target_user_id);

ALTER TABLE blocks
  ADD CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id);

ALTER TABLE reports
  ADD CONSTRAINT reports_not_self CHECK (reporter_id <> reported_id),
  ADD CONSTRAINT reports_reason_allowed CHECK (reason IN ('harassment','spam','fake','inappropriate','underage','other'));

ALTER TABLE messages
  ADD CONSTRAINT messages_text_length CHECK (body IS NULL OR length(body) BETWEEN 1 AND 4000);

ALTER TABLE refresh_tokens
  ADD CONSTRAINT refresh_token_hash_length CHECK (length(token_hash) = 64);

ALTER TABLE password_reset_tokens
  ADD CONSTRAINT reset_token_hash_length CHECK (length(token_hash) = 64);

CREATE INDEX idx_reports_reported_created ON reports(reported_id,created_at DESC);
CREATE INDEX idx_conversation_members_user ON conversation_members(user_id,conversation_id);
