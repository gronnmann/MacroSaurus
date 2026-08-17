# Configuration reference

Spring Boot reads configuration from environment variables and standard Spring
property sources. The root `.env.example` is a reference file; Spring Boot does
not automatically load it as a dotenv file. Export variables in the shell, inject
them through the deployment platform, or map them through an IDE run profile.

## Environment variables

| Variable | Default | Required | Secret | Purpose |
|---|---|---:|---:|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/macrosaurus` | Yes outside local Compose | No | JDBC URL |
| `DATABASE_USERNAME` | `macrosaurus` | Yes | Usually | Database user |
| `DATABASE_PASSWORD` | `macrosaurus` | Yes | Yes | Database password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Production | No | Comma-separated trusted web origins |
| `AUTH0_ISSUER_URI` | blank | Production | No | OIDC issuer; blank disables authentication |
| `AUTH0_AUDIENCE` | `https://api.macrosaurus.app` | With Auth0 | No | Required JWT audience |
| `OFF_BASE_URL` | `https://world.openfoodfacts.org` | No | No | Open Food Facts API base |
| `OFF_USER_AGENT` | placeholder | Real OFF use | No | Identifies the app and contact address |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | No | No | OpenRouter API base |
| `OPENROUTER_API_KEY` | blank | For label extraction | Yes | OpenRouter bearer key |
| `OPENROUTER_MODEL` | `google/gemini-2.5-flash` | For label extraction | No | Image + JSON Schema capable model |

PowerShell example:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/macrosaurus"
$env:DATABASE_USERNAME = "macrosaurus"
$env:DATABASE_PASSWORD = "use-a-secret-manager-in-production"
$env:CORS_ALLOWED_ORIGINS = "https://app.macrosaurus.example"
$env:AUTH0_ISSUER_URI = "https://your-tenant.eu.auth0.com/"
$env:AUTH0_AUDIENCE = "https://api.macrosaurus.app"
$env:OFF_USER_AGENT = "Macrosaurus/0.1 (ops@example.com)"
$env:OPENROUTER_API_KEY = "..."
.\gradlew.bat :backend:bootRun
```

## Local ports

| Service | Port | Notes |
|---|---:|---|
| React/Vite | 5173 | Proxies `/api` to backend |
| Spring Boot | 8080 | API, Swagger, Actuator |
| PostgreSQL | 5432 | Local Compose database |
| MinIO API | 9000 | Provisioned but not currently used by backend |
| MinIO console | 9001 | Local object-storage UI |

## Security modes

### Local open mode

When `AUTH0_ISSUER_URI` is blank:

- All backend endpoints are permitted.
- `X-User-Id` selects a development identity.
- Missing `X-User-Id` falls back to `dev-user`.

Do not expose this mode outside a developer machine.

### OIDC resource-server mode

When `AUTH0_ISSUER_URI` is nonblank:

- Most endpoints require a JWT bearer token.
- Spring discovers issuer metadata and signing keys.
- The issuer and configured audience are validated.
- `sub` becomes the Macrosaurus user ID.
- Health, Swagger/OpenAPI, and public share retrieval remain public.

The React app supports `VITE_AUTH_MODE=auth0` with Auth0 domain, SPA client ID,
and audience variables. For local development, `VITE_AUTH_MODE=dev` sends the
configured `VITE_DEV_USER_ID` while the backend issuer is blank.

## CORS

The backend allows `localhost:5173` and `127.0.0.1:5173` for local development.
Set `CORS_ALLOWED_ORIGINS` to the exact comma-separated frontend origins in each
deployed environment. Do not use a wildcard origin for an authenticated API.

## Actuator

Exposed actuator endpoints:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

Only the health path is explicitly permitted by the security configuration.
Review monitoring authentication and network policies before exposing the other
actuator endpoints publicly.
