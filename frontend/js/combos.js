const API_URL_COMBOS = 'http://localhost:8080/api/combos';
const API_URL_PRODUCTOS = 'http://localhost:8080/api/productos'; // Para obtener lista de productos
const imgPlaceholder = 'https://placehold.co/400x180/442200/fff8ec?text=COMBO+BK-DUOC';

let allProducts = []; // Almacenará todos los productos para referencia

document.addEventListener('DOMContentLoaded', () => {
    // 1. Cargar productos primero para llenar el selector
    fetchProductsForSelector().then(() => {
        // 2. Cargar combos después de cargar productos
        fetchCombos();
    });
    
    document.getElementById('comboForm').addEventListener('submit', handleFormSubmit);
    document.getElementById('resetBtn').addEventListener('click', resetForm);
});

// Función para mostrar mensajes de feedback (errores/éxito)
function showFeedback(message, isError = true) {
    const feedbackEl = document.getElementById('feedback');
    feedbackEl.textContent = message;
    feedbackEl.className = isError ? 'feedback-message error-message' : 'feedback-message success-message';
    feedbackEl.style.display = 'block';
    // Ocultar después de 7 segundos para dar tiempo a leer errores BK
    setTimeout(() => { feedbackEl.style.display = 'none'; }, 7000);
}

function resetForm() {
    document.getElementById('comboForm').reset();
    document.getElementById('comboId').value = '';
    document.getElementById('formTitle').textContent = 'Crear Nuevo Combo';
    document.getElementById('submitBtn').textContent = 'Crear Combo';
    document.getElementById('resetBtn').style.display = 'none';
    
    // Deseleccionar todas las opciones
    Array.from(document.getElementById('productosIds').options).forEach(option => {
        option.selected = false;
    });

    showFeedback('', false); // Limpiar mensaje
}

// ---------------------------------
// Mantenimiento de Productos (para el selector)
// ---------------------------------

// Función para obtener productos y llenar el selector múltiple
async function fetchProductsForSelector() {
    try {
        const response = await fetch(API_URL_PRODUCTOS);
        if (!response.ok) {
            throw new Error('Error al cargar la lista de productos');
        }
        allProducts = await response.json();
        const selector = document.getElementById('productosIds');
        selector.innerHTML = '';

        // Filtrar solo productos activos para combos (BK-015 implícito en la UI)
        const activeProducts = allProducts.filter(p => p.activo);

        if (activeProducts.length === 0) {
            selector.innerHTML = '<option value="" disabled>No hay productos activos para combos (BK-015)</option>';
            return;
        }

        activeProducts.forEach(product => {
            const option = document.createElement('option');
            option.value = product.id;
            // Mostrar nombre, precio (para referencia) y estado
            option.textContent = `${product.nombre} ($${(product.precio || 0).toLocaleString('es-CL')}) - Stock: ${product.stock}`;
            selector.appendChild(option);
        });

    } catch (error) {
        console.error('Fetch error productos:', error);
        showFeedback('Error al cargar productos para el selector. Verifique el backend.', true);
    }
}

// ---------------------------------
// CRUD de Combos
// ---------------------------------

// CRUD: Crear y Editar (POST/PUT)
async function handleFormSubmit(event) {
    event.preventDefault();

    const id = document.getElementById('comboId').value;
    const isEditing = id !== '';
    const method = isEditing ? 'PUT' : 'POST';
    const url = isEditing ? `${API_URL_COMBOS}/${id}` : API_URL_COMBOS;

    const selector = document.getElementById('productosIds');
    const selectedProductsIds = Array.from(selector.options)
        .filter(option => option.selected)
        .map(option => option.value);

    const data = {
        nombre: document.getElementById('nombre').value.trim(),
        productosIds: selectedProductsIds, // Array de ObjectIds como strings
        descripcion: document.getElementById('descripcion').value.trim() || null,
        // Nota: Precio y activo no se envían, son calculados por el backend (BK-014) o manejados por DELETE
    };

    // Validación de Frontend mínima (aunque el Backend es la fuente de verdad)
    if (data.nombre === "") {
        showFeedback("BK-007: El nombre del combo es obligatorio.", true);
        return;
    }
    if (data.productosIds.length === 0) {
        showFeedback("BK-040: Debe seleccionar al menos un producto para el combo.", true);
        return;
    }
    if (data.descripcion && data.descripcion.length > 255) {
        showFeedback("BK-026: La descripción no puede exceder los 255 caracteres.", true);
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
            showFeedback(`Combo ${isEditing ? 'actualizado' : 'creado'} exitosamente. Precio calculado: $${(result.precio || 0).toLocaleString('es-CL')}`, false);
            resetForm();
            fetchCombos();
        } else {
            // Manejo de errores de validación del Backend (Reglas BK)
            showFeedback(`Error de Backend (BK): ${result.error || response.statusText}`, true);
        }
    } catch (error) {
        console.error('Fetch error:', error);
        showFeedback('Error de conexión con el servidor Ktor (Asegúrese que está corriendo).', true);
    }
}

