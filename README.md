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

## Architecture Overview & Data Flow

```mermaid
graph TD
  WA[WhatsApp User] -->|HTTPS POST\nmeta-ajo-webhook.eastus.cloudapp.azure.com| LB

  subgraph AzureInfra[Azure Infrastructure]
    LB["LoadBalancer (Ingress)\n20.241.207.1"]
    LB --> IngressController["NGINX Ingress Controller\n(SSL Termination)"]
    IngressController -->|HTTP Route /webhook| Quarkus
    
    subgraph AjoNS[ajo-namespace]
      Quarkus["Quarkus App\nmeta-whatsapp-webhook"]
    end
    
    Cert["Cert-Manager\n(Let's Encrypt TLS)"] -.->|issues cert| IngressController
    ACR["Azure Container Registry\nacrmetaajodev001"]
    KV["Azure Key Vault\nkv-meta-ajo-dev-001"]
  end

  subgraph ExternalSvc[External Services]
    AI["AI Service\nLangChain4j"]
    CDP["Adobe CDP API"]
    Mongo[("MongoDB Atlas")]
  end

  subgraph CICD[CI/CD - GitHub Actions]
    Repo["GitHub Repo"] -->|push to main| GA["GitHub Actions"]
    GA -->|build and push| ACR
    GA -->|helm deploy| AjoNS
  end

  Quarkus -->|save event| Mongo
  Quarkus -->|text message| AI
  Quarkus -->|button click| CDP
  KV -->|secrets| Quarkus
  ACR -->|pull image| Quarkus
```

### Functional Flow
1. **Webhook Reception**: Meta sends a POST request to our exposed LoadBalancer IP (`/webhook`).
2. **Persistence**: Every incoming message is logged into MongoDB Atlas.
3. **Routing**:
   - If the user sends **text**, the app routes the message to the **AI Fallback Service** (LangChain4j) to generate a dynamic response.
   - If the user clicks a **button**, the app builds a structured payload and sends it to **Adobe CDP** to trigger a journey.

---

## Recent Fixes & Pipeline Resolution (Log)

To achieve the fully functional CI/CD pipeline and expose the endpoint to Meta, the following critical steps were executed:

1. **Dockerfile Creation**: Created `src/main/docker/Dockerfile.jvm` using the `eclipse-temurin:21-jre-alpine` base image and fast-jar Quarkus layout, solving the `lstat no such file or directory` error in GitHub Actions.
2. **GitHub Actions Workflow Correction**: Updated `.github/workflows/azure-aks-ci-cd.yml` to:
   - Use dynamic GitHub Secrets (`ACR_LOGIN_SERVER`, `AKS_CLUSTER_NAME`, etc.) instead of hardcoded incorrect values.
   - Add the `azure/login@v2` step, required for proper authentication before setting the AKS context.
3. **Helm Generation Fix**: Added the missing `io.quarkiverse.helm:quarkus-helm` (v1.2.3) dependency to `pom.xml`, allowing Quarkus to auto-generate the Helm charts in `target/helm/kubernetes/meta-webhook-app` during the build phase.
4. **Service Exposure (LoadBalancer)**: 
   - Dynamically patched the Kubernetes Service to change it from `ClusterIP` to `LoadBalancer`: `kubectl patch svc meta-whatsapp-webhook -n ajo-namespace -p '{"spec": {"type": "LoadBalancer"}}'`.
   - Updated `application.properties` with `quarkus.kubernetes.service-type=load-balancer` to make this change permanent for future deployments.
5. **Meta Verification**: Secured the external IP (`74.179.231.72`) and configured it in the Meta Developer Portal with the token `changeit` to establish the handshake.
6. **DNS Domain Association**: Associated the domain label `meta-ajo-webhook` with the NGINX Ingress controller public IP (`20.241.207.1`), creating the FQDN: `meta-ajo-webhook.eastus.cloudapp.azure.com`.
7. **Cert-Manager Deployment**: Deployed `cert-manager` (v1.12.0) to handle automated TLS certificates using ACME.
8. **Ingress & TLS Configuration**: Created Let's Encrypt `ClusterIssuers` (staging and production) and deployed the `Ingress` rule to redirect all HTTP/HTTPS traffic to the webhook service and enable TLS termination.

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
| `AZURE_CREDENTIALS` | JSON puro (ver bloque abajo) |
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
  CDP_ENDPOINT_URL: "ht"
  CDP_FLOW_ID:      "01"
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
Puedes usar Postman o tu terminal local para enviar este payload de prueba directamente a tu clúster en AKS usando el dominio configurado.

