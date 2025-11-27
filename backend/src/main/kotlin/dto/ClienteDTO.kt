package com.burgerking.duoc.dto

import kotlinx.serialization.Serializable
import com.burgerking.duoc.models.Cliente

/**
 * DTO para la creación y actualización (parcial) de Clientes.
 * Todos los campos son opcionales para permitir actualizaciones parciales (PATCH/PUT).
 * Los campos obligatorios se validarán en el Service.
 */
@Serializable
data class ClienteDTO(
    val nombre: String? = null, // BK-014
    val apellido: String? = null, // BK-015
    val rut: String? = null, // BK-013
    val correo: String? = null, // BK-012
    val telefono: String? = null, // BK-016
    val direccion: String? = null, // BK-017
    val activo: Boolean? = null // Para el soft delete
)

/**
 * DTO para el cambio de estado 'activo' (soft delete).
 */
@Serializable
data class ClienteActivoUpdateDTO(
    val activo: Boolean
)

/**
 * Clase para encapsular errores de validación de forma estructurada (BK-XXX).
 */
@Serializable
data class ValidationError(
    val field: String,
    val message: String
)

/**
 * Respuesta estructurada para el API de Ktor, incluyendo errores si es necesario.
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val errors: List<ValidationError> = emptyList()
)