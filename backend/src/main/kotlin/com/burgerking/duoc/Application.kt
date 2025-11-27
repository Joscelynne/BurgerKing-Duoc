package com.burgerking.duoc

import com.burgerking.duoc.db.Database
import com.burgerking.duoc.modules.appModules
import com.burgerking.duoc.routes.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import com.burgerking.duoc.modules.*



fun main() {
    // 1) Conectar a Mongo Atlas ANTES de iniciar Ktor
    Database.connect()

    // 2) Iniciar servidor Ktor
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0"
    ) {
        module()
    }.start(wait = true)
}

fun Application.module() {

    // -------------------------------
    // 1️⃣  KOIN (Inyección de dependencias)
    // -------------------------------
    install(Koin) {
        printLogger()
        modules(appModules)
    }

    // -------------------------------
    // 2️⃣  CORS
    // -------------------------------
    install(CORS) {
        anyHost()
        allowHeader("*")
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    // -------------------------------
    // 3️⃣  JSON
    // -------------------------------
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
            serializeNulls()
        }
    }

    // -------------------------------
    // 4️⃣  RUTAS API
    // -------------------------------
//    routing {
//        productoRoutes()
//        comboRoutes()
//        clienteRoutes()
//        empleadoRoutes()
//        pedidoRoutes()
//    } AGREGAR CUANDO ARREGLEMOS LAS RUTAS API!

    println(" Servidor BurgerKing-Duoc iniciado en http://localhost:8080")
}
