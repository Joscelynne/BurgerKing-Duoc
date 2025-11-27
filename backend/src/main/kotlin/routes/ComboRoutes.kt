package com.burgerking.duoc.routes

import com.burgerking.duoc.models.ComboDTO
import com.burgerking.duoc.services.ComboService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Inyección de dependencia (Simple)
private val comboService = ComboService()

fun Route.comboRoutes() {
    route("/combos") {

        // [POST] /combos - Crear Combo
        post {
            try {
                // BK-038: Validar tipo de datos (Ktor deserializa a DTO)
                val dto = call.receive<ComboDTO>()
                val createdCombo = comboService.create(dto)
                call.respond(HttpStatusCode.Created, createdCombo)
            } catch (e: IllegalArgumentException) {
                // Manejo de errores de Reglas BK (BK-007, BK-008, BK-011, BK-015, BK-014, etc.)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error interno del servidor al crear combo: ${e.message}"))
            }
        }

        // [GET] /combos - Listar todos los Combos
        get {
            val combos = comboService.findAll()
            call.respond(HttpStatusCode.OK, combos)
        }

        // [GET] /combos/{id} - Obtener Combo por ID
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta ID del combo."))
            try {
                val combo = comboService.findById(id)
                if (combo != null) {
                    call.respond(HttpStatusCode.OK, combo)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Combo no encontrado."))
                }
            } catch (e: IllegalArgumentException) {
                // Manejo de error de formato de ID (BK-037)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }

        // [PUT] /combos/{id} - Actualizar Combo
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta ID del combo."))
            try {
                val dto = call.receive<ComboDTO>()
                val updatedCombo = comboService.update(id, dto)
                if (updatedCombo != null) {
                    call.respond(HttpStatusCode.OK, updatedCombo)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Combo no encontrado para actualizar."))
                }
            } catch (e: IllegalArgumentException) {
                // Manejo de errores de Reglas BK (BK-008, BK-011, BK-015, etc.)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error interno del servidor al actualizar combo: ${e.message}"))
            }
        }

        // [DELETE] /combos/{id} - Eliminación Lógica
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta ID del combo."))
            try {
                val result = comboService.softDelete(id)
                if (result.modifiedCount == 1L) {
                    // BK-005: Eliminación lógica exitosa
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Combo inactivado (Eliminación Lógica BK-005) exitosamente."))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Combo no encontrado para inactivar."))
                }
            } catch (e: IllegalArgumentException) {
                // Manejo de error de formato de ID (BK-037)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        }
    }
}