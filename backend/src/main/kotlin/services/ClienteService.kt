package com.burgerking.duoc.services

import com.burgerking.duoc.dto.ClienteDTO
import com.burgerking.duoc.dto.ValidationError
import com.burgerking.duoc.models.Cliente // <-- Asegurando la importación del modelo
import com.burgerking.duoc.utils.Validation
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates // <-- Asegurando la importación de Updates
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.set
import org.litote.kmongo.setTo


/**
 * Servicio para la entidad Cliente.
 * Centraliza la lógica de negocio, validación y acceso a MongoDB.
 */
class ClienteService(private val client: MongoClient) {
    // Nota: Asegúrate de usar el mismo nombre de base de datos que tu aplicación Ktor
    private val database = client.getDatabase("burgerking_db")
    private val collection: MongoCollection<Cliente> = database.getCollection<Cliente>("clientes")

    /**
     * Verifica la unicidad de RUT (BK-013) y Correo (BK-012).
     */
    private fun checkDuplication(rut: String, correo: String, idToExclude: ObjectId? = null): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // 1. Verificar duplicidad de RUT (BK-013)
        val rutFilter = idToExclude?.let {
            Filters.and(Cliente::rut eq rut, Filters.ne("_id", it))
        } ?: (Cliente::rut eq rut)

        if (collection.countDocuments(rutFilter) > 0) {
            errors.add(ValidationError("rut", "BK-013: El RUT ya está registrado por otro cliente."))
        }

        // 2. Verificar duplicidad de Correo (BK-012)
        val correoFilter = idToExclude?.let {
            Filters.and(Cliente::correo eq correo, Filters.ne("_id", it))
        } ?: (Cliente::correo eq correo)

        if (collection.countDocuments(correoFilter) > 0) {
            errors.add(ValidationError("correo", "BK-012: El correo ya está registrado por otro cliente."))
        }

        return errors
    }

    /**
     * Valida y crea un nuevo cliente.
     */
    fun validateAndCreate(dto: ClienteDTO): Triple<Cliente?, List<ValidationError>, String?> {
        // --- 1. Validaciones BK-00X de formato y obligatoriedad ---
        try {
            Validation.requireNonEmpty(dto.nombre, "nombre") // BK-014
            Validation.requireNonEmpty(dto.apellido, "apellido") // BK-015
            Validation.requireNonEmpty(dto.direccion, "direccion") // BK-017

            Validation.requireValidRut(dto.rut, "rut") // BK-013
            Validation.requireValidEmail(dto.correo, "correo") // BK-012
            Validation.requireValidTelefono(dto.telefono, "telefono") // BK-016
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        // Desempaquetado seguro, garantizado por las validaciones de requireNonEmpty
        val nombre = dto.nombre!!
        val apellido = dto.apellido!!
        val rut = dto.rut!!
        val correo = dto.correo!!
        val telefono = dto.telefono!!
        val direccion = dto.direccion!!

        // --- 2. Control de Duplicidad (Unicidad) ---
        val duplicationErrors = checkDuplication(rut, correo)
        if (duplicationErrors.isNotEmpty()) {
            return Triple(null, duplicationErrors, "Errores de unicidad (RUT/Correo).")
        }

        // --- 3. Creación y Persistencia ---
        val newCliente = Cliente(
            nombre = nombre,
            apellido = apellido,
            rut = rut,
            correo = correo,
            telefono = telefono,
            direccion = direccion,
            activo = dto.activo ?: true // Usa el valor del DTO o true por defecto
        )

        collection.insertOne(newCliente)
        return Triple(newCliente, emptyList(), null)
    }

    /**
     * Actualiza un cliente existente por ID.
     */
    fun update(id: String, dto: ClienteDTO): Triple<Cliente?, List<ValidationError>, String?> {
        // 1. Validación de ID y existencia
        try {
            Validation.requireValidId(id, "ID de Cliente") // BK-037
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        val objectId = ObjectId(id)
        val existingCliente = collection.findOneById(objectId) ?: return Triple(null, emptyList(), "Cliente no encontrado.")

        // Se usa org.bson.conversions.Bson para la lista de updates
        val updateList = mutableListOf<Bson>()
        var newRut: String = existingCliente.rut
        var newCorreo: String = existingCliente.correo

        // 2. Validaciones de campos (solo si se proporcionan en el DTO)
        try {
            dto.nombre?.let {
                Validation.requireNonEmpty(it, "nombre")
                updateList.add(setValue(Cliente::nombre, it))
            }

            dto.apellido?.let {
                Validation.requireNonEmpty(it, "apellido")
                updateList.add(setValue(Cliente::apellido, it))
            }

            dto.direccion?.let {
                Validation.requireNonEmpty(it, "direccion")
                updateList.add(setValue(Cliente::direccion, it))
            }

            dto.rut?.let {
                Validation.requireValidRut(it, "rut")
                newRut = it
                updateList.add(setValue(Cliente::rut, it))
            }

            dto.correo?.let {
                Validation.requireValidEmail(it, "correo")
                newCorreo = it
                updateList.add(setValue(Cliente::correo, it))
            }

            dto.telefono?.let {
                Validation.requireValidTelefono(it, "telefono")
                updateList.add(setValue(Cliente::telefono, it))
            }

            dto.activo?.let {
                updateList.add(setValue(Cliente::activo, it))
            }
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        if (updateList.isEmpty()) return Triple(existingCliente, emptyList(), "No se proporcionaron datos para actualizar.")

        // 3. Control de Duplicidad (solo si RUT o Correo cambiaron)
        if (newRut != existingCliente.rut || newCorreo != existingCliente.correo) {
            val duplicationErrors = checkDuplication(newRut, newCorreo, objectId)
            if (duplicationErrors.isNotEmpty()) {
                return Triple(null, duplicationErrors, "Errores de unicidad (RUT/Correo).")
            }
        }

        // 4. Persistencia
        val result = collection.updateOneById(objectId, Updates.combine(updateList))
        return if (result.modifiedCount == 1L) {
            Triple(collection.findOneById(objectId), emptyList(), null)
        } else {
            Triple(null, emptyList(), "No se pudo actualizar el cliente.")
        }
    }

    /**
     * Obtiene todos los clientes activos.
     */
    fun findAllActive(): List<Cliente> = collection.find(Cliente::activo eq true).toList()

    /**
     * Busca un cliente por ID.
     */
    fun findById(id: String): Cliente? {
        return try {
            Validation.requireValidId(id, "ID de Cliente")
            collection.findOneById(ObjectId(id))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Realiza la eliminación lógica de un cliente (Soft Delete BK-007).
     */
    fun toggleActivo(id: String, activo: Boolean): Boolean {
        return try {
            Validation.requireValidId(id, "ID de Cliente") // BK-037
            val objectId = ObjectId(id)

            // BK-007: Eliminación lógica
            // La variable 'activo' en Updates.set(Cliente::activo, activo) se refiere al parámetro de la función.
            val result = collection.updateOneById(
                objectId,
                setValue(Cliente::activo, activo)
            )
            result.modifiedCount == 1L
        } catch (e: Exception) {
            false
        }
    }
}