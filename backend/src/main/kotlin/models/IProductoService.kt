package com.burgerking.duoc.models

import com.burgerking.duoc.models.Producto
import org.bson.types.ObjectId

/**
 * Interfaz que define las operaciones esenciales de solo lectura para la entidad Producto.
 * Esto permite que otros servicios (como ComboService o PedidoService) accedan a los
 * datos de Producto sin tener una dependencia directa de la implementación concreta (ProductoService).
 */
interface IProductoService {
    /**
     * Busca un producto por su ID.
     */
    fun findById(id: String): Producto?

    /**
     * Busca una lista de productos por sus ObjectIds.
     * Esta función es crucial para el cálculo del precio del Combo (BK-008).
     */
    fun findByIds(ids: List<ObjectId>): List<Producto>

    // Puedes agregar aquí otras funciones de solo lectura si son necesarias para otros servicios,
    // pero evitamos las operaciones de escritura (create, update, softDelete).
}