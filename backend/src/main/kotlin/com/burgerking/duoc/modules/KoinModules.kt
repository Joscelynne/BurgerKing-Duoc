package com.burgerking.duoc.modules

import com.burgerking.duoc.controllers.*
import com.burgerking.duoc.services.*
import com.burgerking.duoc.db.Database
import org.koin.dsl.module

/**
 * Módulo Mongo: expone el MongoClient REAL de Database.kt
 */

/**
 * Módulo Servicios: cada servicio recibe MongoClient desde mongoModule
 */
val serviceModule = module {
    single { ProductoService(get()) }
    single { ComboService(get(), get<ProductoService>()) }
    single { ClienteService(get()) }
    single { EmpleadoService(get()) }
    single { PedidoService(get(), get<ProductoService>()) }
}

/**
 * Módulo Controladores: inyectan sus servicios correspondientes
 */
val controllerModule = module {
    single { ProductoController(get()) }
    single { ComboController(get()) }
    single { ClienteController(get()) }
    single { EmpleadoController(get()) }
    single { PedidoController(get()) }
}

val appModules = listOf(
    mongoModule,
    serviceModule,
    controllerModule
)
