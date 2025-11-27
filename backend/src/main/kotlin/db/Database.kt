package com.burgerking.duoc.db

import com.burgerking.duoc.models.*
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes

/**
 * Database Singleton usando Driver Sync (compatible con KMongo).
 * Conexión a Mongo Atlas + creación de índices únicos + logs personalizados.
 */
object Database {

    private const val DB_NAME = "burgerking"

    // URI original que tú estabas usando en tu código
    private const val ATLAS_URI =
        "mongodb+srv://joscelynne_db_user:cBY7FrIlIdH4xZKk@burgerking.s1qgce8.mongodb.net/?appName=BurgerKing"

    // Cliente Sync compatible con KMongo
    lateinit var client: MongoClient
        private set

    // Base de datos
    lateinit var db: com.mongodb.client.MongoDatabase
        private set

    /**
     * FUNCIÓN QUE TE FALTABA → ahora sí existe Database.connect()
     */
    fun connect() {

        println("🔌 Iniciando conexión a MongoDB Atlas…")

        val connectionUri = System.getenv("MONGO_URI") ?: ATLAS_URI

        client = MongoClients.create(connectionUri)
        db = client.getDatabase(DB_NAME)

        println(" Conectado correctamente a la base de datos '$DB_NAME'")
        println(" URI usada: $connectionUri")

        // Crear índices
        ensureIndexes()
    }

    // Colecciones tipadas (se inicializan DESPUÉS de connect())
    val productosCollection: MongoCollection<Producto> by lazy {
        db.getCollection("productos", Producto::class.java)
    }

    val combosCollection: MongoCollection<Combo> by lazy {
        db.getCollection("combos", Combo::class.java)
    }

    val pedidosCollection: MongoCollection<Pedido> by lazy {
        db.getCollection("pedidos", Pedido::class.java)
    }

    val clientesCollection: MongoCollection<Cliente> by lazy {
        db.getCollection("clientes", Cliente::class.java)
    }

    val empleadosCollection: MongoCollection<Empleado> by lazy {
        db.getCollection("empleados", Empleado::class.java)
    }

    /**
     * Crear índices únicos según tus reglas BK.
     */
    fun ensureIndexes() {

        println("Creando / Verificando índices únicos en MongoDB…")

        val uniqueActive = IndexOptions()
            .unique(true)
            .partialFilterExpression(Filters.eq("activo", true))

        // PRODUCTOS
        productosCollection.createIndex(
            Indexes.ascending(Producto::nombre.name),
            uniqueActive
        )
        println("   ✔ Índice Único 'nombre' en productos (activo = true) OK")

        // CLIENTES
        clientesCollection.createIndex(
            Indexes.ascending(Cliente::rut.name),
            uniqueActive
        )
        clientesCollection.createIndex(
            Indexes.ascending(Cliente::correo.name),
            uniqueActive
        )
        println("   ✔ Índices Únicos 'rut' y 'correo' en clientes OK")

        // EMPLEADOS
        empleadosCollection.createIndex(
            Indexes.ascending(Empleado::rut.name),
            uniqueActive
        )
        empleadosCollection.createIndex(
            Indexes.ascending(Empleado::correo.name),
            uniqueActive
        )
        println("   ✔ Índices Únicos 'rut' y 'correo' en empleados OK")

        // COMBOS
        combosCollection.createIndex(
            Indexes.ascending(Combo::nombre.name),
            uniqueActive
        )
        println("   ✔ Índice Único 'nombre' en combos OK")

        println(" Todos los índices están configurados correctamente.")
    }
}
