const GOOGLE_MAPS_API_KEY = process.env.GOOGLE_MAPS_API_KEY || "AIzaSyD6xFLEqbLGqzeOgqKxbo8sI7ueDNsSRAg";

/**
 * MCP Tool: calculateNearestLocations
 * Calcula distancia y tiempo de viaje desde la ubicación del cliente hasta una lista de sedes
 * @param {Object} origin - { lat: number, lng: number }
 * @param {Array} locations - [{ id, name, lat, lng }]
 * @returns {Array} Lista ordenada por cercanía con distancia, tiempo y link de ruta
 */
export async function calculateNearestLocations(origin, locations) {
  if (!origin || !locations || locations.length === 0) {
    return [];
  }

  const destinationsParam = locations.map(loc => `${loc.lat},${loc.lng}`).join("|");
  const url = `https://maps.googleapis.com/maps/api/distancematrix/json?origins=${origin.lat},${origin.lng}&destinations=${destinationsParam}&key=${GOOGLE_MAPS_API_KEY}&mode=driving&language=es`;

  try {
    const res = await fetch(url);
    const data = await res.json();

    if (data.status !== "OK" || !data.rows?.[0]?.elements) {
      console.warn("Respuesta Google Maps Distance Matrix:", data.status, data.error_message || "");
      return fallbackCalculateDistance(origin, locations);
    }

    const elements = data.rows[0].elements;
    const results = locations.map((loc, index) => {
      const element = elements[index];
      const distanceText = element?.distance?.text || "N/A";
      const distanceValue = element?.distance?.value ?? 9999999;
      const durationText = element?.duration?.text || "N/A";
      const routeUrl = `https://www.google.com/maps/dir/?api=1&origin=${origin.lat},${origin.lng}&destination=${loc.lat},${loc.lng}`;

      return {
        id: loc.id,
        name: loc.name,
        distance: distanceText,
        distanceMeters: distanceValue,
        duration: durationText,
        routeUrl: routeUrl
      };
    });

    return results.sort((a, b) => a.distanceMeters - b.distanceMeters);

  } catch (error) {
    console.error("Error llamando a Google Maps Distance Matrix:", error.message);
    return fallbackCalculateDistance(origin, locations);
  }
}

// Fallback por fórmula Haversine si la API no está habilitada o no hay red
function fallbackCalculateDistance(origin, locations) {
  const toRad = x => (x * Math.PI) / 180;
  const R = 6371; // Radio tierra en km

  return locations.map(loc => {
    const dLat = toRad(loc.lat - origin.lat);
    const dLon = toRad(loc.lng - origin.lng);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(toRad(origin.lat)) * Math.cos(toRad(loc.lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const distKm = R * c;

    return {
      id: loc.id,
      name: loc.name,
      distance: `${distKm.toFixed(1)} km`,
      distanceMeters: Math.round(distKm * 1000),
      duration: `${Math.round(distKm * 3)} min aprox`,
      routeUrl: `https://www.google.com/maps/dir/?api=1&origin=${origin.lat},${origin.lng}&destination=${loc.lat},${loc.lng}`
    };
  }).sort((a, b) => a.distanceMeters - b.distanceMeters);
}
