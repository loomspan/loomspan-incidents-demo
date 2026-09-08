-- Earlier activities predate authentication; leave their actor unknown.
alter table activity add column actor varchar(100);
