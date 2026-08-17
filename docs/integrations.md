# External integrations

## USDA FoodData Central

### Current implementation

Migration `V2__seed_nutrients_and_foods.sql` includes three representative USDA
foods and common nutrient definitions so the app works immediately.

This is not the planned complete USDA seed pipeline. A production importer still
needs to:

1. Download a pinned FoodData Central bulk release.
2. Record release/version metadata and checksums.
3. Map FDC nutrient IDs into canonical nutrient codes.
4. Load staging tables and validate counts/units.
5. Publish atomically without overwriting historical revisions.
6. Preserve the FDC ID and attribution.

USDA FoodData Central data is published as CC0/public domain, but attribution
should still be shown in product and API surfaces.

## Open Food Facts

`OpenFoodFactsClient` calls the current v3 product endpoint for an exact barcode.
It requests only code, name, brand, and nutriments.

Required production configuration:

```text
OFF_BASE_URL=https://world.openfoodfacts.org
OFF_USER_AGENT=Macrosaurus/<version> (<contact email>)
```

Behavior:

- EAN/UPC is normalized to digits and checksum-validated before the request.
- Local accessible matches win; OFF is queried only if none exist.
- The adapter maps known nutrient fields into canonical codes.
- Sodium reported in grams per 100 g is converted to milligrams.
- Import creates an OFF-source food; it does not merge fields with USDA/user data.

Operational/legal requirements:

- Respect API rate limits; this endpoint is not suitable for search-as-you-type.
- Cache/retry behavior is not implemented yet.
- Keep OFF data source-isolated and attributed.
- Review ODbL and Database Contents licensing before production reuse.
- Use OFF staging when developing write support. Macrosaurus currently performs
  reads only.

## OpenRouter label extraction

The label extractor calls:

```text
POST {OPENROUTER_BASE_URL}/chat/completions
```

It submits one to four JPEG/PNG/WebP data URLs and requests a strict JSON Schema
result containing:

- Product/brand/barcode.
- Nutrition basis and quantity.
- Optional serving mass/volume.
- Nutrient code, amount, unit, and confidence.
- Ingredients, allergens, and warnings.

Provider routing includes:

```json
{
  "require_parameters": true,
  "data_collection": "deny"
}
```

Model selection is configuration, not a domain decision. A replacement model must
support image input and `json_schema` response formatting.

Privacy behavior:

- Image data is held in request memory and forwarded to OpenRouter.
- Macrosaurus does not write image bytes to PostgreSQL or MinIO.
- The structured draft is stored for review.
- Confirmation clears the draft and creates a private food.
- The user must review/correct every draft; confidence is informational.

Current limitations:

- Extraction is synchronous and can hold an HTTP request open.
- There is no retry/backoff, cost budget, provider fallback, or circuit breaker.
- Scan expiry is stored but there is no scheduled cleanup.
- MinIO is provisioned locally but is not connected.
- Provider privacy and data-retention terms still need production review.

## Auth0/OIDC

The backend is an OAuth resource server, not an authorization server.

To configure an Auth0 API:

1. Create an Auth0 API whose identifier matches `AUTH0_AUDIENCE`.
2. Use an EU-region tenant for the chosen deployment/privacy model.
3. Set `AUTH0_ISSUER_URI` to the tenant issuer, including the trailing slash.
4. Configure the browser application and allowed callback/logout/origin URLs.
5. Send the returned API access token as a bearer token.

The backend validates issuer and audience and uses `sub` as `user_id`. The web
app implements the SPA redirect and bearer-token flow; its audience must match
the backend configuration.

## MinIO/S3-compatible storage

Compose exposes MinIO on ports 9000/9001 to reserve the local infrastructure seam
for temporary label images and future exports. No backend code currently reads or
writes MinIO. Do not configure a bucket and assume cleanup/security is implemented.
