# 🌟 Adobe AJO – Quarkus Webhook Demo
**Repository:** `springAIQuarkus`
**Author:** Joffre Hermosilla
**Date:** 2026‑06‑11

---

## Table of Contents
1. [Architecture Overview](#architecture-overview)  
2. [Prerequisites](#prerequisites)  
3. [Step‑by‑Step Provisioning](#step‑by‑step-provisioning)   
   - 3.1 [Resource Group](#resource-group)  
   - 3.2 [Azure Container Registry (ACR)](#azure-container-registry-acr)  
   - 3.3 [Azure Kubernetes Service (AKS)](#azure-kubernetes-service-aks)  
   - 3.4 [Key Vault & Service Principal](#key‑vault‑&‑service‑principal)  
   - 3.5 [API Management (APIM)](#api-management-apim) *(optional)*  
   - 3.6 [GitHub Secrets](#github-secrets)  
   - 3.7 [Kubernetes Manifests (ConfigMaps, Deployment, CSI driver)](#kubernetes‑manifests)  
4. [Running the CI/CD Pipeline](#running-the-cicd-pipeline)  
5. [Testing the Webhook](#testing-the-webhook)  
6. [Full Command Reference](#full‑command‑reference)  
7. [License](#license)  

---

## Architecture Overview

```mermaid
graph TD
  subgraph Azure
    subgraph RG[Resource Group<br/>rg-meta-ajo-dev]
      KV[Key Vault<br/>kv-meta-ajo-dev-001]
      ACR[Container Registry<br/>acrmetaajodev001.azurecr.io]
      AKS[AKS Cluster<br/>aks-adobe-meta-dev]
      APIM[API Management<br/>apim-adobe-meta-dev] 
      SP[Service Principal]
    end
  end

  subgraph GitHub
    GH[GitHub Repo<br/>springAIQuarkus]
    GHSecrets[GitHub Secrets]
  end

  subgraph K8s[AKS Namespace<br/>ajo-namespace]
    CM1[ConfigMap<br/>meta-webhook-config]
    CM2[ConfigMap<br/>ajo-config (non‑secret data)]
    Deploy[Deployment<br/>ajo-app]
    CSI[CSI‑driver – secrets‑store]
    Pod[Pod<br/>ajo‑container]
  end

  %% Connections
  GH -->|push triggers| GHActions[GitHub Actions CI/CD]
  GHSecrets -->|used by| GHActions
  GHActions -->|build & push image| ACR
  GHActions -->|kubectl apply| K8s
  ACR -->|pull image| Pod
  KV -->|provides secrets| CSI
  CSI -->|mounts files| Pod
  SP -->|OIDC federated identity| AKS
  APIM -->|exposes| Webhook[Adobe CDP Webhook] 
  Deploy -->|creates| Pod
  CM1 -->|env variables| Deploy
  CM2 -->|env variables| Deploy
```

* **AKS** runs Quarkus, authenticates to **Key Vault** via **Workload Identity** (OIDC).  
* **ConfigMaps** carry non‑sensitive configuration (`CDP_ENDPOINT_URL`, `CDP_FLOW_ID`, etc.).  
* **CSI driver** (Azure Key Vault provider) injects the sensitive values (`clientId`, `clientSecret`, DB passwords, …) as files inside the container.  
* **APIM** (optional) can expose the webhook publicly; its gateway URL is used when registering the webhook in Adobe CDP.  
* **Deployment** creates the pod that runs the application.

---

## Prerequisites

| Tool | Version | How to install |
|------|--------|----------------|
| Azure CLI | `2.62+` | `az upgrade` |
| kubectl | `1.28+` | `az aks install-cli` |
| Git | any | – |
| Docker (or podman) | – | – |
| **GitHub** repository with **GitHub Actions** workflow `azure-aks-ci-cd.yml` (already present in this repo) | – | – |
| PowerShell (or Bash) – the commands below are shown in PowerShell syntax (`` ` `` line‑continuation). | – | – |

You must have **Owner** (or at least **User Access Administrator**) rights on the Azure subscription `1EED0703-BD6C-4E1C-80DA-244268996853`.

---

## Step‑by‑Step Provisioning  

### 1️⃣ Resource Group  
```powershell
$RG   = "rg-meta-ajo-dev"
$LOC  = "eastus"

az group create -n $RG -l $LOC
```

### 2️⃣ Azure Container Registry (ACR)  
```powershell
$ACR_NAME = "acrmetaajodev001"

az acr create `
    -g $RG `
    -n $ACR_NAME `
    --sku Basic `
    --admin-enabled true `
    -l $LOC
```
*Result:* `acrmetaajodev001.azurecr.io` (admin user enabled).  

### 3️⃣ Azure Kubernetes Service (AKS)  
```powershell
$AKS_NAME   = "aks-adobe-meta-dev"
$NODE_COUNT = 1
$VM_SIZE    = "Standard_D2s_v7"      # allowed in this subscription

az aks create `
    -g $RG `
    -n $AKS_NAME `
    --node-count $NODE_COUNT `
    --node-vm-size $VM_SIZE `
    --enable-oidc-issuer `
    --enable-workload-identity `
    --attach-acr $ACR_NAME `
    --generate-ssh-keys `
    -l $LOC
```
*Result:* AKS cluster with OIDC issuer and **Workload Identity** enabled.  

```powershell
# Get credentials for `kubectl`
az aks get-credentials -g $RG -n $AKS_NAME --overwrite-existing
```

### 4️⃣ Key Vault & Service Principal  
```powershell
# 4‑a – Reset client secret (creates a fresh password)
az ad sp credential reset `
    --id 4ca24142-63e4-4d16-90dc-712b03d48e7d `
    --query "{clientId:appId, clientSecret:password}" `
    -o json
```
*Copy the `clientSecret` value – it will become `<TU_CLIENT_SECRET>` in the GitHub secret `AZURE_CREDENTIALS`.*

```powershell
# 4‑b – Verify you have a Key Vault (already created in the project)
az keyvault show -n kv-meta-ajo-dev-001 -g $RG
```
All the variables from `.env.local` have been imported into that Key Vault (script `setup_azure_infra.ps1` performed the import).

### 5️⃣ API Management (APIM) – optional  
If you need a public endpoint for the webhook, create APIM:
```powershell
$APIM_NAME = "apim-adobe-meta-dev"

az apim create `
    -g $RG `
    -n $APIM_NAME `
    -l $LOC `
    --publisher-email "joffre.hermosilla@gmail.com" `
    --publisher-name "Joffre Hermosilla" `
    --sku-name Developer
```
Get the gateway URL (to register in Adobe CDP):
```powershell
az apim show -g $RG -n $APIM_NAME --query "gatewayUrl" -o tsv
```

### 6️⃣ GitHub Secrets  
| Secret | Value (paste exactly) |
|--------|-----------------------|
| `AZURE_CREDENTIALS` | `json {"clientId":"4ca24142-63e4-4d16-90dc-712b03d48e7d","clientSecret":"<TU_CLIENT_SECRET>","subscriptionId":"1EED0703-BD6C-4E1C-80DA-244268996853","tenantId":"6c6cf498-31a0-4d91-ae90-cb1b77642638","activeDirectoryEndpointUrl":"https://login.microsoftonline.com","resourceManagerEndpointUrl":"https://management.azure.com/","activeDirectoryGraphResourceId":"https://graph.windows.net/","sqlManagementEndpointUrl":"https://management.core.windows.net:8443/","galleryEndpointUrl":"https://gallery.azure.com/","managementEndpointUrl":"https://management.core.windows.net/"}` |
| `ACR_USERNAME` | `acrmetaajodev001` |
| `ACR_PASSWORD` | `<PASSWORD_FROM_ACR>` *(see step 2‑b below)* |
| `ACR_LOGIN_SERVER` | `acrmetaajodev001.azurecr.io` |
| `KEYVAULT_NAME` | `kv-meta-ajo-dev-001` |
| `RESOURCE_GROUP` | `rg-meta-ajo-dev` |
| `AKS_CLUSTER_NAME` | `aks-adobe-meta-dev` |
| `APIM_NAME` | `apim-adobe-meta-dev` |
| `SUBSCRIPTION_ID` | `1EED0703-BD6C-4E1C-80DA-244268996853` |
| `REGION` | `eastus` |

**How to get `ACR_PASSWORD`** (already created in step 2):
```powershell
az acr credential show -n $ACR_NAME -g $RG `
    --query "{username:username,password:passwords[0].value}" -o json
```
Copy the `password` field and paste it as `ACR_PASSWORD`.

Create each secret in **Settings → Secrets and variables → Actions → New repository secret**.

### 7️⃣ Kubernetes Manifests  
#### 7‑a Namespace  
```bash
kubectl create namespace ajo-namespace
```
#### 7‑b ConfigMap with CDP data  
```yaml
# k8s/meta-webhook-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: meta-webhook-config
  namespace: ajo-namespace
data:
  CDP_ENDPOINT_URL: "https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d"
  CDP_FLOW_ID:      "086a7d7d-a5bd-41b9-bc84-b75ff53c9f51"
```
Apply:
```bash
kubectl apply -f k8s/meta-webhook-config.yaml
```
#### 7‑c Optional non‑sensitive ConfigMap (`ajo-config`)  
```yaml
# k8s/ajo-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ajo-config
  namespace: ajo-namespace
data:
  KEYVAULT_NAME: kv-meta-ajo-dev-001
  REGION: eastus
  SPRING_PROFILES_ACTIVE: prod
```
```bash
kubectl apply -f k8s/ajo-config.yaml
```
#### 7‑d SecretProviderClass (Azure Key Vault CSI driver) – optional but **recommended**  
```yaml
# k8s/secretproviderclass.yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: azure-keyvault-secrets
  namespace: ajo-namespace
spec:
  provider: azure
  secretObjects:
  - secretName: kv-secrets
    type: Opaque
    data:
    - objectName: AZURE_CLIENT_ID
      key: clientId
    - objectName: AZURE_CLIENT_SECRET
      key: clientSecret
    - objectName: DB_PASSWORD
      key: dbPassword
  parameters:
    usePodIdentity: "false"
    useVMManagedIdentity: "true"
    userAssignedIdentityID: ""                # leave empty if using system‑assigned MI
    keyvaultName: kv-meta-ajo-dev-001
    tenantId: 6c6cf498-31a0-4d91-ae90-cb1b77642638
    objects: |
      array:
        - |
          objectName: AZURE_CLIENT_ID
          objectType: secret
        - |
          objectName: AZURE_CLIENT_SECRET
          objectType: secret
        - |
          objectName: DB_PASSWORD
          objectType: secret
```
Install the driver (once per cluster) and apply the SPC:
```bash
# Install driver
kubectl apply -f https://raw.githubusercontent.com/Azure/secrets-store-csi-driver-provider-azure/master/deploy/provider-azure-installer.yaml

# Apply the SecretProviderClass
kubectl apply -f k8s/secretproviderclass.yaml
```
#### 7‑e Deployment  
```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ajo-app
  namespace: ajo-namespace
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ajo
  template:
    metadata:
      labels:
        app: ajo
    spec:
      # ServiceAccount that has the federated identity (default works when workload‑identity enabled)
      serviceAccountName: default
      containers:
        - name: ajo-container
          image: acrmetaajodev001.azurecr.io/ajo-app:latest   # <-- your built image
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: meta-webhook-config     # CDP_ENDPOINT_URL + CDP_FLOW_ID
            - configMapRef:
                name: ajo-config              # non‑secret data (KV name, region, …)
          # Uncomment the block below **if you installed the CSI driver**
          volumeMounts:
            - name: kv-secrets
              mountPath: "/mnt/secrets"
              readOnly: true
      volumes:
        - name: kv-secrets
          csi:
            driver: secrets-store.csi.k8s.io
            readOnly: true
            volumeAttributes:
              secretProviderClass: "azure-keyvault-secrets"
```
Apply:
```bash
kubectl apply -f k8s/deployment.yaml
```
#### 7‑f Service (optional – expose via LoadBalancer)  
```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: ajo-svc
  namespace: ajo-namespace
spec:
  type: LoadBalancer
  selector:
    app: ajo
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
```
```bash
kubectl apply -f k8s/service.yaml
```
---

## Running the CI/CD Pipeline  
The repository already contains a GitHub Actions workflow `azure-aks-ci-cd.yml`.  
When the **GitHub secrets** from step 6 are present, any push to `main` (or the configured branch) will:
1. **Login to Azure** using `AZURE_CREDENTIALS`.  
2. **Build the Docker image** and push it to `acrmetaajodev001.azurecr.io`.  
3. **Set the AKS context** (`az aks get-credentials`).  
4. **Apply the manifests** (`k8s/*.yaml`).
```bash
git add .
git commit -m "Add infra secrets, ConfigMaps and Deployment"
git push
```
Monitor execution in **GitHub → Actions**.
---

## Testing the Webhook  
1. Retrieve the external IP of the Service (or the APIM gateway URL if you created APIM):  
```bash
kubectl get svc ajo-svc -n ajo-namespace
```
The column `EXTERNAL-IP` will be a public IP (e.g. `20.70.xxx.xxx`).  
2. Send a test POST request to the webhook endpoint (replace `<IP>` with the value you obtained):  
```bash
curl -X POST "http://<IP>/whatsapp/webhook" \
     -H "Content-Type: application/json" \
     -d '{"message":"Hello from test"}'
```
3. Verify in the pod logs that the request is processed and that the payload is forwarded to **Adobe CDP** using the values from `meta-webhook-config`.  
```bash
POD=$(kubectl get pods -n ajo-namespace -l app=ajo -o jsonpath="{.items[0].metadata.name}")
kubectl logs $POD -n ajo-namespace
```
---

## Full Command Reference  
Below is a **single‑shot script** (PowerShell) you can copy‑paste to recreate the whole environment from scratch (except the optional APIM part).  
```powershell
# -------------------------------------------------
# 0️⃣ Variables
# -------------------------------------------------
$RG          = "rg-meta-ajo-dev"
$LOC         = "eastus"
$ACR_NAME    = "acrmetaajodev001"
$AKS_NAME    = "aks-adobe-meta-dev"
$SP_ID       = "4ca24142-63e4-4d16-90dc-712b03d48e7d"
$KV_NAME     = "kv-meta-ajo-dev-001"
$APIM_NAME   = "apim-adobe-meta-dev"
# -------------------------------------------------
# 1️⃣ Resource Group
# -------------------------------------------------
az group create -n $RG -l $LOC

# -------------------------------------------------
# 2️⃣ ACR
# -------------------------------------------------
az acr create -g $RG -n $ACR_NAME --sku Basic --admin-enabled true -l $LOC

# -------------------------------------------------
# 3️⃣ AKS (with ACR attach, OIDC & Workload Identity)
# -------------------------------------------------
az aks create `
    -g $RG `
    -n $AKS_NAME `
    --node-count 1 `
    --node-vm-size Standard_D2s_v7 `
    --enable-oidc-issuer `
    --enable-workload-identity `
    --attach-acr $ACR_NAME `
    --generate-ssh-keys `
    -l $LOC

az aks get-credentials -g $RG -n $AKS_NAME --overwrite-existing

# -------------------------------------------------
# 4️⃣ Service Principal secret (clientSecret)
# -------------------------------------------------
az ad sp credential reset --id $SP_ID --query "{clientId:appId, clientSecret:password}" -o json

# -------------------------------------------------
# 5️⃣ ACR credentials (password)
# -------------------------------------------------
az acr credential show -n $ACR_NAME -g $RG `
    --query "{username:username,password:passwords[0].value}" -o json

# -------------------------------------------------
# 6️⃣ (Optional) APIM
# -------------------------------------------------
az apim create -g $RG -n $APIM_NAME -l $LOC `
    --publisher-email "joffre.hermosilla@gmail.com" `
    --publisher-name "Joffre Hermosilla" `
    --sku-name Developer

az apim show -g $RG -n $APIM_NAME --query "gatewayUrl" -o tsv

# -------------------------------------------------
# 7️⃣ Namespace & ConfigMaps
# -------------------------------------------------
kubectl create namespace ajo-namespace

# meta‑webhook‑config
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: meta-webhook-config
  namespace: ajo-namespace
data:
  CDP_ENDPOINT_URL: "https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d"
  CDP_FLOW_ID: "086a7d7d-a5bd-41b9-bc84-b75ff53c9f51"
EOF

# ajo‑config (non‑secret data)
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: ajo-config
  namespace: ajo-namespace
data:
  KEYVAULT_NAME: "$KV_NAME"
  REGION: "$LOC"
  SPRING_PROFILES_ACTIVE: prod
EOF

# -------------------------------------------------
# 8️⃣ CSI driver (if you want secret injection from KV)
# -------------------------------------------------
kubectl apply -f https://raw.githubusercontent.com/Azure/secrets-store-csi-driver-provider-azure/master/deploy/provider-azure-installer.yaml

cat <<EOF | kubectl apply -f -
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: azure-keyvault-secrets
  namespace: ajo-namespace
spec:
  provider: azure
  secretObjects:
  - secretName: kv-secrets
    type: Opaque
    data:
    - objectName: AZURE_CLIENT_ID
      key: clientId
    - objectName: AZURE_CLIENT_SECRET
      key: clientSecret
    - objectName: DB_PASSWORD
      key: dbPassword
  parameters:
    usePodIdentity: "false"
    useVMManagedIdentity: "true"
    keyvaultName: "$KV_NAME"
    tenantId: "6c6cf498-31a0-4d91-ae90-cb1b77642638"
    objects: |
      array:
        - |
          objectName: AZURE_CLIENT_ID
          objectType: secret
        - |
          objectName: AZURE_CLIENT_SECRET
          objectType: secret
        - |
          objectName: DB_PASSWORD
          objectType: secret
EOF

# -------------------------------------------------
# 9️⃣ Deployment (replace image tag as needed)
# -------------------------------------------------
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ajo-app
  namespace: ajo-namespace
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ajo
  template:
    metadata:
      labels:
        app: ajo
    spec:
      serviceAccountName: default
      containers:
        - name: ajo-container
          image: $ACR_NAME.azurecr.io/ajo-app:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: meta-webhook-config
            - configMapRef:
                name: ajo-config
          volumeMounts:
            - name: kv-secrets
              mountPath: "/mnt/secrets"
              readOnly: true
      volumes:
        - name: kv-secrets
          csi:
            driver: secrets-store.csi.k8s.io
            readOnly: true
            volumeAttributes:
              secretProviderClass: "azure-keyvault-secrets"
EOF

# -------------------------------------------------
# 10️⃣ Service (LoadBalancer)
# -------------------------------------------------
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: ajo-svc
  namespace: ajo-namespace
spec:
  type: LoadBalancer
  selector:
    app: ajo
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
EOF
```
Running the script end‑to‑end will give you a **fully functional** environment ready for the CI/CD pipeline.
---

## License  
This demo project is provided **as‑is** under the MIT License. Feel free to adapt the architecture, scripts, and manifests to your own needs.

---

**Enjoy building!** 🚀
