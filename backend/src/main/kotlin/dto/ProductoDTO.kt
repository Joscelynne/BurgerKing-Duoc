package com.burgerking.duoc.dto

/**
 * DTO (Data Transfer Object) para la creación o actualización de un Producto.
 * Incluye el campo 'activo' (opcional) para permitir la activación/desactivación desde el DTO.
 */
data class ProductoDTO(
    var nombre: String?,
    var precio: Double?,
    var stock: Int?,
    var categoria: String?,
    var descripcion: String?,
    var activo: Boolean? // <--- CAMPO AÑADIDO PARA RESOLVER EL ERROR DE REFERENCIA
    // Nota: El ID (String) se pasaría por la ruta (path) en operaciones de actualización/eliminación.
)