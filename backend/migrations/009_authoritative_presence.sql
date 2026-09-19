ALTER TABLE profiles
  ADD COLUMN presence_active boolean NOT NULL DEFAULT false;

CREATE INDEX idx_profiles_presence_active
  ON profiles(user_id)
  WHERE presence_active=true;
