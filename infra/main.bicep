// One environment (dev, later main) of the whole product on Azure Container Apps: this API, the
// Next.js frontend, PostgreSQL, the upload share and the identity GitHub Actions deploys with.
// Run through infra/deploy.sh, which generates the secrets once and passes them in.
// See docs/deployment.md.

@description('Environment name, used in resource names and as the GitHub environment.')
param env string = 'dev'

param location string = resourceGroup().location

@description('Backend image, e.g. ghcr.io/neszadev/spring-postgres-boilerplate:dev')
param backendImage string

@description('Frontend image, e.g. ghcr.io/neszadev/nextjs-boilerplate:dev')
param frontendImage string

@description('GitHub owner of both repositories (for the deploy identity).')
param githubOwner string = 'neszaDev'

param backendRepo string = 'spring-postgres-boilerplate'
param frontendRepo string = 'nextjs-boilerplate'

// GitHub's OIDC subject names the owner and repositories by their immutable IDs as well
// (repo:<owner>@<id>/<repo>@<id>:environment:<env>), so a renamed or re-created repository
// can't take over the credential. deploy.sh looks them up.
@description('Numeric GitHub ID of the owner.')
param githubOwnerId string

@description('Numeric GitHub ID of the backend repository.')
param backendRepoId string

@description('Numeric GitHub ID of the frontend repository.')
param frontendRepoId string

@description('Bootstrap admin created at first start; empty for none.')
param adminEmail string = ''

@secure()
param dbPassword string

@secure()
param jwtSecret string

@secure()
param adminPassword string

var name = 'boilerplate-${env}'
var suffix = uniqueString(resourceGroup().id)
var backendName = 'ca-${name}-api'
var frontendName = 'ca-${name}-web'
var dbName = 'boilerplate'
var dbUser = 'boilerplate'
// Runtime user of the backend image (docker/Dockerfile: adduser -S app), owner of the share.
var appUid = '100'
var appGid = '101'

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-${name}'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
    // About the free 5 GB a month; logs past the cap are dropped until the next day.
    workspaceCapping: { dailyQuotaGb: json('0.15') }
  }
}

// Uploads (FILES_DIR). Container disks are wiped on every restart, so the files live here.
resource storage 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: take('stbp${env}${suffix}', 24)
  location: location
  sku: { name: 'Standard_LRS' }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    supportsHttpsTrafficOnly: true
    allowBlobPublicAccess: false
  }
}

resource fileService 'Microsoft.Storage/storageAccounts/fileServices@2023-05-01' = {
  parent: storage
  name: 'default'
}

resource share 'Microsoft.Storage/storageAccounts/fileServices/shares@2023-05-01' = {
  parent: fileService
  name: 'files'
  properties: { shareQuota: 5 }
}

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: 'psql-${name}-${take(suffix, 6)}'
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '17'
    administratorLogin: dbUser
    administratorLoginPassword: dbPassword
    // B1ms with 32 GB is the free-account PostgreSQL offer; auto-grow would leave it.
    storage: {
      storageSizeGB: 32
      autoGrow: 'Disabled'
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: { mode: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: dbName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// 0.0.0.0 means "Azure services": Container Apps on the consumption plan has no fixed outbound
// IP. Traffic is TLS (sslmode=require) and password-protected; move to a VNet for production.
resource allowAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: 'cae-${name}'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

resource environmentFiles 'Microsoft.App/managedEnvironments/storages@2024-03-01' = {
  parent: environment
  name: 'files'
  properties: {
    azureFile: {
      accountName: storage.name
      accountKey: storage.listKeys().keys[0].value
      shareName: share.name
      accessMode: 'ReadWrite'
    }
  }
}

var frontendUrl = 'https://${frontendName}.${environment.properties.defaultDomain}'

resource api 'Microsoft.App/containerApps@2024-03-01' = {
  name: backendName
  location: location
  properties: {
    managedEnvironmentId: environment.id
    configuration: {
      activeRevisionsMode: 'Single'
      // Internal only: reachable from the frontend (http://<name>), not from the internet, so
      // Swagger and /actuator/prometheus stay private.
      ingress: {
        external: false
        targetPort: 8080
        transport: 'http'
        allowInsecure: true
      }
      secrets: [
        { name: 'db-password', value: dbPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'admin-password', value: adminPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'api'
          image: backendImage
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'prod' }
            {
              name: 'DB_URL'
              value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${dbName}?sslmode=require'
            }
            { name: 'DB_USERNAME', value: dbUser }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'CORS_ALLOWED_ORIGINS', value: frontendUrl }
            { name: 'ADMIN_EMAIL', value: adminEmail }
            { name: 'ADMIN_PASSWORD', secretRef: 'admin-password' }
            { name: 'FILES_DIR', value: '/app/data/files' }
            { name: 'JAVA_TOOL_OPTIONS', value: '-XX:MaxRAMPercentage=75' }
          ]
          volumeMounts: [
            { volumeName: 'files', mountPath: '/app/data/files' }
          ]
          probes: [
            {
              type: 'Startup'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              initialDelaySeconds: 20
              periodSeconds: 10
              failureThreshold: 10
            }
            {
              type: 'Liveness'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 15
            }
            {
              type: 'Readiness'
              httpGet: { path: '/actuator/health/readiness', port: 8080 }
              periodSeconds: 10
            }
          ]
        }
      ]
      // At most one: rate limits are per instance (docs/deployment.md). Scales to zero when
      // idle to stay in the free grant; the first request after that waits for the JVM to start.
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'http'
            http: { metadata: { concurrentRequests: '50' } }
          }
        ]
      }
      volumes: [
        {
          name: 'files'
          storageType: 'AzureFile'
          storageName: environmentFiles.name
          mountOptions: 'uid=${appUid},gid=${appGid},dir_mode=0750,file_mode=0640'
        }
      ]
    }
  }
  dependsOn: [database, allowAzure]
}

