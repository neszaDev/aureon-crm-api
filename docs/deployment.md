# Deployment (Azure)

Both apps run on Azure Container Apps; `infra/main.bicep` describes one environment. Why it is
built this way: [plans/0003-azure-deployment.md](plans/0003-azure-deployment.md).

```
internet ──HTTPS──▶ ca-boilerplate-<env>-web (Next.js, 0–1 replica)
                          │ http://ca-boilerplate-<env>-api (internal only)
                          ▼
                    ca-boilerplate-<env>-api (this API, 0–1 replica) ──▶ Azure Files share (uploads)
                          │ TLS
                          ▼
                    PostgreSQL Flexible Server
```

| Resource | Name |
|---|---|
| Resource group | `rg-boilerplate-<env>` (Southeast Asia) |
| Container Apps environment + Log Analytics | `cae-boilerplate-<env>`, `log-boilerplate-<env>` |
| Apps | `ca-boilerplate-<env>-api`, `ca-boilerplate-<env>-web` |
| PostgreSQL | `psql-boilerplate-<env>-<suffix>`, database `boilerplate` |
| Storage (uploads) | `stbp<env><suffix>`, share `files` |
| Deploy identity (GitHub OIDC) | `id-boilerplate-<env>-github` |

## Create or change the infrastructure

```sh
az login
ADMIN_EMAIL=you@example.com infra/deploy.sh dev
```

The script registers the resource providers, creates the group, generates the secrets on the
first run (database password, JWT secret, admin password; afterwards it reads them back from the
backend container app) and deploys the template. It is idempotent: edit `main.bicep`, run it again. It keeps the images that are
running, and prints the frontend URL and the GitHub variables below.

## Deploying new versions

Nothing to do by hand. On every push to `dev`, CI/CD builds and publishes the image, then
`publish-image.yml` signs in to Azure (OIDC) and updates the container app to that image's
digest. The step runs only when the GitHub environment has these variables, in **both** repos:

| Variable | Value |
|---|---|
| `AZURE_CLIENT_ID` | printed by `deploy.sh` (the deploy identity) |
| `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | printed by `deploy.sh` |
| `AZURE_RESOURCE_GROUP` | `rg-boilerplate-<env>` |
| `AZURE_CONTAINER_APP` | `ca-boilerplate-<env>-api` here, `ca-boilerplate-<env>-web` in the frontend |

Set them with `gh variable set NAME --env dev --body VALUE --repo <owner>/<repo>`.

## Operating it

- **Logs** (JSON): `az containerapp logs show -n ca-boilerplate-dev-api -g rg-boilerplate-dev --follow`,
  or Log Analytics → `ContainerAppConsoleLogs_CL`.
- **Admin account**: `ADMIN_EMAIL` (set when running `deploy.sh`), password from
  `az containerapp secret show -n ca-boilerplate-dev-api -g rg-boilerplate-dev --secret-name admin-password --query value -o tsv`.
- **Rollback**: `az containerapp update -n <app> -g <rg> --image ghcr.io/<owner>/<repo>:sha-<commit>`.
- **Database access**: add your IP as a firewall rule on the server, then `psql` with
  `sslmode=require`; the password is the backend app's `db-password` secret.
- **Cold starts**: both apps scale to zero after about five idle minutes (to stay in a free
  account's grant). The first request then waits 30–60 s for the backend to start; set its
  `minReplicas` to 1 in `main.bicep` for always-on.
- **Limits**: the backend must stay at one replica at most (rate limits are per instance).
  A redeploy of the frontend can make a form opened before it fail once ("Failed to find Server
  Action"); reloading the page fixes it.
