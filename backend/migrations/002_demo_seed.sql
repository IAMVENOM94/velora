INSERT INTO users(id,email,password_hash,email_verified) VALUES
('00000000-0000-4000-8000-000000000001','demo@velora.app',crypt(encode(gen_random_bytes(32),'hex'),gen_salt('bf',12)),true),
('00000000-0000-4000-8000-000000000002','maya@velora.app',crypt(encode(gen_random_bytes(32),'hex'),gen_salt('bf',12)),true),
('00000000-0000-4000-8000-000000000003','sofia@velora.app',crypt(encode(gen_random_bytes(32),'hex'),gen_salt('bf',12)),true)
ON CONFLICT(id) DO NOTHING;

INSERT INTO profiles(user_id,name,birth_date,gender,city,bio,dating_goal,latitude,longitude,online_at,onboarding_complete) VALUES
('00000000-0000-4000-8000-000000000001','Alex','1997-04-12','man','Lisbon','Art, warm oceans and conversations that make time disappear.','relationship',38.7223,-9.1393,now(),true),
('00000000-0000-4000-8000-000000000002','Maya','1998-06-14','woman','Lisbon','Slow mornings, hidden cafés and spontaneous flights. Looking for something beautifully real.','relationship',38.7430,-9.1450,now(),true),
('00000000-0000-4000-8000-000000000003','Sofia','1996-03-08','woman','Porto','Art director. Sunset swimmer. I will always order dessert for the table.','dating',41.1579,-8.6291,now()-interval '8 minutes',true)
ON CONFLICT(user_id) DO NOTHING;

INSERT INTO preferences(user_id,min_age,max_age,max_distance_km,show_genders) VALUES
('00000000-0000-4000-8000-000000000001',22,38,50,ARRAY['woman']::gender[]),
('00000000-0000-4000-8000-000000000002',22,38,50,ARRAY['man']::gender[]),
('00000000-0000-4000-8000-000000000003',22,38,100,ARRAY['man']::gender[])
ON CONFLICT(user_id) DO NOTHING;

INSERT INTO subscriptions(user_id,tier) VALUES
('00000000-0000-4000-8000-000000000001','free'),
('00000000-0000-4000-8000-000000000002','free'),
('00000000-0000-4000-8000-000000000003','free')
ON CONFLICT(user_id) DO NOTHING;

INSERT INTO user_interests(user_id,interest_id)
SELECT u.id,i.id FROM users u CROSS JOIN interests i
WHERE u.id IN ('00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000003')
AND i.name IN ('Travel','Coffee','Design') ON CONFLICT DO NOTHING;

INSERT INTO likes(from_user_id,to_user_id,kind) VALUES
('00000000-0000-4000-8000-000000000002','00000000-0000-4000-8000-000000000001','like')
ON CONFLICT DO NOTHING;
