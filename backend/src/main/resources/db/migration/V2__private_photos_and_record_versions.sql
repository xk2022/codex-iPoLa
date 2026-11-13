ALTER TABLE observations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE observations ADD COLUMN photo_key VARCHAR(80);
CREATE TABLE observation_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 observation_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL,
 record_version BIGINT NOT NULL,
 occurred_at TIMESTAMP NOT NULL
);
