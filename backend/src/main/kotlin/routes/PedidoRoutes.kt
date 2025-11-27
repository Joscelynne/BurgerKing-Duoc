package com.burgerking.duoc.routes

import com.burgerking.duoc.models.PedidoDTO
import com.burgerking.duoc.services.PedidoService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val pedidoService = PedidoService()

fun Route.pedidoRoutes() {
    route("/pedidos") {

        // [POST] /pedidos - Crear Pedido (incluye BK-031: Descuento stock)
        post {
            try {
                val dto = call.receive<PedidoDTO>()
                val createdPedido = pedidoService.create(dto)
                call.respond(HttpStatusCode.Created, createdPedido)
            } catch (e: IllegalArgumentException) {
                // Manejo de errores de Reglas BK (BK-030, BK-033, BK-022, etc.)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error interno del servidor al crear pedido: ${e.message}"))
            }
        }

        // [GET] /pedidos - Listar todos los Pedidos
        get {
            val pedidos = pedidoService.findAll()
            call.respond(HttpStatusCode.OK, pedidos)
        }

        // [PUT] /pedidos/{id}/estado - Actualizar Estado del Pedido (BK-023)
        put("/{id}/estado") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta ID."))
            try {
                val updateData = call.receive<Map<String, String>>()
                val nuevoEstado = updateData["estado"] ?: throw IllegalArgumentException("BK-023: Se requiere el campo 'estado'.")

                val updatedPedido = pedidoService.updateEstado(id, nuevoEstado)
                if (updatedPedido != null) {
                    call.respond(HttpStatusCode.OK, updatedPedido)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Pedido no encontrado o inactivo."))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error interno: ${e.message}"))
            }
        }

        // [DELETE] /pedidos/{id} - Eliminación Lógica
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta ID."))
            try {
                val result = pedidoService.softDelete(id)
                if (result.modifiedCount == 1L) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Pedido inactivado exitosamente."))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Pedido no encontrado."))
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }
    }
}

// Asegúrate de registrar pedidoRoutes() en tu Application.com.burgerking.duoc.module() junto con otras rutas.