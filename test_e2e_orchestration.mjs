import { getCustomer, getCustomerProducts, getCustomerOffers } from "./mcp_banco_tools.mjs";
import { calculateNearestLocations } from "./mcp_google_maps.mjs";
import { evaluatePolicy } from "./policy_engine.mjs";
import { generateFinancialRecommendation } from "./ai_gateway.mjs";

async function runEndToEndDemo(customerId = "joffre123456789", userQuery = "Quiero ofertas de comida") {
  console.log("==================================================");
  console.log(`1. INICIANDO ORQUESTACIÓN E2E PARA CUSTOMER: ${customerId}`);
  console.log(`2. INTENCIÓN/CONSULTA DEL USUARIO: "${userQuery}"`);
  console.log("==================================================");

  // Paso 1: Policy Engine
  console.log("\n[Paso 1] Evaluando políticas de seguridad con Policy Engine...");
  const policyResult = await evaluatePolicy(userQuery);
  console.log("Resultado Policy Engine:", JSON.stringify(policyResult));

  if (!policyResult.allowed) {
    console.log("⛔ Consulta bloqueada por políticas bancarias:", policyResult.message);
    return;
  }

  // Paso 2: MCP Banco (MongoDB Atlas)
  console.log("\n[Paso 2] Consultando MCP Banco en MongoDB Atlas...");
  const customer = await getCustomer(customerId);
  const products = await getCustomerProducts(customerId);
  const offers = await getCustomerOffers();

  console.log(`- Cliente: ${customer?.first_name} (ID: ${customer?.customerId})`);
  console.log(`- Productos activos: ${products.map(p => p.name).join(", ")}`);
  console.log(`- Total ofertas en catálogo: ${offers.length}`);

  // Paso 3: MCP Google Maps
  console.log("\n[Paso 3] Calculando distancias con Google Maps Platform...");
  const origin = { lat: customer?.latitude || -12.096, lng: customer?.longitude || -77.036 };
  const pizzaOffer = offers.find(o => o.offerId === "OFERTA_PIZZA_40");
  const nearestLocations = await calculateNearestLocations(origin, pizzaOffer?.locations || []);

  console.log(`- Local más cercano: ${nearestLocations[0]?.name} a ${nearestLocations[0]?.distance} (${nearestLocations[0]?.duration})`);
  console.log(`- Ruta Google Maps: ${nearestLocations[0]?.routeUrl}`);

  // Paso 4: AI Gateway
  console.log("\n[Paso 4] Invocando AI Gateway (Groq LPU / OpenAI OSS)...");
  const aiRecommendation = await generateFinancialRecommendation({
    customer,
    products,
    offers,
    nearestLocations
  });

  console.log("\n==================================================");
  console.log("RESPUESTA FINAL GENERADA PARA WHATSAPP FLOW:");
  console.log(aiRecommendation);
  console.log("==================================================");

  // Paso 5: Contrato del Flow Data Exchange para Meta
  const flowResponsePayload = {
    version: "7.3",
    screen: "WELCOME",
    data: {
      first_name: customer?.first_name || "Cliente BCP",
      category_title: customer?.category_title || "Catálogo BCP",
      notification: aiRecommendation,
      nearest_store: nearestLocations[0]?.name || "",
      nearest_distance: nearestLocations[0]?.distance || "",
      route_url: nearestLocations[0]?.routeUrl || ""
    }
  };

  console.log("\n[Paso 5] Payload listo para cifrar y entregar a Meta WhatsApp Flow:");
  console.log(JSON.stringify(flowResponsePayload, null, 2));
}

runEndToEndDemo().then(() => process.exit(0));