```bash
curl -X POST "http://meta-ajo-webhook.eastus.cloudapp.azure.com/whatsapp/webhook" \
-H "Content-Type: application/json" \
-d '{
  "object": "whatsapp_business_account",
  "entry": [
    {
      "id": "123",
      "changes": [
        {
          "value": {
            "messaging_product": "whatsapp",
            "contacts": [{"wa_id": "51918209989"}],
            "messages": [
              {
                "id": "msg_123",
                "type": "text",
                "text": {"body": "Hola, necesito ayuda con mi préstamo"}
              }
            ]
          },
          "field": "messages"
        }
      ]
    }
  ]
}'
```

Verifica en los logs de tu pod que el request se recibe exitosamente:
```bash
kubectl logs deployment/meta-whatsapp-webhook -n ajo-namespace
```
---


## Full Command Reference  

A continuación, el script automatizado en PowerShell para aprovisionar toda la infraestructura, configurar Azure Workload Identity e inyectar permisos de Key Vault de forma nativa.

```powershell
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
    azure.workload.identity/client-id: "`$MiClientId"
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
$spJson = az ad sp create-for-rbac -n "sp-github-actions" --role Contributor --scopes /subscriptions/`$SubscriptionId/resourceGroups/`$RG --sdk-auth -o json

Write-Host "`n=== AGREGA ESTO A TUS GITHUB SECRETS ==="
Write-Host "AZURE_CREDENTIALS=`$spJson"
```
Running the script end‑to‑end will give you a **fully functional** environment ready for the CI/CD pipeline.
---

## License  
This demo project is provided **as‑is** under the MIT License. Feel free to adapt the architecture, scripts, and manifests to your own needs.

---


## Flujo de Customer ID desde AJO → Meta WhatsApp → Webhook → Adobe CDP

```

mermaid
sequenceDiagram
    participant AJO as Adobe Journey Optimizer
    participant META as Meta WhatsApp API
    participant USER as Cliente WhatsApp
    participant WEBHOOK as Quarkus Webhook (AKS)
    participant CDP as Adobe Experience Platform CDP

    Note over AJO: Customer Profile<br/>customerId=ronald123

    AJO->>META: Send Template Message
    Note right of AJO: Payload botón<br/>"payload":"SI|customerId=ronald123|templateName"

    META->>USER: Entrega plantilla WhatsApp

    USER->>META: Click Quick Reply

    META->>WEBHOOK: POST /webhook
    Note right of META: message.context.id<br/>button.payload<br/>wa_id

    WEBHOOK->>WEBHOOK: Parse payload
    Note over WEBHOOK: customerId=ronald123<br/>reply=SI<br/>templateName=o_cef_mm_p_automatico_util_v3

    WEBHOOK->>CDP: POST Event
    Note right of WEBHOOK: eventType=whatsapp.feedback.reply

    CDP-->>WEBHOOK: 200 OK

    WEBHOOK-->>META: 200 OK
```
## Arquitectura de Integración

```

mermaid
flowchart LR

    AJO["Adobe Journey Optimizer"]
    META["Meta WhatsApp Cloud API"]
    USER["Cliente WhatsApp"]
    AKS["Quarkus Webhook<br>AKS Kubernetes"]
    CDP["Adobe Experience Platform CDP"]

    AJO -->|"Template + customerId en payload"| META

    META -->|"Mensaje WhatsApp"| USER

    USER -->|"Quick Reply"| META

    META -->|"Webhook Event"| AKS

    AKS -->|"Extrae customerId"| AKS

    AKS -->|"whatsapp.feedback.reply"| CDP
```
## Transformación del Payload

```

mermaid
flowchart TD

    A["AJO Profile<br/>customerId=ronald123"]
    B["Payload botón Meta<br/>SI|customerId=ronald123|templateName"]
    C["Respuesta usuario"]
    D["Webhook Quarkus"]
    E["Evento XDM CDP"]

    A --> B
    B --> C
    C --> D

    D --> E

    E -->|"identity.customerId = ronald123"| X["Adobe CDP"]
    E -->|"feedback.reply = SI"| X
    E -->|"templateName"| X
    E -->|"wamId"| X
