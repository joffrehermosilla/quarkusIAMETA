import { getAllowedTopics } from "./mcp_banco_tools.mjs";

// Mapeo determinista de palabras clave a tópicos bancarios
const TOPIC_KEYWORDS = {
  ACCOUNT: ["saldo", "cuenta", "ahorros", "movimientos", "cci", "estado de cuenta"],
  PRODUCTS: ["tarjeta", "tarjetas", "visa", "mastercard", "debito", "credito", "apertura"],
  LOANS: ["prestamo", "préstamo", "credito", "crédito", "financiamiento", "tasa", "tea", "cuotas"],
  OFFERS: ["oferta", "ofertas", "promocion", "promoción", "descuento", "catalogo", "catálogo"],
  MILES: ["millas", "latam", "canje", "puntos", "viajes"],
  BENEFITS: ["beneficio", "beneficios", "restaurante", "pizza", "comida", "tienda"]
};

// Tópicos explícitamente prohibidos / fuera de alcance bancario
const RESTRICTED_PATTERNS = [
  "politica", "política", "elecciones", "presidente", "congreso",
  "futbol", "fútbol", "partido", "gol", "mundial", "champions",
  "clima", "temperatura", "lluvia",
  "receta", "cocina", "horoscopo", "horóscopo", "chiste", "poema"
];

/**
 * Evalúa si una consulta o intención está autorizada por el Policy Engine de BCP
 * @param {string} text - Texto o selección del usuario
 * @returns {Promise<{ allowed: boolean, topic: string|null, message?: string }>}
 */
export async function evaluatePolicy(text = "") {
  const policy = await getAllowedTopics();
  const allowedList = policy?.allowed || ["ACCOUNT", "PRODUCTS", "LOANS", "OFFERS", "MILES", "BENEFITS"];
  const defaultRejectedMsg = policy?.rejected_message || "Por políticas de seguridad de BCP, solo puedo ayudarte con temas sobre tus productos bancarios, tarjetas, préstamos, millas y promociones.";

  const normalized = text.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");

  // 1. Detección rápida de contenido restringido
  for (const forbidden of RESTRICTED_PATTERNS) {
    if (normalized.includes(forbidden)) {
      return {
        allowed: false,
        topic: "FORBIDDEN",
        reason: `Contenido fuera del dominio bancario (${forbidden})`,
        message: defaultRejectedMsg
      };
    }
  }

  // 2. Clasificación de tópico permitido
  for (const [topic, keywords] of Object.entries(TOPIC_KEYWORDS)) {
    if (allowedList.includes(topic)) {
      for (const kw of keywords) {
        if (normalized.includes(kw)) {
          return {
            allowed: true,
            topic: topic
          };
        }
      }
    }
  }

  // 3. Si no coincide con palabras clave conocidas
  return {
    allowed: false,
    topic: "UNKNOWN",
    reason: "Tema no identificado dentro del alcance financiero",
    message: defaultRejectedMsg
  };
}
