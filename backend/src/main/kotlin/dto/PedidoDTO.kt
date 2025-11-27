package com.burgerking.duoc.dto

/**
 * DTO para recibir datos de productos dentro de la lista del Pedido.
 * Recibe el ID del producto como String.
 */
data class ProductoPedidoDTO(
    val productoId: String, // String desde el frontend
    val cantidad: Int?
)

/**
 * DTO para recibir datos de creación/actualización de un Pedido.
 */
data class PedidoDTO(
    val clienteId: String?,
    val productos: List<ProductoPedidoDTO>?, // Lista de IDs de producto y cantidad
    val metodoPago: String?,
    val banco: String? = null,
    val direccionEntrega: String?,
    val estado: String? // Para actualización de estado (BK-023)
)

/**
 * DTO simple para recibir solo el nuevo estado del pedido.
 */
data class PedidoEstadoUpdateDTO(
    val estado: String?
)