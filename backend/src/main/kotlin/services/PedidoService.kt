package com.burgerking.duoc.services

// --- Imports de paquetes internos ---
// Se hacen explícitos para mayor claridad y evitar conflictos tras la refactorización
import com.burgerking.duoc.dto.PedidoDTO
import com.burgerking.duoc.models.EstadoPedido
import com.burgerking.duoc.models.IProductoService // Necesario para la inyección por interfaz
import com.burgerking.duoc.models.MetodoPago
import com.burgerking.duoc.models.Pedido
import com.burgerking.duoc.models.Producto
import com.burgerking.duoc.models.ProductoPedido
import com.burgerking.duoc.utils.Validation
// ------------------------------------

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import org.bson.types.ObjectId
import org.litote.kmongo.*
import java.time.Instant

/**
 * Servicio para la entidad Pedido.
 * Contiene la lógica de negocio, incluyendo manejo de stock y cálculos de descuento/total.
 *
 * @property productoService Es crucial para la validación y actualización de stock (BK-030/031).
 */
class PedidoService(
    private val mongoClient: MongoClient,
    // CORRECCIÓN CLAVE: El constructor debe aceptar la interfaz, no la implementación concreta,
    // para que la inyección de Koin funcione con get<IProductoService>().
    private val productoService: IProductoService
) {
    private val database = mongoClient.getDatabase("burgerking_db")
    private val pedidoCollection: MongoCollection<Pedido> = database.getCollection("pedidos", Pedido::class.java)
    // El acceso directo a productoCollection debe seguir existiendo para las operaciones de stock.
    // Usamos productoCollection en lugar de productoService.getCollection()
    private val productoCollection: MongoCollection<Producto> = database.getCollection("productos", Producto::class.java)

    /**
     * Valida, calcula el total, actualiza el stock (BK-030/031) y crea el pedido.
     */
    fun create(dto: PedidoDTO): Pedido {
        // --- 1. Validaciones Generales BK-00X ---
        Validation.requireValidId(dto.clienteId!!, "clienteId") // BK-037
        Validation.requireNonEmpty(dto.direccionEntrega, "direccionEntrega") // BK-033
        Validation.requireNonEmpty(dto.metodoPago, "metodoPago")
        Validation.requireNonEmptyList(dto.productos, "productos") // BK-040

        val metodoPago = try {
            MetodoPago.valueOf(dto.metodoPago!!.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("BK-XXX: Método de pago inválido.")
        }

        // BK-022: Validar banco si aplica
        if (metodoPago != MetodoPago.EFECTIVO) {
            if (dto.banco.isNullOrBlank() || dto.banco !in Validation.VALID_BANKS) {
                throw IllegalArgumentException("BK-022: Banco inválido para el método de pago ${metodoPago.name}.")
            }
        }

        // --- 2. Validar y Obtener Productos + Stock (BK-030) ---
        // Mapa: [ID del producto] -> [Producto con precio y stock actual]
        val productosEnBD = mutableMapOf<ObjectId, Producto>()
        val productosPedido = mutableListOf<ProductoPedido>()
        var subtotal = 0.0

        for (item in dto.productos!!) {
            Validation.requireValidId(item.productoId, "productoId en lista")
            if (item.cantidad == null || item.cantidad <= 0) {
                throw IllegalArgumentException("BK-XXX: Cantidad de producto '${item.productoId}' debe ser positiva.")
            }

            val productoId = ObjectId(item.productoId)

            // Usamos findById de IProductoService para obtener el producto
            val producto = productoService.findById(item.productoId)
                ?: throw IllegalArgumentException("BK-037: Producto con ID '${item.productoId}' no encontrado.")

            // Si el producto no es nulo, lo agregamos al mapa para verificación de stock/precio
            productosEnBD[productoId] = producto

            // BK-015: Producto debe estar activo
            if (!producto.activo) {
                throw IllegalArgumentException("BK-015: Producto '${producto.nombre}' no está activo y no puede ser pedido.")
            }

            // BK-030: Validar Stock Suficiente
            if (producto.stock < item.cantidad) {
                throw IllegalArgumentException("BK-030: Stock insuficiente para el producto '${producto.nombre}'. Stock actual: ${producto.stock}, solicitado: ${item.cantidad}.")
            }

            // Guardar datos inmutables del producto en el pedido
            productosPedido.add(ProductoPedido(productoId, producto.nombre, producto.precio, item.cantidad))
            subtotal += producto.precio * item.cantidad
        }

        // --- 3. Cálculo de Descuento y Total (BK-021, BK-010) ---
        var descuento = 0.0
        if (metodoPago != MetodoPago.EFECTIVO && dto.banco == "Santander") {
            // Ejemplo de BK-021: Descuento del 10% para Banco Santander
            descuento = subtotal * 0.10
        }
        val totalFinal = subtotal - descuento

        // --- 4. Actualización de Stock (BK-031) y Creación de Pedido ---
        val session = mongoClient.startSession()
        session.use {
            // 4.1. Actualizar Stock
            // Nota: Aquí se usa la colección local de PedidoService, no es un método de IProductoService
            val bulkUpdates = productosPedido.map { item ->
                Updates.inc(Producto::stock.name, -item.cantidad)
            }
            productoCollection.updateMany(session, Filters.`in`("_id", productosEnBD.keys), bulkUpdates)

            // 4.2. Crear Pedido
            val newPedido = Pedido(
                clienteId = ObjectId(dto.clienteId),
                productos = productosPedido,
                metodoPago = metodoPago,
                banco = dto.banco,
                direccionEntrega = dto.direccionEntrega!!,
                descuento = descuento,
                total = totalFinal,
                fechaCreacion = Instant.now() // BK-011: Fecha de hoy
            )
            pedidoCollection.insertOne(session, newPedido)
            return newPedido
        }
    }

    /**
     * Actualiza el estado de un pedido (BK-023).
     */
    fun updateEstado(id: String, estado: String): Pedido? {
        Validation.requireValidId(id) // BK-037

        val newEstado = try {
            EstadoPedido.valueOf(estado.replace(" ", "_").uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("BK-023: Estado de pedido inválido. Valores permitidos: ${EstadoPedido.values().joinToString()}")
        }

        val result = pedidoCollection.updateOneById(
            ObjectId(id),
            set(Pedido::estado setTo newEstado)
        )
        return if (result.modifiedCount == 1L) pedidoCollection.findOneById(ObjectId(id)) else null
    }

    // ... findAll, findById, softDelete (similar a otros servicios)

    fun softDelete(id: String): UpdateResult {
        Validation.requireValidId(id) // BK-037
        // BK-007: Eliminación lógica
        return pedidoCollection.updateOneById(
            ObjectId(id),
            set(Pedido::activo setTo false)
        )
    }

    fun findAll(): List<Pedido> {
        return pedidoCollection.find(Pedido::activo eq true).toList()
    }
}