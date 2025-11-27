package com.burgerking.duoc.services

import com.burgerking.duoc.dto.ComboActivoUpdateDTO
import com.burgerking.duoc.dto.ComboDTO
import com.burgerking.duoc.dto.ValidationError
import com.burgerking.duoc.models.Combo
import com.burgerking.duoc.models.IProductoService
import com.burgerking.duoc.utils.Validation
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.*
import java.time.Instant

/**
 * Servicio para la entidad Combo.
 * Contiene validaciones BK-0XX, lógica de negocio y acceso a MongoDB.
 */
class ComboService(client: MongoClient, private val productoService: IProductoService) {

    private val database = client.getDatabase("burgerking_db")
    private val collection: MongoCollection<Combo> =
        database.getCollection("combos", Combo::class.java)

    private val DISCOUNT_RATE = 0.9

    // ---------------------------------------------------------------------
    // VALIDACIÓN DE UNICIDAD BK-009
    // ---------------------------------------------------------------------
    private fun checkDuplication(nombre: String, idToExclude: ObjectId? = null): List<ValidationError> {

        val filter = if (idToExclude != null) {
            and(Combo::nombre eq nombre, Combo::id ne idToExclude)
        } else {
            Combo::nombre eq nombre
        }

        return if (collection.countDocuments(filter) > 0) {
            listOf(ValidationError("nombre", "BK-009: El nombre del combo ya existe."))
        } else emptyList()
    }

    // ---------------------------------------------------------------------
    // VALIDAR IDs DE PRODUCTOS Y CALCULAR PRECIO BK-008 / BK-040 / BK-037
    // ---------------------------------------------------------------------
    private fun validateProductsAndCalculatePrice(productosIdsStr: List<String>): Triple<Double?, List<ValidationError>, String?> {

        val errors = mutableListOf<ValidationError>()

        // BK-040: lista obligatoria
        if (productosIdsStr.isEmpty()) {
            errors.add(ValidationError("productosIds", "BK-040: La lista de productos no puede estar vacía."))
            return Triple(null, errors, "Faltan productos obligatorios.")
        }

        // Validar formato ObjectId
        val objectIds = productosIdsStr.mapNotNull {
            try { ObjectId(it) }
            catch (_: Exception) {
                errors.add(ValidationError("productosIds", "BK-037: ID de producto '$it' tiene formato inválido."))
                null
            }
        }

        if (errors.isNotEmpty()) return Triple(null, errors, "Errores de formato de ID.")

        // Validar existencia
        val productos = productoService.findByIds(objectIds)
        val encontrados = productos.map { it._id }.toSet()

        val faltantes = objectIds.filter { it !in encontrados }
        if (faltantes.isNotEmpty()) {
            faltantes.forEach {
                errors.add(ValidationError("productosIds", "BK-037: El producto '$it' no existe en la base de datos."))
            }
            return Triple(null, errors, "Productos no encontrados.")
        }

        // Validar que estén activos
        val inactivos = productos.filter { !it.activo }
        if (inactivos.isNotEmpty()) {
            errors.add(ValidationError("productosIds", "Los productos ${inactivos.map { it._id }} están inactivos."))
            return Triple(null, errors, "Productos inactivos.")
        }

        // BK-008 — calcular precio con descuento
        val suma = productos.sumOf { it.precio }
        val precioFinal = String.format("%.2f", suma * DISCOUNT_RATE).toDouble()

        if (precioFinal <= 0) {
            errors.add(ValidationError("precio", "BK-008: El precio calculado ($precioFinal) es inválido."))
            return Triple(null, errors, "Error en cálculo del precio.")
        }

        return Triple(precioFinal, emptyList(), null)
    }

