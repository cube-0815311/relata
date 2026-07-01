alter table data_source add column if not exists status varchar(32) default 'NOT_TESTED';
alter table data_source add column if not exists last_tested_at timestamp;
alter table data_source add column if not exists last_collected_at timestamp;

alter table column_metadata add column if not exists foreign_key_flag boolean default false;
