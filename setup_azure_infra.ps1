$SubscriptionId = "1EED0703-BD6C-4E1C-80DA-244268996853"
$Location = "eastus"
$ResourceGroup = "rg-meta-ajo-dev"
$AcrName = "acradobemetadev"
$KvName = "kv-meta-ajo-dev-001"
$AksName = "aks-adobe-meta-dev"
$ApimName = "apim-adobe-meta-dev"
$SpName = "sp-ajo-ci-cd"
$EnvFilePath = "C:\Users\aluca\OneDrive\Desktop\ia\adobe\ajo\springAIQuarkus\.env.local"

# Set subscription
az account set --subscription $SubscriptionId

# Create Resource Group
az group create -n $ResourceGroup -l $Location

# Create ACR
az acr create -g $ResourceGroup -n $AcrName --sku Basic --admin-enabled true
$AcrCred = az acr credential show -n $AcrName --query "{username:username,password:passwords[0].value}" -o json | ConvertFrom-Json

# Create Key Vault
az keyvault create -n $KvName -g $ResourceGroup -l $Location --enable-rbac-authorization true --sku standard

# Import secrets from .env.local
Get-Content $EnvFilePath |
  Where-Object { $_ -match '\S' -and $_ -notmatch '^#' } |
  ForEach-Object {
    $pair = $_ -split '=',2
    $key = $pair[0].Trim()
    $val = $pair[1].Trim()
    $kvSecretName = $key.ToLower().Replace('_','-')
    az keyvault secret set -n $kvSecretName --vault-name $KvName --value $val > $null
  }

# Create Service Principal
$spJson = az ad sp create-for-rbac -n $SpName --role Contributor --scopes /subscriptions/$SubscriptionId/resourceGroups/$ResourceGroup --sdk-auth -o json
# Assign AcrPull role to the SP
$sp = $spJson | ConvertFrom-Json
az role assignment create --assignee-object-id $sp.appId --role "AcrPull" --scope /subscriptions/$SubscriptionId/resourceGroups/$ResourceGroup/providers/Microsoft.ContainerRegistry/registries/$AcrName

# Create AKS
az aks create -g $ResourceGroup -n $AksName --node-count 2 --node-vm-size Standard_DS2_v2 --enable-oidc-issuer --enable-workload-identity --attach-acr $AcrName --generate-ssh-keys --location $Location

# Get AKS credentials (optional, for testing)
az aks get-credentials -g $ResourceGroup -n $AksName --overwrite-existing

# Create APIM
az apim create -g $ResourceGroup -n $ApimName --publisher-email "joffre.hermosilla@gmail.com" --publisher-name "Joffre Hermosilla" --sku Developer --location $Location

# Output important values for GitHub secrets
Write-Host "=== VALUES FOR GITHUB SECRETS ==="
Write-Host "AZURE_CREDENTIALS=$spJson"
Write-Host "ACR_USERNAME=$($AcrCred.username)"
Write-Host "ACR_PASSWORD=$($AcrCred.password)"
Write-Host "ACR_LOGIN_SERVER=$AcrName.azurecr.io"
Write-Host "KEYVAULT_NAME=$KvName"
Write-Host "RESOURCE_GROUP=$ResourceGroup"
Write-Host "AKS_CLUSTER_NAME=$AksName"
Write-Host "APIM_NAME=$ApimName"
Write-Host "SUBSCRIPTION_ID=$SubscriptionId"
Write-Host "REGION=$Location"
