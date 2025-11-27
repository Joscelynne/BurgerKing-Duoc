package com.burgerking.duoc.routes

import com.burgerking.duoc.controllers.ProductoController
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.productoRoutes() {

    val controller by inject<ProductoController>()

    routing {
        route("/api/productos") {

            // GET /api/productos
            get {
                controller.getAllProductos(call)
            }

            // GET /api/productos/{id}
            get("/{id}") {
                controller.getProductoById(call)
            }

            // POST /api/productos
            post {
                controller.createProducto(call)
            }

            // PUT /api/productos/{id}
            put("/{id}") {
                controller.updateProducto(call)
            }

            // DELETE /api/productos/{id}
            delete("/{id}") {
                controller.softDeleteProducto(call)
            }
        }
    }
}
