package com.burgerking.duoc.tests

import com.burgerking.duoc.db.Database
import com.burgerking.duoc.models.Producto
import com.mongodb.client.MongoCollection

fun main() {
    println("🔍 Iniciando test de conexión Mongo Atlas...")

    // Conectar primero
    Database.connect()

    val db = Database.db

    // Obtener colección de productos
    val productos: MongoCollection<Producto> =
        db.getCollection("productos", Producto::class.java)

    val lista = productos.find().toList()

    println("📦 Productos encontrados: ${lista.size}")

    lista.forEach { p ->
        println("➡ ID: ${p._id}| ${p.nombre} | $${p.precio} | Stock: ${p.stock}")
    }

    println("✔ Test finalizado correctamente.")
}
