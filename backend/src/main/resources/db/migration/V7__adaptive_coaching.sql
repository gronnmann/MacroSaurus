create table nutrition_day_reviews (
    user_id varchar(160) not null,
    local_date date not null,
    status varchar(32) not null,
    estimated_total_kcal numeric(12, 2),
    updated_at timestamptz not null default current_timestamp,
    primary key(user_id, local_date),
    constraint nutrition_day_review_estimate check (
        (status = 'ESTIMATED_TOTAL' and estimated_total_kcal is not null and estimated_total_kcal > 0)
        or (status <> 'ESTIMATED_TOTAL' and estimated_total_kcal is null)
    )
);

create table weight_goals (
    id uuid primary key,
    user_id varchar(160) not null,
    goal_type varchar(16) not null,
    starting_weight_kg numeric(8, 3) not null,
    target_weight_kg numeric(8, 3),
    weekly_rate_percent numeric(6, 3) not null,
    status varchar(16) not null,
    started_on date not null,
    ended_on date,
    created_at timestamptz not null default current_timestamp
);
create index weight_goals_user_status_idx on weight_goals(user_id, status, started_on desc);

create table nutrition_program_revisions (
    id uuid primary key,
    user_id varchar(160) not null,
    goal_id uuid references weight_goals(id),
    style varchar(16) not null,
    effective_from date not null,
    effective_to date,
    energy_kcal numeric(12, 2),
    protein_g numeric(12, 2),
    carbohydrate_g numeric(12, 2),
    fat_g numeric(12, 2),
    protein_g_per_kg numeric(8, 3),
    fat_energy_percent numeric(8, 3),
    expenditure_kcal numeric(12, 2),
    expenditure_lower_kcal numeric(12, 2),
    expenditure_upper_kcal numeric(12, 2),
    algorithm_version varchar(40),
    source varchar(24) not null,
    created_at timestamptz not null default current_timestamp,
    constraint nutrition_program_dates check (effective_to is null or effective_to >= effective_from)
);
create index nutrition_program_user_dates_idx on nutrition_program_revisions(user_id, effective_from desc);
create unique index nutrition_program_one_active_idx on nutrition_program_revisions(user_id) where effective_to is null;

create table coaching_setup_drafts (
    user_id varchar(160) primary key,
    current_step integer not null default 1,
    payload jsonb not null,
    updated_at timestamptz not null default current_timestamp
);

create table weekly_check_ins (
    id uuid primary key,
    user_id varchar(160) not null,
    week_start date not null,
    status varchar(16) not null,
    proposal jsonb,
    created_at timestamptz not null default current_timestamp,
    resolved_at timestamptz,
    unique(user_id, week_start)
);

alter table energy_estimates
    add column lower_kcal numeric(12, 2),
    add column upper_kcal numeric(12, 2),
    add column trend_weight_kg numeric(8, 3),
    add column trend_weight_lower_kg numeric(8, 3),
    add column trend_weight_upper_kg numeric(8, 3),
    add column model_state varchar(24) not null default 'BASELINE',
    add column requirements jsonb not null default '{}'::jsonb;
