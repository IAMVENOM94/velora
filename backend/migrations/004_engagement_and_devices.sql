CREATE TYPE discover_action_kind AS ENUM ('pass', 'like', 'super_like');

CREATE TABLE discover_actions (
  id bigserial PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind discover_action_kind NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_discover_actions_daily
  ON discover_actions(user_id, created_at DESC);
CREATE INDEX idx_discover_actions_passes
  ON discover_actions(user_id, target_user_id, created_at DESC)
  WHERE kind = 'pass';

ALTER TABLE profiles ADD COLUMN boosted_until timestamptz;
CREATE INDEX idx_profiles_boosted
  ON profiles(boosted_until DESC)
  WHERE boosted_until IS NOT NULL;

CREATE TABLE notification_devices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token text NOT NULL UNIQUE,
  platform varchar(20) NOT NULL CHECK (platform IN ('android', 'ios', 'development')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_devices_user ON notification_devices(user_id);
