create table if not exists ai_model_prompt (
    id varchar(64) primary key,
    relation_model_id varchar(64) not null,
    content clob not null,
    sort_order int default 0,
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);
