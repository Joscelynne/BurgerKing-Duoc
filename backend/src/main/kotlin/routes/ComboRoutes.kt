package com.burgerking.duoc.routes

import com.burgerking.duoc.controllers.ComboController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Rutas de Combo para Ktor.
 * Utiliza inyección de dependencias con Koin para obtener ComboController.
 */
fun Route.comboRoutes() {

    // Inyección del controlador usando Koin
    val controller: ComboController by inject()

    // Se delega a la función 'route' del controlador
    controller.route(this)
}
