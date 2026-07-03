create table if not exists ai_model_summary (
    relation_model_id varchar(64) primary key,
    summary clob not null,
    provider varchar(32),
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);

