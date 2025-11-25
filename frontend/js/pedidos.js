const API_URL_PEDIDOS = 'http://localhost:8080/api/pedidos';
const API_URL_PRODUCTOS = 'http://localhost:8080/api/productos';
const API_URL_CLIENTES = 'http://localhost:8080/api/clientes';

let allProducts = []; // Para cálculo de total
let allClients = [];  // Para mostrar nombres

const ESTADOS_PEDIDO = [
    'PENDIENTE', 
    'PREPARACION', 
    'LISTO', 
    'ENTREGADO', 
    'CANCELADO'
];

document.addEventListener('DOMContentLoaded', async () => {
    // Mostrar fecha de hoy (BK-011)
    document.getElementById('fechaPedido').textContent = new Date().toLocaleDateString('es-CL');

    // Cargar datos necesarios
    await loadInitialData();
    
    // Configurar listeners
    document.getElementById('pedidoForm').addEventListener('submit', handleFormSubmit);
    document.getElementById('resetBtn').addEventListener('click', resetForm);
    
    // Listener para recalcular total y mostrar campo Banco (BK-022)
    document.getElementById('productos').addEventListener('change', updateTotals);
    document.getElementById('metodoPago').addEventListener('change', updatePaymentFields);
    document.getElementById('metodoPago').addEventListener('change', updateTotals);
    document.getElementById('banco').addEventListener('input', updateTotals);
});

async function loadInitialData() {
    await fetchProductsForPedido(); // Necesario para selector y cálculo
    await fetchClientsForSelector(); // Necesario para selector de cliente
    fetchPedidos(); // Cargar la lista de pedidos
}

// ---------------------------------
// Mantenimiento de Datos para Selectores
// ---------------------------------

async function fetchProductsForPedido() {
    try {
        const response = await fetch(API_URL_PRODUCTOS);
        if (!response.ok) throw new Error('Error al cargar productos');
        allProducts = await response.json();
        const selector = document.getElementById('productos');
        selector.innerHTML = '';

        const activeProducts = allProducts.filter(p => p.activo);
        
        if (activeProducts.length === 0) {
            selector.innerHTML = '<option value="" disabled>No hay productos activos disponibles.</option>';
            return;
        }

        activeProducts.forEach(product => {
            const option = document.createElement('option');
            option.value = product.id;
            // Guardamos precio y stock como atributos para fácil acceso en updateTotals
            option.setAttribute('data-price', product.precio);
            option.setAttribute('data-stock', product.stock);
            option.textContent = `${product.nombre} ($${product.precio.toLocaleString('es-CL')}) - Stock: ${product.stock}`;
            selector.appendChild(option);
        });

    } catch (error) {
        console.error('Fetch error productos:', error);
    }
}

async function fetchClientsForSelector() {
    try {
        const response = await fetch(API_URL_CLIENTES);
        if (!response.ok) throw new Error('Error al cargar clientes');
        allClients = await response.json();
        const selector = document.getElementById('clienteId');
        selector.innerHTML = '<option value="" disabled selected>Seleccione un cliente</option>';

        const activeClients = allClients.filter(c => c.activo); // Asumiendo que Cliente tiene campo 'activo'
        
        if (activeClients.length === 0) {
            selector.innerHTML = '<option value="" disabled>No hay clientes activos disponibles.</option>';
            return;
        }

        activeClients.forEach(client => {
            const option = document.createElement('option');
            option.value = client.id;
            option.textContent = `${client.nombre} ${client.apellido} (${client.rut})`;
            selector.appendChild(option);
        });
        
    } catch (error) {
        console.error('Fetch error clientes:', error);
    }
}

// ---------------------------------
// Lógica de Formulario
// ---------------------------------

// Lógica de visualización de campo Banco (BK-022)
function updatePaymentFields() {
    const metodoPago = document.getElementById('metodoPago').value;
    const bancoGroup = document.getElementById('bancoGroup');
    const bancoInput = document.getElementById('banco');

    if (metodoPago === 'DEBITO' || metodoPago === 'CREDITO') {
        bancoGroup.style.display = 'block';
        bancoInput.required = true;
    } else {
        bancoGroup.style.display = 'none';
        bancoInput.required = false;
        bancoInput.value = '';
    }
}