```





---

**Enjoy building!** 🚀



joffre [ ~ ]$ kubectl get deployment meta-whatsapp-webhook \ -n ajo-namespace \ -o yaml | grep -A120 "env:" - env: - name: KUBERNETES_NAMESPACE valueFrom: fieldRef: apiVersion: v1 fieldPath: metadata.namespace - name: CDP_FLOW_ID value: f3cffd10-8fe9-4952-9e39-91d03bab4f85 - name: OPENAI_API_KEY value: dummy - name: MINIMAX_API_KEY value: dummy - name: MOONSHOT_KEY value: dummy - name: CDP_AUTH_TOKEN value: dummy - name: GROQ_API_KEY value: dummy - name: META_VERIFY_TOKEN value: changeit - name: GOOGLE_AI_API_KEY value: dummy - name: MONGODB_URI value: mongodb://localhost:27017 - name: CDP_ENDPOINT_URL value: https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d - name: DEEPSEEK_API_KEY value: dummy envFrom: - configMapRef: name: meta-webhook-config image: acrmetaajodev001.azurecr.io/meta-whatsapp-webhook:84c364bb98794ab99840448ac3ce841b894d98ff imagePullPolicy: Always name: meta-whatsapp-webhook ports: - containerPort: 8080 name: http protocol: TCP resources: {} terminationMessagePath: /dev/termination-log terminationMessagePolicy: File dnsPolicy: ClusterFirst restartPolicy: Always schedulerName: default-scheduler securityContext: {} terminationGracePeriodSeconds: 30 status: availableReplicas: 1 conditions: - lastTransitionTime: "2026-06-20T14:36:24Z" lastUpdateTime: "2026-06-20T14:36:24Z" message: Deployment has minimum availability. reason: MinimumReplicasAvailable status: "True" type: Available - lastTransitionTime: "2026-06-16T04:26:40Z" lastUpdateTime: "2026-06-28T21:18:42Z" message: ReplicaSet "meta-whatsapp-webhook-559c6c8f49" has successfully progressed. reason: NewReplicaSetAvailable status: "True" type: Progressing observedGeneration: 9 readyReplicas: 1 replicas: 1 updatedReplicas: 1 joffre [ ~ ]$ kubectl describe secret meta-webhook-secrets \ -n ajo-namespace Name: meta-webhook-secrets Namespace: ajo-namespace Labels: <none> Annotations: <none> Type: Opaque Data ==== CDP_AUTH_TOKEN: 3 bytes DEEPSEEK_API_KEY: 3 bytes GOOGLE_AI_API_KEY: 3 bytes GROQ_API_KEY: 3 bytes META_VERIFY_TOKEN: 3 bytes MINIMAX_API_KEY: 3 bytes MONGODB_URI: 3 bytes MOONSHOT_KEY: 3 bytes OPENAI_API_KEY: 3 bytes joffre [ ~ ]$

kubectl delete secret meta-webhook-secrets -n ajo-namespace secret "meta-webhook-secrets" deleted from ajo-namespace namespace joffre [ ~ ]$ kubectl create secret generic meta-webhook-secrets \ -n ajo-namespace \ --from-literal=MONGODB_URI='mongodb+srv://joffre:joffre@bootcamp-microservicios.c9yhl.mongodb.net/ajo_meta_db?retryWrites=true' \ --from-literal=META_VERIFY_TOKEN='changeit' secret/meta-webhook-secrets created joffre [ ~ ]$ kubectl describe secret meta-webhook-secrets -n ajo-namespace Name: meta-webhook-secrets Namespace: ajo-namespace Labels: <none> Annotations: <none> Type: Opaque Data ==== META_VERIFY_TOKEN: 8 bytes MONGODB_URI: 98 bytes joffre [ ~ ]$ grep -R "mongodb://localhost:27017" . joffre [ ~ ]$ grep -R "OPENAI_API_KEY" . ./.bash_history:kubectl create secret generic meta-webhook-secrets -n ajo-namespace --from-literal=MONGODB_URI='...' --from-literal=OPENAI_API_KEY='...' --from-literal=DEEPSEEK_API_KEY='...' --from-literal=GROQ_API_KEY='...' --from-literal=GOOGLE_AI_API_KEY='...' --from-literal=MOONSHOT_KEY='...' --from-literal=MINIMAX_API_KEY='...' --from-literal=CDP_AUTH_TOKEN='...' --from-literal=META_VERIFY_TOKEN='...' --dry-run=client -o yaml | kubectl apply -f - joffre [ ~ ]$ grep -R "quarkus.kubernetes.env" . joffre [ ~ ]$



Pasted text.txt
Documento
donde creo el configmap
ya puedo hacer push para que el github actions 
ya puedo pushear ?
offre [ ~ ]$ kubectl apply -f k8s/configmap.yaml
error: the path "k8s/configmap.yaml" does not exist
joffre [ ~ ]$ kubectl apply -f k8s/01-configmap.yaml
error: the path "k8s/01-configmap.yaml" does not exist
esto me dice otra IA 0s
Run azure/login@v2
  with:
    creds: ***
    enable-AzPSSession: false
    environment: azurecloud
    allow-no-subscriptions: false
    audience: api://AzureADTokenExchange
    auth-type: SERVICE_PRINCIPAL
  env:
    ACR_LOGIN_SERVER: ***
    ACR_USERNAME: ***
    ACR_PASSWORD: ***
    AKS_CLUSTER: ***
    AKS_RG: ***
    IMAGE_NAME: meta-whatsapp-webhook
    JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.11-10/x64
    JAVA_HOME_21_X64: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/21.0.11-10/x64
    DOCKER_CONFIG: /home/runner/work/_temp/docker_login_1781583367601
Error: Login failed with SyntaxError: Unexpected token '', "json {"cl"... is not valid JSON. Double check if the 'auth-type' is correct. Refer to https://github.com/Azure/login#readme for more information.
me salio eso en github action
Pasted text(1).txt
Documento
mport axios from "axios";

export default defineComponent({
 async run({ steps, $ }) {
 const event = steps.trigger.event;

 // ---------- HANDSHAKE ----------
 if (event.method === "GET") {
 const q = event.query;
 if (
 q["hub.mode"] === "subscribe" &&
 q["hub.verify_token"] === "changeit"
 ) {
 return await $.respond({
 status: 200,
 body: q["hub.challenge"],
 });
 }
 return await $.respond({ status: 403 });
 }

 // ---------- EVENTO META ----------
 if (event.method === "POST") {
 const value = event.body?.entry?.[0]?.changes?.[0]?.value;
 const message = value?.messages?.[0];
 const contact = value?.contacts?.[0];

 if (!message || !contact) {
 return await $.respond({ status: 200 });
 }

 // ✅ Identidad real del cliente
 const waId = contact.wa_id;
 
 // ✅ IDs de mensaje
 const replyWamId = message.id;
 const originalWamId = message.context?.id || null;

 // ✅ Respuesta del usuario
 let buttonReply = null;
 if (message.type === "button") {
 buttonReply = message.button?.text;
 }
 if (message.type === "text") {
 buttonReply = message.text?.body;
 }

 // ✅ Payload CDP
 const cdpPayload = {
 _bcp: {
 identity:{
 customerId:"joffre1234"
 },
 transient: {
 customer: {
 feedback: {
 reply: buttonReply,
 channel: "whatsapp",
 templateName: "o_018003_bid_mm_cef_casodeuso_paso3",
 wamId: originalWamId,
 },
 },
 },
 },
 
 _id: crypto.randomUUID(),
 eventType: "whatsapp.feedback.reply",

 timestamp: new Date().toISOString(),
 };

 // ✅ Endpoint Adobe CDP
 const url =
 "https://dcs.adobedc.net/collection/e65e89630b3479fe88994d69106307462dabf30fe2f648b3d178aeded18b3d4d";

 // ✅ Envío a CDP
 await axios.post(url, cdpPayload, {
 headers: {
 "Content-Type": "application/json",
 "x-adobe-flow-id":
 "086a7d7d-a5bd-41b9-bc84-b75ff53c9f51",
 },
 });

 return await $.respond({
 status: 200,
 body: {
 message: "Event received",
 },
 });
 }
 },
});


curl --location --request POST 'https://graph.facebook.com/v22.0/1039463512592670/messages' \
--header 'Authorization: Bearer EAAVSoi2xF7YBRk4fCdZAFL7ZBZCr7HRhHBgZB2K7ZCQmFILMZChG3wlKjfct2MlF83CxcYmMP2qimCUxiyHZCO7e0pBpLl1I0NrndOaAmlzmZCJzu0f32ZALnFj8VYkS9xyTcZBnXWTsa6WGa9hETSXbFDbIbNrl2Xf2VlM4Ute11nrwf5KpzDUKoJEmZAle9JEQHXsOyHt01OBKtnLPBueBTJQf8zEdIH4XGMai8ic' \
--header 'Content-Type: application/json' \
--data-raw '{
  "messaging_product": "whatsapp",
  "to": "51989168761",
  "type": "template",
  "template": {
    "name": "o_cef_mm_p_automatico_util_v3",
    "language": {
      "code": "es_PE"
    },
    "components": [
      {
        "type": "header",
        "parameters": [
          {
            "type": "image",
            "image": {
              "link": "https://delivery-p187030-e1959235.adobeaemcloud.com/adobe/assets/urn:aaid:aem:64012564-d3d4-46ab-af25-c329a0f2a4b8/as/CEF-PDH-V2.avif?width=1024&quality=65&assetName=CEF-PDH-V2.png"
            }
          }
        ]
      },
      {
        "type": "body",
        "parameters": [
          {
            "type": "text",
            "text": "Joffre"
          }
        ]
      },
      {
        "type": "button",
        "sub_type": "quick_reply",
        "index": "1",
        "parameters": [
          {
            "type": "payload",
            "payload": "En otro momento|customerId=ronald123|o_cef_mm_p_automatico_util_v3" 
          }
        ]
      }
    ]
  }
}'

dentro de la customizacion que se esta configurando en AJO 

{
	"messaging_product": "whatsapp",
	"to": {
		"toBeMapped": true,
		"dataType": "string",
		"label": "numeroTelefono"
	},
	"type": "template",
	"template": {
		"name": {
			"toBeMapped": true,
			"dataType": "string",
			"label": "templateName"
		},
		"language": {
			"code": "es_PE"
		},
		"components": [
			{
				"type": "header",
				"parameters": [
					{
						"type": "image",
						"image": {
							"link": "https://fastly.picsum.photos/id/273/200/300.jpg?hmac=C0IK2DPqr03oiShSklDGIHBzHorcmVrky7A_uvBEzIM"
						}
					}
				]
			},
			{
				"type": "body",
				"parameters": [
					{
						"type": "text",
						"text": "Ronald"
					}
				]
			}
		]
	}
}


{
	"messaging_product": "string",
	"contacts": [
		{
			"input": "string",
			"wa_id": "string"
		}
	],
	"messages": [
		{
			"message_status": "string",
			"id": "string"
		}
	]
}


offre [ ~ ]$ kubectl get pods -n ajo-namespace
NAME                                     READY   STATUS    RESTARTS   AGE
meta-whatsapp-webhook-7b7dc8f847-q7wp7   1/1     Running   0          77s
joffre [ ~ ]$ kubectl get pods -n ajo-namespacekubectl get pods -n ajo-namespace
Error from server (NotFound): pods "get" not found
Error from server (NotFound): pods "pods" not found
joffre [ ~ ]$ kubectl logs meta-whatsapp-webhook-7b7dc8f847-q7wp7  -n ajo-namespace --previous
Error from server (BadRequest): previous terminated container "meta-whatsapp-webhook" in pod "meta-whatsapp-webhook-7b7dc8f847-q7wp7" not found
joffre [ ~ ]$ kubectl logs meta-whatsapp-webhook-7b7dc8f847-q7wp7 -n ajo-namespace --previous
Error from server (BadRequest): previous terminated container "meta-whatsapp-webhook" in pod "meta-whatsapp-webhook-7b7dc8f847-q7wp7" not found
joffre [ ~ ]$ kubectl logs meta-whatsapp-webhook-7b7dc8f847-q7wp7 -n ajo-namespace 
__  ____  __  _____   ___  __ ____  ______ 
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
2026-07-05 04:12:33,548 WARN  [io.qua.config] (main) Unrecognized configuration key "quarkus.azure.keyvault.config-ordinal" was provided; it will be ignored; verify that the dependency extension for this configuration is set or that you did not make a typo
2026-07-05 04:12:34,453 INFO  [io.quarkus] (main) meta-whatsapp-webhook 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.10.0) started in 1.227s. Listening on: http://0.0.0.0:8080
2026-07-05 04:12:34,454 INFO  [io.quarkus] (main) Profile prod activated. 
2026-07-05 04:12:34,454 INFO  [io.quarkus] (main) Installed features: [azure-keyvault-secret, cdi, kubernetes, mongodb-client, mongodb-panache, narayana-jta, rest, rest-client, rest-client-jackson, rest-jackson, smallrye-context-propagation, smallrye-health, smallrye-openapi, vertx]
2026-07-05 04:12:40,644 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:12:40 +0000] "GET /health/started HTTP/1.1" 200 45
2026-07-05 04:12:40,981 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:12:40 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:12:50,550 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:12:50 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:12:50,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:12:50 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:13:00,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:13:00 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:13:00,975 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:13:00 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:13:10,550 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:13:10 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:13:10,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:13:10 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:13:20,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:13:20 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:13:21,005 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:13:21 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:13:30,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:13:30 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:13:30,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:13:30 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:13:40,550 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:13:40 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:13:40,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:13:40 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:13:50,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:13:50 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:13:50,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:13:50 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:14:00,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:14:00 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:14:00,982 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:14:00 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:14:10,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:14:10 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:14:10,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:14:10 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:14:20,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:14:20 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:14:20,973 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:14:20 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:14:30,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:14:30 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:14:30,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:14:30 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:14:40,550 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:14:40 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:14:40,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:14:40 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:14:50,550 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:14:50 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:14:50,974 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:14:50 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:15:00,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:15:00 +0000] "GET /health/live HTTP/1.1" 200 45
2026-07-05 04:15:00,973 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-0) 10.224.0.4 - - [05/Jul/2026:04:15:00 +0000] "GET /health/ready HTTP/1.1" 200 147
2026-07-05 04:15:10,549 INFO  [io.qua.htt.access-log] (vert.x-eventloop-thread-1) 10.224.0.4 - - [05/Jul/2026:04:15:10 +0000] "GET /health/live HTTP/1.1" 200 45
joffre [ ~ ]$ curl -i "https://meta-ajo-webhook.eastus.cloudapp.azure.com/webhook?hub.mode=subscribe&hub.verify_token=changeit&hub.challenge=123456"
HTTP/2 200 
date: Sun, 05 Jul 2026 04:17:32 GMT
content-type: text/plain;charset=UTF-8
content-length: 6
strict-transport-security: max-age=31536000; includeSubDomains

123456joffre [kubectl get ingress -n ajo-namespaceespace
NAME                            CLASS    HOSTS                                        ADDRESS        PORTS     AGE
meta-whatsapp-webhook-ingress   <none>   meta-ajo-webhook.eastus.cloudapp.azure.com   20.241.207.1   80, 443   13d
joffre [ ~ ]$ kubectl describe ingress meta-whatsapp-webhook-ingress -n ajo-namespace
Name:             meta-whatsapp-webhook-ingress
Labels:           <none>
Namespace:        ajo-namespace
Address:          20.241.207.1
Ingress Class:    <none>
Default backend:  <default>
TLS:
  meta-webhook-tls-secret terminates meta-ajo-webhook.eastus.cloudapp.azure.com
Rules:
  Host                                        Path  Backends
  ----                                        ----  --------
  meta-ajo-webhook.eastus.cloudapp.azure.com  
                                              /webhook   meta-whatsapp-webhook:80 (10.244.0.213:8080)
Annotations:                                  cert-manager.io/cluster-issuer: letsencrypt-prod
                                              kubernetes.io/ingress.class: nginx
                                              nginx.ingress.kubernetes.io/proxy-body-size: 10m
                                              nginx.ingress.kubernetes.io/proxy-read-timeout: 60
                                              nginx.ingress.kubernetes.io/proxy-send-timeout: 60
                                              nginx.ingress.kubernetes.io/ssl-redirect: true
Events:                                       <none>
joffre [ ~ ]$ kubectl get svc -n ajo-namespace
NAME                    TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)   AGE
meta-whatsapp-webhook   ClusterIP   10.0.75.30   <none>        80/TCP    18d
joffre [ ~ ]$ kubectl get endpoints -n ajo-namespace
Warning: v1 Endpoints is deprecated in v1.33+; use discovery.k8s.io/v1 EndpointSlice
NAME                    ENDPOINTS           AGE
meta-whatsapp-webhook   10.244.0.213:8080   18d
joffre [ ~ ]$ 


az aks get-credentials --resource-group rg-meta-ajo-dev --name aks-adobe-meta-dev


offre [ ~ ]$ kubectl exec -it -n ajo-namespace deploy/meta-whatsapp-webhook -- printenv | grep MONGODB
MONGODB_URI=mongodb+srv://joffre:joffre@bootcamp-microservicios.c9yhl.mongodb.net/ajo-cdp-meta-db?retryWrites=true&w=majority
joffre [ ~ ]$ 