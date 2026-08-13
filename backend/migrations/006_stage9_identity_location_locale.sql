ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE auth_identities (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider varchar(20) NOT NULL CHECK (provider IN ('email','google','phone','x','vk')),
  provider_subject varchar(255) NOT NULL,
  email text,
  phone varchar(16),
  email_verified boolean NOT NULL DEFAULT false,
  phone_verified boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(provider,provider_subject),
  UNIQUE(user_id,provider),
  CHECK (email IS NULL OR email=lower(email)),
  CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}$')
);

INSERT INTO auth_identities(user_id,provider,provider_subject,email,email_verified,created_at,last_used_at)
SELECT id,'email',lower(email),lower(email),email_verified,created_at,updated_at
FROM users WHERE email IS NOT NULL
ON CONFLICT(provider,provider_subject) DO NOTHING;

ALTER TABLE profiles ADD COLUMN preferred_locale varchar(12);
ALTER TABLE profiles ADD CONSTRAINT profiles_preferred_locale CHECK (preferred_locale IS NULL OR preferred_locale IN ('ru','en'));

CREATE TABLE profile_locations (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  country varchar(80),
  region varchar(120),
  city varchar(120) NOT NULL,
  latitude numeric(8,3),
  longitude numeric(9,3),
  location_source varchar(12) NOT NULL CHECK (location_source IN ('gps','manual')),
  accuracy_m integer CHECK (accuracy_m IS NULL OR accuracy_m BETWEEN 0 AND 100000),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  CHECK ((latitude IS NULL) = (longitude IS NULL))
);

INSERT INTO profile_locations(user_id,city,latitude,longitude,location_source,updated_at)
SELECT user_id,city,round(latitude,3),round(longitude,3),CASE WHEN latitude IS NULL THEN 'manual' ELSE 'gps' END,now()
FROM profiles
ON CONFLICT(user_id) DO NOTHING;

CREATE INDEX idx_profile_locations_coordinates ON profile_locations(latitude,longitude) WHERE latitude IS NOT NULL;

CREATE TABLE social_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider varchar(20) NOT NULL CHECK (provider IN ('instagram','x','vk')),
  username varchar(80) NOT NULL,
  profile_url varchar(500) NOT NULL,
  provider_user_id varchar(255),
  verified boolean NOT NULL DEFAULT false,
  visible_in_profile boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_id,provider)
);

CREATE UNIQUE INDEX idx_social_provider_subject ON social_profiles(provider,provider_user_id) WHERE provider_user_id IS NOT NULL;

CREATE TABLE phone_otp_challenges (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  phone varchar(16) NOT NULL CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
  code_hash char(64) NOT NULL,
  request_ip inet,
  device_id varchar(128) NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  failed_attempts smallint NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 10),
  locked_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_phone_otp_phone_created ON phone_otp_challenges(phone,created_at DESC);
CREATE INDEX idx_phone_otp_ip_created ON phone_otp_challenges(request_ip,created_at DESC);
CREATE INDEX idx_phone_otp_device_created ON phone_otp_challenges(device_id,created_at DESC);

CREATE TABLE oauth_authorization_states (
  state_hash char(64) PRIMARY KEY,
  provider varchar(20) NOT NULL CHECK (provider IN ('x','vk')),
  mode varchar(10) NOT NULL CHECK (mode IN ('login','link')),
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  verifier_ciphertext text NOT NULL,
  redirect_uri varchar(500) NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK ((mode='link')=(user_id IS NOT NULL))
);

CREATE INDEX idx_oauth_states_expiry ON oauth_authorization_states(expires_at) WHERE consumed_at IS NULL;