// Recalcular Subtotal, Descuento y Total (BK-010, BK-021)
function updateTotals() {
    const selector = document.getElementById('productos');
    const metodoPago = document.getElementById('metodoPago').value;
    const banco = document.getElementById('banco').value.trim().toUpperCase();
    
    let subtotal = 0;
    
    // 1. Calcular Subtotal
    Array.from(selector.options).forEach(option => {
        if (option.selected) {
            // Nota: El frontend no maneja cantidad, solo 1 unidad. El backend debe manejar cantidad.
            // Para simplificar la UI, asumiremos cantidad 1 en el frontend o usaríamos un modal para cantidad.
            const price = parseFloat(option.getAttribute('data-price'));
            subtotal += price; 
            // Si estuviéramos en modo edición, deberíamos cargar la cantidad real del pedido.
        }
    });

    // 2. Aplicar Regla BK-021 (Descuento)
    let descuentoRate = 0.0;
    const bancosConDescuento = ['SANTANDER', 'BCI', 'CHILE']; // Mismo listado que en el backend
    
    if ((metodoPago === 'DEBITO' || metodoPago === 'CREDITO') && bancosConDescuento.includes(banco)) {
        descuentoRate = 0.15; // 15% de descuento
    }

    const descuentoAmount = subtotal * descuentoRate;
    const totalFinal = subtotal - descuentoAmount;

    // 3. Mostrar resultados
    document.getElementById('subtotalDisplay').textContent = `$${subtotal.toLocaleString('es-CL', { minimumFractionDigits: 0 })}`;
    document.getElementById('descuentoDisplay').textContent = `${(descuentoRate * 100)}%`;
    document.getElementById('totalDisplay').textContent = `$${totalFinal.toLocaleString('es-CL', { minimumFractionDigits: 2 })}`;

    // Validación BK-010: El total debe ser positivo
    if (totalFinal <= 0.0) {
        document.getElementById('totalDisplay').classList.add('text-red-600');
        document.getElementById('totalDisplay').classList.remove('text-bk-accent');
    } else {
        document.getElementById('totalDisplay').classList.add('text-bk-accent');
        document.getElementById('totalDisplay').classList.remove('text-red-600');
    }
}


// CRUD: Crear (POST)
async function handleFormSubmit(event) {
    event.preventDefault();

    const id = document.getElementById('pedidoId').value;
    if (id !== '') {
        showFeedback("La edición de pedidos solo permite cambiar el estado. Use el botón 'Cambiar Estado'.", true);
        return;
    }
    
    const selector = document.getElementById('productos');
    const selectedProducts = Array.from(selector.options)
        .filter(option => option.selected)
        .map(option => ({
            productoId: option.value,
            cantidad: 1 // **IMPORTANTE: Asumimos cantidad 1 por simplificación de UI. Backend debe validar stock por cantidad.**
        }));
        
    const metodoPago = document.getElementById('metodoPago').value;
    const banco = document.getElementById('banco').value.trim() || null;

    const data = {
        clienteId: document.getElementById('clienteId').value,
        productos: selectedProducts,
        metodoPago: metodoPago,
        banco: metodoPago !== 'EFECTIVO' ? banco : null,
        direccionEntrega: document.getElementById('direccionEntrega').value.trim()
    };
    
    // Validación de Frontend mínima (Dirección obligatoria BK-033)
    if (data.direccionEntrega === "") {
        showFeedback("BK-033: La dirección de entrega es obligatoria.", true);
        return;
    }


    try {
        const response = await fetch(API_URL_PEDIDOS, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
        });

        const result = await response.json();

        if (response.ok) {
            showFeedback(`Pedido creado exitosamente. Total: $${(result.total || 0).toLocaleString('es-CL', { minimumFractionDigits: 2 })}`, false);
            resetForm();
            fetchPedidos();
            // Actualizar productos después de un pedido (por la resta de stock BK-031)
            await fetchProductsForPedido(); 
        } else {
            // Manejo de errores de validación del Backend (Reglas BK-030, BK-022, etc.)
            showFeedback(`Error de Backend (BK): ${result.error || response.statusText}`, true);
        }
    } catch (error) {
        console.error('Fetch error:', error);
        showFeedback('Error de conexión con el servidor Ktor.', true);
    }
}

