package com.burgerking.duoc.routes

import com.burgerking.duoc.controllers.ProductoController
import com.burgerking.duoc.services.ProductoService
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureProductoRoutes() {
    val controller = ProductoController(ProductoService())

    routing {
        route("/api/productos") {
            // CRUD Completo
            get { controller.getAllProductos(call) } // Listar (Read)
            post { controller.createProducto(call) } // Crear (Create)
            get("/{id}") { controller.getProducto(call) } // Obtener por ID (Read)
            put("/{id}") { controller.updateProducto(call) } // Actualizar (Update)
            delete("/{id}") { controller.deleteProducto(call) } // Eliminar Lógico (Delete)
        }
    }
}