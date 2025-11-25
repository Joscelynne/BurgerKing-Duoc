// URL Base del API (Ktor)
const API_URL = 'http://localhost:8080/api/empleados'; // Ajusta si tu puerto Ktor es diferente

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('employee-form');
    const formTitle = document.getElementById('form-title');
    const employeeIdInput = document.getElementById('employee-id');
    const submitButton = document.getElementById('submit-button');
    const cancelButton = document.getElementById('cancel-button');
    const employeesList = document.getElementById('employees-list');
    const statusMessage = document.getElementById('status-message');
    const activoToggle = document.getElementById('activo');
    const activoLabel = document.getElementById('activo-label');
    const ROLES = ["ADMINISTRATIVO", "CAJERO", "COCINERO", "REPARTIDOR"];

    // Inicializa la carga de empleados
    fetchEmployees();
    
    // Listener para el toggle de Activo/Inactivo
    activoToggle.addEventListener('change', () => {
        activoLabel.textContent = activoToggle.checked ? 'Estado: Activo' : 'Estado: Inactivo';
    });


    // --- Utilidades de Validación ---

    // BK-018: Función para validar el Módulo 11 (RUT Chileno)
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

    // Validar formato email (Opcional, pero si existe debe ser válido)
    function validateEmail(email) {
        if (!email) return true; // Es opcional
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    // Validar teléfono (Opcional, pero si existe deben ser 9 dígitos)
    function validatePhone(phone) {
        if (!phone) return true; // Es opcional
        return /^\d{9}$/.test(phone);
    }

    // BK-019, BK-020: Validar que no esté vacío
    function validateNonEmptyString(value) {
        return value && value.trim().length > 0;
    }
    
    // Validar Rol
    function validateRole(role) {
        return ROLES.includes(role);
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

        // BK-019: Validar nombre (Obligatorio)
        if (!validateNonEmptyString(data.nombre)) {
            showValidationError('nombre', 'BK-019: El nombre es obligatorio.');
            isValid = false;
        } else { hideValidationError('nombre'); }

        // BK-020: Validar apellido (Obligatorio)
        if (!validateNonEmptyString(data.apellido)) {
            showValidationError('apellido', 'BK-020: El apellido es obligatorio.');
            isValid = false;
        } else { hideValidationError('apellido'); }

        // BK-018: Validar RUT (Obligatorio y Módulo 11)
        if (!validateNonEmptyString(data.rut) || !validateRut(data.rut)) {
            showValidationError('rut', 'BK-018: RUT obligatorio e inválido (Módulo 11).');
            isValid = false;
        } else { hideValidationError('rut'); }
        
        // Validar Rol (Obligatorio)
        if (!validateRole(data.rol)) {
            showValidationError('rol', 'El rol es obligatorio y debe ser uno de los roles válidos.');
            isValid = false;
        } else { hideValidationError('rol'); }
        
        // Validar Teléfono (Opcional, pero si existe 9 dígitos)
        if (!validatePhone(data.telefono)) {
            showValidationError('telefono', 'El teléfono debe tener exactamente 9 dígitos numéricos.');
            isValid = false;
        } else { hideValidationError('telefono'); }
        
        // Validar Email (Opcional, pero si existe formato válido)
        if (!validateEmail(data.correo)) {
            showValidationError('correo', 'El email, si se proporciona, debe tener un formato válido.');
            isValid = false;
        } else { hideValidationError('correo'); }
        
        // Dirección es opcional, no necesita validación de no vacío aquí.

        return isValid;
    }
    
    function resetForm() {
        form.reset();
        employeeIdInput.value = '';
        formTitle.textContent = 'Nuevo Empleado';
        submitButton.innerHTML = '<i class="fas fa-save mr-2"></i>Guardar Empleado';
        cancelButton.classList.add('hidden');
        statusMessage.classList.add('hidden');
        
        // Limpiar estilos de error
        ['nombre', 'apellido', 'rut', 'rol', 'telefono', 'correo', 'direccion'].forEach(hideValidationError);
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

        const employeeId = employeeIdInput.value;
        const method = employeeId ? 'PUT' : 'POST';
        const url = employeeId ? `${API_URL}/${employeeId}` : API_URL;

        // Limpiar strings opcionales si están vacíos, para que Ktor los reciba como null
        const rawTelefono = document.getElementById('telefono').value.trim();
        const rawCorreo = document.getElementById('correo').value.trim();
        const rawDireccion = document.getElementById('direccion').value.trim();

        const data = {
            nombre: document.getElementById('nombre').value.trim(),
            apellido: document.getElementById('apellido').value.trim(),
            rut: document.getElementById('rut').value.trim(),
            rol: document.getElementById('rol').value,
            telefono: rawTelefono || null,
            correo: rawCorreo || null,
            direccion: rawDireccion || null,
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
                showStatus(result.message || `Empleado ${employeeId ? 'actualizado' : 'creado'} con éxito.`, true);
                resetForm();
                fetchEmployees(); // Refrescar lista
            } else {
                // Manejo de errores del backend (duplicados, etc.)
                let errorMessage = `Error en el servidor (${response.status}): ${result.message || 'Error desconocido'}`;
                
                // Mostrar errores específicos de validación de duplicidad (RUT)
                if (result.errors && result.errors.length > 0) {
                    result.errors.forEach(err => {
                         if (err.field === 'rut') {
                            showValidationError('rut', err.message);
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

    // Cargar empleados desde el API
    async function fetchEmployees() {
        try {
            const response = await fetch(API_URL);
            if (!response.ok) throw new Error('Error al obtener empleados');
            
            const employees = await response.json();
            renderEmployees(employees);

        } catch (error) {
            console.error('Error fetching employees:', error);
            employeesList.innerHTML = `<p class="text-red-500 col-span-full">Error al cargar empleados. Verifique la conexión con el backend.</p>`;
        }
    }

    // Renderizar la lista de empleados en cards
    function renderEmployees(employees) {
        employeesList.innerHTML = ''; // Limpiar lista anterior
        
        if (employees.length === 0) {
            employeesList.innerHTML = `<div class="col-span-full p-4 bg-yellow-100 border-l-4 border-bk-secondary text-gray-700 rounded-md">No hay empleados registrados.</div>`;
            return;
        }

        employees.forEach(employee => {
            const card = document.createElement('div');
            card.className = 'bg-white p-6 rounded-xl bk-card-shadow border-l-4 ' + (employee.activo ? 'border-bk-primary' : 'border-gray-400');
            
            const statusText = employee.activo ? 'Activo' : 'Inactivo';
            const statusColor = employee.activo ? 'text-green-600 bg-green-100' : 'text-gray-600 bg-gray-200';
            const telText = employee.telefono || 'N/A';
            const mailText = employee.correo || 'N/A';

            card.innerHTML = `
                <div class="flex justify-between items-start mb-4">
                    <h3 class="text-xl font-bold text-bk-dark">${employee.nombre} ${employee.apellido}</h3>
                    <span class="text-xs font-semibold px-2.5 py-0.5 rounded-full ${statusColor}">${statusText}</span>
                </div>
                <div class="space-y-2 text-sm text-gray-600">
                    <p><strong>Rol:</strong> <span class="font-bold text-bk-primary">${employee.rol}</span></p>
                    <p><strong>RUT:</strong> ${employee.rut}</p>
                    <p><strong>Email:</strong> ${mailText}</p>
                    <p><strong>Teléfono:</strong> ${telText}</p>
                </div>
                
                <!-- Botones de Acción (Idénticos a Clientes/Combos) -->
                <div class="mt-4 pt-4 border-t border-gray-100 flex justify-end space-x-2">
                    <button data-id="${employee.id}" class="edit-btn px-3 py-1 bg-bk-secondary text-bk-dark font-semibold rounded-lg hover:opacity-80 transition duration-150 ease-in-out shadow-md shadow-bk-secondary/50 flex items-center">
                        <i class="fas fa-edit mr-1"></i> Editar
                    </button>
                    <button data-id="${employee.id}" data-activo="${employee.activo}" class="delete-btn px-3 py-1 ${employee.activo ? 'bg-red-500 hover:bg-red-600' : 'bg-green-500 hover:bg-green-600'} text-white font-semibold rounded-lg transition duration-150 ease-in-out shadow-md shadow-red-500/50 flex items-center">
                        <i class="fas ${employee.activo ? 'fa-trash-alt' : 'fa-undo'} mr-1"></i> ${employee.activo ? 'Eliminar Lógico' : 'Reactivar'}
                    </button>
                </div>
            `;
            employeesList.appendChild(card);
        });
        
        attachEventListeners();
    }
    
    // Asignar listeners a botones de cards
    function attachEventListeners() {
        document.querySelectorAll('.edit-btn').forEach(button => {
            button.addEventListener('click', () => loadEmployeeForEdit(button.dataset.id));
        });

        document.querySelectorAll('.delete-btn').forEach(button => {
            button.addEventListener('click', () => toggleEmployeeActive(button.dataset.id, button.dataset.activo === 'true'));
        });
    }

    // Cargar datos del empleado al formulario para edición
    async function loadEmployeeForEdit(id) {
        try {
            const response = await fetch(`${API_URL}/${id}`);
            if (!response.ok) throw new Error('Empleado no encontrado');
            
            const employee = await response.json();
            
            employeeIdInput.value = employee.id;
            document.getElementById('nombre').value = employee.nombre;
            document.getElementById('apellido').value = employee.apellido;
            document.getElementById('rut').value = employee.rut;
            document.getElementById('rol').value = employee.rol;
            document.getElementById('telefono').value = employee.telefono || '';
            document.getElementById('correo').value = employee.correo || '';
            document.getElementById('direccion').value = employee.direccion || '';
            activoToggle.checked = employee.activo;
            activoLabel.textContent = employee.activo ? 'Estado: Activo' : 'Estado: Inactivo';
            
            formTitle.textContent = `Editar Empleado: ${employee.nombre} ${employee.apellido}`;
            submitButton.innerHTML = '<i class="fas fa-save mr-2"></i>Actualizar Empleado';
            cancelButton.classList.remove('hidden');
            
            // Scroll al formulario
            window.scrollTo({ top: 0, behavior: 'smooth' });

        } catch (error) {
            showStatus(`Error al cargar empleado para edición: ${error.message}`, false);
        }
    }

    // Eliminar Lógico (Cambio de estado activo)
    async function toggleEmployeeActive(id, currentActive) {
        const newActiveState = !currentActive;
        const action = newActiveState ? 'Reactivar' : 'Eliminar Lógico';
        
        if (!confirm(`¿Estás seguro de que deseas ${action} a este empleado?`)) {
            return;
        }

        try {
            // Se utiliza una ruta diferente para el toggle (se asume que el backend la soporta)
            const response = await fetch(`${API_URL}/${id}/toggle-active`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ activo: newActiveState })
            });
            
            const result = await response.json();

            if (response.ok) {
                showStatus(result.message || `Empleado ${newActiveState ? 'reactivado' : 'eliminado lógicamente'} con éxito.`, true);
                fetchEmployees();
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
    // Usar window.confirm por simplicidad, pero idealmente sería un modal custom
    return window.confirm(message); 
}