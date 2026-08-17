create table user_profiles (
    user_id varchar(160) primary key,
    display_name varchar(120) not null,
    locale varchar(35) not null default 'en',
    timezone varchar(80) not null default 'UTC',
    unit_system varchar(16) not null default 'METRIC',
    birth_date date,
    height_cm numeric(8, 3),
    formula_sex varchar(16),
    activity_multiplier numeric(6, 3) not null default 1.2,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table nutrient_definitions (
    code varchar(80) primary key,
    display_name varchar(120) not null,
    category varchar(24) not null,
    unit varchar(16) not null,
    sort_order integer not null default 1000
);

create table foods (
    id uuid primary key,
    owner_user_id varchar(160),
    source_kind varchar(24) not null,
    external_id varchar(160),
    barcode varchar(32),
    created_at timestamptz not null default current_timestamp,
    constraint foods_owner_source_check check (
        (source_kind = 'USER' and owner_user_id is not null) or source_kind <> 'USER'
    )
);
create index foods_barcode_idx on foods(barcode);
create index foods_owner_idx on foods(owner_user_id);

create table food_revisions (
    id uuid primary key,
    food_id uuid not null references foods(id),
    revision integer not null,
    name varchar(240) not null,
    brand varchar(160),
    basis_type varchar(24) not null,
    basis_amount numeric(16, 6) not null,
    basis_unit varchar(16) not null,
    density_g_per_ml numeric(16, 8),
    locale varchar(35),
    source_payload jsonb,
    created_at timestamptz not null default current_timestamp,
    unique(food_id, revision)
);
create index food_revisions_food_idx on food_revisions(food_id, revision desc);

create table food_nutrients (
    food_revision_id uuid not null references food_revisions(id) on delete cascade,
    nutrient_code varchar(80) not null references nutrient_definitions(code),
    amount numeric(20, 8) not null,
    value_kind varchar(20) not null default 'REPORTED',
    primary key(food_revision_id, nutrient_code)
);

create table portions (
    id uuid primary key,
    food_revision_id uuid not null references food_revisions(id) on delete cascade,
    name varchar(100) not null,
    quantity numeric(16, 6) not null default 1,
    gram_weight numeric(16, 6),
    milliliter_volume numeric(16, 6),
    is_default boolean not null default false
);

create table diary_entries (
    id uuid primary key,
    user_id varchar(160) not null,
    local_date date not null,
    consumed_at timestamptz not null,
    meal varchar(40) not null,
    display_name varchar(240) not null,
    entry_type varchar(24) not null,
    source_revision_id uuid,
    quantity numeric(16, 6),
    unit varchar(16),
    nutrients jsonb not null,
    created_at timestamptz not null default current_timestamp
);
create index diary_entries_user_date_idx on diary_entries(user_id, local_date);

create table recipes (
    id uuid primary key,
    owner_user_id varchar(160) not null,
    created_at timestamptz not null default current_timestamp
);

create table recipe_revisions (
    id uuid primary key,
    recipe_id uuid not null references recipes(id),
    revision integer not null,
    name varchar(240) not null,
    servings numeric(12, 4) not null,
    explicit_yield_g numeric(16, 6),
    estimated_yield_g numeric(16, 6),
    nutrients jsonb not null,
    created_at timestamptz not null default current_timestamp,
    unique(recipe_id, revision)
);

create table recipe_ingredients (
    id uuid primary key,
    recipe_revision_id uuid not null references recipe_revisions(id) on delete cascade,
    food_revision_id uuid not null references food_revisions(id),
    quantity numeric(16, 6) not null,
    unit varchar(16) not null,
    portion_id uuid references portions(id),
    resolved_grams numeric(16, 6),
    nutrients jsonb not null
);

create table weight_measurements (
    id uuid primary key,
    user_id varchar(160) not null,
    measured_at timestamptz not null,
    weight_kg numeric(8, 3) not null,
    note varchar(500),
    created_at timestamptz not null default current_timestamp
);
create index weight_measurements_user_time_idx on weight_measurements(user_id, measured_at desc);

create table share_links (
    id uuid primary key,
    owner_user_id varchar(160) not null,
    token_hash varchar(128) not null unique,
    resource_type varchar(24) not null,
    resource_revision_id uuid not null,
    snapshot jsonb not null,
    expires_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null default current_timestamp
);

create table scan_jobs (
    id uuid primary key,
    user_id varchar(160) not null,
    status varchar(24) not null,
    result jsonb,
    error_message varchar(500),
    created_at timestamptz not null default current_timestamp,
    expires_at timestamptz not null
);
