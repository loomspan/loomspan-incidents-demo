CREATE TABLE environment (
  id INT PRIMARY KEY CHECK (id = 1), revision INT NOT NULL,
  checkout_running BOOLEAN NOT NULL, database_running BOOLEAN NOT NULL,
  link_allowed BOOLEAN NOT NULL, bad_deploy BOOLEAN NOT NULL
);
INSERT INTO environment VALUES (1, 1, TRUE, TRUE, TRUE, FALSE);
CREATE TABLE incident (
  id VARCHAR(36) PRIMARY KEY, title VARCHAR(300) NOT NULL,
  status VARCHAR(30) NOT NULL, created_at VARCHAR(40) NOT NULL,
  last_run_id VARCHAR(36)
);
CREATE TABLE investigation (
  id VARCHAR(36) PRIMARY KEY, incident_id VARCHAR(36) NOT NULL REFERENCES incident(id),
  status VARCHAR(30) NOT NULL, created_at VARCHAR(40) NOT NULL,
  snapshot_json CLOB NOT NULL, report_json CLOB, session_id VARCHAR(100),
  events_json CLOB NOT NULL DEFAULT '[]', error_message VARCHAR(500)
);
CREATE TABLE evidence (
  id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL REFERENCES investigation(id),
  created_at VARCHAR(40) NOT NULL, receipt_json CLOB NOT NULL
);
CREATE TABLE activity (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  incident_id VARCHAR(36) REFERENCES incident(id), created_at VARCHAR(40) NOT NULL,
  kind VARCHAR(40) NOT NULL, message VARCHAR(1000) NOT NULL, revision INT NOT NULL
);
CREATE TABLE repair (
  run_id VARCHAR(36) NOT NULL REFERENCES investigation(id), action_id VARCHAR(60) NOT NULL,
  revision INT NOT NULL, PRIMARY KEY (run_id, action_id)
);
