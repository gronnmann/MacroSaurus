# Architecture

## Architectural style

Macrosaurus is a package-by-feature modular monolith. It has one Spring Boot
runtime and one PostgreSQL database, while Spring Modulith treats each direct
package below `com.macrosaurus` as an application module.

This gives the project transactional simplicity now without turning every
feature into an inseparable layer. A module can later be extracted only if scale,
team ownership, or deployment requirements justify it.

```mermaid
flowchart LR
    Web[React PWA] --> API[Spring MVC /api/v1]
    API --> Modules[Feature modules]
    Modules --> DB[(PostgreSQL)]
    Modules --> OFF[Open Food Facts]
    Modules --> OR[OpenRouter]
    Auth[Auth0 / OIDC] --> API
```

## Feature modules

| Module | Owns | May depend on |
|---|---|---|
| `identity` | Request user identity, profiles, nutrient targets, security configuration | `shared` |
| `catalog` | Nutrient definitions, foods, immutable food revisions, portions | `identity`, `shared` |
| `recipes` | Recipe revisions, ingredient quantities, yields | `catalog`, `identity`, `shared` |
| `tracking` | Diary entries, quick tracking, daily totals | `catalog`, `recipes`, `identity`, `shared` |
| `measurements` | Weight measurements | `identity`, `shared` |
| `expenditure` | Baseline and adaptive expenditure estimates | `identity`, `measurements`, `tracking`, `shared` |
| `goals` | Calorie rules and resolved macro targets | `expenditure`, `measurements`, `identity`, `shared` |
| `acquisition` | EAN/UPC validation, OFF lookup, OpenRouter extraction | `catalog`, `identity`, `shared` |
| `sharing` | Revocable immutable snapshots | `catalog`, `recipes`, `identity`, `shared` |
| `shared` | Nutrient arithmetic, small enums, JSON support, API problems | Nothing feature-specific |

Dependency direction is intentionally one-way:

```mermaid
flowchart TD
    shared
    identity --> shared
    catalog --> identity
    catalog --> shared
    recipes --> catalog
    tracking --> catalog
    tracking --> recipes
    measurements --> identity
    expenditure --> measurements
    expenditure --> tracking
    acquisition --> catalog
    sharing --> catalog
    sharing --> recipes
```

`ModularityTest` calls `ApplicationModules.verify()` and fails when code introduces
cycles or violates Spring Modulith's module rules.

## Module rules

1. A module owns its tables and business invariants.
2. A module may call another module's public application service.
3. A module must not issue SQL against another module's tables.
4. REST request/response classes belong to the module that exposes the endpoint.
5. Source-provider payloads are translated at the acquisition boundary.
6. Cross-module primitives stay small. `shared` must not become a miscellaneous
   domain dumping ground.
7. Historical calculations reference immutable revisions or store nutrient
   snapshots.

The current code uses jOOQ's `DSLContext` with explicit SQL. Flyway, not application
startup code, owns schema evolution.

## Main request flows

### Log a food

```mermaid
sequenceDiagram
    participant W as Web/API client
    participant T as Tracking
    participant C as Catalog
    participant D as PostgreSQL
    W->>T: POST diary-entries/food
    T->>C: Resolve revision + quantity + portion
    C->>D: Load food revision, nutrients, portions
    C-->>T: Resolved nutrient snapshot
    T->>D: Insert diary entry with snapshot
    T-->>W: DiaryEntryView
```

The diary entry is not recalculated when the food later changes.

### Build a recipe

The recipe module resolves every ingredient through the catalog, sums nutrient
snapshots, and stores an immutable recipe revision. If all ingredients have mass,
their sum becomes an estimated raw yield. An explicit finished weight takes
precedence for per-100-g calculations.

### Scan a product

1. Normalize and checksum-validate the EAN/UPC.
2. Look for accessible local records.
3. Query Open Food Facts only when there is no local match.
4. If a label image is used, forward data URLs to OpenRouter without persisting
   the image bytes.
5. Store only the structured draft for review and record a 24-hour expiry
   timestamp.
6. Require a corrected/confirmed food request before inserting a private food.

The current extraction call is synchronous, and expiry is not yet enforced by a
cleanup task. `scan_jobs` preserves a job-shaped API so execution can move to a
worker without changing the client contract.

## Persistence and transactions

- PostgreSQL 17 is the target database.
- Flyway migrations live in `backend/src/main/resources/db/migration`.
- Numeric nutrition and measurement values use PostgreSQL `numeric` and Kotlin
  `BigDecimal`.
- Structured snapshots use `jsonb`.
- User-owned rows always include a user identifier checked in repository queries.
- Create/revise operations that affect multiple tables use Spring transactions.

See [Data model](data-model.md) for table ownership and invariants.

## Authentication and authorization

With `AUTH0_ISSUER_URI` configured, Spring Security validates JWT signature,
issuer, and the configured audience. `UserContext` uses the JWT subject as the
Macrosaurus user identifier.

With no issuer configured, the backend permits requests and uses `X-User-Id` or
`dev-user`. This makes local development easy but is unsafe for a public network.

Public share retrieval at `/api/v1/shared/{token}` does not require authentication.
Share tokens are random, stored only as SHA-256 hashes, optional-expiry, and
revocable.

## Frontend

The React PWA is organized by route-level features over a reusable component and
design-system layer. React Router owns public/protected navigation, TanStack
Query owns server state, React Aria supplies accessible interaction primitives,
and `web/src/lib/api.ts` centralizes the hand-written API contract and Auth0/dev
headers. `App.tsx` is intentionally only a router entry point.

The generated service worker caches the application shell and brand assets. API
requests remain network-only; offline mutations and conflict resolution are not
implemented. See [Frontend architecture](frontend-architecture.md).

## Intentional future seams

- Acquisition providers sit behind services rather than domain types.
- The scan-job shape allows a separate worker and temporary object storage later.
- Food and recipe revisions can be exported without rewriting diary history.
- Spring Modulith's JDBC event registry and Jackson 3 serializer are installed as
  a future seam, but durable business events and webhook delivery are not yet
  implemented.
- The generated OpenAPI document can replace the current hand-written TypeScript
  client with generated SDKs.
