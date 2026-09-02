# Project status

This document separates implemented behavior from architecture planned for later.

## Implemented

### Platform

- Java 26 / Kotlin 2.4 / Spring Boot 4.1 build.
- Maven 3.9 build with an enforced JDK 26 runtime.
- Spring Modulith boundary verification.
- PostgreSQL schema managed by Flyway.
- RFC 9457-style API errors and generated OpenAPI/Swagger UI.
- Supabase JWT validation through the project's asymmetric JWKS.
- React/Vite PWA built with pnpm, responsive feature routes, email OTP login, and
  a documented development identity adapter.
- Storybook component catalogue, Vitest component tests, and mocked Playwright
  desktop/mobile journeys.
- PostgreSQL Testcontainers integration coverage for migrations and core
  persistence workflows.
- Multi-platform backend/web images published to GitHub Container Registry and
  a health-aware Docker Compose deployment script.

### Nutrition and tracking

- Data-driven nutrient definitions and starter nutrients.
- Three representative seeded USDA foods.
- Private foods with immutable revisions.
- Per-100-g, per-100-ml, and per-serving bases.
- Named gram/volume portions and density-aware conversions.
- Daily food/recipe/quick diary entries with nutrient snapshots.
- Remembered food/recipe amounts and local-time habit suggestions derived from diary history.
- Full diary entry editing, deletion, and exact copies to another date/time.
- Unified food and recipe tracking search.
- Macro-calculated calories and optional explicit calories.
- Required, reload-safe guided goal setup after account creation, with coached or
  fixed manual targets and profile reruns that create revisions.
- Custom micronutrient targets.

### Recipes, weight, and expenditure

- Versioned recipes with resolved ingredient nutrients.
- Per-serving calculations.
- Estimated raw yield and explicit finished weight.
- Weight measurements.
- Mifflin–St Jeor baseline plus robust 21-day adaptive expenditure and weight
  trend estimates with confidence and uncertainty bands.
- Monday check-ins with missing/partial-day review, optional estimates or
  exclusions, weigh-in prompts, explained proposals, and explicit accept/skip.
- Date-effective goal and nutrition-program history with guarded weekly changes.

### Acquisition and sharing

- EAN/UPC checksum validation.
- Browser-side barcode decoding; camera frames are never uploaded.
- Exact Open Food Facts lookup and import.
- Per-user, admin-granted single-photo OpenRouter extraction from barcode no-match or food creation.
- Versioned normalized Matvaretabellen, USDA Foundation, and USDA SR Legacy release imports.
- Searchable localized food aliases and source/release provenance.
- Mandatory confirmation into a private food.
- Immutable, expiring/revocable share snapshots.

## Partially implemented

- PWA: shell caching exists; offline data/sync does not.
- Scan jobs: job records and expiry exist; processing is synchronous and cleanup is
  not scheduled.
- Spring Modulith's JDBC registry and Jackson 3 serializer are configured;
  business outbox events are not wired yet.
- MinIO exists in Compose; backend object storage does not.
- Nutrient reference-set tables exist; authoritative EU/US datasets are not seeded.
- OpenAPI is generated; TypeScript client generation is not wired.

## Not implemented yet

- Automated scheduling and hosting of catalog release imports.
- Open Food Facts caching, retry/backoff, and text search.
- Personal API tokens, approved third-party app registration, quotas, and webhooks.
- Account export, deletion, and retention workflows.
- Delegated human-coach access.
- Activity events, wearables, Apple Health, or Health Connect.
- Background worker deployment and durable job queue.
- Automatic image/draft cleanup.
- Native mobile applications.
- Production-ready reference-intake recommendations.
- Rate limiting and a full observability pipeline.

## Product assumptions

- Initial users are adults 18+.
- The product is a wellness tracker, not a medical device.
- User foods and recipes are private unless shared by an unlisted snapshot link.
- Missing nutrient values remain unknown.
- Grams are preferred but mass is never fabricated for serving-only foods.
- Liquids stay per 100 ml unless a reliable density allows conversion.
- Expenditure suggestions are explained and require user judgment.
