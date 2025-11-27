package com.burgerking.duoc.utils

import com.burgerking.duoc.models.EmpleadoRol // Importar el enum de roles
import org.bson.types.ObjectId
import java.time.LocalDate
import java.util.regex.Pattern

/**
 * Objeto con funciones de utilidad estáticas y reglas de validación comunes para el backend BK-00X.
 * Lanza IllegalArgumentException si la validación falla, conteniendo la clave BK.
 */
object Validation {

    // BK-006: Categorías válidas (Ejemplo)
    val VALID_CATEGORIES = setOf("Hamburguesas", "Bebidas", "Acompañamientos", "Postres")
    // BK-022: Bancos válidos para descuento (Ejemplo)
    val VALID_BANKS = setOf("Santander", "Chile", "BCI", "Estado")
    // BK-023: Estados válidos para pedidos (Se usa el enum, pero mantenemos esta lista por si acaso)
    val VALID_ESTADOS_PEDIDO = setOf("Pendiente", "En Preparación", "Listo para Retiro", "Entregado", "Cancelado")

    /**
     * Valida el formato de ObjectId de MongoDB. (BK-037)
     */
    private fun isValidObjectId(id: String): Boolean {
        return try {
            ObjectId.isValid(id)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Asegura que el ID sea un ObjectId válido, sino lanza una excepción BK-037.
     */
    fun requireValidId(id: String?, fieldName: String = "ID") {
        if (id.isNullOrBlank()) {
            throw IllegalArgumentException("BK-037: El $fieldName es obligatorio y no puede estar vacío.")
        }
        if (!isValidObjectId(id)) {
            throw IllegalArgumentException("BK-037: El $fieldName proporcionado '$id' no tiene un formato válido de MongoDB ObjectId.")
        }
    }

    /**
     * Valida que un valor String no sea nulo ni esté vacío (incluyendo solo espacios). (BK-002, BK-014, BK-015, BK-017, BK-019, BK-020)
     */
    fun requireNonEmpty(value: String?, fieldName: String) {
        if (value.isNullOrBlank()) {
            // Usamos el código específico de error si es conocido
            val errorCode = when (fieldName) {
                "nombre" -> "BK-014/BK-019"
                "apellido" -> "BK-015/BK-020"
                "direccion" -> "BK-017"
                else -> "BK-00X"
            }
            throw IllegalArgumentException("$errorCode: El campo '$fieldName' es obligatorio y no puede estar vacío.")
        }
    }

    /**
     * Valida que un valor Double no sea nulo y sea estrictamente positivo ( > 0 ). (BK-001)
     */
    fun requirePositiveDouble(value: Double?, fieldName: String) {
        if (value == null) {
            throw IllegalArgumentException("BK-00X: El campo '$fieldName' es obligatorio y no puede ser nulo.")
        }
        if (value <= 0.0) {
            throw IllegalArgumentException("BK-001: El '$fieldName' debe ser un valor positivo (mayor que cero).")
        }
    }

    /**
     * Valida que un valor Int no sea nulo y sea no negativo ( >= 0 ). (BK-005)
     */
    fun requireNonNegativeInt(value: Int?, fieldName: String) {
        if (value == null) {
            throw IllegalArgumentException("BK-00X: El campo '$fieldName' es obligatorio y no puede ser nulo.")
        }
        if (value < 0) {
            throw IllegalArgumentException("BK-005: El '$fieldName' debe ser un valor no negativo (mayor o igual a cero).")
        }
    }

    // --- Funciones de Validación de Cliente/Empleado ---

    /**
     * Valida si la cadena es un email válido. (BK-012)
     */
    private fun isValidEmail(email: String): Boolean {
        return email.matches(Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$"))
    }

    /**
     * Asegura que la cadena sea un email válido. (BK-012)
     */
    fun requireValidEmail(email: String?, fieldName: String) {
        requireNonEmpty(email, fieldName) // Comprueba que no es nulo/vacío
        if (email != null && !isValidEmail(email)) {
            throw IllegalArgumentException("BK-012: El '$fieldName' no tiene un formato de correo electrónico válido.")
        }
    }

    /**
     * Valida si la cadena es un teléfono de 9 dígitos. (BK-016)
     */
    private fun isValidTelefono(telefono: String): Boolean {
        // Asume formato simple de 9 dígitos.
        return telefono.matches(Regex("^\\d{9}$"))
    }

    /**
     * Asegura que la cadena sea un teléfono de 9 dígitos. (BK-016)
     */
    fun requireValidTelefono(telefono: String?, fieldName: String) {
        requireNonEmpty(telefono, fieldName) // Comprueba que no es nulo/vacío
        if (telefono != null && !isValidTelefono(telefono)) {
            throw IllegalArgumentException("BK-016: El '$fieldName' debe contener 9 dígitos numéricos.")
        }
    }

    /**
     * Lógica de Módulo 11 para validar el RUT chileno.
     */
    private fun checkModule11(rutClean: String): Boolean {
        val (num, dvStr) = rutClean.replace(".", "").split("-")
        val dv = dvStr.lowercase()
        val numClean = num.replace(".", "")

        var sum = 0
        var factor = 2

        for (i in numClean.length - 1 downTo 0) {
            sum += numClean[i].toString().toInt() * factor
            factor = if (factor % 7 == 0) 2 else factor + 1
        }

        val calculatedDv = 11 - (sum % 11)
        val expectedDv = when (calculatedDv) {
            11 -> "0"
            10 -> "k"
            else -> calculatedDv.toString()
        }

        return expectedDv == dv
    }

    /**
     * Valida que el RUT chileno tenga el formato XX.XXX.XXX-X (Ejemplo) y pase Módulo 11. (BK-013, BK-018)
     */
    fun requireValidRut(rut: String?, fieldName: String) {
        requireNonEmpty(rut, fieldName) // Comprueba que no es nulo/vacío

        // Verifica formato: XX.XXX.XXX-X
        val rutPattern = Pattern.compile("^\\d{1,2}\\.\\d{3}\\.\\d{3}-([0-9Kk])\$")
        if (rut != null && !rutPattern.matcher(rut).matches()) {
            throw IllegalArgumentException("BK-013/BK-018: El '$fieldName' no tiene el formato esperado (XX.XXX.XXX-X).")
        }

        // Verifica Módulo 11
        if (rut != null && !checkModule11(rut)) {
            throw IllegalArgumentException("BK-013/BK-018: El '$fieldName' es un RUT inválido (falló el Módulo 11).")
        }
    }

    /**
     * Asegura que el rol proporcionado sea uno de los definidos en EmpleadoRol. (BK-032)
     */
    fun requireValidRol(rol: String?, fieldName: String) {
        requireNonEmpty(rol, fieldName)
        if (rol != null) {
            val validRoles = EmpleadoRol.entries.map { it.value }
            if (!validRoles.contains(rol)) {
                throw IllegalArgumentException("BK-032: El '$fieldName' es inválido. Debe ser uno de: ${validRoles.joinToString(", ")}.")
            }
        }
    }

    // --- Funciones de Validación Generales ---

    /**
     * Valida que la fecha sea igual o anterior al día de hoy. (BK-011)
     */
    fun requireDateIsTodayOrPast(date: LocalDate, fieldName: String) {
        if (date > LocalDate.now()) {
            throw IllegalArgumentException("BK-011: La fecha del '$fieldName' no puede ser posterior al día de hoy.")
        }
    }

    /**
     * Valida si una lista no es nula ni vacía. (BK-040)
     */
    fun requireNonEmptyList(list: List<*>?, fieldName: String) {
        if (list.isNullOrEmpty()) {
            throw IllegalArgumentException("BK-040: La lista '$fieldName' no puede estar vacía. Se requiere al menos un elemento.")
        }
    }
}