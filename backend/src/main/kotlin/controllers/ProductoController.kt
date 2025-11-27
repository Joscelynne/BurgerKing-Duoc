package com.burgerking.duoc.controllers

import com.burgerking.duoc.dto.ProductoDTO
import com.burgerking.duoc.services.ProductoService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Controlador de Productos. Responsable del flujo HTTP y manejo de errores.
 */
class ProductoController(private val service: ProductoService) {

    private suspend fun handleException(call: ApplicationCall, e: Exception, defaultMessage: String, status: HttpStatusCode = HttpStatusCode.InternalServerError) {
        val message = when (e) {
            is IllegalArgumentException -> {
                call.response.status(HttpStatusCode.BadRequest) // 400 Bad Request
                e.message
            }
            else -> defaultMessage
        }
        call.respond(mapOf("error" to message))
    }

    /**
     * [POST] /productos - Crea un nuevo Producto.
     */
    suspend fun createProducto(call: ApplicationCall) {
        try {
            val dto = call.receive<ProductoDTO>()
            val producto = service.create(dto)
            call.respond(HttpStatusCode.Created, producto)
        } catch (e: Exception) {
            handleException(call, e, "Error al crear producto. Revise campos requeridos o duplicidad (BK-004).", HttpStatusCode.BadRequest)
        }
    }

    /**
     * [GET] /productos - Lista todos los Productos activos.
     */
    suspend fun getAllProductos(call: ApplicationCall) {
        try {
            val productos = service.findAll()
            call.respond(HttpStatusCode.OK, productos)
        } catch (e: Exception) {
            handleException(call, e, "Error al listar productos.")
        }
    }

    /**
     * [GET] /productos/{id} - Obtiene un Producto por su ID.
     */
    suspend fun getProductoById(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID es requerido."))
        try {
            val producto = service.findById(id)
            if (producto != null) {
                call.respond(HttpStatusCode.OK, producto)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Producto no encontrado o inactivo."))
            }
        } catch (e: Exception) {
            handleException(call, e, "Error al buscar producto (BK-037: formato ID inválido).", HttpStatusCode.BadRequest)
        }
    }

    /**
     * [PUT] /productos/{id} - Actualiza un Producto existente.
     */
    suspend fun updateProducto(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID es requerido."))
        try {
            val dto = call.receive<ProductoDTO>()
            val updatedProducto = service.update(id, dto)

            if (updatedProducto != null) {
                call.respond(HttpStatusCode.OK, updatedProducto)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Producto no encontrado para actualizar."))
            }
        } catch (e: Exception) {
            handleException(call, e, "Error al actualizar producto. Revise datos o duplicidad de nombre (BK-004).", HttpStatusCode.BadRequest)
        }
    }

    /**
     * [DELETE] /productos/{id} - Eliminación Lógica (Soft Delete BK-003).
     */
    suspend fun softDeleteProducto(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID es requerido."))
        try {
            val result = service.softDelete(id)
            if (result.matchedCount == 0L) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Producto no encontrado para inactivar."))
            } else if (result.modifiedCount == 0L) {
                call.respond(HttpStatusCode.NoContent) // Ya estaba inactivo
            } else {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Producto inactivado (Eliminación Lógica BK-003) exitosamente."))
            }
        } catch (e: Exception) {
            handleException(call, e, "Error al realizar la eliminación lógica (BK-037: formato ID inválido).", HttpStatusCode.BadRequest)
        }
    }
}