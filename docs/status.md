# Project status

This document separates implemented behavior from architecture planned for later.

## Implemented

### Platform

- Java 26 / Kotlin 2.4 / Spring Boot 4.1 build.
- Gradle wrapper with automatic toolchain provisioning.
- Spring Modulith boundary verification.
- PostgreSQL schema managed by Flyway.
- RFC 9457-style API errors and generated OpenAPI/Swagger UI.
- Auth0-compatible JWT validation when configured.
- React/Vite PWA built with pnpm, responsive feature routes, Auth0 SPA login, and
  a documented development identity adapter.
- Storybook component catalogue, Vitest component tests, and mocked Playwright
  desktop/mobile journeys.

### Nutrition and tracking

- Data-driven nutrient definitions and starter nutrients.
- Three representative seeded USDA foods.
- Private foods with immutable revisions.
- Per-100-g, per-100-ml, and per-serving bases.
- Named gram/volume portions and density-aware conversions.
- Daily food/recipe/quick diary entries with nutrient snapshots.
- Full diary entry editing, deletion, and exact copies to another date/time.
- Unified food and recipe tracking search.
- Macro-calculated calories and optional explicit calories.
- Fixed or expenditure-relative calorie goals, guided g/kg macros, custom grams,
  percentage splits, and custom micronutrient targets.

### Recipes, weight, and expenditure

- Versioned recipes with resolved ingredient nutrients.
- Per-serving calculations.
- Estimated raw yield and explicit finished weight.
- Weight measurements.
- Mifflin–St Jeor baseline and guarded adaptive estimate.

### Acquisition and sharing

- EAN/UPC checksum validation.
- Browser-side barcode decoding; camera frames are never uploaded.
- Exact Open Food Facts lookup and import.
- Single-photo OpenRouter multimodal structured extraction after barcode no-match.
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

- Complete versioned USDA bulk importer.
- Open Food Facts caching, retry/backoff, and text search.
- Personal API tokens, approved third-party app registration, quotas, and webhooks.
- Account export, deletion, and retention workflows.
- Delegated coach access or coaching UI.
- Activity events, wearables, Apple Health, or Health Connect.
- Background worker deployment and durable job queue.
- Automatic image/draft cleanup.
- Native mobile applications.
- Production-ready reference-intake recommendations.
- Database-backed integration test suite.
- Production deployment manifests, rate limiting, and full observability pipeline.

## Product assumptions

- Initial users are adults 18+.
- The product is a wellness tracker, not a medical device.
- User foods and recipes are private unless shared by an unlisted snapshot link.
- Missing nutrient values remain unknown.
- Grams are preferred but mass is never fabricated for serving-only foods.
- Liquids stay per 100 ml unless a reliable density allows conversion.
- Expenditure suggestions are explained and require user judgment.
