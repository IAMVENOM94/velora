ALTER TABLE users
  ADD COLUMN deleted_at timestamptz,
  ADD COLUMN purge_after timestamptz,
  ADD COLUMN suspended_at timestamptz;

ALTER TABLE profiles
  ADD COLUMN verified boolean NOT NULL DEFAULT false;

ALTER TABLE preferences
  ADD COLUMN show_online_status boolean NOT NULL DEFAULT true,
  ADD COLUMN show_distance boolean NOT NULL DEFAULT true,
  ADD COLUMN show_city boolean NOT NULL DEFAULT true,
  ADD COLUMN discoverable boolean NOT NULL DEFAULT true,
  ADD COLUMN read_receipts boolean NOT NULL DEFAULT true;

ALTER TABLE photos
  ADD COLUMN mime_type varchar(40),
  ADD COLUMN size_bytes bigint,
  ADD COLUMN width integer,
  ADD COLUMN height integer,
  ADD COLUMN thumbnail_key text,
  ADD COLUMN medium_key text,
  ADD COLUMN full_key text,
  ADD COLUMN processing_status varchar(24) NOT NULL DEFAULT 'legacy',
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE photos DROP CONSTRAINT photos_user_id_position_key;
ALTER TABLE photos ADD CONSTRAINT photos_user_position_unique UNIQUE(user_id,position) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE photos
  ADD CONSTRAINT photos_size_valid CHECK (size_bytes IS NULL OR size_bytes BETWEEN 1 AND 10485760),
  ADD CONSTRAINT photos_dimensions_valid CHECK (
    (width IS NULL AND height IS NULL) OR
    (width BETWEEN 1 AND 12000 AND height BETWEEN 1 AND 12000)
  ),
  ADD CONSTRAINT photos_processing_status CHECK (processing_status IN ('legacy','processing','ready','rejected'));

CREATE UNIQUE INDEX idx_photos_thumbnail_key ON photos(thumbnail_key) WHERE thumbnail_key IS NOT NULL;
CREATE UNIQUE INDEX idx_photos_medium_key ON photos(medium_key) WHERE medium_key IS NOT NULL;
CREATE UNIQUE INDEX idx_photos_full_key ON photos(full_key) WHERE full_key IS NOT NULL;
CREATE INDEX idx_photos_ready_user_position ON photos(user_id,position) WHERE processing_status='ready';

ALTER TABLE messages
  ADD COLUMN client_message_id uuid,
  ADD COLUMN delivered_at timestamptz;

UPDATE messages SET delivered_at=created_at WHERE delivered_at IS NULL;

CREATE UNIQUE INDEX idx_messages_sender_client_id
  ON messages(sender_id,client_message_id)
  WHERE client_message_id IS NOT NULL;
CREATE INDEX idx_messages_cursor
  ON messages(conversation_id,created_at DESC,id DESC);

ALTER TABLE reports
  ADD COLUMN moderation_status varchar(24) NOT NULL DEFAULT 'pending',
  ADD COLUMN retained_until timestamptz,
  ADD CONSTRAINT reports_moderation_status CHECK (moderation_status IN ('pending','reviewing','resolved','dismissed'));

ALTER TABLE notification_devices
  ADD COLUMN enabled boolean NOT NULL DEFAULT true,
  ADD COLUMN last_seen_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN failure_count smallint NOT NULL DEFAULT 0 CHECK (failure_count BETWEEN 0 AND 100);

CREATE TABLE profile_views (
  viewer_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  viewed_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  viewed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(viewer_id,viewed_id,viewed_at),
  CHECK(viewer_id<>viewed_id)
);
CREATE INDEX idx_profile_views_viewer_time ON profile_views(viewer_id,viewed_at DESC);

CREATE TABLE rate_limit_events (
  id bigserial PRIMARY KEY,
  scope varchar(40) NOT NULL,
  subject_hash char(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_rate_limit_scope_subject_time
  ON rate_limit_events(scope,subject_hash,created_at DESC);

CREATE TABLE notification_delivery_attempts (
  id bigserial PRIMARY KEY,
  notification_id uuid NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  device_id uuid REFERENCES notification_devices(id) ON DELETE SET NULL,
  provider varchar(24) NOT NULL,
  status varchar(24) NOT NULL CHECK (status IN ('queued','sent','failed','disabled')),
  error_code varchar(80),
  attempted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_delivery_notification ON notification_delivery_attempts(notification_id,attempted_at DESC);

CREATE INDEX idx_matches_cursor ON matches(created_at DESC,id DESC) WHERE unmatched_at IS NULL;
CREATE INDEX idx_notifications_cursor ON notifications(user_id,created_at DESC,id DESC);
CREATE INDEX idx_users_beta_active ON users(id) WHERE active=true AND deleted_at IS NULL AND suspended_at IS NULL;
