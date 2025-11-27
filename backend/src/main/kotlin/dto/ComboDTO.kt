package com.burgerking.duoc.dto

import kotlinx.serialization.Serializable

/**
 * DTO para recibir datos de creación/actualización de un Combo.
 * - 'productosIds' se recibe como lista de Strings para facilitar la entrada JSON.
 * - 'precio' no se incluye porque es CALCULADO (BK-008).
 */
@Serializable
data class ComboDTO(
    val nombre: String? = null,
    val productosIds: List<String>? = null, // Recibido como Strings (ObjectId en formato String)
    val descripcion: String? = null,
    val activo: Boolean? = null
)

/**
 * DTO para el cambio de estado 'activo' (soft delete).
 */
@Serializable
data class ComboActivoUpdateDTO(
    val activo: Boolean
)