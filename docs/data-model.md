# Data model and nutrition rules

## Schema evolution

Flyway applies migrations in order:

| Migration | Contents |
|---|---|
| `V1__initial_schema.sql` | Profiles, nutrients, foods, diary, recipes, weights, shares, scans |
| `V2__seed_nutrients_and_foods.sql` | Canonical starter nutrients and three representative USDA foods |
| `V3__energy_estimates.sql` | Persisted expenditure estimates |
| `V4__nutrient_targets.sql` | Reference-set structure and custom user targets |
| `V5__app_tracking_and_goals.sql` | Diary portion identity and calorie/macro goal rules |
| `V6__tracking_history_indexes.sql` | History lookup indexes |
| `V7__adaptive_coaching.sql` | Setup drafts, weight goals, program revisions, check-ins, day review, and uncertainty fields |

Never edit a migration after it has been applied outside an expendable local
database. Add a new numbered migration.

## Canonical nutrients

`nutrient_definitions` is data-driven. A nutrient has:

- Stable canonical code such as `protein_g` or `vitamin_d_ug`.
- Display name.
- Category such as `MACRO`, `MINERAL`, or `VITAMIN`.
- Canonical unit.
- Sort order.

Food nutrient maps refer to these codes. Adding a nutrient therefore does not
require adding a column to every food or diary table.

Missing and zero are different:

- Missing: the source did not provide the nutrient.
- Zero: the source explicitly reported or the user entered zero.

Aggregation sums known values. It must not manufacture missing nutrient keys with
zero merely to make a chart look complete.

## Foods and revisions

`foods` holds identity, ownership, barcode, and source. `food_revisions` holds the
editable presentation and nutrition basis. `food_nutrients` and `portions` belong
to a specific revision.

Editing a private food inserts another revision. External-source foods cannot be
edited through `PUT /foods/{id}`.

Sources:

- `USDA`: seeded/imported reference data.
- `OPEN_FOOD_FACTS`: records obtained from OFF.
- `USER`: private foods requiring `owner_user_id`.

Source records are not field-merged. A client may rank candidates and let the
user choose, but a resulting record retains one provenance.

## Nutrition bases

| Basis | Meaning |
|---|---|
| `PER_100_G` | Stored amounts apply to the configured gram basis, normally 100 g |
| `PER_100_ML` | Stored amounts apply to the configured volume basis, normally 100 ml |
| `PER_SERVING` | Stored amounts apply to a named/logical serving with no required mass |

Mass-to-volume or volume-to-mass conversion is allowed only when the food has
`densityGPerMl`. This prevents a 100 ml beverage label from being silently treated
as 100 g.

A portion may define a gram weight or milliliter volume. Examples:

- `medium banana = 118 g`
- `large egg = 50 g`
- `tablespoon = 15 ml`

## Diary snapshots

Diary entries contain:

- User and local date.
- Actual timestamp with offset.
- Meal slot and display name.
- Type: food, recipe, or quick entry.
- Optional source revision.
- Entered amount/unit.
- Full nutrient snapshot in `jsonb`.

The snapshot guarantees that correcting a food next week does not rewrite what a
user saw and logged today.

Quick entries store supplied macros. If calories are omitted, the current factors
are protein 4, carbohydrate 4, fat 9, and alcohol 7 kcal/g. If `saveAsFood` is
true, the same snapshot becomes a private `PER_SERVING` food.

## Recipes and yield

A recipe owns immutable revisions. Each ingredient stores:

- Food revision.
- Entered quantity/unit/portion.
- Resolved gram amount when possible.
- Nutrient snapshot.

Total nutrients are the sum of ingredient snapshots. Per-serving nutrients divide
by the recipe's serving count.

Yield precedence:

1. `explicit_yield_g`, representing finished/cooked weight.
2. `estimated_yield_g`, available only when every ingredient resolves to mass.
3. No per-100-g result if neither is available.

## Goals and nutrition programs

`weight_goals` stores a user's intended direction, target weight, weekly rate,
and lifecycle. Only the active goal drives coaching. Rerunning setup archives the
old goal and creates a new one rather than rewriting history.

`nutrition_program_revisions` stores date-effective calories, macros, expenditure
inputs, algorithm version, source, and coached/manual style. Each user has at most
one open-ended revision. Accepting a check-in closes the previous revision and
starts another, so historical diary dates resolve against the targets in effect
at the time.

`coaching_setup_drafts` contains the current step and JSON payload. The draft is
deleted only after setup completes. `weekly_check_ins` records the Monday week,
calculated proposal, and whether it was accepted or skipped.

## Reviewed nutrition, weigh-ins, and expenditure

Weight is stored in kilograms regardless of display preference.

`nutrition_day_reviews` is separate from diary entries. Confirmed days have full
weight in the model, estimates have partial weight, and excluded/fasting days do
not contribute intake. An estimate never fabricates foods or nutrient snapshots.

The baseline estimate uses Mifflin–St Jeor when the profile provides adult age,
height, supported formula sex, activity multiplier, and a weight measurement.

Adaptive eligibility requires, within the 21-day window:

- At least 14 effective reviewed nutrition days.
- At least four distinct weigh-in days.
- At least 14 days between first and last relevant measurement.
- At least one weigh-in in the most recent seven days.

`energy-v2` uses a Huber-weighted linear regression for weight trend, combines
mean reviewed intake with weight change using 7,700 kcal/kg, and blends that
adaptive result with the baseline by inverse uncertainty. It exposes 95% model
ranges for both expenditure and trend weight. These are model uncertainty ranges,
not guaranteed physiological bounds or clinical predictions.

Persisted estimates include algorithm version, inputs-derived explanation,
confidence, model state, uncertainty bounds, requirements, and date so future
algorithm versions do not become ambiguous.

## Sharing

Creating a link serializes an immutable food or recipe revision snapshot. The raw
32-byte random token is returned to the client; only its SHA-256 hash is stored.
Links can expire or be revoked. Editing the original resource never changes an
existing snapshot.

## Label scans

The database stores scan status, structured result, error message, and expiry.
Image data URLs are forwarded directly to OpenRouter and are not written to the
database. Confirmation clears the stored draft after creating the corrected
private food.

The expiry timestamp is stored but is not currently enforced during reads, and an
automatic expired-row cleanup job is not yet implemented.
