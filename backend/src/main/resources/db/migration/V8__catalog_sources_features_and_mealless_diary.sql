alter table diary_entries drop column meal;

alter table foods add column active boolean not null default true;

create unique index foods_source_external_id_idx
    on foods(source_kind, external_id)
    where external_id is not null;

create table food_source_releases (
    id uuid primary key,
    source_kind varchar(40) not null,
    release_key varchar(120) not null,
    checksum varchar(128) not null,
    status varchar(24) not null,
    record_count integer not null default 0,
    imported_at timestamptz not null default current_timestamp,
    unique(source_kind, release_key, checksum)
);

alter table food_revisions
    add column source_release_id uuid references food_source_releases(id);

create table food_aliases (
    food_id uuid not null references foods(id) on delete cascade,
    locale varchar(35) not null,
    name varchar(240) not null,
    primary key(food_id, locale, name)
);
create index food_aliases_name_idx on food_aliases(lower(name));

create table user_feature_grants (
    user_id varchar(160) not null references user_profiles(user_id) on delete cascade,
    feature_code varchar(80) not null,
    enabled boolean not null default true,
    granted_by varchar(160) not null,
    granted_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    primary key(user_id, feature_code)
);

insert into nutrient_definitions(code, display_name, category, unit, sort_order)
values
    ('cholesterol_mg', 'Cholesterol', 'MICRONUTRIENT', 'mg', 75),
    ('trans_fat_g', 'Trans fat', 'MACRO', 'g', 45),
    ('monounsaturated_fat_g', 'Monounsaturated fat', 'MACRO', 'g', 46),
    ('polyunsaturated_fat_g', 'Polyunsaturated fat', 'MACRO', 'g', 47),
    ('vitamin_a_ug', 'Vitamin A', 'MICRONUTRIENT', 'ug', 110),
    ('vitamin_e_mg', 'Vitamin E', 'MICRONUTRIENT', 'mg', 120),
    ('vitamin_k_ug', 'Vitamin K', 'MICRONUTRIENT', 'ug', 130),
    ('thiamin_mg', 'Thiamin', 'MICRONUTRIENT', 'mg', 140),
    ('riboflavin_mg', 'Riboflavin', 'MICRONUTRIENT', 'mg', 150),
    ('niacin_mg', 'Niacin', 'MICRONUTRIENT', 'mg', 160),
    ('vitamin_b6_mg', 'Vitamin B6', 'MICRONUTRIENT', 'mg', 170),
    ('folate_ug', 'Folate', 'MICRONUTRIENT', 'ug', 180),
    ('vitamin_b12_ug', 'Vitamin B12', 'MICRONUTRIENT', 'ug', 190),
    ('magnesium_mg', 'Magnesium', 'MICRONUTRIENT', 'mg', 200),
    ('phosphorus_mg', 'Phosphorus', 'MICRONUTRIENT', 'mg', 210),
    ('zinc_mg', 'Zinc', 'MICRONUTRIENT', 'mg', 220),
    ('copper_mg', 'Copper', 'MICRONUTRIENT', 'mg', 230),
    ('manganese_mg', 'Manganese', 'MICRONUTRIENT', 'mg', 240),
    ('selenium_ug', 'Selenium', 'MICRONUTRIENT', 'ug', 250)
on conflict (code) do nothing;
