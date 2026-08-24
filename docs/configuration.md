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
| `SUPABASE_URL` | blank | Production | No | Project URL used to derive the Auth issuer and asymmetric JWKS |
| `OFF_BASE_URL` | `https://world.openfoodfacts.org` | No | No | Open Food Facts API base |
| `OFF_USER_AGENT` | placeholder | Real OFF use | No | Identifies the app and contact address |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | No | No | OpenRouter API base |
| `OPENROUTER_API_KEY` | blank | For label extraction | Yes | OpenRouter bearer key |
| `OPENROUTER_MODEL` | `google/gemini-2.5-flash` | For label extraction | No | Image + JSON Schema capable model |
| `OFF_CONNECT_TIMEOUT` | `5s` | No | No | Open Food Facts connection timeout |
| `OFF_READ_TIMEOUT` | `15s` | No | No | Open Food Facts response timeout |
| `OPENROUTER_CONNECT_TIMEOUT` | `5s` | No | No | OpenRouter connection timeout |
| `OPENROUTER_READ_TIMEOUT` | `90s` | No | No | OpenRouter extraction response timeout |
| `DEV_AUTH_ENABLED` | `true` | Local only | No | Enables development identity; production forces this off |

PowerShell example:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/macrosaurus"
$env:DATABASE_USERNAME = "macrosaurus"
$env:DATABASE_PASSWORD = "use-a-secret-manager-in-production"
$env:CORS_ALLOWED_ORIGINS = "https://app.macrosaurus.example"
$env:SUPABASE_URL = "https://your-project-ref.supabase.co"
$env:OFF_USER_AGENT = "Macrosaurus/0.1 (ops@example.com)"
$env:OPENROUTER_API_KEY = "..."
mvn -pl backend spring-boot:run
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

When `DEV_AUTH_ENABLED=true` (the local default):

- All backend endpoints are permitted.
- `X-User-Id` selects a development identity.
- Missing `X-User-Id` falls back to `dev-user`.

Do not expose this mode outside a developer machine.

### Supabase resource-server mode

When `DEV_AUTH_ENABLED=false`:

- Most endpoints require a JWT bearer token.
- Spring loads asymmetric signing keys from the project JWKS.
- Issuer, expiry, `authenticated` audience and role, non-anonymous status, and a
  UUID subject are validated.
- `sub` becomes the Macrosaurus user ID; the backend does not read `auth.users`.
- Health, Swagger/OpenAPI, and public share retrieval remain public.

The `prod` Spring profile always sets development identity to false and requires
an absolute HTTPS `SUPABASE_URL` during startup. It also disables Swagger/OpenAPI
and limits Actuator exposure to health and info. Missing configuration therefore
makes production startup fail closed.

The React app supports `VITE_AUTH_MODE=supabase` with
`VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY`. It uses the Supabase
client for Auth only. For local development, `VITE_AUTH_MODE=dev` sends the
configured `VITE_DEV_USER_ID` while backend development identity is enabled.

The publishable key is intentionally embedded in the browser build. Never put a
Supabase secret or service-role key in a `VITE_` variable or in the backend; the
backend needs only the project URL and PostgreSQL credentials.

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
