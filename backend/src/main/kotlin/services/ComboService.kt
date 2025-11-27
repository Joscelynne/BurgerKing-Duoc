package com.burgerking.duoc.services

import com.burgerking.duoc.dto.ComboActivoUpdateDTO
import com.burgerking.duoc.dto.ComboDTO
import com.burgerking.duoc.dto.ValidationError
import com.burgerking.duoc.models.Combo
import com.burgerking.duoc.models.IProductoService
import com.burgerking.duoc.models.Producto
import com.burgerking.duoc.utils.Validation
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Updates // <<-- IMPORTACIÓN NECESARIA para Updates.combine/set
import com.mongodb.client.result.UpdateResult
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.*
import org.litote.kmongo.eq
import java.time.Instant // <<-- IMPORTACIÓN NECESARIA para Instant.now()
import org.litote.kmongo.and
import org.litote.kmongo.ne // Para usar Combo::id ne idToExclude

/**
 * Servicio para la entidad Combo.
 * Centraliza la lógica de negocio, validación y acceso a MongoDB.
 *
 * Se inyecta IProductoService para validaciones cruzadas.
 */
class ComboService(client: MongoClient, private val productoService: IProductoService) {
    private val database = client.getDatabase("burgerking_db")
    private val collection: MongoCollection<Combo> = database.getCollection<Combo>("combos")
    private val DISCOUNT_RATE = 0.9 // 10% de descuento (precio final es 90% de la suma)

    /**
     * Valida la unicidad del nombre del Combo (BK-009).
     */
    private fun checkDuplication(nombre: String, idToExclude: ObjectId? = null): List<ValidationError> {
        // CORRECCIÓN: Usamos la sintaxis limpia de KMongo 'and' y 'ne' para filtros
        val filter = if (idToExclude != null) {
            and(Combo::nombre eq nombre, Combo::id ne idToExclude)
        } else {
            Combo::nombre eq nombre
        }

        // CORRECCIÓN: collection.countDocuments(filter) ahora funciona correctamente.
        return if (collection.countDocuments(filter) > 0) {
            listOf(ValidationError("nombre", "BK-009: El nombre del combo ya existe."))
        } else {
            emptyList()
        }
    }

    /**
     * Valida los productos asociados y calcula el precio (BK-008, BK-037, BK-040).
     * @return Triple<PrecioCalculado, ListaErrores, MensajeError>
     */
    private fun validateProductsAndCalculatePrice(productosIdsStr: List<String>): Triple<Double?, List<ValidationError>, String?> {
        val validationErrors = mutableListOf<ValidationError>()

        // 1. Validar lista no vacía (BK-040)
        if (productosIdsStr.isEmpty()) {
            validationErrors.add(ValidationError("productosIds", "BK-040: La lista de productos no puede estar vacía."))
            return Triple(null, validationErrors, "Faltan productos obligatorios.")
        }

        // CORRECCIÓN: Reemplazamos la llamada a Validation.isValidObjectId (que es privado)
        // por un try-catch al construir el ObjectId, que es la forma estándar de validar formato.
        val objectIds = productosIdsStr.mapNotNull {
            try {
                ObjectId(it)
            } catch (e: Exception) {
                // BK-037: ID de producto con formato inválido.
                validationErrors.add(ValidationError("productosIds", "BK-037: ID de producto '$it' con formato inválido."))
                null
            }
        }

        if (validationErrors.isNotEmpty()) return Triple(null, validationErrors, "Errores de formato de ID.")

        // 2. Validar existencia de IDs y obtener precios (BK-037)
        val productosExistentes = productoService.findByIds(objectIds)

        if (productosExistentes.size != objectIds.size) {
            // CORRECCIÓN: Usamos it._id, que es el campo real en el modelo Producto.
            val foundIds = productosExistentes.map { it._id }.toSet()
            val missingIds = objectIds.filter { it !in foundIds }
            missingIds.forEach {
                validationErrors.add(ValidationError("productosIds", "BK-037: El ID de producto '$it' no existe en la base de datos."))
            }
            return Triple(null, validationErrors, "Productos referenciados no encontrados.")
        }

        // 3. Validar productos activos (asunción: solo productos activos pueden formar un combo)
        val inactiveProducts = productosExistentes.filter { !it.activo }
        if (inactiveProducts.isNotEmpty()) {
            // CORRECCIÓN: Usamos it._id, que es el campo real en el modelo Producto.
            validationErrors.add(ValidationError("productosIds", "Productos con IDs ${inactiveProducts.map { it._id }} están inactivos y no pueden formar parte de un combo."))
            return Triple(null, validationErrors, "Productos inactivos en la lista.")
        }

        // 4. Calcular precio y aplicar descuento (BK-008)
        val sumPrecio = productosExistentes.sumOf { it.precio }
        // Redondear a dos decimales
        val calculatedPrecio = String.format("%.2f", sumPrecio * DISCOUNT_RATE).toDouble()

        // El precio calculado (BK-008) debe ser positivo (implícito si los precios base son positivos BK-001)
        if (calculatedPrecio <= 0.0) {
            validationErrors.add(ValidationError("precio", "BK-008: El precio calculado ($calculatedPrecio) es inválido."))
            return Triple(null, validationErrors, "Error en el cálculo del precio.")
        }

        return Triple(calculatedPrecio, emptyList(), null)
    }

