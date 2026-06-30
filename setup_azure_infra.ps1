# -------------------------------------------------
# 0️⃣ Variables de Entorno
# -------------------------------------------------
$SubscriptionId = "1EED0703-BD6C-4E1C-80DA-244268996853"
$RG             = "rg-meta-ajo-dev"
$LOC            = "eastus"
$ACR_NAME       = "acrmetaajodev001"
$AKS_NAME       = "aks-adobe-meta-dev"
$KV_NAME        = "kv-meta-ajo-dev-001"
$MI_NAME        = "id-meta-webhook" # Managed Identity para el Pod
$EnvFilePath    = ".\.env.local"

az account set --subscription $SubscriptionId

# -------------------------------------------------
# 1️⃣ Grupo de Recursos y ACR
# -------------------------------------------------
az group create -n $RG -l $LOC
az acr create -g $RG -n $ACR_NAME --sku Basic --admin-enabled true
$AcrCred = az acr credential show -n $ACR_NAME --query "{username:username,password:passwords[0].value}" -o json | ConvertFrom-Json

# -------------------------------------------------
# 2️⃣ Key Vault e Importación de Secretos
# -------------------------------------------------
az keyvault create -n $KV_NAME -g $RG -l $LOC --enable-rbac-authorization true --sku standard

# Importa TODO desde el .env.local al Key Vault automáticamente
Get-Content $EnvFilePath | Where-Object { $_ -match '\S' -and $_ -notmatch '^#' } | ForEach-Object {
    $pair = $_ -split '=',2
    $kvSecretName = $pair[0].Trim().ToLower().Replace('_','-')
    az keyvault secret set -n $kvSecretName --vault-name $KV_NAME --value $pair[1].Trim() > $null
}

# -------------------------------------------------
# 3️⃣ AKS con Workload Identity Habilitado
# -------------------------------------------------
az aks create -g $RG -n $AKS_NAME --node-count 1 --node-vm-size Standard_D2s_v7 --enable-oidc-issuer --enable-workload-identity --attach-acr $ACR_NAME --generate-ssh-keys -l $LOC

# -------------------------------------------------
# 4️⃣ Azure Workload Identity (El corazón de la seguridad)
# -------------------------------------------------
# Crear Managed Identity para la aplicación
az identity create --name $MI_NAME --resource-group $RG
$MiClientId = az identity show --name $MI_NAME --resource-group $RG --query 'clientId' -o tsv

# Otorgar permiso de LEER SECRETOS en el Key Vault a esta Identidad
az role assignment create --role "Key Vault Secrets User" --assignee $MiClientId --scope /subscriptions/$SubscriptionId/resourceGroups/$RG/providers/Microsoft.KeyVault/vaults/$KV_NAME

# Obtener el Issuer OIDC del clúster AKS
$AksOidcIssuer = az aks show -n $AKS_NAME -g $RG --query "oidcIssuerProfile.issuerUrl" -o tsv

# Crear la Credencial Federada uniendo Azure AD con Kubernetes
az identity federated-credential create --name fed-meta-webhook --identity-name $MI_NAME --resource-group $RG --issuer $AksOidcIssuer --subject system:serviceaccount:ajo-namespace:meta-webhook-sa --audience api://AzureADTokenExchange

# -------------------------------------------------
# 5️⃣ Configuración en Kubernetes (ConfigMaps y ServiceAccount)
# -------------------------------------------------
az aks get-credentials -g $RG -n $AKS_NAME --overwrite-existing
kubectl create namespace ajo-namespace --dry-run=client -o yaml | kubectl apply -f -

# Crear el ServiceAccount con el ClientID de Azure
@"
apiVersion: v1
kind: ServiceAccount
metadata:
  name: meta-webhook-sa
  namespace: ajo-namespace
  annotations:
    azure.workload.identity/client-id: "$MiClientId"
"@ | kubectl apply -f -

# Aplicar el ConfigMap Público (CDP Endpoint, etc)
@"
apiVersion: v1
kind: ConfigMap
metadata:
  name: meta-webhook-config
  namespace: ajo-namespace
data:
  CDP_ENDPOINT_URL: "https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d"
  CDP_FLOW_ID: "01"
"@ | kubectl apply -f -

# -------------------------------------------------
# 6️⃣ Github Actions Service Principal (Solo para el CI/CD)
# -------------------------------------------------
$spJson = az ad sp create-for-rbac -n "sp-github-actions" --role Contributor --scopes /subscriptions/$SubscriptionId/resourceGroups/$RG --sdk-auth -o json

Write-Host "`n=== AGREGA ESTO A TUS GITHUB SECRETS ==="
Write-Host "AZURE_CREDENTIALS=$spJson"
Write-Host "ACR_USERNAME=$($AcrCred.username)"
Write-Host "ACR_PASSWORD=$($AcrCred.password)"
Write-Host "ACR_LOGIN_SERVER=$ACR_NAME.azurecr.io"
Write-Host "KEYVAULT_NAME=$KV_NAME"
Write-Host "RESOURCE_GROUP=$RG"
Write-Host "AKS_CLUSTER_NAME=$AKS_NAME"
Write-Host "SUBSCRIPTION_ID=$SubscriptionId"
Write-Host "REGION=$LOC"
