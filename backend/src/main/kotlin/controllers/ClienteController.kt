package com.burgerking.duoc.controllers

import com.burgerking.duoc.dto.ApiResponse
import com.burgerking.duoc.dto.ClienteActivoUpdateDTO
import com.burgerking.duoc.dto.ClienteDTO
import com.burgerking.duoc.services.ClienteService // La clase ClienteController necesita el servicio
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * CLASE CONTROLADORA: Contiene toda la lógica de rutas y depende de ClienteService.
 * Koin ahora puede inyectar esta clase.
 */
class ClienteController(private val clienteService: ClienteService) {

    /**
     * Define las rutas (endpoints) para la gestión de Clientes.
     * Esta función solo se encarga de delegar las peticiones a la lógica del servicio.
     */
    fun route(route: Route) = route.apply {

        route("/api/clientes") {

            // GET /api/clientes - Obtener todos los clientes activos
            get {
                val clientes = clienteService.findAllActive()
                call.respond(HttpStatusCode.OK, ApiResponse(true, "Lista de clientes activos.", clientes))
            }

            // POST /api/clientes - Crear un nuevo cliente
            post {
                val dto = try { call.receive<ClienteDTO>() } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val (newCliente, validationErrors, errorMessage) = clienteService.validateAndCreate(dto)

                when {
                    newCliente != null -> call.respond(HttpStatusCode.Created, ApiResponse(true, "Cliente creado con éxito.", newCliente))
                    validationErrors.isNotEmpty() -> call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(false, errorMessage ?: "Errores de unicidad (RUT/Correo).", errors = validationErrors))
                    else -> call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, errorMessage ?: "Faltan campos obligatorios o formato inválido."))
                }
            }

            // GET /api/clientes/{id} - Obtener un cliente por ID
            get("{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del cliente."))
                val cliente = clienteService.findById(id)

                if (cliente != null) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, "Cliente encontrado.", cliente))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "Cliente no encontrado o ID inválido."))
                }
            }

            // PUT /api/clientes/{id} - Actualizar un cliente existente
            put("{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del cliente."))
                val dto = try { call.receive<ClienteDTO>() } catch (e: Exception) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val (updatedCliente, validationErrors, errorMessage) = clienteService.update(id, dto)

                when {
                    updatedCliente != null -> call.respond(HttpStatusCode.OK, ApiResponse(true, "Cliente actualizado con éxito.", updatedCliente))
                    validationErrors.isNotEmpty() -> call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(false, errorMessage ?: "Errores de unicidad (RUT/Correo).", errors = validationErrors))
                    errorMessage == "Cliente no encontrado." -> call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, errorMessage))
                    else -> call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, errorMessage ?: "Error de validación o campos faltantes."))
                }
            }

            // PUT /api/clientes/{id}/toggle-active - Eliminación Lógica (BK-007)
            put("{id}/toggle-active") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del cliente."))
                val update = try { call.receive<ClienteActivoUpdateDTO>() } catch (e: Exception) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val action = if (update.activo) "reactivado" else "inactivado (eliminación lógica)"

                if (clienteService.toggleActivo(id, update.activo)) {
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(true, "Cliente $action con éxito."))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo cambiar el estado, cliente no encontrado o ID inválido."))
                }
            }
        }
    }
}