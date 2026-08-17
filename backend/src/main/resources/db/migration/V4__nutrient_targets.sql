create table reference_intake_sets (
    code varchar(80) primary key,
    display_name varchar(160) not null,
    region varchar(40) not null,
    source_version varchar(80) not null,
    published_at date
);

create table reference_intake_values (
    set_code varchar(80) not null references reference_intake_sets(code),
    nutrient_code varchar(80) not null references nutrient_definitions(code),
    target_amount numeric(20, 8),
    minimum_amount numeric(20, 8),
    maximum_amount numeric(20, 8),
    primary key(set_code, nutrient_code)
);

create table user_nutrient_targets (
    user_id varchar(160) not null,
    nutrient_code varchar(80) not null references nutrient_definitions(code),
    target_amount numeric(20, 8),
    minimum_amount numeric(20, 8),
    maximum_amount numeric(20, 8),
    updated_at timestamptz not null default current_timestamp,
    primary key(user_id, nutrient_code),
    constraint user_target_has_value check (
        target_amount is not null or minimum_amount is not null or maximum_amount is not null
    )
);
