create table if not exists data_source (
    id varchar(64) primary key,
    name varchar(128) not null,
    database_type varchar(32) not null,
    jdbc_url varchar(512) not null,
    username varchar(128) not null,
    password_cipher varchar(1024),
    description varchar(512),
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);

create table if not exists table_metadata (
    id varchar(64) primary key,
    data_source_id varchar(64) not null,
    schema_name varchar(128),
    table_name varchar(128) not null,
    table_comment varchar(512),
    table_type varchar(32),
    created_at timestamp default current_timestamp
);

create table if not exists column_metadata (
    id varchar(64) primary key,
    table_metadata_id varchar(64) not null,
    column_name varchar(128) not null,
    data_type varchar(128),
    column_comment varchar(512),
    nullable_flag boolean,
    primary_key_flag boolean,
    ordinal_position int,
    created_at timestamp default current_timestamp
);

create table if not exists relation_model (
    id varchar(64) primary key,
    data_source_id varchar(64) not null,
    name varchar(128) not null,
    main_table varchar(128),
    description varchar(512),
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);

create table if not exists relation_model_node (
    id varchar(64) primary key,
    relation_model_id varchar(64) not null,
    table_name varchar(128) not null,
    x int default 0,
    y int default 0,
    status varchar(32),
    created_at timestamp default current_timestamp
);

create table if not exists relation_model_edge (
    id varchar(64) primary key,
    relation_model_id varchar(64) not null,
    source_table varchar(128) not null,
    source_column varchar(128) not null,
    target_table varchar(128) not null,
    target_column varchar(128) not null,
    relation_type varchar(32) not null,
    confidence decimal(6, 4),
    confirmed_flag boolean default false,
    enabled_flag boolean default true,
    ai_reason varchar(1024),
    created_at timestamp default current_timestamp
);

create table if not exists relation_query_record (
    id varchar(64) primary key,
    relation_model_id varchar(64) not null,
    main_table varchar(128) not null,
    key_column varchar(128) not null,
    key_value varchar(256) not null,
    status varchar(32),
    created_at timestamp default current_timestamp
);

create table if not exists ai_chat_session (
    id varchar(64) primary key,
    data_source_id varchar(64) not null,
    relation_model_id varchar(64),
    title varchar(256),
    created_at timestamp default current_timestamp
);

create table if not exists ai_chat_message (
    id varchar(64) primary key,
    session_id varchar(64) not null,
    role varchar(32) not null,
    content clob,
    sql_text clob,
    created_at timestamp default current_timestamp
);

create table if not exists sync_task (
    id varchar(64) primary key,
    relation_query_record_id varchar(64) not null,
    target_data_source_id varchar(64),
    sync_mode varchar(32),
    status varchar(32),
    created_at timestamp default current_timestamp
);
