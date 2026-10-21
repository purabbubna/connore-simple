-- Automated integration tests only. Never used by startup or normal setup.
-- The integration runner refuses nonempty databases and names without _test.
INSERT INTO category(category_id,name) VALUES(1,'Integration test category');
INSERT INTO interest(interest_id,name,category_id) VALUES(1,'Integration test interest',1);
INSERT INTO community(community_id,interest_id,description) VALUES(1,1,'Integration test only');
