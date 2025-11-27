package com.burgerking.duoc.controllers

import com.burgerking.duoc.dto.ApiResponse
import com.burgerking.duoc.dto.EmpleadoActivoUpdateDTO
import com.burgerking.duoc.dto.EmpleadoDTO
import com.burgerking.duoc.services.EmpleadoService // La clase EmpleadoController necesita el servicio
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * CLASE CONTROLADORA: Contiene toda la lógica de rutas y depende de EmpleadoService.
 * Koin ahora puede inyectar esta clase.
 */
class EmpleadoController(private val empleadoService: EmpleadoService) {

    /**
     * Define las rutas (endpoints) para la gestión de Empleados.
     * Esta función solo se encarga de delegar las peticiones a la lógica del servicio.
     */
    fun route(route: Route) = route.apply {

        route("/api/empleados") {

            // GET /api/empleados - Obtener todos los empleados activos (por defecto) o todos
            get {
                // Se puede extender para aceptar un query parameter `?activo=false` para ver inactivos
                val activoParam = call.request.queryParameters["activo"]?.toBooleanStrictOrNull() ?: true
                val empleados = empleadoService.findAll(activoParam)
                call.respond(HttpStatusCode.OK, ApiResponse(true, "Lista de empleados.", empleados))
            }

            // POST /api/empleados - Crear un nuevo empleado
            post {
                val dto = try { call.receive<EmpleadoDTO>() } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val (newEmpleado, validationErrors, errorMessage) = empleadoService.validateAndCreate(dto)

                when {
                    newEmpleado != null -> call.respond(HttpStatusCode.Created, ApiResponse(true, "Empleado creado con éxito.", newEmpleado))
                    validationErrors.isNotEmpty() -> call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(false, errorMessage ?: "Errores de unicidad (RUT/Correo).", errors = validationErrors))
                    else -> call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, errorMessage ?: "Faltan campos obligatorios o formato inválido."))
                }
            }

            // GET /api/empleados/{id} - Obtener un empleado por ID
            get("{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del empleado."))
                val empleado = empleadoService.findById(id)

                if (empleado != null) {
                    call.respond(HttpStatusCode.OK, ApiResponse(true, "Empleado encontrado.", empleado))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "Empleado no encontrado o ID inválido."))
                }
            }

            // PUT /api/empleados/{id} - Actualizar un empleado existente
            put("{id}") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del empleado."))
                val dto = try { call.receive<EmpleadoDTO>() } catch (e: Exception) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val (updatedEmpleado, validationErrors, errorMessage) = empleadoService.update(id, dto)

                when {
                    updatedEmpleado != null -> call.respond(HttpStatusCode.OK, ApiResponse(true, "Empleado actualizado con éxito.", updatedEmpleado))
                    validationErrors.isNotEmpty() -> call.respond(HttpStatusCode.Conflict, ApiResponse<Unit>(false, errorMessage ?: "Errores de unicidad (RUT/Correo).", errors = validationErrors))
                    errorMessage == "Empleado no encontrado." -> call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, errorMessage))
                    else -> call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, errorMessage ?: "Error de validación o campos faltantes."))
                }
            }

            // PUT /api/empleados/{id}/toggle-active - Eliminación Lógica (BK-033)
            put("{id}/toggle-active") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Falta el ID del empleado."))
                // Usamos EmpleadoActivoUpdateDTO del paquete 'dto'
                val update = try { call.receive<EmpleadoActivoUpdateDTO>() } catch (e: Exception) {
                    return@put call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(false, "Error en el formato de datos (JSON inválido)."))
                }

                val action = if (update.activo) "reactivado" else "inactivado (eliminación lógica BK-033)"

                if (empleadoService.toggleActivo(id, update.activo)) {
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(true, "Empleado $action con éxito."))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(false, "No se pudo cambiar el estado, empleado no encontrado o ID inválido."))
                }
            }
        }
    }
}