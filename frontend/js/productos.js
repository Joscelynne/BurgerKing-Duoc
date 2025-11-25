const API_URL = 'http://localhost:8080/api/productos';
const imgPlaceholder = 'https://placehold.co/400x180/442200/fff8ec?text=BK-DUOC';

document.addEventListener('DOMContentLoaded', () => {
    fetchProducts();
    document.getElementById('productForm').addEventListener('submit', handleFormSubmit);
    document.getElementById('resetBtn').addEventListener('click', resetForm);
});

// Función para mostrar mensajes de feedback (errores/éxito)
function showFeedback(message, isError = true) {
    const feedbackEl = document.getElementById('feedback');
    feedbackEl.textContent = message;
    feedbackEl.className = isError ? 'feedback-message error-message' : 'feedback-message success-message';
    feedbackEl.style.display = 'block';
    // Ocultar después de 5 segundos
    setTimeout(() => { feedbackEl.style.display = 'none'; }, 5000);
}

function resetForm() {
    document.getElementById('productForm').reset();
    document.getElementById('productId').value = '';
    document.getElementById('formTitle').textContent = 'Crear Nuevo Producto';
    document.getElementById('submitBtn').textContent = 'Crear Producto';
    document.getElementById('resetBtn').style.display = 'none';
    showFeedback('', false); // Limpiar mensaje
}


// Validaciones Frontend (Adicional a las del Backend)
function validateFrontend(data) {
    if (!data.nombre || data.nombre.trim() === "") {
        return "BK-002: El nombre del producto no puede estar vacío.";
    }
    // Modificado para validar entero
    if (isNaN(data.precio) || data.precio === null || data.precio <= 0) {
        return "BK-001: El precio debe ser un número entero mayor a 0.";
    }
    if (data.stock === null || data.stock < 0) {
        return "BK-005: El stock debe ser mayor o igual a 0.";
    }
    if (!data.categoria || data.categoria.trim() === "") {
         return "BK-006: Debe seleccionar una categoría válida.";
    }
    if (data.descripcion && data.descripcion.length > 255) {
        return "BK-026: La descripción no puede exceder los 255 caracteres.";
    }
    return null; // Null significa que pasa la validación
}

// CRUD: Crear y Editar (POST/PUT)
async function handleFormSubmit(event) {
    event.preventDefault();

    const id = document.getElementById('productId').value;
    const isEditing = id !== '';
    const method = isEditing ? 'PUT' : 'POST';
    const url = isEditing ? `${API_URL}/${id}` : API_URL;

    // CAMBIO CLAVE: Usar parseInt en lugar de parseFloat para el precio entero
    const priceValue = document.getElementById('precio').value.trim();
    const data = {
        nombre: document.getElementById('nombre').value.trim(),
        precio: priceValue ? parseInt(priceValue, 10) : null, // Convertir a entero o null
        stock: parseInt(document.getElementById('stock').value),
        categoria: document.getElementById('categoria').value,
        descripcion: document.getElementById('descripcion').value.trim() || null,
        imagenUrl: document.getElementById('imagenUrl').value.trim() || null,
    };

    const feError = validateFrontend(data);
    if (feError) {
        showFeedback("Error de Frontend: " + feError, true);
        return;
    }

    try {
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
        });

        const result = await response.json();

        if (response.ok) {
            showFeedback(`Producto ${isEditing ? 'actualizado' : 'creado'} exitosamente.`, false);
            resetForm();
            fetchProducts();
        } else {
            // Manejo de errores de validación del Backend (Reglas BK)
            showFeedback(`Error de Backend (BK): ${result.error || response.statusText}`, true);
        }
    } catch (error) {
        console.error('Fetch error:', error);
        showFeedback('Error de conexión con el servidor Ktor (Asegúrese que está corriendo en puerto 8080).', true);
    }
}

// CRUD: Listar (GET)
async function fetchProducts() {
    try {
        const response = await fetch(API_URL);
        if (!response.ok) {
            throw new Error('Error al cargar los productos');
        }
        const products = await response.json();
        renderProducts(products);
    } catch (error) {
        console.error('Fetch error:', error);
        showFeedback('No se pudieron cargar los productos.', true);
    }
}

// CRUD: Eliminar (DELETE - Soft Delete)
window.softDeleteProduct = async function(id) {
    // IMPORTANTE: NO USAR window.confirm(), DEBE USARSE MODAL PERSONALIZADO (pero manteniendo la funcionalidad temporalmente)
    if (!confirm('¿Está seguro de INACTIVAR este producto (Eliminación Lógica BK-003)?')) return;

    try {
        const response = await fetch(`${API_URL}/${id}`, {
            method: 'DELETE',
        });

        if (response.ok || response.status === 204) {
            showFeedback('Producto inactivado (Eliminación Lógica BK-003).', false);
            fetchProducts();
        } else {
            const errorData = await response.json();
            showFeedback(`Error al inactivar: ${errorData.error || response.statusText}`, true);
        }
    } catch (error) {
        console.error('Delete error:', error);
        showFeedback('Error de conexión.', true);
    }
}

// Funciones para Editar (CRUD - Cargar datos para edición)
window.editProduct = function(product) {
    document.getElementById('productId').value = product.id;
    document.getElementById('nombre').value = product.nombre;
    // Se asigna el valor directo, el input HTML ahora solo acepta enteros.
    document.getElementById('precio').value = product.precio; 
    document.getElementById('stock').value = product.stock;
    document.getElementById('categoria').value = product.categoria;
    document.getElementById('descripcion').value = product.descripcion || '';
    document.getElementById('imagenUrl').value = product.imagenUrl || '';

    document.getElementById('formTitle').textContent = 'Editar Producto: ' + product.nombre;
    document.getElementById('submitBtn').textContent = 'Guardar Cambios';
    document.getElementById('resetBtn').style.display = 'inline-block';
    showFeedback('', false); // Limpiar mensaje
    window.scrollTo(0, 0);
}

// Renderizar la lista en Cards Modernas
function renderProducts(products) {
    const listDiv = document.getElementById('productList');
    listDiv.innerHTML = '';
    
    if (products.length === 0) {
        listDiv.innerHTML = '<p class="feedback-message">No hay productos para mostrar.</p>';
        return;
    }

    products.forEach(product => {
        const statusClass = product.activo ? 'status-active' : 'status-inactive';
        const statusText = product.activo ? 'ACTIVO' : 'INACTIVO';

        const productCard = document.createElement('div');
        productCard.classList.add('card');
        productCard.innerHTML = `
            <img src="${product.imagenUrl || imgPlaceholder}" alt="${product.nombre}" class="card-img" onerror="this.onerror=null; this.src='${imgPlaceholder}';">
            <div class="card-content">
                <h3>${product.nombre}</h3>
                <p>${product.descripcion || 'Sin descripción detallada.'}</p>
                <div class="card-footer">
                    <span class="card-price">$${(product.precio || 0).toLocaleString('es-CL')}</span>
                    <span class="card-status ${statusClass}">${statusText}</span>
                </div>
                <p style="font-size: 0.85rem; color: #777;">Stock: ${product.stock} | Cat: ${product.categoria}</p>
                <button onclick='editProduct(${JSON.stringify(product)})' class="btn btn-secondary">Editar</button>
                <button onclick='softDeleteProduct("${product.id}")' class="btn btn-delete">Inactivar</button>
            </div>
        `;
        listDiv.appendChild(productCard);
    });
}