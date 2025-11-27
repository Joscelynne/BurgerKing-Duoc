package com.burgerking.duoc.controllers

import com.burgerking.duoc.dto.ApiResponse
import com.burgerking.duoc.dto.ComboActivoUpdateDTO
import com.burgerking.duoc.dto.ComboDTO
import com.burgerking.duoc.services.ComboService // La clase ComboController necesita el servicio
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.mongodb.client.result.UpdateResult

/**
 * CLASE CONTROLADORA: Contiene toda la lógica de rutas y depende de ComboService.
 * Koin ahora puede inyectar esta clase.
 */
class ComboController(private val comboService: ComboService) {

    /**
     * Define las rutas (endpoints) para la gestión de Combos.
     * Esta función solo se encarga de delegar las peticiones a la lógica del servicio.
     */
    fun route(route: Route) = route.apply {

        route("/api/combos") {

            // GET /api/combos - Obtener todos los combos activos (por defecto) o todos
            get {
                val activoParam = call.request.queryParameters["activo"]?.toBooleanStrictOrNull() ?: true
                val combos = comboService.findAll(activoParam)
                call.respond(HttpStatusCode.OK, ApiResponse(true, "Lista de combos.", combos))
            }

            // POST /api/combos - Crear un nuevo combo
            post {
                // BK-038: Validar tipo de datos DTO
                val dto = try { call.receive<ComboDTO>() } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "BK-038: Error en el formato de datos (JSON inválido)."))
                }

                val (newCombo, validationErrors, errorMessage) = comboService.validateAndCreate(dto)

                when {
                    newCombo != null -> call.respond(HttpStatusCode.Created, ApiResponse(true, "Combo creado con éxito.", newCombo))
                    // BK-009 (Nombre duplicado), BK-037 (ID de producto inválido), BK-040 (Lista vacía)
                    validationErrors.isNotEmpty() -> call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(false, errorMessage ?: "Errores de unicidad o validación cruzada.", errors = validationErrors))
                    else -> call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, errorMessage ?: "Faltan campos obligatorios o formato inválido."))
                }
            }

            // GET /api/combos/{id} - Obtener un combo por ID
            get("{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del combo."))
                val combo = comboService.findById(id)

                if (combo != null) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, "Combo encontrado.", combo))
                } else {
                    // BK-037: Si el ID es inválido, findById retorna null. Si no existe, retorna null.
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "Combo no encontrado o ID inválido."))
                }
            }

            // PUT /api/combos/{id} - Actualizar un combo existente
            put("{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del combo."))
                val dto = try { call.receive<ComboDTO>() } catch (e: Exception) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "BK-038: Error en el formato de datos (JSON inválido)."))
                }

                val (updatedCombo, validationErrors, errorMessage) = comboService.update(id, dto)

                when {
                    updatedCombo != null -> call.respond(HttpStatusCode.OK, ApiResponse(true, "Combo actualizado con éxito.", updatedCombo))
                    validationErrors.isNotEmpty() -> call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(false, errorMessage ?: "Errores de unicidad o validación cruzada.", errors = validationErrors))
                    errorMessage == "Combo no encontrado." -> call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, errorMessage))
                    else -> call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, errorMessage ?: "Error de validación o campos faltantes."))
                }
            }

            // PUT /api/combos/{id}/toggle-active - Eliminación Lógica (BK-007)
            put("{id}/toggle-active") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del combo."))
                val update = try { call.receive<ComboActivoUpdateDTO>() } catch (e: Exception) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val action = if (update.activo) "reactivado" else "inactivado (eliminación lógica BK-007)"

                val result: UpdateResult
                try {
                    result = comboService.toggleActivo(id, update.activo)
                } catch (e: IllegalArgumentException) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, e.message ?: "ID inválido (BK-037)."))
                }

                if (result.modifiedCount == 1L) {
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(true, "Combo $action con éxito."))
                } else if (result.matchedCount == 1L && result.modifiedCount == 0L) {
                    // El combo existía, pero el estado ya era el solicitado.
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(true, "Combo ya estaba $action."))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo cambiar el estado, combo no encontrado o ID inválido."))
                }
            }
        }
    }
}