// CRUD: Listar (GET)
async function fetchPedidos() {
    const loadingMessage = document.getElementById('loadingMessage');
    loadingMessage.textContent = 'Cargando pedidos...';
    try {
        const response = await fetch(API_URL_PEDIDOS);
        if (!response.ok) throw new Error('Error al cargar los pedidos');
        const pedidos = await response.json();
        renderPedidos(pedidos);
    } catch (error) {
        console.error('Fetch error:', error);
        showFeedback('No se pudieron cargar los pedidos.', true);
        loadingMessage.textContent = 'Error al cargar los pedidos.';
    }
}

// ---------------------------------
// Lógica de Botones y Renderizado
// ---------------------------------

// Actualizar Estado del Pedido (BK-023)
window.updateStatus = async function(id, currentStatus) {
    const nextStatusIndex = ESTADOS_PEDIDO.indexOf(currentStatus) + 1;
    let newStatus = ESTADOS_PEDIDO[nextStatusIndex];
    
    // Si ya es el último estado (Entregado/Cancelado), no permitir más cambios
    if (currentStatus === 'ENTREGADO' || currentStatus === 'CANCELADO') {
         showFeedback(`El pedido ya está en estado final: ${currentStatus}.`, true);
         return;
    }
    
    // Preguntar al usuario (temporalmente con confirm())
    if (!confirm(`¿Cambiar estado de ${currentStatus} a ${newStatus}?`)) return;

    try {
        const response = await fetch(`${API_URL_PEDIDOS}/${id}/estado`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ estado: newStatus })
        });
        
        const result = await response.json();

        if (response.ok) {
            showFeedback(`Estado del pedido ${id} actualizado a ${result.estado}.`, false);
            fetchPedidos();
        } else {
            showFeedback(`Error al actualizar estado: ${result.error || response.statusText}`, true);
        }

    } catch (error) {
        console.error('Update status error:', error);
        showFeedback('Error de conexión al cambiar estado.', true);
    }
}


// CRUD: Eliminar (DELETE - Soft Delete)
window.softDeletePedido = async function(id) {
    if (!confirm('¿Está seguro de INACTIVAR este pedido?')) return; // Reemplazar por modal personalizado

    try {
        const response = await fetch(`${API_URL_PEDIDOS}/${id}`, {
            method: 'DELETE',
        });

        const result = await response.json();

        if (response.ok) {
            showFeedback('Pedido inactivado exitosamente.', false);
            fetchPedidos();
        } else {
            showFeedback(`Error al inactivar: ${result.error || response.statusText}`, true);
        }
    } catch (error) {
        console.error('Delete error:', error);
        showFeedback('Error de conexión.', true);
    }
}

