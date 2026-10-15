CREATE TABLE members (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 nickname VARCHAR(60) NOT NULL,
 password_hash VARCHAR(100) NOT NULL
);
CREATE TABLE access_tokens (
 token_hash VARCHAR(64) PRIMARY KEY,
 member_id BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE TABLE animals (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 species VARCHAR(10) NOT NULL,
 name VARCHAR(80) NOT NULL,
 description VARCHAR(1000) NOT NULL,
 creator_id BIGINT NOT NULL,
 FOREIGN KEY (creator_id) REFERENCES members(id),
 CHECK (species IN ('CAT', 'DOG'))
);
CREATE TABLE observations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 animal_id BIGINT,
 member_id BIGINT NOT NULL,
 kind VARCHAR(10) NOT NULL,
 observed_at TIMESTAMP NOT NULL,
 latitude DECIMAL(9,6) NOT NULL,
 longitude DECIMAL(10,6) NOT NULL,
 notes VARCHAR(1000) NOT NULL,
 photo_url VARCHAR(1000),
 FOREIGN KEY (animal_id) REFERENCES animals(id),
 FOREIGN KEY (member_id) REFERENCES members(id),
 CHECK (kind IN ('SIGHTING', 'FEEDING')),
 CHECK (latitude BETWEEN -90 AND 90),
 CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX idx_observation_animal_time ON observations(animal_id, observed_at);
CREATE INDEX idx_observation_member_time ON observations(member_id, observed_at);
