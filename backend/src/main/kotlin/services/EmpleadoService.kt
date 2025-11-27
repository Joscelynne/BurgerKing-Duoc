package com.burgerking.duoc.services

import com.burgerking.duoc.dto.EmpleadoActivoUpdateDTO
import com.burgerking.duoc.dto.EmpleadoDTO
import com.burgerking.duoc.dto.ValidationError
import com.burgerking.duoc.models.Cliente
import com.burgerking.duoc.models.Empleado
import com.burgerking.duoc.models.EmpleadoRol
import com.burgerking.duoc.utils.Validation // Importación de la utilidad de validación
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.*

/**
 * Servicio para la entidad Empleado.
 * Centraliza la lógica de negocio, validación y acceso a MongoDB.
 */
class EmpleadoService(private val client: MongoClient) {
    private val database = client.getDatabase("burgerking_db") // Usando la misma DB
    private val collection: MongoCollection<Empleado> = database.getCollection<Empleado>("empleados")

    /**
     * Valida si el RUT ya está registrado por otro empleado (BK-018).
     * También valida si el correo ya está registrado por otro empleado.
     */
    private fun checkDuplication(rut: String, correo: String? = null, idToExclude: ObjectId? = null): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // 1. Verificar duplicidad de RUT (BK-018)
        val rutFilter = idToExclude?.let {
            Filters.and(Empleado::rut eq rut, Filters.ne("_id", it))
        } ?: (Empleado::rut eq rut)

        if (collection.countDocuments(rutFilter) > 0) {
            errors.add(ValidationError("rut", "BK-018: El RUT ya está registrado por otro empleado."))
        }

        // 2. Verificar duplicidad de Correo (solo si se proporciona y es único)
        if (!correo.isNullOrBlank()) {
            val correoFilter = idToExclude?.let {
                Filters.and(Empleado::correo eq correo, Filters.ne("_id", it))
            } ?: (Empleado::correo eq correo)

            if (collection.countDocuments(correoFilter) > 0) {
                errors.add(ValidationError("correo", "BK-035: El correo ya está registrado por otro empleado."))
            }
        }

        return errors
    }

    /**
     * Valida y crea un nuevo empleado.
     * @return Triple<Empleado?, List<ValidationError>, String?>
     */
    fun validateAndCreate(dto: EmpleadoDTO): Triple<Empleado?, List<ValidationError>, String?> {
        // --- 1. Validaciones BK-0XX de formato y obligatoriedad ---
        try {
            Validation.requireNonEmpty(dto.nombre, "nombre") // BK-019
            Validation.requireNonEmpty(dto.apellido, "apellido") // BK-020
            Validation.requireValidRut(dto.rut, "rut") // BK-018
            Validation.requireValidRol(dto.rol, "rol") // BK-032

            // Campos opcionales
            dto.telefono?.let { Validation.requireValidTelefono(it, "telefono") }
            dto.correo?.let { Validation.requireValidEmail(it, "correo") }

        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        // Desempaquetado seguro, garantizado por las validaciones de requireNonEmpty
        val nombre = dto.nombre!!
        val apellido = dto.apellido!!
        val rut = dto.rut!!
        val rol = dto.rol!!

        // --- 2. Control de Duplicidad ---
        val duplicationErrors = checkDuplication(rut, dto.correo)
        if (duplicationErrors.isNotEmpty()) {
            return Triple(null, duplicationErrors, "Errores de unicidad (RUT/Correo).")
        }

        // --- 3. Creación y Persistencia ---
        val newEmpleado = Empleado(
            nombre = nombre,
            apellido = apellido,
            rut = rut,
            rol = rol,
            telefono = dto.telefono,
            correo = dto.correo,
            direccion = dto.direccion,
            activo = dto.activo ?: true // BK-033
        )

        collection.insertOne(newEmpleado)
        return Triple(newEmpleado, emptyList(), null)
    }

    /**
     * Actualiza un empleado existente por ID.
     * @return Triple<Empleado?, List<ValidationError>, String?>
     */
    fun update(id: String, dto: EmpleadoDTO): Triple<Empleado?, List<ValidationError>, String?> {
        // 1. Validación de ID y existencia
        try {
            Validation.requireValidId(id, "ID de Empleado") // BK-037
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        val objectId = ObjectId(id)
        val existingEmpleado = collection.findOneById(objectId) ?: return Triple(null, emptyList(), "Empleado no encontrado.")

        val updateList = mutableListOf<Bson>()
        var newRut: String = existingEmpleado.rut
        var newCorreo: String? = existingEmpleado.correo

        // 2. Validaciones de campos (solo si se proporcionan)
        try {
            dto.nombre?.let {
                Validation.requireNonEmpty(it, "nombre")
                updateList.add(setValue(Cliente::nombre, it))
            }

            dto.apellido?.let {
                Validation.requireNonEmpty(it, "apellido") // BK-020
                updateList.add(setValue(Empleado::apellido, it))
            }

            dto.direccion?.let {
                Validation.requireNonEmpty(it, "direccion")
                updateList.add(setValue(Empleado::direccion, it))
            }

            dto.rut?.let {
                Validation.requireValidRut(it, "rut") // BK-018
                newRut = it
                updateList.add(setValue(Empleado::rut, it))
            }

            dto.rol?.let {
                Validation.requireValidRol(it, "rol") // BK-032
                updateList.add(setValue(Empleado::rol, it))
            }

            dto.correo?.let {
                Validation.requireValidEmail(it, "correo") // BK-012
                newCorreo = it
                updateList.add(setValue(Empleado::correo, it))
            }

            dto.telefono?.let {
                Validation.requireValidTelefono(it, "telefono") // BK-016
                updateList.add(setValue(Empleado::telefono, it))
            }

            dto.activo?.let {
                updateList.add(setValue(Empleado::activo, it)) // BK-033
            }
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        if (updateList.isEmpty()) return Triple(existingEmpleado, emptyList(), "No se proporcionaron datos para actualizar.")

        // 3. Control de Duplicidad (solo si RUT o Correo cambiaron)
        if (newRut != existingEmpleado.rut || newCorreo != existingEmpleado.correo) {
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
            Triple(null, emptyList(), "No se pudo actualizar el empleado.")
        }
    }

    /**
     * Obtiene todos los empleados. (Puede ser con filtro activo=true o todos)
     */
    fun findAll(activo: Boolean? = true): List<Empleado> {
        return if (activo != null) {
            collection.find(Empleado::activo eq activo).toList()
        } else {
            collection.find().toList()
        }
    }

    /**
     * Busca un empleado por ID.
     */
    fun findById(id: String): Empleado? {
        return try {
            Validation.requireValidId(id, "ID de Empleado")
            collection.findOneById(ObjectId(id))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Realiza la eliminación lógica de un empleado (Soft Delete BK-033).
     */
    fun toggleActivo(id: String, activo: Boolean): Boolean {
        return try {
            Validation.requireValidId(id, "ID de Empleado") // BK-037
            val objectId = ObjectId(id)

            // BK-033: Eliminación lógica
            val result = collection.updateOneById(
                objectId,
                setValue(Empleado::activo, activo)
            )
            result.modifiedCount == 1L
        } catch (e: Exception) {
            false
        }
    }
}