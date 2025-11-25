// URL Base del API (Ktor)
const API_URL = 'http://localhost:8080/api/clientes'; // Ajusta si tu puerto Ktor es diferente

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('client-form');
    const formTitle = document.getElementById('form-title');
    const clientIdInput = document.getElementById('client-id');
    const submitButton = document.getElementById('submit-button');
    const cancelButton = document.getElementById('cancel-button');
    const clientsList = document.getElementById('clients-list');
    const statusMessage = document.getElementById('status-message');
    const activoToggle = document.getElementById('activo');
    const activoLabel = document.getElementById('activo-label');

    // Inicializa la carga de clientes
    fetchClients();
    
    // Listener para el toggle de Activo/Inactivo
    activoToggle.addEventListener('change', () => {
        activoLabel.textContent = activoToggle.checked ? 'Estado: Activo' : 'Estado: Inactivo';
    });


    // --- Utilidades de Validación ---

    // Función para validar el Módulo 11 (RUT Chileno)
    function validateRut(rut) {
        if (!/^\d{1,2}\.\d{3}\.\d{3}-[\dkK]$/.test(rut)) {
            return false; // Formato incorrecto
        }
        
        let [num, dv] = rut.replace(/\./g, '').split('-');
        dv = dv.toLowerCase();
        
        let sum = 0;
        let factor = 2;
        
        for (let i = num.length - 1; i >= 0; i--) {
            sum += parseInt(num[i]) * factor;
            factor = (factor % 7) + 1;
        }
        
        const calculatedDv = 11 - (sum % 11);
        let expectedDv;
        
        if (calculatedDv === 11) {
            expectedDv = '0';
        } else if (calculatedDv === 10) {
            expectedDv = 'k';
        } else {
            expectedDv = calculatedDv.toString();
        }
        
        return expectedDv === dv;
    }

    // BK-012: Validar formato email
    function validateEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    // BK-016: Validar teléfono (9 dígitos numéricos)
    function validatePhone(phone) {
        return /^\d{9}$/.test(phone);
    }

    // BK-014, BK-015, BK-017: Validar que no esté vacío
    function validateNonEmptyString(value) {
        return value && value.trim().length > 0;
    }
    
    // --- Lógica de Formulario ---
    
    function showValidationError(field, message) {
        const input = document.getElementById(field);
        const errorElement = document.getElementById(`error-${field}`);
        input.classList.add('border-red-500');
        errorElement.textContent = message;
        errorElement.classList.remove('hidden');
    }

    function hideValidationError(field) {
        const input = document.getElementById(field);
        const errorElement = document.getElementById(`error-${field}`);
        input.classList.remove('border-red-500');
        errorElement.classList.add('hidden');
    }
    
    function validateForm(data) {
        let isValid = true;

        // BK-014: Validar nombre
        if (!validateNonEmptyString(data.nombre)) {
            showValidationError('nombre', 'BK-014: El nombre es obligatorio.');
            isValid = false;
        } else { hideValidationError('nombre'); }

        // BK-015: Validar apellido
        if (!validateNonEmptyString(data.apellido)) {
            showValidationError('apellido', 'BK-015: El apellido es obligatorio.');
            isValid = false;
        } else { hideValidationError('apellido'); }

        // BK-013: Validar RUT
        if (!validateNonEmptyString(data.rut) || !validateRut(data.rut)) {
            showValidationError('rut', 'BK-013: RUT obligatorio e inválido (Módulo 11).');
            isValid = false;
        } else { hideValidationError('rut'); }

        // BK-012: Validar Email
        if (!validateNonEmptyString(data.correo) || !validateEmail(data.correo)) {
            showValidationError('correo', 'BK-012: Email obligatorio y con formato válido.');
            isValid = false;
        } else { hideValidationError('correo'); }

        // BK-016: Validar Teléfono
        if (!validatePhone(data.telefono)) {
            showValidationError('telefono', 'BK-016: Teléfono obligatorio (9 dígitos numéricos).');
            isValid = false;
        } else { hideValidationError('telefono'); }

        // BK-017: Validar Dirección
        if (!validateNonEmptyString(data.direccion)) {
            showValidationError('direccion', 'BK-017: La dirección es obligatoria.');
            isValid = false;
        } else { hideValidationError('direccion'); }

        return isValid;
    }
    
    function resetForm() {
        form.reset();
        clientIdInput.value = '';
        formTitle.textContent = 'Nuevo Cliente';
        submitButton.innerHTML = '<i class="fas fa-save mr-2"></i>Guardar Cliente';
        cancelButton.classList.add('hidden');
        statusMessage.classList.add('hidden');
        
        // Limpiar estilos de error
        ['nombre', 'apellido', 'rut', 'correo', 'telefono', 'direccion'].forEach(hideValidationError);
        activoLabel.textContent = 'Estado: Activo';
    }

    function showStatus(message, isSuccess = true) {
        statusMessage.textContent = message;
        statusMessage.classList.remove('hidden', 'bg-red-100', 'border-red-500', 'text-red-700', 'bg-bk-accent/20', 'border-bk-accent/50', 'text-gray-900');
        
        if (isSuccess) {
            statusMessage.classList.add('bg-bk-accent/20', 'border', 'border-bk-accent/50', 'text-gray-900');
        } else {
            statusMessage.classList.add('bg-red-100', 'border', 'border-red-500', 'text-red-700');
        }
        setTimeout(() => {
            statusMessage.classList.add('hidden');
        }, 5000);
    }

    // --- Lógica CRUD con Fetch API ---

    // Manejar el envío del formulario (Crear o Actualizar)
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const clientId = clientIdInput.value;
        const method = clientId ? 'PUT' : 'POST';
        const url = clientId ? `${API_URL}/${clientId}` : API_URL;

        const data = {
            nombre: document.getElementById('nombre').value.trim(),
            apellido: document.getElementById('apellido').value.trim(),
            rut: document.getElementById('rut').value.trim(),
            correo: document.getElementById('correo').value.trim(),
            telefono: document.getElementById('telefono').value.trim(),
            direccion: document.getElementById('direccion').value.trim(),
            activo: activoToggle.checked,
        };

        if (!validateForm(data)) {
            showStatus("Por favor, corrige los errores de validación en el formulario.", false);
            return;
        }
        
        submitButton.disabled = true;

        try {
            const response = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(data),
            });

            const result = await response.json();

            if (response.ok) {
                showStatus(result.message || `Cliente ${clientId ? 'actualizado' : 'creado'} con éxito.`, true);
                resetForm();
                fetchClients(); // Refrescar lista
            } else {
                // Manejo de errores del backend (duplicados, etc.)
                let errorMessage = `Error en el servidor (${response.status}): ${result.message || 'Error desconocido'}`;
                
                // Mostrar errores específicos de validación de duplicidad (RUT o Email)
                if (result.errors && result.errors.length > 0) {
                    result.errors.forEach(err => {
                         if (err.field === 'rut') {
                            showValidationError('rut', err.message);
                         } else if (err.field === 'correo') {
                            showValidationError('correo', err.message);
                         } else {
                            errorMessage += `\n- ${err.message}`;
                         }
                    });
                }
                
                showStatus(errorMessage, false);
            }
        } catch (error) {
            console.error('Fetch error:', error);
            showStatus('Error de conexión con el servidor Ktor. Asegúrate de que esté corriendo.', false);
        } finally {
            submitButton.disabled = false;
        }
    });

    // Cargar clientes desde el API
    async function fetchClients() {
        try {
            const response = await fetch(API_URL);
            if (!response.ok) throw new Error('Error al obtener clientes');
            
            const clients = await response.json();
            renderClients(clients);

        } catch (error) {
            console.error('Error fetching clients:', error);
            clientsList.innerHTML = `<p class="text-red-500 col-span-full">Error al cargar clientes. Verifique la conexión con el backend.</p>`;
        }
    }

    // Renderizar la lista de clientes en cards
    function renderClients(clients) {
        clientsList.innerHTML = ''; // Limpiar lista anterior
        
        if (clients.length === 0) {
            clientsList.innerHTML = `<div class="col-span-full p-4 bg-yellow-100 border-l-4 border-bk-secondary text-gray-700 rounded-md">No hay clientes registrados.</div>`;
            return;
        }

        clients.forEach(client => {
            const card = document.createElement('div');
            card.className = 'bg-white p-6 rounded-xl bk-card-shadow border-l-4 ' + (client.activo ? 'border-bk-primary' : 'border-gray-400');
            
            const statusText = client.activo ? 'Activo' : 'Inactivo';
            const statusColor = client.activo ? 'text-green-600 bg-green-100' : 'text-gray-600 bg-gray-200';

            card.innerHTML = `
                <div class="flex justify-between items-start mb-4">
                    <h3 class="text-xl font-bold text-bk-dark">${client.nombre} ${client.apellido}</h3>
                    <span class="text-xs font-semibold px-2.5 py-0.5 rounded-full ${statusColor}">${statusText}</span>
                </div>
                <div class="space-y-2 text-sm text-gray-600">
                    <p><strong>RUT:</strong> ${client.rut}</p>
                    <p><strong>Email:</strong> ${client.correo}</p>
                    <p><strong>Teléfono:</strong> ${client.telefono}</p>
                    <p><strong>Dirección:</strong> ${client.direccion}</p>
                </div>
                
                <!-- Botones de Acción (Idénticos a Combos) -->
                <div class="mt-4 pt-4 border-t border-gray-100 flex justify-end space-x-2">
                    <button data-id="${client.id}" class="edit-btn px-3 py-1 bg-bk-secondary text-bk-dark font-semibold rounded-lg hover:opacity-80 transition duration-150 ease-in-out shadow-md shadow-bk-secondary/50 flex items-center">
                        <i class="fas fa-edit mr-1"></i> Editar
                    </button>
                    <button data-id="${client.id}" data-activo="${client.activo}" class="delete-btn px-3 py-1 ${client.activo ? 'bg-red-500 hover:bg-red-600' : 'bg-green-500 hover:bg-green-600'} text-white font-semibold rounded-lg transition duration-150 ease-in-out shadow-md shadow-red-500/50 flex items-center">
                        <i class="fas ${client.activo ? 'fa-trash-alt' : 'fa-undo'} mr-1"></i> ${client.activo ? 'Eliminar Lógico' : 'Reactivar'}
                    </button>
                </div>
            `;
            clientsList.appendChild(card);
        });
        
        attachEventListeners();
    }
    
    // Asignar listeners a botones de cards
    function attachEventListeners() {
        document.querySelectorAll('.edit-btn').forEach(button => {
            button.addEventListener('click', () => loadClientForEdit(button.dataset.id));
        });

        document.querySelectorAll('.delete-btn').forEach(button => {
            button.addEventListener('click', () => toggleClientActive(button.dataset.id, button.dataset.activo === 'true'));
        });
    }

    // Cargar datos del cliente al formulario para edición
    async function loadClientForEdit(id) {
        try {
            const response = await fetch(`${API_URL}/${id}`);
            if (!response.ok) throw new Error('Cliente no encontrado');
            
            const client = await response.json();
            
            clientIdInput.value = client.id;
            document.getElementById('nombre').value = client.nombre;
            document.getElementById('apellido').value = client.apellido;
            document.getElementById('rut').value = client.rut;
            document.getElementById('correo').value = client.correo;
            document.getElementById('telefono').value = client.telefono;
            document.getElementById('direccion').value = client.direccion;
            activoToggle.checked = client.activo;
            activoLabel.textContent = client.activo ? 'Estado: Activo' : 'Estado: Inactivo';
            
            formTitle.textContent = `Editar Cliente: ${client.nombre} ${client.apellido}`;
            submitButton.innerHTML = '<i class="fas fa-save mr-2"></i>Actualizar Cliente';
            cancelButton.classList.remove('hidden');
            
            // Scroll al formulario
            window.scrollTo({ top: 0, behavior: 'smooth' });

        } catch (error) {
            showStatus(`Error al cargar cliente para edición: ${error.message}`, false);
        }
    }

    // Eliminar Lógico (Cambio de estado activo)
    async function toggleClientActive(id, currentActive) {
        const newActiveState = !currentActive;
        const action = newActiveState ? 'Reactivar' : 'Eliminar Lógico';
        
        if (!confirm(`¿Estás seguro de que deseas ${action} a este cliente?`)) {
            return;
        }

        try {
            const response = await fetch(`${API_URL}/${id}/toggle-active`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ activo: newActiveState })
            });
            
            const result = await response.json();

            if (response.ok) {
                showStatus(result.message || `Cliente ${newActiveState ? 'reactivado' : 'eliminado lógicamente'} con éxito.`, true);
                fetchClients();
            } else {
                showStatus(`Error al cambiar estado: ${result.message || 'Error desconocido.'}`, false);
            }
        } catch (error) {
            console.error('Delete/Toggle error:', error);
            showStatus('Error de conexión con el servidor Ktor.', false);
        }
    }
    
    // Botón Cancelar
    cancelButton.addEventListener('click', resetForm);
});

// Polyfill para confirm (solo si no es soportado en el entorno)
function confirm(message) {
    // En un entorno de producción real, esto debería ser un modal
    return window.confirm(message); 
}