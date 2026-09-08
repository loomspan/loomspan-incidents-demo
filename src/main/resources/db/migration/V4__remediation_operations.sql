CREATE TABLE operation (
  id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL REFERENCES incident(id),
  created_at VARCHAR(40) NOT NULL, mode VARCHAR(20) NOT NULL,
  allowed_actions_json CLOB NOT NULL, max_repairs INT NOT NULL,
  repairs INT NOT NULL DEFAULT 0, status VARCHAR(30) NOT NULL,
  current_run_id VARCHAR(36) NOT NULL, expected_revision INT NOT NULL,
  message VARCHAR(500) NOT NULL
);
ALTER TABLE investigation ADD operation_id VARCHAR(36);
ALTER TABLE investigation ADD correction_json CLOB;
