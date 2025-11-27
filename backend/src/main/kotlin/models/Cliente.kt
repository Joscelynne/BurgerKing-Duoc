package com.burgerking.duoc.models

import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import kotlinx.serialization.Serializable

@Serializable
data class Cliente(
    @BsonId val id: ObjectId = ObjectId(),

    // Todos var → permite Updates.set() al 100%
    var nombre: String,           // BK-014
    var apellido: String,         // BK-015
    var rut: String,              // BK-013
    var correo: String,           // BK-012
    var telefono: String,         // BK-016
    var direccion: String,        // BK-017
    var activo: Boolean = true    // BK-007 Soft Delete
)
