# Development guide

## First-time setup

From the repository root:

```powershell
pnpm install --frozen-lockfile
docker compose up -d postgres
mvn -pl backend spring-boot:run
```

In another terminal:

```powershell
pnpm --dir web dev
```

The backend requires JDK 26 and Maven 3.9 or newer. This repository intentionally
uses system Maven rather than committing a Maven wrapper.

## Daily commands

| Task | Command |
|---|---|
| Start database | `docker compose up -d postgres` |
| Follow database logs | `docker compose logs -f postgres` |
| Run backend | `mvn -pl backend spring-boot:run` |
| Run web dev server | `pnpm --dir web dev` |
| Fast backend tests | `mvn -pl backend test` |
| All backend checks | `mvn -pl backend verify` |
| Backend executable | `mvn -pl backend package` |
| Format all source | `pnpm format` |
| All commit checks | `pnpm quality` |
| Frontend type check | `pnpm --dir web check` |
| Frontend component tests | `pnpm --dir web test` |
| Frontend browser journeys | `pnpm --dir web e2e` |
| Frontend production build | `pnpm --dir web build` |
| Component catalogue | `pnpm --dir web storybook` |
| Stop infrastructure | `docker compose down` |

`pnpm install` configures the tracked Husky pre-commit hook. It runs
`pnpm quality`, which combines Biome, TypeScript, Spotless/ktlint, backend tests,
Spring Modulith boundary verification, and PostgreSQL integration tests. Docker
must be running for the Testcontainers-backed integration suite. CI runs the
same command.

To run the browser suite against a real local backend rather than its normal API
fixtures, start PostgreSQL and the Spring backend, then run:

```powershell
$env:E2E_LIVE_BACKEND = "1"
pnpm --dir web exec playwright test e2e/live-backend.spec.ts `
    --project=desktop-chromium
```

The live test uses an isolated development user, creates a quick entry through
the rendered app, verifies it in the Food Log, and deletes it again. Set
`E2E_BASE_URL` when Vite is not on the default `http://127.0.0.1:5173` address.

## Local development identity

The web client sends `X-User-Id: dev-user`. To test isolation manually, send
different header values:

```powershell
$alice = @{ "X-User-Id" = "alice" }
$bob = @{ "X-User-Id" = "bob" }

Invoke-RestMethod "http://localhost:8080/api/v1/foods?query=" -Headers $alice
Invoke-RestMethod "http://localhost:8080/api/v1/foods?query=" -Headers $bob
```

Each user should see public source foods plus only their own private foods.

## Database workflow

Flyway runs automatically before the application begins serving requests.

To inspect PostgreSQL:

```powershell
docker compose exec postgres psql -U macrosaurus -d macrosaurus
```

Useful psql commands:

```text
\dt
select version, description, success from flyway_schema_history order by installed_rank;
select code, display_name, unit from nutrient_definitions order by sort_order;
```

Add schema changes as a new migration. Keep SQL compatible with PostgreSQL; the
current migrations use `jsonb`, `timestamptz`, `numeric`, and `distinct on`.

Reset only when local data can be lost:

```powershell
docker compose down --volumes
docker compose up -d postgres
```

## Adding a backend feature

1. Decide which module owns the rule.
2. Add transport DTOs and a controller under `web`, orchestration under
   `application`, pure rules under `domain`, and SQL under `persistence`.
3. Add a Flyway migration if persistence changes.
4. Put only stable cross-module interfaces and snapshots in the feature root;
   depend on those contracts rather than another feature's implementation.
5. Add unit tests for calculations and failure modes.
6. Run `ModularityTest` with the full backend test task.
7. Update `docs/api.md`, `docs/data-model.md`, or configuration docs as relevant.

Do not create generic `controllers`, `services`, and `repositories` top-level
packages. Feature ownership is more important than technical-layer grouping.

## Adding a nutrient

1. Add a new migration inserting a stable code into `nutrient_definitions`.
2. Choose one canonical unit.
3. Add mappings in source adapters as needed.
4. Ensure the web UI can display the value without assuming all nutrients use
   grams.
5. Test that absent values remain absent.

Do not rename an existing code after it has been used in diary JSON snapshots.
Introduce a migration/compatibility mapping instead.

## Frontend data flow

- `web/src/App.tsx` composes only the router.
- `web/src/router.tsx` defines public and protected route trees.
- `web/src/routes` owns feature orchestration.
- `web/src/components` contains reusable UI and domain presentation.
- `web/src/lib/api.ts` owns authenticated transport, endpoint types, problems,
  and query keys.
- TanStack Query owns server state and invalidation; URL parameters own shareable
  filters and dates.
- `vite-plugin-pwa` caches the shell while API requests remain network-only.

See [Frontend architecture](frontend-architecture.md) and the live Storybook.

## Debugging common problems

### Docker client cannot connect

Start Docker Desktop and wait until its engine reports ready. Then run:

```powershell
docker info
docker compose up -d postgres
```

### Backend cannot connect to PostgreSQL

Check the container and port:

```powershell
docker compose ps
docker compose logs postgres
```

Also check `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` in the
shell that starts Maven.

### PostgreSQL reports that an application table does not exist

Macrosaurus uses Flyway for all application tables. Spring Boot 4 requires the
`spring-boot-starter-flyway` integration in addition to PostgreSQL's Flyway
database module; keeping only `flyway-core` does not activate Boot's migration
auto-configuration.

The application baselines an existing unversioned schema at version `0`. This
supports databases where Spring Modulith created `event_publication` before
Flyway was introduced while still applying every Macrosaurus migration from
version `1` onward. Do not change that baseline to version `1`, because doing so
would skip the initial application schema.

Inspect migration state without changing it:

```powershell
docker compose exec -T postgres psql -U macrosaurus -d macrosaurus `
    -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

On a working database, migrations `1` through `5` are successful and tables
such as `diary_entries` exist. Do not delete the Compose volume as a routine
migration fix; investigate failed history rows first.

### Spring reports that `EventSerializer` is missing

The JDBC event publication registry requires a serialization implementation.
Macrosaurus includes `spring-modulith-events-jackson`, the Jackson 3 serializer
for Spring Boot 4. If this error appears after changing dependencies, confirm that
both `spring-modulith-events-jdbc` and `spring-modulith-events-jackson` remain on
the runtime classpath and use the same Spring Modulith BOM version.

### Maven or Java version is rejected

Confirm that system Maven 3.9+ is using JDK 26:

```powershell
java -version
mvn -version
```

Install or select JDK 26 before running Maven; the build intentionally does not
download a JDK automatically.

### Vite cannot resolve dependencies

Use pnpm from the repository root and do not mix package managers:

```powershell
pnpm install --frozen-lockfile
pnpm --dir web check
```

Delete neither the pnpm lock nor workspace configuration to work around package
build-script warnings; `pnpm-workspace.yaml` explicitly permits esbuild.

### OpenRouter scan returns a configuration error

Set `OPENROUTER_API_KEY` in the backend process. The selected model must support
both image input and strict JSON Schema output.

### Supabase mode makes the web app return 401

Confirm the web and backend use the same `SUPABASE_URL`, the project uses an
asymmetric signing key, and both processes were restarted after changing
variables. Use `VITE_AUTH_MODE=dev` only while `DEV_AUTH_ENABLED=true`.