    /**
     * Crea un nuevo combo.
     * @return Triple<Combo?, List<ValidationError>, String?>
     */
    fun validateAndCreate(dto: ComboDTO): Triple<Combo?, List<ValidationError>, String?> {
        // --- 1. Validaciones BK-0XX de formato y obligatoriedad ---
        try {
            Validation.requireNonEmpty(dto.nombre, "nombre") // BK-009
            Validation.requireNonEmptyList(dto.productosIds, "productosIds") // BK-040
            // La descripción es opcional, no necesita validación de formato aquí.
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        val nombre = dto.nombre!!
        val productosIdsStr = dto.productosIds!!

        // --- 2. Control de Duplicidad (Nombre) ---
        val duplicationErrors = checkDuplication(nombre)
        if (duplicationErrors.isNotEmpty()) {
            return Triple(null, duplicationErrors, "Errores de unicidad (Nombre).")
        }

        // --- 3. Validación Cruzada de Productos y Cálculo de Precio ---
        val (precioCalculado, productErrors, productErrorMsg) = validateProductsAndCalculatePrice(productosIdsStr)
        if (precioCalculado == null) {
            return Triple(null, productErrors, productErrorMsg)
        }

        // --- 4. Creación y Persistencia ---
        // CORRECCIÓN: Inicialización de createdAt y updatedAt con Instant.now()
        val now = Instant.now()
        val newCombo = Combo(
            nombre = nombre,
            productosIds = productosIdsStr.map { ObjectId(it) },
            precio = precioCalculado,
            descripcion = dto.descripcion,
            activo = dto.activo ?: true, // BK-007
            createdAt = now,
            updatedAt = now
        )

        collection.insertOne(newCombo)
        return Triple(newCombo, emptyList(), null)
    }

    /**
     * Actualiza un combo existente por ID.
     * @return Triple<Combo?, List<ValidationError>, String?>
     */
    fun update(id: String, dto: ComboDTO): Triple<Combo?, List<ValidationError>, String?> {
        // 1. Validación de ID y existencia
        try {
            Validation.requireValidId(id, "ID de Combo") // BK-037
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        val objectId = ObjectId(id)
        val existingCombo = collection.findOneById(objectId) ?: return Triple(null, emptyList(), "Combo no encontrado.")

        val updateList = mutableListOf<Bson>()
        var newNombre: String = existingCombo.nombre
        var newPrecio: Double = existingCombo.precio
        var newProductosIdsStr: List<String> = existingCombo.productosIds.map { it.toHexString() }
        var needsPriceRecalculation = false

        // 2. Validaciones de campos (solo si se proporcionan)
        try {
            dto.nombre?.let {
                Validation.requireNonEmpty(it, "nombre") // BK-009
                newNombre = it
                updateList.add(Updates.set(Combo::nombre.name, it))
            }

            dto.descripcion?.let {
                // No es obligatorio, solo se actualiza si viene
                updateList.add(Updates.set(Combo::descripcion.name, it))
            }

            dto.productosIds?.let {
                Validation.requireNonEmptyList(it, "productosIds") // BK-040
                newProductosIdsStr = it
                // Esto siempre obliga a recalcular el precio
                needsPriceRecalculation = true
            }

            dto.activo?.let {
                updateList.add(Updates.set(Combo::activo.name, it)) // BK-007
            }
        } catch (e: IllegalArgumentException) {
            return Triple(null, emptyList(), e.message)
        }

        if (updateList.isEmpty() && !needsPriceRecalculation) return Triple(existingCombo, emptyList(), "No se proporcionaron datos para actualizar.")

        // 3. Control de Duplicidad (solo si Nombre cambió)
        if (newNombre != existingCombo.nombre) {
            val duplicationErrors = checkDuplication(newNombre, objectId)
            if (duplicationErrors.isNotEmpty()) {
                return Triple(null, duplicationErrors, "Errores de unicidad (Nombre).")
            }
        }

        // 4. Recalcular precio si los productos cambiaron
        if (needsPriceRecalculation) {
            val (calculatedPrice, productErrors, productErrorMsg) = validateProductsAndCalculatePrice(newProductosIdsStr)

            if (calculatedPrice == null) {
                return Triple(null, productErrors, productErrorMsg)
            }

            newPrecio = calculatedPrice
            updateList.add(Updates.set(Combo::precio.name, calculatedPrice))
            updateList.add(Updates.set(Combo::productosIds.name, newProductosIdsStr.map { ObjectId(it) }))
        }

        // 5. Persistencia
        // CORRECCIÓN: Usamos Updates.set, Instant.now() y la importación de Updates.
        updateList.add(Updates.set(Combo::updatedAt.name, Instant.now()))

        // CORRECCIÓN: Updates.combine ahora está importado y funciona
        val result = collection.updateOneById(objectId, Updates.combine(updateList))

        return if (result.modifiedCount == 1L) {
            Triple(collection.findOneById(objectId), emptyList(), null)
        } else {
            Triple(null, emptyList(), "No se pudo actualizar el combo.")
        }
    }

    /**
     * Obtiene todos los combos activos o todos.
     */
    fun findAll(activo: Boolean? = true): List<Combo> {
        return if (activo != null) {
            collection.find(Combo::activo eq activo).toList()
        } else {
            collection.find().toList()
        }
    }

    /**
     * Busca un combo por ID.
     */
    fun findById(id: String): Combo? {
        return try {
            Validation.requireValidId(id, "ID de Combo")
            collection.findOneById(ObjectId(id))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Realiza la eliminación lógica de un combo (Soft Delete BK-007).
     *
     * @param id El ID del combo.
     * @param activo El nuevo estado (false para inactivar / soft delete).
     * @return UpdateResult de la operación.
     */
    fun toggleActivo(id: String, activo: Boolean): UpdateResult {
        try {
            Validation.requireValidId(id, "ID de Combo") // BK-037
            val objectId = ObjectId(id)

            // BK-007: Eliminación lógica y actualización de updatedAt
            return collection.updateOneById(
                objectId,
                Updates.combine(
                    Updates.set(Combo::activo.name, activo),
                    Updates.set(Combo::updatedAt.name, Instant.now())
                )
            )
        } catch (e: IllegalArgumentException) {
            throw e
        }
    }
}