// CRUD: Listar (GET)
async function fetchCombos() {
    const loadingMessage = document.getElementById('loadingMessage');
    loadingMessage.textContent = 'Cargando combos...';
    try {
        const response = await fetch(API_URL_COMBOS);
        if (!response.ok) {
            throw new Error('Error al cargar los combos');
        }
        const combos = await response.json();
        renderCombos(combos);
    } catch (error) {
        console.error('Fetch error:', error);
        showFeedback('No se pudieron cargar los combos.', true);
        loadingMessage.textContent = 'Error al cargar los combos.';
    }
}

// CRUD: Eliminar (DELETE - Soft Delete)
window.softDeleteCombo = async function(id) {
    if (!confirm('¿Está seguro de INACTIVAR este combo (Eliminación Lógica BK-005)?')) return;

    try {
        const response = await fetch(`${API_URL_COMBOS}/${id}`, {
            method: 'DELETE',
        });

        const result = await response.json();

        if (response.ok || response.status === 200) {
            showFeedback(result.message || 'Combo inactivado (Eliminación Lógica BK-005).', false);
            fetchCombos();
        } else {
            showFeedback(`Error al inactivar: ${result.error || response.statusText}`, true);
        }
    } catch (error) {
        console.error('Delete error:', error);
        showFeedback('Error de conexión.', true);
    }
}

// Funciones para Editar (CRUD - Cargar datos para edición)
window.editCombo = function(combo) {
    document.getElementById('comboId').value = combo.id;
    document.getElementById('nombre').value = combo.nombre;
    document.getElementById('descripcion').value = combo.descripcion || '';
    
    const selector = document.getElementById('productosIds');
    const comboProductIds = combo.productosIds.map(p => (typeof p === 'object' ? p.id : p)); // Asegurar que sea el String ID
    
    // Deseleccionar todo primero
    Array.from(selector.options).forEach(option => {
        option.selected = false;
    });

    // Seleccionar los productos que tiene el combo
    Array.from(selector.options).forEach(option => {
        if (comboProductIds.includes(option.value)) {
            option.selected = true;
        }
    });
    

    document.getElementById('formTitle').textContent = 'Editar Combo: ' + combo.nombre;
    document.getElementById('submitBtn').textContent = 'Guardar Cambios';
    document.getElementById('resetBtn').style.display = 'inline-block';
    showFeedback('', false); // Limpiar mensaje
    window.scrollTo(0, 0);
}

// Renderizar la lista en Cards Modernas
function renderCombos(combos) {
    const listDiv = document.getElementById('comboList');
    listDiv.innerHTML = '';
    
    if (combos.length === 0) {
        listDiv.innerHTML = '<p class="feedback-message">No hay combos para mostrar.</p>';
        return;
    }

    // Mapear IDs de producto a nombres para mostrar en la Card
    const productMap = allProducts.reduce((map, product) => {
        map[product.id] = product.nombre;
        return map;
    }, {});

    combos.forEach(combo => {
        const statusClass = combo.activo ? 'status-active' : 'status-inactive';
        const statusText = combo.activo ? 'ACTIVO' : 'INACTIVO (BK-005)' ;

        const includedProductNames = (combo.productosIds || [])
            .map(id => productMap[id] || 'Producto Desconocido')
            .join(', ');

        const productIdsString = JSON.stringify(combo.productosIds);
        
        // Crear un objeto de combo limpio para pasar a editCombo, asegurando que los IDs son strings
        const comboToEdit = {
            ...combo,
            id: combo.id, // ID de combo como string
            productosIds: combo.productosIds.map(p => p.toString()) // IDs de productos como strings
        };


        const comboCard = document.createElement('div');
        comboCard.classList.add('card');
        comboCard.innerHTML = `
            <img src="${imgPlaceholder}" alt="${combo.nombre}" class="card-img">
            <div class="card-content">
                <h3>${combo.nombre}</h3>
                <p class="text-gray-600">${combo.descripcion || 'Sin descripción.'}</p>
                <p class="text-sm text-gray-500 mt-2">Productos: <span class="font-semibold">${includedProductNames}</span></p>
                <div class="card-footer">
                    <div class="text-sm text-bk-primary">
                        <span class="card-price">$${(combo.precio || 0).toLocaleString('es-CL')}</span>
                        <p class="text-xs text-gray-400">Precio Recalculado (BK-014)</p>
                    </div>
                    <span class="card-status ${statusClass}">${statusText}</span>
                </div>
                <div class="mt-4 flex space-x-2">
                    <button onclick='editCombo(${JSON.stringify(comboToEdit)})' class="btn btn-secondary flex-1">Editar</button>
                    <button onclick='softDeleteCombo("${combo.id}")' class="btn btn-delete flex-1">Inactivar</button>
                </div>
            </div>
        `;
        listDiv.appendChild(comboCard);
    });
}