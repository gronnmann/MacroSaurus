insert into nutrient_definitions(code, display_name, category, unit, sort_order) values
('energy_kcal', 'Energy', 'ENERGY', 'kcal', 10),
('protein_g', 'Protein', 'MACRO', 'g', 20),
('carbohydrate_g', 'Carbohydrate', 'MACRO', 'g', 30),
('fat_g', 'Fat', 'MACRO', 'g', 40),
('fiber_g', 'Fiber', 'MACRO', 'g', 50),
('sugars_g', 'Sugars', 'MACRO', 'g', 60),
('saturated_fat_g', 'Saturated fat', 'MACRO', 'g', 70),
('sodium_mg', 'Sodium', 'MINERAL', 'mg', 100),
('calcium_mg', 'Calcium', 'MINERAL', 'mg', 110),
('iron_mg', 'Iron', 'MINERAL', 'mg', 120),
('potassium_mg', 'Potassium', 'MINERAL', 'mg', 130),
('vitamin_c_mg', 'Vitamin C', 'VITAMIN', 'mg', 200),
('vitamin_d_ug', 'Vitamin D', 'VITAMIN', 'ug', 210);

insert into foods(id, source_kind, external_id, barcode) values
('10000000-0000-0000-0000-000000000001', 'USDA', '171688', null),
('10000000-0000-0000-0000-000000000002', 'USDA', '171077', null),
('10000000-0000-0000-0000-000000000003', 'USDA', '174272', null);

insert into food_revisions(id, food_id, revision, name, basis_type, basis_amount, basis_unit, locale) values
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 1, 'Banana, raw', 'PER_100_G', 100, 'g', 'en'),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 1, 'Egg, whole, cooked', 'PER_100_G', 100, 'g', 'en'),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 1, 'Chicken breast, roasted', 'PER_100_G', 100, 'g', 'en');

insert into food_nutrients(food_revision_id, nutrient_code, amount) values
('20000000-0000-0000-0000-000000000001', 'energy_kcal', 89),
('20000000-0000-0000-0000-000000000001', 'protein_g', 1.09),
('20000000-0000-0000-0000-000000000001', 'carbohydrate_g', 22.84),
('20000000-0000-0000-0000-000000000001', 'fat_g', 0.33),
('20000000-0000-0000-0000-000000000001', 'fiber_g', 2.6),
('20000000-0000-0000-0000-000000000002', 'energy_kcal', 155),
('20000000-0000-0000-0000-000000000002', 'protein_g', 12.58),
('20000000-0000-0000-0000-000000000002', 'carbohydrate_g', 1.12),
('20000000-0000-0000-0000-000000000002', 'fat_g', 10.61),
('20000000-0000-0000-0000-000000000003', 'energy_kcal', 165),
('20000000-0000-0000-0000-000000000003', 'protein_g', 31.02),
('20000000-0000-0000-0000-000000000003', 'carbohydrate_g', 0),
('20000000-0000-0000-0000-000000000003', 'fat_g', 3.57);

insert into portions(id, food_revision_id, name, gram_weight, is_default) values
('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'medium banana', 118, true),
('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000002', 'large egg', 50, true),
('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000003', 'breast', 172, true);
