ALTER TABLE environment ADD checkout_b_running BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE environment ADD demand_rps INT NOT NULL DEFAULT 60 CHECK (demand_rps BETWEEN 1 AND 400);
-- Commissioning B changes the world, invalidating pre-upgrade repair proposals.
UPDATE environment SET revision=revision+1 WHERE id=1;
INSERT INTO activity(incident_id,created_at,kind,message,revision)
SELECT NULL, CAST(CURRENT_TIMESTAMP AS VARCHAR), 'UPGRADE',
       'Checkout B commissioned. Capacity is 100 requests/s per online instance; target is two instances.', revision
FROM environment WHERE id=1;
