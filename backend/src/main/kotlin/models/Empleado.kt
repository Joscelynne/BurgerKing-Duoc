package com.burgerking.duoc.models

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import kotlinx.serialization.Serializable

/**
 * Roles válidos para un Empleado (BK-032).
 */
enum class EmpleadoRol(val value: String) {
    ADMINISTRATIVO("ADMINISTRATIVO"),
    CAJERO("CAJERO"),
    COCINERO("COCINERO"),
    REPARTIDOR("REPARTIDOR")
}

/**
 * Modelo de la entidad Empleado para persistencia en MongoDB.
 */
@Serializable
data class Empleado(
    @BsonId val id: ObjectId = ObjectId(),
    val nombre: String,           // BK-019
    val apellido: String,         // BK-020
    val rut: String,              // BK-018 (Validación Módulo 11 y unicidad)
    val rol: String,              // BK-032 (Validar contra EmpleadoRol)
    val telefono: String? = null, // BK-016 (9 dígitos si existe)
    val correo: String? = null,   // BK-012 (formato válido si existe)
    val direccion: String? = null,// Opcional
    val activo: Boolean = true    // BK-033 (Soft delete)
)