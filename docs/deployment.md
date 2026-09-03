# Deploy Macrosaurus with Docker and Nginx

This tutorial deploys the web application and Spring Boot API with Docker
Compose, uses managed PostgreSQL for durable data, and terminates HTTPS at Nginx
on an Ubuntu host. The application containers expose only a loopback port; the
database and backend are never published directly.

## 1. Prepare the external services

Before provisioning the server, create:

- A DNS record such as `macrosaurus.example.com` pointing to the server.
- A Supabase project in the chosen region. Configure email OTP Auth, custom SMTP,
  an asymmetric RS256 signing key, and a dedicated `macrosaurus_app` PostgreSQL
  login as described in [Integrations](integrations.md).
- Disable the project's Data API integration. Macrosaurus uses the `public`
  schema through JDBC, but never uses generated REST or GraphQL endpoints.
- Optional OpenRouter credentials if nutrition-label extraction is enabled.

Use the direct Supabase database endpoint for this persistent backend when the
host supports IPv6. On IPv4-only hosts use the Supavisor session endpoint on port
5432. Convert the selected connection string to JDBC syntax and require TLS, for
example `jdbc:postgresql://host:5432/postgres?sslmode=require`.

## 2. Prepare an Ubuntu host

Use a supported Ubuntu release with ports 22, 80, and 443 allowed by its
firewall. Install Docker Engine and its Compose plugin using Docker's official
repository, then install the host reverse proxy and Certbot:

```bash
sudo apt update
sudo apt install -y nginx certbot python3-certbot-nginx
# Ubuntu 24.04 and newer (use python3-psycopg2 on Ubuntu 22.04):
sudo apt install -y python3-psycopg
docker version
docker compose version
```

Create a non-root deployment account, grant only the access it needs, and clone
the repository under that account. Membership in the `docker` group is
root-equivalent and should be treated accordingly.

## 3. Configure production

From the repository root:

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

Edit every placeholder in `.env.production`. In particular:

- Keep `IMAGE_REPOSITORY=ghcr.io/gronnmann/macrosaurus` for this repository, or
  change it when publishing a fork under another GitHub account.
- Set `APP_VERSION` to `main` for a test deployment or, preferably, an immutable
  release (`v0.2.0`) or commit (`sha-a1b2c3d`) tag.
- Set `APP_ORIGIN` to the exact public HTTPS origin, without a trailing path.
- Use the selected Supabase JDBC endpoint with `sslmode=require` and the
  dedicated database login.
- Set the real Supabase project URL and publishable key. Never configure a secret
  or service-role API key in the app.
- Replace the Open Food Facts contact placeholder.
- Leave `OPENROUTER_API_KEY` blank only when label extraction is intentionally
  disabled.

The production Spring profile disables development identity and refuses to
start without an absolute HTTPS Supabase project URL. Never commit
`.env.production`.

## 4. Publish images with GitHub Actions

The `Quality and containers` workflow verifies the repository and publishes the
backend and web images to GitHub Container Registry. Configure these public
build-time values under **Repository settings → Secrets and variables → Actions
→ Variables** before running it:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

No registry password is needed in Actions: the workflow uses its scoped
`GITHUB_TOKEN`. A push to `main` publishes `main` and `sha-*` tags. A `v*` Git
tag also publishes the original release tag, semantic version tags, and
`latest`. The workflow can also be started manually from the Actions tab.

The resulting image names are:

```text
ghcr.io/gronnmann/macrosaurus-backend:<tag>
ghcr.io/gronnmann/macrosaurus-web:<tag>
```

Make the packages public in their GitHub package settings for anonymous pulls.
For private packages, authenticate the deployment host using a token with only
`read:packages` access:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io --username YOUR_GITHUB_USER --password-stdin
```

## 5. Pull and start the application

The deployment script validates the environment, pulls both images, recreates
the services without building on the server, waits for the backend health check,
and prints recent logs if deployment fails:

```bash
./scripts/deploy.sh v0.2.0
```

Omit the argument to use `APP_VERSION` from `.env.production`. Use `main` for a
quick test of the most recently published main-branch build:

```bash
./scripts/deploy.sh main
```

The backend image is built in Actions with Maven and JDK 26, then runs as a
non-root user on a JRE-only image. The web image is built with Node 24 and served
by Nginx. The Supabase URL and publishable key are public build-time values;
database and provider secrets are supplied only to the backend at runtime.

Flyway applies database migrations while the backend starts. Keep the backend
at one replica during migrations and do not route traffic until its readiness
check passes:

```bash
docker compose --env-file .env.production -f compose.production.yml logs -f backend
curl --fail http://127.0.0.1:8080/
```

Seed public food catalogs separately from deployment. The Python script reads the
database credentials from `.env.production`, downloads the selected datasets,
and writes directly to PostgreSQL. It does not use Docker, start the backend, or
make application HTTP requests:

```bash
./scripts/seed.py --source both
```

Use `usda` or `matvaretabellen` instead of `both` to update only that source.
The command is idempotent for an unchanged release and does not require a user
access token. Ubuntu 22.04 calls the compatible database-driver package
`python3-psycopg2`; install that instead if `python3-psycopg` is unavailable.
See [Integrations](integrations.md) for release overrides and source-specific
behavior.

## 6. Put HTTPS in front of the containers

Create `/etc/nginx/sites-available/macrosaurus` on the host, replacing the
example domain if necessary:

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name macrosaurus.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable it, validate Nginx, obtain a certificate, and verify automatic renewal:

```bash
sudo ln -s /etc/nginx/sites-available/macrosaurus /etc/nginx/sites-enabled/macrosaurus
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d macrosaurus.example.com
sudo certbot renew --dry-run
```

When a cloud load balancer terminates TLS instead, point it at the same
loopback-bound service through an appropriate private network and preserve the
forwarded host, client address, and scheme headers.

## 7. Verify the deployment

Verify the browser flow and the authenticated API:

```bash
curl --fail https://macrosaurus.example.com/
curl --include https://macrosaurus.example.com/api/v1/shared/not-a-real-token
docker compose --env-file .env.production -f compose.production.yml logs --tail=200 backend web
```

The fake share token should return a structured `404` problem, which confirms
that HTTPS, Nginx, and the API path are connected. Also verify email OTP login,
profile isolation between two accounts, a food entry, logout, and that a request
to the Supabase Data API cannot read an application table. The backend health
endpoint is intentionally used by the private container health check rather
than exposed through the public web proxy.

## Updates and rollback

Back up the database through the managed provider before releases containing
migrations. Publish a Git tag, wait for the workflow to complete, then deploy
that exact tag:

```bash
git tag v0.2.0
git push origin v0.2.0
./scripts/deploy.sh v0.2.0
```

Keep the previous Git tag and images until verification completes. To roll the
containers back, run the script with the previous tag. Flyway migrations are
forward-only: never assume an older application can run against a newly migrated
schema. Use a tested forward fix or restore the managed database to a separate
instance when a migration itself must be rolled back.

## Operations checklist

- Monitor container restarts, readiness, HTTP error rate, latency, and managed
  PostgreSQL storage/connections.
- Test database restore procedures regularly; a configured backup is not enough.
- Rotate database and provider credentials and exercise the Supabase signing-key
  rotation procedure without deploying private signing material.
- Do not log tokens, request bodies, label images, diary data, or weight history.
- Keep Docker, Nginx, base images, Maven dependencies, and pnpm dependencies
  patched.
- Review Open Food Facts attribution/licensing and OpenRouter cost limits before
  enabling those integrations publicly.
- Complete account export/deletion, retention jobs, rate limiting, and security
  review before treating the pre-release application as production-ready.
