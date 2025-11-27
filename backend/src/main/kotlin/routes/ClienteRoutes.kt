package com.bkduoc.routes

import com.bkduoc.model.ApiResponse
import com.bkduoc.model.Cliente
import com.bkduoc.model.ClienteActivoUpdate
import com.bkduoc.model.ValidationError
import com.bkduoc.service.ClienteService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.clienteRouting() {
    val clienteService by inject<ClienteService>()

    route("/api/clientes") {

        // GET /api/clientes - Obtener todos los clientes
        get {
            val clientes = clienteService.obtenerClientes()
            call.respond(clientes)
        }

        // POST /api/clientes - Crear un nuevo cliente
        post {
            val cliente = try { call.receive<Cliente>() } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
            }

            val validationErrors = clienteService.validarCliente(cliente)

            if (validationErrors.isNotEmpty()) {
                // Si hay errores de validación (incluyendo duplicados BK-012/BK-013)
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Errores de validación.", errors = validationErrors))
                return@post
            }

            val newCliente = clienteService.crearCliente(cliente)
            if (newCliente != null) {
                call.respond(HttpStatusCode.Created, ApiResponse(true, "Cliente creado con éxito.", newCliente))
            } else {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(false, "No se pudo crear el cliente."))
            }
        }

        // GET /api/clientes/{id} - Obtener un cliente por ID
        get("{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del cliente."))
            val cliente = clienteService.obtenerClientePorId(id)

            if (cliente != null) {
                call.respond(HttpStatusCode.OK, cliente)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "Cliente no encontrado."))
            }
        }

        // PUT /api/clientes/{id} - Actualizar un cliente existente
        put("{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del cliente."))
            val cliente = try { call.receive<Cliente>() } catch (e: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
            }

            val validationErrors = clienteService.validarCliente(cliente.copy(id = org.bson.types.ObjectId(id)), isUpdate = true)

            if (validationErrors.isNotEmpty()) {
                // Si hay errores de validación (incluyendo duplicados BK-012/BK-013)
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Errores de validación.", errors = validationErrors))
                return@put
            }

            if (clienteService.actualizarCliente(id, cliente)) {
                call.respond(HttpStatusCode.OK, ApiResponse(true, "Cliente actualizado con éxito."))
            } else {
                call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo actualizar o cliente no encontrado."))
            }
        }

        // PUT /api/clientes/{id}/toggle-active - Eliminar Lógico (cambiar estado activo)
        put("{id}/toggle-active") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del cliente."))
            val update = try { call.receive<ClienteActivoUpdate>() } catch (e: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
            }

            if (clienteService.toggleActivo(id, update.activo)) {
                val action = if (update.activo) "reactivado" else "inactivado (eliminación lógica)"
                call.respond(HttpStatusCode.OK, ApiResponse(true, "Cliente $action con éxito."))
            } else {
                call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo cambiar el estado o cliente no encontrado."))
            }
        }

        // DELETE /api/clientes/{id} - NO IMPLEMENTADO (usamos soft delete/toggle)
        // Puedes agregar esta ruta si deseas el hard delete, pero se omite por BK-007.
    }
}