    // ---------------------------------------------------------------------
    // CREAR COMBO BK-009 / BK-040 / BK-008 / BK-037
    // ---------------------------------------------------------------------
    fun validateAndCreate(dto: ComboDTO): Triple<Combo?, List<ValidationError>, String?> {

        try {
            Validation.requireNonEmpty(dto.nombre, "nombre")
            Validation.requireNonEmptyList(dto.productosIds, "productosIds")
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        val nombre = dto.nombre!!

        // VALIDACIÓN EXTRA — evitar cadenas vacías o espacios
        if (nombre.isBlank()) {
            return Triple(null, listOf(ValidationError("nombre", "BK-009: El nombre no puede estar vacío.")), "Nombre inválido.")
        }

        val productosIdsStr = dto.productosIds!!

        // Duplicidad de nombre
        val duplication = checkDuplication(nombre)
        if (duplication.isNotEmpty()) {
            return Triple(null, duplication, "Errores de unicidad (Nombre).")
        }

        // Validar productos y precio
        val (precioCalc, prodErrors, prodMsg) = validateProductsAndCalculatePrice(productosIdsStr)
        if (precioCalc == null) {
            return Triple(null, prodErrors, prodMsg)
        }

        val now = Instant.now()
        val combo = Combo(
            nombre = nombre,
            productosIds = productosIdsStr.map { ObjectId(it) },
            precio = precioCalc,
            descripcion = dto.descripcion,
            activo = dto.activo ?: true,
            createdAt = now,
            updatedAt = now
        )

        collection.insertOne(combo)
        return Triple(combo, emptyList(), null)
    }

    // ---------------------------------------------------------------------
    // UPDATE COMPLETO BK-009 / BK-008 / BK-037 / BK-040
    // ---------------------------------------------------------------------
    fun update(id: String, dto: ComboDTO): Triple<Combo?, List<ValidationError>, String?> {

        try {
            Validation.requireValidId(id, "ID de Combo")
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        val objectId = ObjectId(id)
        val existing = collection.findOneById(objectId)
            ?: return Triple(null, emptyList(), "Combo no encontrado.")

        val updates = mutableListOf<Bson>()

        var nuevoNombre = existing.nombre
        var nuevosProductosStr = existing.productosIds.map { it.toHexString() }
        var recalcularPrecio = false

        // Nombre
        dto.nombre?.let {
            if (it.isBlank()) {
                return Triple(null, listOf(ValidationError("nombre", "BK-009: El nombre no puede estar vacío.")), "Nombre inválido.")
            }
            nuevoNombre = it
            updates.add(Updates.set(Combo::nombre.name, it))
        }

        // Descripción
        dto.descripcion?.let {
            updates.add(Updates.set(Combo::descripcion.name, it))
        }

        // Productos
        dto.productosIds?.let {
            Validation.requireNonEmptyList(it, "productosIds")
            nuevosProductosStr = it
            recalcularPrecio = true
        }

        // Activo
        dto.activo?.let {
            updates.add(Updates.set(Combo::activo.name, it))
        }

        // Duplicidad de nombre
        if (nuevoNombre != existing.nombre) {
            val dup = checkDuplication(nuevoNombre, objectId)
            if (dup.isNotEmpty()) {
                return Triple(null, dup, "Errores de unicidad (Nombre).")
            }
        }

        // Recalcular precio si los productos cambiaron
        if (recalcularPrecio) {
            val (precioCalc, prodErrors, prodMsg) = validateProductsAndCalculatePrice(nuevosProductosStr)
            if (precioCalc == null) {
                return Triple(null, prodErrors, prodMsg)
            }

            updates.add(Updates.set(Combo::precio.name, precioCalc))
            updates.add(Updates.set(Combo::productosIds.name, nuevosProductosStr.map { ObjectId(it) }))
        }

        updates.add(Updates.set(Combo::updatedAt.name, Instant.now()))

        val result = collection.updateOneById(objectId, Updates.combine(updates))

        return if (result.modifiedCount == 1L) {
            Triple(collection.findOneById(objectId), emptyList(), null)
        } else {
            Triple(null, emptyList(), "No se pudo actualizar el combo.")
        }
    }

    // ---------------------------------------------------------------------
    // FINDERS
    // ---------------------------------------------------------------------
    fun findAll(activo: Boolean? = true): List<Combo> {
        return if (activo != null) collection.find(Combo::activo eq activo).toList()
        else collection.find().toList()
    }

    fun findById(id: String): Combo? {
        return try {
            Validation.requireValidId(id, "ID de Combo")
            collection.findOneById(ObjectId(id))
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------
    // SOFT DELETE BK-007
    // ---------------------------------------------------------------------
    fun toggleActivo(id: String, activo: Boolean): UpdateResult {
        Validation.requireValidId(id, "ID de Combo")
        val oid = ObjectId(id)

        return collection.updateOneById(
            oid,
            Updates.combine(
                Updates.set(Combo::activo.name, activo),
                Updates.set(Combo::updatedAt.name, Instant.now())
            )
        )
    }
}
