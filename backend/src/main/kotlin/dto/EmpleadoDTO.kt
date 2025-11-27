package com.burgerking.duoc.dto

import kotlinx.serialization.Serializable

/**
 * DTO para la creación y actualización (parcial) de Empleados.
 * Todos los campos son opcionales para permitir la actualización parcial,
 * excepto los obligatorios que se verifican en el Service.
 */
@Serializable
data class EmpleadoDTO(
    val nombre: String? = null,           // BK-019
    val apellido: String? = null,         // BK-020
    val rut: String? = null,              // BK-018
    val rol: String? = null,              // BK-032
    val telefono: String? = null,         // Opcional
    val correo: String? = null,           // Opcional
    val direccion: String? = null,        // Opcional
    val activo: Boolean? = null           // BK-033
)

/**
 * DTO para el cambio de estado 'activo' (soft delete).
 */
@Serializable
data class EmpleadoActivoUpdateDTO(
    val activo: Boolean
)