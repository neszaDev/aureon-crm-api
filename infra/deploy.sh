#!/usr/bin/env bash
# Create or update one Azure environment from infra/main.bicep. Idempotent: run it again after
# changing the template. Secrets are generated on the first run, kept as secrets of the backend
# container app, and read back from there on every later run (no Key Vault: not in the free tier).
#
#   az login
#   ADMIN_EMAIL=me@example.com infra/deploy.sh dev
#
# Afterwards GitHub Actions rolls out new images (publish-image.yml); this script keeps the
# images that are running.
set -euo pipefail

ENV="${1:-dev}"
LOCATION="${AZURE_LOCATION:-southeastasia}"
RG="${AZURE_RESOURCE_GROUP:-rg-boilerplate-$ENV}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"
OWNER="${GITHUB_OWNER:-neszaDev}"
# Image tags follow the environment: :dev for dev, :main for main.
DEFAULT_BACKEND="ghcr.io/$(printf '%s' "$OWNER" | tr '[:upper:]' '[:lower:]')/spring-postgres-boilerplate:$ENV"
DEFAULT_FRONTEND="ghcr.io/$(printf '%s' "$OWNER" | tr '[:upper:]' '[:lower:]')/nextjs-boilerplate:$ENV"
BACKEND_APP="ca-boilerplate-$ENV-api"
FRONTEND_APP="ca-boilerplate-$ENV-web"
here="$(cd "$(dirname "$0")" && pwd)"

# Numeric IDs for the GitHub OIDC subject (public, no token needed).
github_id() {
  curl -fsS "https://api.github.com/repos/$OWNER/$1" |
    python3 -c "import json, sys; d = json.load(sys.stdin); print(d['$2'] if '$2' == 'id' else d['owner']['id'])"
}

step() { printf '\n==> %s\n' "$*"; }

step "Subscription"
az account show --query '{name:name, id:id, user:user.name}' -o table
SUB="$(az account show --query id -o tsv)"

step "Resource providers"
for ns in Microsoft.App Microsoft.OperationalInsights Microsoft.DBforPostgreSQL Microsoft.Storage \
  Microsoft.ManagedIdentity; do
  az provider register --namespace "$ns" --wait -o none
done

step "Resource group $RG ($LOCATION)"
az group create -n "$RG" -l "$LOCATION" -o none

# Postgres wants three character classes; hex plus a fixed prefix has four.
db_password() { printf 'Pg-%s' "$(openssl rand -hex 16)"; }
jwt_secret() { openssl rand -hex 32; }
admin_password() { openssl rand -base64 18 | tr -d '\n'; }

# Prints the backend app's secret, or a new value from the generator on the first run.
secret() {
  local value
  value="$(az containerapp secret show -n "$BACKEND_APP" -g "$RG" --secret-name "$1" \
    --query value -o tsv 2>/dev/null || true)"
  if [ -n "$value" ]; then printf '%s' "$value"; else "$2"; fi
}

step "Secrets"
DB_PASSWORD="$(secret db-password db_password)"
JWT_SECRET="$(secret jwt-secret jwt_secret)"
ADMIN_PASSWORD="$(secret admin-password admin_password)"

# Keep whatever image is running (GitHub Actions deploys new ones); first run uses the tag.
current_image() {
  az containerapp show -n "$1" -g "$RG" --query 'properties.template.containers[0].image' -o tsv 2>/dev/null || true
}
BACKEND_IMAGE="$(current_image "$BACKEND_APP")"
FRONTEND_IMAGE="$(current_image "$FRONTEND_APP")"

step "GitHub IDs"
OWNER_ID="$(github_id spring-postgres-boilerplate owner)"
BACKEND_REPO_ID="$(github_id spring-postgres-boilerplate id)"
FRONTEND_REPO_ID="$(github_id nextjs-boilerplate id)"
echo "owner $OWNER_ID, backend $BACKEND_REPO_ID, frontend $FRONTEND_REPO_ID"

step "Deploy infra/main.bicep"
params="$(mktemp)"
trap 'rm -f "$params"' EXIT
chmod 600 "$params"
cat > "$params" <<JSON
{
  "\$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#",
  "contentVersion": "1.0.0.0",
  "parameters": {
    "env": { "value": "$ENV" },
    "githubOwner": { "value": "$OWNER" },
    "githubOwnerId": { "value": "$OWNER_ID" },
    "backendRepoId": { "value": "$BACKEND_REPO_ID" },
    "frontendRepoId": { "value": "$FRONTEND_REPO_ID" },
    "backendImage": { "value": "${BACKEND_IMAGE:-$DEFAULT_BACKEND}" },
    "frontendImage": { "value": "${FRONTEND_IMAGE:-$DEFAULT_FRONTEND}" },
    "adminEmail": { "value": "$ADMIN_EMAIL" },
    "dbPassword": { "value": "$DB_PASSWORD" },
    "jwtSecret": { "value": "$JWT_SECRET" },
    "adminPassword": { "value": "$ADMIN_PASSWORD" }
  }
}
JSON
az deployment group create -g "$RG" -n "boilerplate-$ENV" -f "$here/main.bicep" -p "@$params" -o none

out() { az deployment group show -g "$RG" -n "boilerplate-$ENV" --query "properties.outputs.$1.value" -o tsv; }

step "Done"
cat <<EOF
Frontend:  $(out frontendUrl)
Backend:   $(out backendApp) (internal only)
Database:  $(out postgresHost)

GitHub environment "$ENV" variables, in both repositories (used by publish-image.yml):
  AZURE_CLIENT_ID=$(out deployClientId)
  AZURE_TENANT_ID=$(az account show --query tenantId -o tsv)
  AZURE_SUBSCRIPTION_ID=$SUB
  AZURE_RESOURCE_GROUP=$RG
  AZURE_CONTAINER_APP=<$BACKEND_APP in the backend repo, $FRONTEND_APP in the frontend repo>

Admin password: az containerapp secret show -n $BACKEND_APP -g $RG --secret-name admin-password --query value -o tsv
EOF
