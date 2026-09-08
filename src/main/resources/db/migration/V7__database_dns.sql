alter table environment add column dns_healthy boolean not null default true;
-- Existing environments remain identical; absent DNS in historical snapshots means healthy.