resource web 'Microsoft.App/containerApps@2024-03-01' = {
  name: frontendName
  location: location
  properties: {
    managedEnvironmentId: environment.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 3000
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: 'web'
          image: frontendImage
          resources: {
            cpu: json('0.25')
            memory: '0.5Gi'
          }
          env: [
            { name: 'BACKEND_URL', value: 'http://${backendName}' }
            { name: 'APP_URL', value: frontendUrl }
          ]
          probes: [
            {
              type: 'Liveness'
              httpGet: { path: '/', port: 3000 }
              periodSeconds: 30
            }
            {
              type: 'Readiness'
              httpGet: { path: '/', port: 3000 }
              periodSeconds: 10
            }
          ]
        }
      ]
      // Scales to zero when idle (a few seconds' cold start). One replica keeps the free grant;
      // more would share the image's build-time server-actions key, so raising it is safe.
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'http'
            http: { metadata: { concurrentRequests: '50' } }
          }
        ]
      }
    }
  }
}

// GitHub Actions signs in as this identity (OIDC, no stored secret) and may only update the two
// container apps (and read their environment). Federated credentials on one identity must be
// created one at a time.
resource deployer 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'id-${name}-github'
  location: location
}

resource backendCredential 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: deployer
  name: 'github-${backendRepo}-${env}'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    subject: 'repo:${githubOwner}@${githubOwnerId}/${backendRepo}@${backendRepoId}:environment:${env}'
    audiences: ['api://AzureADTokenExchange']
  }
}

resource frontendCredential 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: deployer
  name: 'github-${frontendRepo}-${env}'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    subject: 'repo:${githubOwner}@${githubOwnerId}/${frontendRepo}@${frontendRepoId}:environment:${env}'
    audiences: ['api://AzureADTokenExchange']
  }
  dependsOn: [backendCredential]
}

var contributorRole = subscriptionResourceId(
  'Microsoft.Authorization/roleDefinitions',
  'b24988ac-6180-42a0-ab88-20f7382dd24c'
)

resource apiDeployer 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: api
  name: guid(api.id, deployer.id, contributorRole)
  properties: {
    roleDefinitionId: contributorRole
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource webDeployer 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: web
  name: guid(web.id, deployer.id, contributorRole)
  properties: {
    roleDefinitionId: contributorRole
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// `az containerapp update` reads the environment the app runs in.
var readerRole = subscriptionResourceId(
  'Microsoft.Authorization/roleDefinitions',
  'acdd72a7-3385-48ef-bd42-f606fba81ae7'
)

resource environmentReader 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  scope: environment
  name: guid(environment.id, deployer.id, readerRole)
  properties: {
    roleDefinitionId: readerRole
    principalId: deployer.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

output frontendUrl string = frontendUrl
output backendApp string = api.name
output frontendApp string = web.name
output postgresHost string = postgres.properties.fullyQualifiedDomainName
output deployClientId string = deployer.properties.clientId
