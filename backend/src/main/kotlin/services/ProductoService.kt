package com.burgerking.duoc.services

import com.burgerking.duoc.dto.ProductoDTO
import com.burgerking.duoc.models.Producto
import com.burgerking.duoc.utils.Validation
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import com.burgerking.duoc.models.IProductoService
import org.bson.types.ObjectId
import org.litote.kmongo.*
import java.time.Instant

/**
 * Servicio para la entidad Producto.
 * Implementa IProductoService para permitir la inyección de dependencia en otros servicios (como ComboService).
 * Maneja la lógica de negocio para la gestión de productos, incluyendo validaciones BK-00X y auditoría.
 */
class ProductoService(private val mongoClient: MongoClient) : IProductoService {
    private val database = mongoClient.getDatabase("burgerking_db")
    // Usamos la sintaxis genérica de KMongo
    private val productoCollection: MongoCollection<Producto> = database.getCollection<Producto>("productos")

    /**
     * Valida y crea un nuevo producto en la base de datos.
     * Aplica validaciones BK-001, BK-002, BK-005, BK-006, BK-004.
     */
    fun create(dto: ProductoDTO): Producto {
        // --- 1. Validaciones BK-00X ---
        Validation.requireNonEmpty(dto.nombre, "nombre") // BK-002
        Validation.requirePositiveDouble(dto.precio, "precio") // BK-001
        Validation.requireNonNegativeInt(dto.stock, "stock") // BK-005
        Validation.requireNonEmpty(dto.categoria, "categoria") // BK-006

        // BK-004: Validar duplicados por nombre
        if (productoCollection.countDocuments(Producto::nombre eq dto.nombre) > 0) {
            throw IllegalArgumentException("BK-004: Ya existe un producto con el nombre '${dto.nombre}'.")
        }

        // --- 2. Creación del Modelo y Persistencia ---
        val now = Instant.now() // <--- DESCOMENTADO: Valor para auditoría
        val newProducto = Producto(
            nombre = dto.nombre!!,
            precio = dto.precio!!,
            stock = dto.stock!!,
            categoria = dto.categoria!!,
            descripcion = dto.descripcion,
            activo = dto.activo ?: true, // CORREGIDO: 'activo' existe en DTO
            createdAt = now, // <--- DESCOMENTADO: Campo de auditoría
            updatedAt = now // <--- DESCOMENTADO: Campo de auditoría
        )
        productoCollection.insertOne(newProducto)
        return newProducto
    }

    /**
     * Actualiza un producto existente por su ID.
     * Mantiene las validaciones BK-00X y evita la duplicidad de nombre.
     */
    fun update(id: String, dto: ProductoDTO): Producto? {
        Validation.requireValidId(id, "ID de Producto") // BK-037
        val objectId = ObjectId(id)

        // Buscar el producto existente para validaciones
        val existingProducto = productoCollection.findOneById(objectId)
            ?: return null

        // --- 1. Validaciones BK-00X para campos a actualizar ---

        val updateList = mutableListOf<org.bson.conversions.Bson>()

        dto.nombre?.let {
            Validation.requireNonEmpty(it, "nombre") // BK-002
            // BK-004: Validar duplicados por nombre, excluyendo el producto actual
            if (productoCollection.countDocuments(Filters.and(Producto::nombre eq it, Producto::_id ne objectId)) > 0) {
                throw IllegalArgumentException("BK-004: Ya existe otro producto con el nombre '$it'.")
            }
            updateList.add(set(Producto::nombre setTo it))
        }

        dto.precio?.let {
            Validation.requirePositiveDouble(it, "precio") // BK-001
            updateList.add(set(Producto::precio setTo it))
        }

        dto.stock?.let {
            Validation.requireNonNegativeInt(it, "stock") // BK-005
            updateList.add(set(Producto::stock setTo it))
        }

        dto.categoria?.let {
            Validation.requireNonEmpty(it, "categoria") // BK-006
            updateList.add(set(Producto::categoria setTo it))
        }

        dto.descripcion?.let {
            updateList.add(set(Producto::descripcion setTo it))
        }

        dto.activo?.let {
            updateList.add(set(Producto::activo setTo it)) // CORREGIDO: Ahora 'activo' existe en DTO
        }

        if (updateList.isEmpty()) return existingProducto // No hay cambios

        // --- 2. Persistencia ---
        updateList.add(set(Producto::updatedAt setTo Instant.now())) // <--- DESCOMENTADO: Campo de auditoría

        val result = productoCollection.updateOneById(
            objectId,
            Updates.combine(updateList)
        )

        // Si se modificó, devolver el producto actualizado.
        return if (result.modifiedCount == 1L) productoCollection.findOneById(objectId) else existingProducto
    }

    /**
     * Realiza la eliminación lógica de un producto (Soft Delete BK-003).
     */
    fun softDelete(id: String): UpdateResult {
        Validation.requireValidId(id, "ID de Producto") // BK-037
        // BK-003: Eliminación lógica
        return productoCollection.updateOneById(
            ObjectId(id),
            set(Producto::activo setTo false, Producto::updatedAt setTo Instant.now()) // <--- DESCOMENTADO: Campo de auditoría
        )
    }

    /**
     * Obtiene todos los productos (sin filtrar por activo).
     */
    fun findAll(): List<Producto> {
        return productoCollection.find().toList()
    }

    /**
     * Busca un producto por ID.
     * @inheritdoc
     */
    override fun findById(id: String): Producto? {
        // No lanzamos excepción aquí, solo devolvemos null si el ID es inválido.
        return try {
            Validation.requireValidId(id, "ID de Producto")
            productoCollection.findOneById(ObjectId(id))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Busca una lista de productos por sus ObjectIds.
     * Crucial para validar y calcular el precio de un Combo (BK-008).
     * @inheritdoc
     */
    override fun findByIds(ids: List<ObjectId>): List<Producto> {
        if (ids.isEmpty()) return emptyList()
        // CORRECCIÓN: Usamos Producto::_id que es el campo real en tu modelo
        // Buscar por múltiples IDs usando el operador 'in' de MongoDB
        return productoCollection.find(Producto::_id `in` ids).toList()
    }
}