alter table diary_entries
    add column portion_id uuid references portions(id);

create table user_goal_settings (
    user_id varchar(160) primary key,
    energy_mode varchar(24) not null,
    energy_value numeric(16, 6),
    macro_mode varchar(24) not null,
    protein_g_per_kg numeric(10, 4),
    fat_energy_percent numeric(10, 4),
    weight_basis varchar(24),
    manual_weight_kg numeric(8, 3),
    protein_target_g numeric(16, 6),
    carbohydrate_target_g numeric(16, 6),
    fat_target_g numeric(16, 6),
    protein_energy_percent numeric(10, 4),
    carbohydrate_energy_percent numeric(10, 4),
    updated_at timestamptz not null default current_timestamp
);
