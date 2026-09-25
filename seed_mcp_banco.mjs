import { MongoClient } from "mongodb";

const MONGODB_URI = "mongodb+srv://joffre:joffre@bootcamp-microservicios.c9yhl.mongodb.net/ajo-cdp-meta-db?retryWrites=true&w=majority";
const DB_NAME = "ajo-cdp-meta-db";

const customerProducts = [
  {
    customerId: "joffre123456789",
    products: [
      { id: "card_visa_signature", type: "card", name: "Visa Signature BCP", line: "S/ 25,000", status: "ACTIVA" },
      { id: "loan_preapproved_personal", type: "loan", name: "Préstamo Efectivo al Instante", amount: "S/ 15,000", tea: "12.5%" }
    ]
  },
  {
    customerId: "carlita1234",
    products: [
      { id: "card_visa_gold", type: "card", name: "Visa Gold BCP", line: "S/ 8,000", status: "ACTIVA" }
    ]
  }
];

const offers = [
  {
    offerId: "OFERTA_TARJETA_PREMIUM",
    category: "Tarjetas",
    title: "Upgrade a Tarjeta Visa Infinite BCP",
    description: "Sin cobro de membresía el primer año y 10,000 millas de bienvenida.",
    cta_url: "https://www.viabcp.com/tarjetas/credito/visa-infinite"
  },
  {
    offerId: "OFERTA_PRESTAMO_PERSONAL",
    category: "Préstamos",
    title: "Préstamo Personal con Tasa Exclusiva",
    description: "Desembolso directo a tu cuenta de ahorros en 1 clic con TEA promocional.",
    cta_url: "https://www.viabcp.com/creditos/prestamo-personal"
  },
  {
    offerId: "OFERTA_PIZZA_40",
    category: "Beneficios",
    title: "40% de Descuento en Pizza",
    description: "Válido pagando con tus tarjetas BCP en locales seleccionados.",
    locations: [
      { id: "PIZZA_SAN_ISIDRO", name: "Pizza San Isidro", lat: -12.096, lng: -77.036 },
      { id: "PIZZA_MIRAFLORES", name: "Pizza Miraflores", lat: -12.122, lng: -77.029 }
    ]
  }
];

const allowedTopics = {
  policy_id: "BANCO_GENERAL_V1",
  allowed: ["ACCOUNT", "PRODUCTS", "LOANS", "OFFERS", "MILES", "BENEFITS"],
  rejected_message: "Por políticas de seguridad de BCP, solo puedo ayudarte con temas sobre tus productos bancarios, tarjetas, préstamos, millas y promociones."
};

async function seedMCPData() {
  const client = new MongoClient(MONGODB_URI);
  try {
    console.log("Conectando a MongoDB Atlas para poblar MCP Banco...");
    await client.connect();
    const db = client.db(DB_NAME);

    // 1. customer_products
    const prodCol = db.collection("customer_products");
    for (const cp of customerProducts) {
      await prodCol.updateOne({ customerId: cp.customerId }, { $set: cp }, { upsert: true });
    }
    console.log("✅ Colección 'customer_products' poblada.");

    // 2. offers
    const offerCol = db.collection("offers");
    for (const off of offers) {
      await offerCol.updateOne({ offerId: off.offerId }, { $set: off }, { upsert: true });
    }
    console.log("✅ Colección 'offers' poblada.");

    // 3. allowed_topics (Policy Engine)
    const policyCol = db.collection("allowed_topics");
    await policyCol.updateOne({ policy_id: allowedTopics.policy_id }, { $set: allowedTopics }, { upsert: true });
    console.log("✅ Colección 'allowed_topics' (Policy Engine) poblada.");

    console.log("Poblado de MCP Banco completado con éxito.");
  } catch (err) {
    console.error("Error al poblar datos MCP:", err);
  } finally {
    await client.close();
  }
}

seedMCPData();
