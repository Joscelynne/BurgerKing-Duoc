package com.burgerking.duoc.modules

import com.burgerking.duoc.db.Database
import com.mongodb.client.MongoClient
import org.koin.dsl.module

/**
 * Modulo Koin que expone MongoClient para inyección en Services.
 */
val mongoModule = module {

    // Expone el cliente mongo ya inicializado en Database.kt
    single<MongoClient> {
        Database.client
    }
}
