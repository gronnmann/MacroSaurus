create table energy_estimates (
    id uuid primary key,
    user_id varchar(160) not null,
    estimate_date date not null,
    baseline_kcal numeric(12, 2),
    adaptive_kcal numeric(12, 2),
    suggested_kcal numeric(12, 2),
    confidence varchar(24) not null,
    algorithm_version varchar(40) not null,
    explanation jsonb not null,
    created_at timestamptz not null default current_timestamp
);
create index energy_estimates_user_date_idx on energy_estimates(user_id, estimate_date desc);
