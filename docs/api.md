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
| `GET` | `/me/goals` | Return calorie and macro goal rules |
| `PUT` | `/me/goals` | Save calorie and macro goal rules |
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

Energy modes are `FIXED`, `MAINTENANCE`, `KCAL_DELTA`, and `PERCENT_DELTA`.
Macro modes are `GUIDED`, `CUSTOM_GRAMS`, and `PERCENT_SPLIT`. Guided mode uses
protein g/kg, fat percentage, and carbohydrate calories as the remainder:

```json
{
  "energyMode": "PERCENT_DELTA",
  "energyValue": -10,
  "macroMode": "GUIDED",
  "proteinGPerKg": 1.8,
  "fatEnergyPercent": 25,
  "weightBasis": "LATEST_WEIGHT"
}
```

Relative energy goals use the latest expenditure estimate. `MANUAL_WEIGHT`
requires `manualWeightKg`; `LATEST_WEIGHT` uses the newest applicable weigh-in.

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
  "consumedAt": "2026-08-17T08:15:00+02:00",
  "meal": "BREAKFAST"
}
```

Quick track:

```json
{
  "name": "Post-workout shake",
  "localDate": "2026-08-17",
  "meal": "SNACK",
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

```json
{
  "weightKg": 72.4,
  "measuredAt": "2026-08-17T07:10:00+02:00",
  "note": "morning"
}
```

Append `?persist=true` to the expenditure endpoint to store the calculated
estimate. Adaptive mode requires 14 logged diary days and four weigh-ins spanning
at least 14 days within the 21-day window.

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
