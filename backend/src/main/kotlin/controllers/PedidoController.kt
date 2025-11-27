package com.burgerking.duoc.controllers

import com.burgerking.duoc.dto.PedidoDTO
import com.burgerking.duoc.dto.PedidoEstadoUpdateDTO
import com.burgerking.duoc.services.PedidoService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

/**
 * Controlador de Pedidos. Responsable del flujo HTTP.
 */
class PedidoController(private val service: PedidoService) {

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
     * [POST] /pedidos - Crea un nuevo Pedido (incluye validación y ajuste de stock BK-031).
     */
    suspend fun createPedido(call: ApplicationCall) {
        try {
            val dto = call.receive<PedidoDTO>()
            val pedido = service.create(dto)
            call.respond(HttpStatusCode.Created, pedido)
        } catch (e: Exception) {
            handleException(call, e, "Error al crear pedido. Revise IDs, stock, o formato (BK-038).", HttpStatusCode.BadRequest)
        }
    }

    /**
     * [GET] /pedidos - Lista todos los Pedidos activos.
     */
    suspend fun getAllPedidos(call: ApplicationCall) {
        try {
            val pedidos = service.findAll()
            call.respond(HttpStatusCode.OK, pedidos)
        } catch (e: Exception) {
            handleException(call, e, "Error al listar pedidos.")
        }
    }

    /**
     * [PUT] /pedidos/{id}/estado - Actualiza el estado de un pedido (BK-023).
     */
    suspend fun updateEstadoPedido(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID es requerido."))
        try {
            val dto = call.receive<PedidoEstadoUpdateDTO>()
            val updatedPedido = service.updateEstado(id, dto.estado!!)
            if (updatedPedido != null) {
                call.respond(HttpStatusCode.OK, updatedPedido)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Pedido no encontrado para actualizar estado."))
            }
        } catch (e: Exception) {
            handleException(call, e, "Error al actualizar estado. Revise el formato ID o el estado (BK-023).", HttpStatusCode.BadRequest)
        }
    }

    /**
     * [DELETE] /pedidos/{id} - Eliminación Lógica (BK-007).
     */
    suspend fun softDeletePedido(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID es requerido."))
        try {
            val result = service.softDelete(id)
            if (result.matchedCount == 0L) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Pedido no encontrado para inactivar."))
            } else if (result.modifiedCount == 0L) {
                call.respond(HttpStatusCode.NoContent) // Ya estaba inactivo
            } else {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Pedido inactivado (Eliminación Lógica BK-007) exitosamente."))
            }
        } catch (e: Exception) {
            handleException(call, e, "Error al realizar la eliminación lógica (BK-037: formato ID inválido).", HttpStatusCode.BadRequest)
        }
    }
}