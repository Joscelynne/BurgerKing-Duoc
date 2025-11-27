package com.burgerking.duoc.routes

import com.burgerking.duoc.controllers.EmpleadoController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Rutas de Empleado para Ktor.
 * Utiliza inyección de dependencias con Koin para obtener EmpleadoController.
 */
fun Route.empleadoRoutes() {

    // Inyección del controlador usando Koin:
    val controller: EmpleadoController by inject()

    // Delegar a la función "route" del controlador
    controller.route(this)
}
