CREATE OR REPLACE FUNCTION sync_profile_location_from_legacy() RETURNS trigger AS $$
BEGIN
  INSERT INTO profile_locations(user_id,city,latitude,longitude,location_source,updated_at)
  VALUES(NEW.user_id,NEW.city,round(NEW.latitude,3),round(NEW.longitude,3),CASE WHEN NEW.latitude IS NULL THEN 'manual' ELSE 'gps' END,now())
  ON CONFLICT(user_id) DO UPDATE SET
    city=EXCLUDED.city,
    latitude=EXCLUDED.latitude,
    longitude=EXCLUDED.longitude,
    location_source=EXCLUDED.location_source,
    updated_at=now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profiles_location_compatibility
AFTER INSERT OR UPDATE OF city,latitude,longitude ON profiles
FOR EACH ROW EXECUTE FUNCTION sync_profile_location_from_legacy();
