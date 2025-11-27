package com.burgerking.duoc.routes

import com.burgerking.duoc.controllers.ClienteController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.clienteRoutes() {

    val controller by inject<ClienteController>()

    routing {
        // TODAS LAS RUTAS YA ESTÁN DEFINIDAS EN controller.route()
        controller.route(this)
    }
}