// Renderizar la lista en Cards Modernas (REPLICANDO ESTILO PRODUCTOS)
function renderPedidos(pedidos) {
    const listDiv = document.getElementById('pedidoList');
    listDiv.innerHTML = '';
    
    if (pedidos.length === 0) {
        listDiv.innerHTML = '<p class="feedback-message">No hay pedidos para mostrar.</p>';
        return;
    }

    // Mapa de clientes para mostrar nombre en lugar de ID
    const clientMap = allClients.reduce((map, client) => {
        map[client.id] = `${client.nombre} ${client.apellido}`;
        return map;
    }, {});

    pedidos.forEach(pedido => {
        // Estilos de estado (BK-023)
        let statusClass = 'bg-gray-200 text-gray-800';
        switch (pedido.estado) {
            case 'PENDIENTE': statusClass = 'bg-yellow-100 text-yellow-800'; break;
            case 'PREPARACION': statusClass = 'bg-blue-100 text-blue-800'; break;
            case 'LISTO': statusClass = 'bg-green-100 text-green-800'; break;
            case 'ENTREGADO': statusClass = 'bg-purple-100 text-purple-800'; break;
            case 'CANCELADO': statusClass = 'bg-red-100 text-red-800'; break;
        }
        
        const clientName = clientMap[pedido.clienteId] || 'Cliente Desconocido';
        const productsList = pedido.productos.map(p => `${p.nombre} (x${p.cantidad})`).join(', ');
        const date = new Date(pedido.fechaCreacion).toLocaleDateString('es-CL');
        const nextStatus = ESTADOS_PEDIDO[ESTADOS_PEDIDO.indexOf(pedido.estado) + 1];
        
        const card = document.createElement('div');
        card.classList.add('card');
        card.innerHTML = `
            <img src="https://placehold.co/400x180/442200/fff8ec?text=PEDIDO+BK-DUOC" alt="Pedido ${pedido.id}" class="card-img" onerror="this.onerror=null; this.src='https://placehold.co/400x180/442200/fff8ec?text=PEDIDO+BK-DUOC';">
            <div class="card-content">
                <h3>Pedido #${pedido.id.slice(-6)} - ${clientName}</h3>
                <p class="text-sm text-gray-600">Dirección: ${pedido.direccionEntrega}</p>
                <p class="text-xs text-gray-500 mt-2">Productos: <span class="font-semibold">${productsList}</span></p>
                
                <div class="card-footer">
                    <div class="text-sm text-bk-primary">
                        <span class="card-price">$${pedido.total.toLocaleString('es-CL', { minimumFractionDigits: 2 })}</span>
                        <p class="text-xs text-gray-400">Total (Desc: ${(pedido.descuento * 100).toFixed(0)}%)</p>
                    </div>
                    <span class="card-status ${statusClass}">${pedido.estado}</span>
                </div>
                
                <div class="mt-4 text-xs text-gray-500">
                    <p>Pago: ${pedido.metodoPago} ${pedido.banco ? `(${pedido.banco})` : ''} | Fecha: ${date}</p>
                </div>

                <div class="mt-4 flex space-x-2">
                    <button onclick='updateStatus("${pedido.id}", "${pedido.estado}")' 
                            class="btn btn-secondary flex-1 ${pedido.estado === 'ENTREGADO' || pedido.estado === 'CANCELADO' ? 'opacity-50 cursor-not-allowed' : ''}"
                            ${pedido.estado === 'ENTREGADO' || pedido.estado === 'CANCELADO' ? 'disabled' : ''}>
                        ${nextStatus ? `→ ${nextStatus}` : 'Estado Final'}
                    </button>
                    <button onclick='softDeletePedido("${pedido.id}")' class="btn btn-delete flex-1">Inactivar</button>
                </div>
            </div>
        `;
        listDiv.appendChild(card);
    });
}

function showFeedback(message, isError = true) {
    const feedbackEl = document.getElementById('feedback');
    feedbackEl.textContent = message;
    feedbackEl.className = isError ? 'feedback-message error-message' : 'feedback-message success-message';
    feedbackEl.style.display = 'block';
    setTimeout(() => { feedbackEl.style.display = 'none'; }, 7000);
}

function resetForm() {
    document.getElementById('pedidoForm').reset();
    document.getElementById('pedidoId').value = '';
    document.getElementById('formTitle').textContent = 'Registrar Nuevo Pedido';
    document.getElementById('submitBtn').textContent = 'Registrar Pedido';
    document.getElementById('resetBtn').style.display = 'none';
    
    // Resetear selectores múltiples
    Array.from(document.getElementById('productos').options).forEach(option => {
        option.selected = false;
    });

    updatePaymentFields(); // Ocultar banco
    updateTotals(); // Recalcular a cero

    showFeedback('', false);
}