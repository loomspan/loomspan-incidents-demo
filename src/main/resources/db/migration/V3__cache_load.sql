ALTER TABLE environment ADD cache_running BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE environment ADD cache_hit_percent INT NOT NULL DEFAULT 90 CHECK (cache_hit_percent BETWEEN 0 AND 100);
CREATE SEQUENCE receipt_sequence START WITH 1;
UPDATE environment SET revision=revision+1 WHERE id=1;
INSERT INTO activity(incident_id,created_at,kind,message,revision)
SELECT NULL, CAST(CURRENT_TIMESTAMP AS VARCHAR), 'UPGRADE',
       'Cache commissioned at 90% hit rate. Database capacity is 200 operations/s; checkout requires one write and one read per cache miss.', revision
FROM environment WHERE id=1;
