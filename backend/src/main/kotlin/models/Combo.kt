package com.burgerking.duoc.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

/**
 * Modelo de la entidad Combo para persistencia en MongoDB.
 */
@Serializable
data class Combo(
    @BsonId val id: ObjectId = ObjectId(),
    val nombre: String,                         // BK-009: No vacío
    val productosIds: List<ObjectId>,           // BK-040: IDs de productos incluidos (Debe existir en Producto, BK-037)
    val precio: Double,                         // BK-008: Calculado (SUM(productos) * 0.9) y positivo.
    val descripcion: String? = null,            // Opcional
    val activo: Boolean = true,                 // BK-007: Eliminación lógica
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)