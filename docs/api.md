# API guide

## Base URLs and documentation

Local base URL: `http://localhost:8080/api/v1`

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Health: `http://localhost:8080/actuator/health`

All request and response bodies use JSON. Dates use `YYYY-MM-DD`; instants use
ISO-8601 offsets such as `2026-08-17T10:30:00+02:00`.

## Authentication

Production requests use:

```http
Authorization: Bearer <Supabase access token>
```

When `DEV_AUTH_ENABLED=true`, authentication is disabled for development. Send
a stable test identity with:

```http
X-User-Id: demo-user
```

Without either, the local identity is `dev-user`.

## Errors

Domain and validation failures use RFC 9457-style problem responses:

```json
{
  "type": "https://api.macrosaurus.app/problems/unprocessable_entity",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Density is required to convert this volume-based food to grams"
}
```

Validation errors may include an `errors` object keyed by field name.

## Profiles and nutrient targets

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/me/profile` | Return the current profile or local defaults |
| `PUT` | `/me/profile` | Create/update profile inputs |
| `GET` | `/me/targets` | List every nutrient and current custom target |
| `PUT` | `/me/targets/{nutrientCode}` | Set target/minimum/maximum |
| `DELETE` | `/me/targets/{nutrientCode}` | Clear a custom target |
| `GET` | `/me/goals/resolved?from={date}&to={date}` | Resolve up to 31 days of targets |

Profile example:

```json
{
  "displayName": "Ada",
  "locale": "en-NO",
  "timezone": "Europe/Oslo",
  "unitSystem": "METRIC",
  "birthDate": "1990-05-10",
  "heightCm": 171.5,
  "formulaSex": "FEMALE",
  "activityMultiplier": 1.4
}
```

Target example:

```json
{
  "targetAmount": 120,
  "minimumAmount": 100,
  "maximumAmount": 150
}
```

At least one target field is required, and minimum cannot exceed maximum.

## Feature access and administration

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/me/features` | Return admin status and AI scan grant/provider availability |
| `GET` | `/admin/users?query={text}` | Admin-only profile/grant search |
| `PUT` | `/admin/users/{userId}/features/ai-label-scan` | Admin-only idempotent grant update with `{ "enabled": true }` |
| `POST` | `/admin/catalog-imports` | Admin-only normalized Matvaretabellen/USDA release import |

Administrator identity comes only from `ADMIN_USER_IDS`; it is not assignable
through the API. AI scan endpoints return 403 without a grant and 503 when the
configured provider is unavailable. See the integrations guide for the catalog
release contract.

## Goal setup and weekly coaching

The setup draft is persisted after each step, so an interrupted onboarding or
profile rerun resumes on another device. Completing setup atomically saves the
profile, initial weigh-in, active weight goal, and a dated nutrition-program
revision.

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/me/coaching/status` | Setup state, active goal/program, and Monday check-in due date |
| `GET` | `/me/coaching/setup-draft` | Resume setup or populate a rerun from current values |
| `PUT` | `/me/coaching/setup-draft` | Save the current setup step and inputs |
| `POST` | `/me/coaching/setup-draft/preview` | Preview expenditure, targets, and estimated completion |
| `POST` | `/me/coaching/setup-draft/complete` | Start or revise the active goal and program |
| `GET` | `/me/coaching/check-ins/current` | Open the due weekly review and detect incomplete nutrition days |
| `POST` | `/me/coaching/check-ins/{id}/refresh` | Recalculate expenditure and propose targets after data review |
| `POST` | `/me/coaching/check-ins/{id}/accept` | Accept the proposal as a new dated program revision |
| `POST` | `/me/coaching/check-ins/{id}/skip` | Keep current targets and close this week's review |

Setup accepts `LOSS`, `MAINTAIN`, or `GAIN`, plus either a `COACHED` adaptive
program or fixed `MANUAL` calories and macros. Coached loss rates are 0.25–1.0%
of body weight per week and gain rates are 0.10–0.50%:

```json
{
  "currentStep": 5,
  "displayName": "Ada",
  "locale": "en-NO",
  "timezone": "Europe/Oslo",
  "birthDate": "1990-05-10",
  "heightCm": 171.5,
  "formulaSex": "FEMALE",
  "activityMultiplier": 1.4,
  "weightKg": 72.4,
  "goalType": "LOSS",
  "targetWeightKg": 68,
  "weeklyRatePercent": 0.5,
  "programStyle": "COACHED",
  "proteinGPerKg": 1.8,
  "fatEnergyPercent": 25
}
```

Accepted target changes are limited to 100 kcal/day per weekly review. Manual
programs still receive trend and expenditure insights, but check-ins do not alter
their targets.

## Nutrients and foods

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/nutrients` | List canonical nutrient definitions |
| `GET` | `/foods?query={text}&limit=25` | Search public and current user's private foods |
| `GET` | `/foods/{foodId}` | Return latest accessible revision |
| `GET` | `/food-revisions/{revisionId}` | Return one accessible revision |
| `POST` | `/foods` | Create private food revision 1 |
| `PUT` | `/foods/{foodId}` | Add a revision to an owned user food |
| `POST` | `/food-revisions/{revisionId}/resolve` | Resolve quantity/portion into nutrients |

