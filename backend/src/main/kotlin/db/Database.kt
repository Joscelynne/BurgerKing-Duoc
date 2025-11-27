package com.burgerking.duoc.db

import com.burgerking.duoc.models.*
import com.mongodb.MongoCommandException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexModel
import com.mongodb.client.model.IndexOptions
import org.bson.Document

object Database {

    private const val DB_NAME = "burgerking"

    private const val ATLAS_URI =
        "mongodb+srv://joscelynne_db_user:cBY7FrIlIdH4xZKk@burgerking.s1qgce8.mongodb.net/?appName=BurgerKing"

    lateinit var client: MongoClient
        private set

    lateinit var db: com.mongodb.client.MongoDatabase
        private set

    // ----------------------------------
    // SAFE INDEX (NUNCA CRASHEA)
    // ----------------------------------
    private fun <T> MongoCollection<T>.safeCreateIndex(model: IndexModel) {
        try {
            this.createIndexes(listOf(model))
        } catch (e: MongoCommandException) {
            if (e.errorCode == 86 || e.errorCode == 85) {
                println("⚠️ Índice ignorado (ya existía con otra configuración): ${model.options?.name}")
            } else {
                throw e
            }
        }
    }

    // ----------------------------------
    // CONEXIÓN
    // ----------------------------------
    fun connect() {

        println("🔌 Conectando a MongoDB Atlas…")

        val connectionUri = System.getenv("MONGO_URI") ?: ATLAS_URI

        client = MongoClients.create(connectionUri)
        db = client.getDatabase(DB_NAME)

        println("✔ Conexión establecida con '$DB_NAME'")
        println("   Usando URI: $connectionUri")
    }

    // ----------------------------------
    // COLECCIONES
    // ----------------------------------
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

    // ----------------------------------
    // CONSTANTE: índice único sólo si activo = true
    // ----------------------------------
    private val uniqueIfActive = IndexOptions()
        .unique(true)
        .partialFilterExpression(Document("activo", true))

    // ----------------------------------
    // ÍNDICES ÚNICOS
    // ----------------------------------
    fun ensureIndexes() {

        println("🔧 Creando/verificando índices únicos…")

        // ---------------- PRODUCTOS ----------------
        productosCollection.safeCreateIndex(
            IndexModel(
                Document("nombre", 1),
                uniqueIfActive.name("producto_nombre_unico")
            )
        )

        // ---------------- CLIENTES ----------------
        clientesCollection.safeCreateIndex(
            IndexModel(
                Document("rut", 1),
                uniqueIfActive.name("cliente_rut_unico")
            )
        )

        clientesCollection.safeCreateIndex(
            IndexModel(
                Document("correo", 1),
                IndexOptions()
                    .unique(true)
                    .partialFilterExpression(Document("activo", true))
                    .name("cliente_correo_unico")
            )
        )

        // ---------------- EMPLEADOS ----------------
        empleadosCollection.safeCreateIndex(
            IndexModel(
                Document("rut", 1),
                uniqueIfActive.name("empleado_rut_unico")
            )
        )

        empleadosCollection.safeCreateIndex(
            IndexModel(
                Document("correo", 1),
                IndexOptions()
                    .unique(true)
                    .partialFilterExpression(Document("activo", true))
                    .name("empleado_correo_unico")
            )
        )

        // ---------------- COMBOS ----------------
        combosCollection.safeCreateIndex(
            IndexModel(
                Document("nombre", 1),
                uniqueIfActive.name("combo_nombre_unico")
            )
        )

        println("✔ Índices listos sin conflictos ✨")
    }
}
