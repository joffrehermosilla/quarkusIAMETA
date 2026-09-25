import { MongoClient } from "mongodb";

const MONGODB_URI = process.env.MONGODB_URI || "mongodb+srv://joffre:joffre@bootcamp-microservicios.c9yhl.mongodb.net/ajo-cdp-meta-db?retryWrites=true&w=majority";
const DB_NAME = "ajo-cdp-meta-db";

let cachedClient = null;

async function getDb() {
  if (!cachedClient) {
    cachedClient = new MongoClient(MONGODB_URI);
    await cachedClient.connect();
  }
  return cachedClient.db(DB_NAME);
}

/**
 * MCP Tool: get_customer
 * Obtiene el perfil del cliente por customerId
 */
export async function getCustomer(customerId) {
  const db = await getDb();
  return await db.collection("customers").findOne({ customerId });
}

/**
 * MCP Tool: get_customer_products
 * Obtiene los productos financieros vigentes del cliente
 */
export async function getCustomerProducts(customerId) {
  const db = await getDb();
  const result = await db.collection("customer_products").findOne({ customerId });
  return result?.products || [];
}

/**
 * MCP Tool: get_customer_offers
 * Obtiene las ofertas del catálogo filtradas por categoría opcional
 */
export async function getCustomerOffers(category = null) {
  const db = await getDb();
  const filter = category ? { category } : {};
  return await db.collection("offers").find(filter).toArray();
}

/**
 * MCP Tool: get_allowed_topics (Policy Engine)
 * Consulta los temas permitidos y la respuesta estándar ante desvíos
 */
export async function getAllowedTopics() {
  const db = await getDb();
  return await db.collection("allowed_topics").findOne({ policy_id: "BANCO_GENERAL_V1" });
}

/**
 * MCP Tool: save_interaction
 * Registra auditoría local de la interacción del usuario
 */
export async function saveInteraction(customerId, interaction) {
  const db = await getDb();
  return await db.collection("audit_interactions").insertOne({
    customerId,
    interaction,
    timestamp: new Date()
  });
}