Create a per-100-g food:

```json
{
  "name": "Homemade granola",
  "brand": null,
  "barcode": null,
  "basisType": "PER_100_G",
  "basisAmount": 100,
  "basisUnit": "g",
  "nutrients": {
    "energy_kcal": 460,
    "protein_g": 12.5,
    "carbohydrate_g": 58,
    "fat_g": 20,
    "fiber_g": 8
  },
  "portions": [
    {
      "name": "scoop",
      "quantity": 1,
      "gramWeight": 32,
      "default": true
    }
  ]
}
```

Valid bases are `PER_100_G`, `PER_100_ML`, and `PER_SERVING`. A named portion
requires either `gramWeight` or `milliliterVolume`. Mass/volume crossing requires
`densityGPerMl` on the food.

Resolve 1.5 scoops:

```json
{
  "quantity": 1.5,
  "unit": "portion",
  "portionId": "<portion UUID>"
}
```

## Diary and quick tracking

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/diary-days/{date}` | Entries and nutrient totals for one date |
| `GET` | `/diary-days?from={date}&to={date}` | Up to 93 inclusive days |
| `PUT` | `/diary-days/{date}/analysis` | Confirm, estimate, exclude, or mark a day as fasting for coaching |
| `POST` | `/diary-entries/food` | Track a resolved food amount |
| `POST` | `/diary-entries/recipe` | Track recipe servings |
| `POST` | `/quick-entries` | Track macros without requiring a food |
| `DELETE` | `/diary-entries/{entryId}` | Delete owned entry |
| `PUT` | `/diary-entries/{entryId}` | Edit timing and type-specific values |
| `POST` | `/diary-entries/{entryId}/copies` | Copy the exact entry to another date/time |
| `GET` | `/trackables?query={text}&type=ALL&limit=30` | Search foods and recipes together |
| `GET` | `/trackables/{type}/revisions/{revisionId}/last-amount` | Last valid quantity/unit for a food or recipe |
| `GET` | `/trackables/suggestions/time-of-day?type=ALL&limit=5` | Habitual items around the user's current local time |

Track 118 g of a food:

```json
{
  "foodRevisionId": "<food revision UUID>",
  "quantity": 118,
  "unit": "g",
  "localDate": "2026-08-17",
  "consumedAt": "2026-08-17T08:15:00+02:00"
}
```

Quick track:

```json
{
  "name": "Post-workout shake",
  "localDate": "2026-08-17",
  "calories": null,
  "proteinG": 30,
  "carbohydrateG": 20,
  "fatG": 4,
  "fiberG": 2,
  "saveAsFood": true
}
```

If calories are absent, the API calculates them with 4 kcal/g protein, 4 kcal/g
carbohydrate, 9 kcal/g fat, and 7 kcal/g alcohol when present. An explicit calorie
value is preserved even when it differs from the calculated value.

Edits keep the original food or recipe revision. Food and recipe nutrients are
re-resolved from the edited quantity; quick-entry nutrients come from the edited
macro fields. Copying preserves the exact original nutrient snapshot:

```json
{
  "destinationDate": "2026-08-18",
  "destinationTime": "08:15"
}
```

Omit `destinationTime` to preserve the original wall-clock time in the user's
profile timezone.

The weekly check-in flags blank days and unusually low days. A review status is
one of `CONFIRMED_COMPLETE`, `ESTIMATED_TOTAL`, `EXCLUDED`, or `FASTING`.
`ESTIMATED_TOTAL` requires `estimatedTotalKcal`. Estimated totals affect only the
expenditure model; they never insert synthetic diary foods or macros.

## Recipes

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/recipes` | List latest owned recipe revisions |
| `GET` | `/recipes/{recipeId}` | Get latest owned revision |
| `GET` | `/recipes/revisions/{revisionId}` | Get one owned revision |
| `POST` | `/recipes` | Create recipe and revision 1 |
| `PUT` | `/recipes/{recipeId}` | Add an immutable revision |

