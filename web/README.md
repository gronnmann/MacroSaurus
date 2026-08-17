# Macrosaurus web application

The web workspace is the installable, mobile-first Macrosaurus client. It covers
the diary, complete nutrient display, food and portion management, recipes,
barcode and label scanning, weigh-ins, expenditure estimates, goals, profile
settings, Auth0, and public share snapshots.

## Start locally

From the repository root:

```powershell
pnpm install --frozen-lockfile
Copy-Item web/.env.example web/.env.local
pnpm --dir web dev
```

Run PostgreSQL and the Spring backend as described in the [root setup
guide](../README.md). Open <http://localhost:5173>. Vite forwards `/api` to
`http://localhost:8080`.

The example environment uses development identity mode:

```text
VITE_AUTH_MODE=dev
VITE_DEV_USER_ID=dev-user
```

Do not use this mode on a public deployment. Production uses `auth0` plus the
domain, SPA client ID, and API audience documented in
[`../docs/frontend-architecture.md`](../docs/frontend-architecture.md).

## Commands

| Command | Purpose |
|---|---|
| `pnpm --dir web dev` | Run Vite with API proxy and hot reload |
| `pnpm --dir web format` | Format source with Biome and four-space indentation |
| `pnpm --dir web lint` | Run Biome static and accessibility checks |
| `pnpm --dir web quality` | Run Biome CI checks and strict TypeScript |
| `pnpm --dir web check` | Strict TypeScript check |
| `pnpm --dir web test` | Vitest component and domain-presentation tests |
| `pnpm --dir web e2e` | Mocked desktop/mobile Playwright journeys |
| `pnpm --dir web build` | Type-check and build the production PWA |
| `pnpm --dir web storybook` | Run the component catalogue on port 6006 |
| `pnpm --dir web storybook:build` | Verify the static component catalogue |

Install the isolated browser once before the first end-to-end run:

```powershell
pnpm --dir web exec playwright install chromium
```

All dependency changes must use pnpm. Do not create `package-lock.json` or run
npm installation commands in this workspace.

The repository root `pnpm quality` command also runs backend formatting checks,
tests, and Spring Modulith boundary verification. It is enforced by the tracked
pre-commit hook and CI workflow.

## Structure

- `src/components`: reusable UI primitives and nutrition components.
- `src/routes`: API-backed product workflows.
- `src/lib`: auth adapter, transport, query keys, and formatting.
- `src/styles`: brand tokens and responsive component rules.
- `.storybook`: component documentation and accessibility configuration.
- `e2e`: browser journeys with deterministic API fixtures.

`App.tsx` intentionally contains only `RouterProvider`; screens do not belong in
the application entry point. See the [frontend architecture
guide](../docs/frontend-architecture.md) before adding a route and the [design
system guide](../docs/design-system.md) before adding visual primitives.

## Camera and PWA notes

- Camera barcode scanning requires a secure context in production. Localhost is
  treated as secure by modern browsers.
- Permission failure always leaves manual barcode and image-upload fallbacks.
- Label images are submitted only after the user chooses them and are converted
  to supported data URLs for the backend extraction endpoint.
- The service worker precaches the shell and brand assets. API requests remain
  network-only, and mutations fail clearly while offline rather than being
  replayed later.
