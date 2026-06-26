$uri = 'mongodb+srv://joffre:joffre@bootcamp-microservicios.c9yhl.mongodb.net/ajo_meta_db?retryWrites=true&w=majority'
az keyvault secret set --vault-name 'kv-meta-ajo-dev-001' --name 'MONGODB-URI' --value $uri
