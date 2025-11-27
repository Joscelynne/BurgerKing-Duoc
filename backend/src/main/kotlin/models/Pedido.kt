package com.burgerking.duoc.models

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

// Definición de estados del pedido (BK-023)
enum class EstadoPedido {
    PENDIENTE,
    PREPARACION,
    LISTO,
    ENTREGADO,
    CANCELADO
}

// Definición de métodos de pago
enum class MetodoPago {
    DEBITO,
    CREDITO,
    EFECTIVO
}

/**
 * Sub-documento para almacenar la información esencial del producto en el pedido.
 * Evita depender del stock/precio actual en la colección productos.
 */
data class ProductoPedido(
    val productoId: ObjectId,
    val nombre: String,
    val precioUnitario: Double, // Usar Double para precisión y consistencia con Producto.precio
    val cantidad: Int
)

/**
 * Modelo de datos para un Pedido en MongoDB.
 * Incorpora la eliminación lógica (soft delete) y campos de reglas BK-00X.
 */
data class Pedido(
    @BsonId val _id: ObjectId = ObjectId(),
    val clienteId: ObjectId,                // Cliente que realiza el pedido
    val productos: List<ProductoPedido>,     // Lista de productos y cantidades
    val metodoPago: MetodoPago,             // Tarjeta, Efectivo (BK-022 aplica)
    val banco: String? = null,              // Banco, solo si es débito/crédito
    val direccionEntrega: String,           // BK-033: Dirección obligatoria
    val descuento: Double = 0.0,            // BK-021: Aplicado solo si cumple reglas
    val total: Double,                      // BK-010: Total calculado
    val estado: EstadoPedido = EstadoPedido.PENDIENTE, // BK-023: Estado inicial
    val activo: Boolean = true,             // Eliminación lógica (soft delete)
    val fechaCreacion: Instant = Instant.now() // BK-011: Fecha automática (hoy)
)