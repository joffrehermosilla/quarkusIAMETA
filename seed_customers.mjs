import { MongoClient } from "mongodb";

const MONGODB_URI = "mongodb+srv://joffre:joffre@bootcamp-microservicios.c9yhl.mongodb.net/ajo-cdp-meta-db?retryWrites=true&w=majority";
const DB_NAME = "ajo-cdp-meta-db";
const COLLECTION_NAME = "customers";

const customers = [
  { customerId: "carlita1234", first_name: "Carla Vera", category_title: "Tarjetas Exclusivas", notification: "Tienes una tarjeta preaprobada" },
  { customerId: "TEST923456811", first_name: "Mariela Valerio", category_title: "Créditos BCP", notification: "Oferta especial de préstamos" },
  { customerId: "TEST92345679", first_name: "John Contreras", category_title: "Catálogo BCP", notification: "Incremento de línea disponible" },
  { customerId: "TEST92345688", first_name: "Carlos Toledo", category_title: "Tarjetas BCP", notification: "Millas acumuladas listas" },
  { customerId: "TEST92345680", first_name: "Blanquita", category_title: "Beneficios BCP", notification: "Descuentos en comercios" },
  { customerId: "TEST92345681", first_name: "Samir", category_title: "Catálogo BCP", notification: "Oferta en Préstamo Vehicular" },
  { customerId: "TEST92345682", first_name: "Ronald Guillen", category_title: "Préstamos BCP", notification: "Tasa preferencial disponible" },
  { customerId: "jorzondelgado123", first_name: "Jorzon", category_title: "Catálogo BCP", notification: "Promoción activa" },
  { customerId: "joffre123456789", first_name: "Joffre", category_title: "Catálogo Exclusivo BCP", notification: "Tienes ofertas preaprobadas" },
  { customerId: "giulli1234", first_name: "Giulliana", category_title: "Tarjetas Premium", notification: "Beneficios en viajes" },
  { customerId: "TEST92345683", first_name: "Nancy", category_title: "Catálogo BCP", notification: "Actualización de cuenta" },
  { customerId: "TEST0001", first_name: "Ana Falcon", category_title: "Catálogo BCP", notification: "Beneficios exclusivos" },
  { customerId: "TEST0002", first_name: "Mari Carmen", category_title: "Catálogo BCP", notification: "Préstamo aprobado" },
  { customerId: "TEST0003", first_name: "Fabrizio", category_title: "Tarjetas BCP", notification: "Tarjeta Oro disponible" },
  { customerId: "TEST0004", first_name: "Ronald Navarrete", category_title: "Catálogo BCP", notification: "Ofertas del mes" },
  { customerId: "TEST0005", first_name: "Mauricio Zapata", category_title: "Catálogo BCP", notification: "Tasa reducida para ti" },
  { customerId: "TEST0006", first_name: "Corazon", category_title: "Catálogo BCP", notification: "Bono de bienvenida activo" },
  { customerId: "TEST0007", first_name: "Frank Huerta", category_title: "Préstamos BCP", notification: "Preaprobación disponible" },
  { customerId: "TEST0008", first_name: "Cristian Vasquez", category_title: "Catálogo BCP", notification: "Nuevas opciones de ahorro" },
  { customerId: "TEST92345686", first_name: "Ronnie Rantes", category_title: "Tarjetas BCP", notification: "Promoción en millas" }
];

async function seed() {
  const client = new MongoClient(MONGODB_URI);
  try {
    console.log("Conectando a MongoDB Atlas...");
    await client.connect();
    console.log("Conectado con éxito.");

    const db = client.db(DB_NAME);
    const collection = db.collection(COLLECTION_NAME);

    console.log(`Insertando/actualizando ${customers.length} clientes en '${COLLECTION_NAME}'...`);
    
    // Usamos updateOne con upsert para no duplicar si ya existen
    for (const c of customers) {
      await collection.updateOne(
        { customerId: c.customerId },
        { $set: c },
        { upsert: true }
      );
    }

    const total = await collection.countDocuments();
    console.log(`Operación completada. Total de registros en '${COLLECTION_NAME}': ${total}`);
  } catch (error) {
    console.error("Error en seed:", error);
  } finally {
    await client.close();
  }
}

seed();
