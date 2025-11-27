package com.burgerking.duoc.routes

import com.burgerking.duoc.controllers.PedidoController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.pedidoRoutes() {

    val controller by inject<PedidoController>()

    route("/api/pedidos") {

        post {
            controller.createPedido(call)
        }

        get {
            controller.getAllPedidos(call)
        }

        put("{id}/estado") {
            controller.updateEstadoPedido(call)
        }

        delete("{id}") {
            controller.softDeletePedido(call)
        }
    }
}
