# Azure deployment

Status: dev environment in progress (2026-10-02). Branch `feat/azure-deploy` in both repos.

## Decisions

| Topic | Decision | Rejected |
|---|---|---|
| Hosting | Azure Container Apps (consumption) for both apps: the images CI already builds and smoke-tests run unchanged; scale to zero for the frontend. | App Service: one plan per app, less natural for two containers talking privately. AKS: far more to operate. |
| Database | Azure Database for PostgreSQL Flexible Server, Burstable B1ms, PostgreSQL 17 (same major as compose and tests), TLS required. | Postgres in a container: no backups, data tied to a replica. |
| Network | Frontend public (HTTPS, managed certificate). Backend **internal** ingress only, so Swagger and `/actuator/prometheus` aren't on the internet. Database allows Azure services (0.0.0.0 rule) because consumption Container Apps have no fixed outbound IP. | A VNet for dev: more cost and setup; do it for production. |
| Uploads | Azure Files share mounted at `/app/data/files` (owned by the image's `app` user), so files survive restarts; the code is unchanged (`LocalFileStorage`). | Blob storage now: needs a second `FileStorage` implementation. |
| Scale | Both apps scale to zero when idle and to at most one replica (backend 0.5 vCPU / 1 GiB, frontend 0.25 / 0.5 GiB), which keeps a free account within the Container Apps monthly grant. The backend can't go above one: rate limits are per instance. | Always-on backend: about US$27/month beyond the grant. |
| Secrets | Generated once by `infra/deploy.sh`, passed to the template as secure parameters, stored as secrets of the backend container app and read back from there on later runs. | Key Vault: not in the free tier. Secrets in the repo or in GitHub. |
| Deploys | Infrastructure: `infra/deploy.sh` (Bicep), run by hand. Images: each repo's `publish-image.yml` updates its container app by image digest, signing in through GitHub OIDC to a user-assigned identity that is Contributor on those two apps only. | A long-lived service-principal secret in GitHub. |
| Region | Southeast Asia (Singapore), closest to the users. | |

## Cost (dev, free account)

Sized for an Azure free account:

- **PostgreSQL** B1ms, 32 GB, auto-grow off: the 12-month free offer (750 hours a month).
- **Container Apps**: the monthly grant (180,000 vCPU-s, 360,000 GiB-s) covers roughly 70 hours of
  both apps awake; beyond that about US$0.06 per backend hour. Idle apps cost nothing.
- **Log Analytics** capped at 0.15 GB/day (the free 5 GB/month); the 5 GB file share is within
  the free-account storage offer.

The price of free: after about five idle minutes both apps sleep, and the first visit waits for
them (30–60 s while the backend's JVM starts). For always-on, set the backend's `minReplicas` to 1.

## Follow-ups

- `main` environment: release `dev` → `main` in both repos, then `infra/deploy.sh main`.
- Production hardening: VNet with private endpoints for PostgreSQL and storage, a custom
  domain, Blob storage and a shared rate-limit store before scaling the backend out.
