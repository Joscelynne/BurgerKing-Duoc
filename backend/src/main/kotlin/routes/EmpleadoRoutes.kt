package com.bkduoc.routes

import com.bkduoc.model.ApiResponse
import com.bkduoc.model.Empleado
import com.bkduoc.model.EmpleadoActivoUpdate
import com.bkduoc.model.ValidationError
import com.bkduoc.service.EmpleadoService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.empleadoRouting() {
    val empleadoService by inject<EmpleadoService>()

    route("/api/empleados") {

        // GET /api/empleados - Obtener todos los empleados
        get {
            val empleados = empleadoService.obtenerEmpleados()
            call.respond(empleados)
        }

        // POST /api/empleados - Crear un nuevo empleado
        post {
            val empleado = try { call.receive<Empleado>() } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
            }

            val validationErrors = empleadoService.validarEmpleado(empleado)

            if (validationErrors.isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Errores de validación.", errors = validationErrors))
                return@post
            }

            val newEmpleado = try { empleadoService.crearEmpleado(empleado) } catch (e: Exception) { null }

            if (newEmpleado != null) {
                call.respond(HttpStatusCode.Created, ApiResponse(true, "Empleado creado con éxito.", newEmpleado))
            } else {
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(false, "No se pudo crear el empleado."))
            }
        }

        // GET /api/empleados/{id} - Obtener un empleado por ID
        get("{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del empleado."))
            val empleado = empleadoService.obtenerEmpleadoPorId(id)

            if (empleado != null) {
                call.respond(HttpStatusCode.OK, empleado)
            } else {
                call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "Empleado no encontrado."))
            }
        }

        // PUT /api/empleados/{id} - Actualizar un empleado existente
        put("{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del empleado."))
            val empleado = try { call.receive<Empleado>() } catch (e: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
            }

            val empleadoWithId = try { empleado.copy(id = org.bson.types.ObjectId(id)) } catch (e: Exception) { empleado }
            val validationErrors = empleadoService.validarEmpleado(empleadoWithId, isUpdate = true)

            if (validationErrors.isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Errores de validación.", errors = validationErrors))
                return@put
            }

            try {
                if (empleadoService.actualizarEmpleado(id, empleado)) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, "Empleado actualizado con éxito."))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo actualizar o empleado no encontrado."))
                }
            } catch (e: Exception) {
                // Manejo genérico de error de actualización si la validación falla internamente
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en la actualización: ${e.message}"))
            }
        }

        // PUT /api/empleados/{id}/toggle-active - Eliminar Lógico (cambiar estado activo)
        put("{id}/toggle-active") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del empleado."))
            val update = try { call.receive<EmpleadoActivoUpdate>() } catch (e: Exception) {
                return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
            }

            if (empleadoService.toggleActivo(id, update.activo)) {
                val action = if (update.activo) "reactivado" else "inactivado (eliminación lógica)"
                call.respond(HttpStatusCode.OK, ApiResponse(true, "Empleado $action con éxito."))
            } else {
                call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo cambiar el estado o empleado no encontrado."))
            }
        }

        // DELETE /api/empleados/{id} - NO IMPLEMENTADO (usamos soft delete)
    }
}