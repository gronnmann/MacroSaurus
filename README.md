<p align="center">
  <img src="web/src/assets/mascot/dino-mark-v2.webp" width="150" alt="Macrosaurus dinosaur mascot" />
</p>

<h1 align="center">MACRO<span>SAURUS</span></h1>

<p align="center">
  <strong>Eat big. Lift big. Track everything.</strong><br />
  Complete nutrition tracking without turning food into homework.
</p>

<p align="center">
  <img alt="Java 26" src="https://img.shields.io/badge/Java-26-ED8B00?style=flat-square&logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 4.1" src="https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img alt="Kotlin 2.4" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React-19-149ECA?style=flat-square&logo=react&logoColor=white" />
  <img alt="pnpm 10" src="https://img.shields.io/badge/pnpm-10-F69220?style=flat-square&logo=pnpm&logoColor=white" />
</p>

<p align="center">
  <img src="web/src/assets/mascot/coach.webp" width="390" alt="Macrosaurus coach giving a thumbs up" />
</p>

## What is Macrosaurus?

Macrosaurus is an installable nutrition-tracking web app backed by a versioned
JSON API. It combines a fast daily workflow with complete macro- and
micronutrient data, flexible portions, recipes, weigh-ins, and adaptive weekly
nutrition coaching.

| Track | Build | Understand |
|---|---|---|
| Foods, recipes, quick macros | Custom foods and named portions | Daily and weekly nutrition |
| Grams, millilitres, servings | Shareable recipes | Weight trends |
| Browser-decoded EAN/UPC barcodes | Single-photo label review | Adaptive calorie needs and weight trends |

New users complete a guided goal and program setup. Every Monday, Macrosaurus can
review incomplete logs, learn from weigh-ins and intake, show uncertainty, and
propose the next target revision for the user to accept or skip.

The main app stays intentionally small:

```text
Dashboard  ·  Food Log  ·  + Track  ·  Progress  ·  Profile
```

`+ Track` is the centre of the experience. Search foods and recipes together,
scan a barcode, enter macros quickly, create something new, or add a weigh-in.
Food and weight entries use the current date and time automatically.

> Macrosaurus is an adult wellness application under active development. It is
> not medical advice or a substitute for a qualified health professional.

## Quick start

### Prerequisites

- Docker Desktop, or another Docker Compose-compatible runtime
- Node.js 24+
- pnpm 10.25.0
- JDK 26
- Maven 3.9+

From the repository root:

```powershell
# 1. Install the web workspace and development hooks
pnpm install --frozen-lockfile

# 2. Start PostgreSQL
docker compose up -d postgres

# 3. Start the Spring API
mvn -pl backend spring-boot:run
```

In a second terminal:

```powershell
# 4. Start the web app
pnpm --dir web dev
```

Open **http://localhost:5173**. The API runs at **http://localhost:8080** and
Swagger UI is available at **http://localhost:8080/swagger-ui.html**.

### Verify the services

```powershell
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected health response:

```json
{
    "status": "UP"
}
```

The local build uses development identity mode by default. Copy
`web/.env.example` to `web/.env.local` if you want to set an explicit local user.
Production Supabase configuration is covered in [Configuration](docs/configuration.md).

## Development commands

The root commands are the normal development entry points:

| Command | What it does |
|---|---|
| `pnpm format` | Formats Kotlin, TypeScript, React, CSS, and JSON |
| `pnpm format:check` | Verifies formatting without changing files |
| `pnpm quality` | Runs Biome, TypeScript, ktlint, backend tests, and module-boundary checks |
| `pnpm --dir web test` | Runs frontend unit/component tests |
| `pnpm --dir web e2e` | Runs desktop and mobile Playwright journeys |
| `pnpm --dir web build` | Builds the production PWA |
| `pnpm --dir web storybook` | Opens the reusable component catalogue |

`pnpm install` enables the tracked Husky pre-commit hook. Every commit must pass
`pnpm quality`. Editors also inherit the repository-wide four-space policy from
`.editorconfig`.

## Deploy a published image

Pushes to `main` and `v*` tags publish multi-platform backend and web images to
GitHub Container Registry after the quality checks pass. On a configured host:

```bash
cp .env.production.example .env.production
# Fill in .env.production, then deploy a published tag:
./scripts/deploy.sh main
```

Use an immutable release tag such as `v0.2.0` for production. See the
[deployment tutorial](docs/deployment.md) for required GitHub variables, private
registry login, HTTPS setup, updates, and rollback.

## Architecture

```text
Macrosaurus/
├── backend/
│   ├── acquisition/     barcode and label acquisition
│   ├── catalog/         foods, nutrients, revisions, and portions
│   ├── expenditure/     calorie-needs estimates
│   ├── goals/           resolved calorie and macro targets
│   ├── identity/        user context and profile
│   ├── measurements/    weigh-ins
│   ├── recipes/         recipe composition and yields
│   ├── sharing/         revocable share links
│   ├── tracking/        Food Log and quick tracking
│   └── shared/          small cross-cutting primitives
├── web/
│   └── src/
│       ├── components/  reusable accessible UI
│       ├── routes/      product workflows
│       ├── lib/         API transport, auth, and utilities
│       └── styles/      tokens, components, and responsive rules
├── docs/                architecture and operations guides
└── compose.yaml         local PostgreSQL and optional MinIO
```

The backend is a package-by-feature modular monolith. Spring Modulith verifies
module boundaries during tests. Flyway owns the application schema and applies
migrations automatically before jOOQ-backed services initialize. The frontend uses route-level code splitting,
TanStack Query for server state, and a small shared component system rather than
screen-specific copies.

Read [Architecture](docs/architecture.md) and
[Frontend architecture](docs/frontend-architecture.md) before introducing a new
cross-feature dependency.

## Data and integrations

- Versioned food releases can be seeded directly into PostgreSQL from USDA FoodData Central and Matvaretabellen.
- Exact barcode matches can be imported from Open Food Facts.
- User-created foods are private unless deliberately shared.
- Admin-granted users can extract a reviewed nutrition draft from one label image
  after a barcode miss or while creating a food.
- Barcode frames remain in the browser; the backend receives the decoded number.

External providers are optional for normal local development. See
[Integrations](docs/integrations.md) for environment variables and operational
expectations.

## Documentation

- [Development guide](docs/development.md)
- [Architecture and module boundaries](docs/architecture.md)
- [API guide](docs/api.md)
- [Data model and nutrition rules](docs/data-model.md)
- [Configuration](docs/configuration.md)
- [Deployment and operations](docs/deployment.md)
- [Design system and mascot usage](docs/design-system.md)
- [Current status](docs/status.md)
- [Contributing](CONTRIBUTING.md)

## Project status

Macrosaurus is pre-release software. Core tracking flows work, but production
hardening, full USDA import automation, reference nutrient datasets, quotas, and
third-party API credentials remain deployment work.

No project license has been selected yet. USDA and Open Food Facts records retain
their own licensing and attribution requirements.
