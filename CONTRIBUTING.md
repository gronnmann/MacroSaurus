# Contributing to Macrosaurus

## Before changing code

1. Read [Architecture](docs/architecture.md), especially module ownership and
   dependency direction.
2. Check [Project status](docs/status.md) to distinguish implemented behavior from
   planned work.
3. Start PostgreSQL and run the existing tests.

## Development workflow

```powershell
docker compose up -d postgres
pnpm install --frozen-lockfile
pnpm quality
```

Use a short-lived branch and keep commits focused. Do not include `.env`, API keys,
database exports, user data, generated build output, or IDE configuration.

## Backend conventions

- Put a capability in the package that owns the business rule, not in a generic
  controller/service/repository layer.
- A module may call another module's root-package contract but may not import its
  internal packages or query its tables.
- Within a feature, keep HTTP binding in `web`, orchestration and transactions in
  `application`, framework-free rules in `domain`, and jOOQ in `persistence`.
- Do not create a global `models` package; transport DTOs and domain snapshots
  belong to the feature that owns their meaning.
- Keep REST DTOs separate from stored source payloads and calculations.
- Use `BigDecimal` for nutrient, quantity, weight, and energy arithmetic.
- Treat missing nutrients as unknown. Never synthesize zero for absent source data.
- Create immutable revisions for edits that could otherwise change historical
  diary or recipe calculations.
- Validate resource ownership in every user-scoped query.
- Add Flyway migrations; never modify an already-released migration.
- Use RFC 9457 `ProblemDetail` responses through the shared exception handler.

## Frontend conventions

- Use pnpm exclusively; do not add npm or Yarn lockfiles.
- Keep API access in `web/src/lib/api.ts` until a generated client replaces it.
- Use TanStack Query for server state and invalidate the narrowest relevant key.
- Preserve accessible labels, focus states, semantic buttons, and keyboard use.
- Do not put provider secrets or privileged API calls in the browser.

## Required checks

```powershell
pnpm format
pnpm quality
pnpm --dir web test
pnpm --dir web build
```

The installed Husky hook runs `pnpm quality` before every commit. Biome owns web
formatting and linting; Spotless with ktlint owns Kotlin formatting through Maven.
Both use four-space indentation. Do not add a second formatter for the same files.

Changes to module dependencies must keep `ModularityTest` passing. Changes to API
request/response shapes must update [API documentation](docs/api.md).
