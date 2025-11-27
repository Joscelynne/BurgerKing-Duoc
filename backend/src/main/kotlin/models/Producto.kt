package com.burgerking.duoc.models

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant // <--- IMPORTACIÓN NECESARIA

/**
 * Modelo de Producto persistido en MongoDB.
 * Contiene el estado 'activo' para la eliminación lógica (Soft Delete BK-003) y campos de auditoría.
 */
data class Producto(
    @BsonId val _id: ObjectId = ObjectId(),
    val nombre: String,
    val precio: Double,
    val stock: Int,
    val categoria: String,
    val descripcion: String?,
    val activo: Boolean = true, // BK-003: Eliminación lógica
    val createdAt: Instant,     // <--- CAMPO AÑADIDO
    val updatedAt: Instant      // <--- CAMPO AÑADIDO
)