const GOOGLE_AI_API_KEY = process.env.GOOGLE_AI_API_KEY || "";
const GROQ_API_KEY = process.env.GROQ_API_KEY || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";

/**
 * AI Gateway: Genera una recomendación personalizada y concisa usando el contexto bancario real
 * @param {Object} context - { customer, products, offers, nearestLocations }
 * @returns {Promise<string>} Mensaje generado para WhatsApp
 */
export async function generateFinancialRecommendation(context) {
  const { customer, products = [], offers = [], nearestLocations = [] } = context;

  const prompt = `
Eres el Asistente Financiero Inteligente de BCP para WhatsApp.
Genera una respuesta muy breve (máximo 4 líneas), cálida y profesional para el cliente en Perú.
Contexto verificado del cliente:
- Nombre: ${customer?.first_name || "Cliente"}
- Productos actuales: ${products.map(p => p.name).join(", ") || "Ninguno"}
- Ofertas destacadas: ${offers.map(o => `${o.title} (${o.description})`).join(" | ") || "Ninguna"}
- Locales más cercanos de la promoción: ${nearestLocations.map(l => `${l.name} (${l.distance}, ${l.duration})`).join(" | ") || "No disponible"}

Instrucciones:
- Saluda al cliente por su nombre.
- Menciona la oferta más relevante y el local más cercano.
- No agregues enlaces falsos ni datos inventados.
- Usa emojis de forma sobria y bancaria (💳, 🍕, 📍).
`;

  // Intento 1: Groq (openai/gpt-oss-120b - Ultra rápido < 0.4s)
  try {
    const groqRes = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${GROQ_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: "openai/gpt-oss-120b",
        messages: [{ role: "user", content: prompt }],
        max_tokens: 500,
        temperature: 0.3
      })
    });
    if (groqRes.ok) {
      const data = await groqRes.json();
      const reply = data.choices?.[0]?.message?.content?.trim();
      if (reply) return reply;
    }
  } catch (err) {
    console.warn("Groq Gateway no disponible, intentando fallback...");
  }

  // Intento 2: Google Gemini
  try {
    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${GOOGLE_AI_API_KEY}`;
    const geminiRes = await fetch(geminiUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { maxOutputTokens: 200, temperature: 0.3 }
      })
    });
    if (geminiRes.ok) {
      const data = await geminiRes.json();
      const reply = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
      if (reply) return reply;
    }
  } catch (err) {
    console.warn("Google Gemini no disponible, intentando OpenAI...");
  }

  // Intento 3: OpenAI (GPT)
  try {
    const openaiRes = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: [{ role: "user", content: prompt }],
        max_tokens: 200,
        temperature: 0.3
      })
    });
    if (openaiRes.ok) {
      const data = await openaiRes.json();
      const reply = data.choices?.[0]?.message?.content?.trim();
      if (reply) return reply;
    }
  } catch (err) {
    console.warn("OpenAI no disponible");
  }

  // Fallback determinista seguro sin IA
  return `¡Hola ${customer?.first_name || "Cliente"}! 👋 Tienes ofertas exclusivas preaprobadas en tus tarjetas BCP. Revisa tus opciones en el catálogo.`;
}