```json
{
  "name": "Banana egg pancakes",
  "servings": 2,
  "finishedWeightG": 280,
  "ingredients": [
    {
      "foodRevisionId": "<banana revision UUID>",
      "quantity": 118,
      "unit": "g"
    },
    {
      "foodRevisionId": "<egg revision UUID>",
      "quantity": 2,
      "unit": "portion",
      "portionId": "<large egg portion UUID>"
    }
  ]
}
```

The response exposes total nutrients, per-serving nutrients, and per-100-g
nutrients when an explicit or complete estimated yield is available.

## Weight and expenditure

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/weight-measurements?limit=100` | List newest first |
| `POST` | `/weight-measurements` | Add 10–700 kg measurement |
| `DELETE` | `/weight-measurements/{id}` | Delete owned measurement |
| `GET` | `/expenditure-estimates/current` | Calculate current estimate |
| `GET` | `/expenditure-estimates/series?from={date}&to={date}` | Weight/expenditure trend and uncertainty series |

```json
{
  "weightKg": 72.4,
  "measuredAt": "2026-08-17T07:10:00+02:00",
  "note": "morning"
}
```

Append `?persist=true` to the current endpoint to store the calculated estimate.
`energy-v2` starts with Mifflin–St Jeor, then uses reviewed intake and a robust
21-day weight regression. Adaptive mode requires 14 effective reviewed days and
four weigh-in days spanning at least 14 days, including a recent weigh-in.
Responses include calorie and trend-weight lower/upper bounds, confidence, and a
model state: `BASELINE`, `UPDATING`, `HOLDING`, or `INSUFFICIENT`.

## Barcodes and label extraction

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/barcodes/{eanOrUpc}` | Validate and find ranked candidates |
| `POST` | `/barcodes/{eanOrUpc}/import` | Import the first candidate |
| `POST` | `/food-scans` | Extract a structured draft from one label photo |
| `GET` | `/food-scans/{scanId}` | Retrieve owned scan status/draft |
| `POST` | `/food-scans/{scanId}/confirm` | Create corrected private food |

Image requests contain JPEG, PNG, or WebP data URLs:

```json
{
  "image": "data:image/jpeg;base64,<base64>",
  "barcode": "3017620422003",
  "localeHint": "nb-NO"
}
```

Confirmation uses the same body as `POST /foods`; the extraction is only a draft.
The draft exposes both `per100Nutrients` and `perServingNutrients`, plus the
normalized `nutrients` selected for the food basis.
Barcode camera frames never use this endpoint. The web client decodes the camera
stream locally and calls `/barcodes/{eanOrUpc}` with text only.

## Sharing

| Method | Path | Behavior |
|---|---|---|
| `POST` | `/share-links` | Snapshot an owned food or recipe revision |
| `DELETE` | `/share-links/{shareId}` | Revoke owned link |
| `GET` | `/shared/{rawToken}` | Publicly retrieve active snapshot |

```json
{
  "resourceType": "RECIPE",
  "resourceRevisionId": "<recipe revision UUID>",
  "expiresAt": "2026-09-01T00:00:00Z"
}
```

The raw token is returned only when creating the share. Store the share ID if the
client needs to revoke it later.

## Current API limitations

- Search uses a `limit` rather than cursor pagination.
- There are no personal access token or third-party app-registration endpoints.
- There is no bulk export/account-deletion endpoint yet.
- OpenAPI is generated from controllers; no generated frontend client is wired in.
- Idempotency keys and ETags are not implemented yet.
