let registerData = {};
function compileCode() {

    const sourceCodeInput = $('#sourceCodeInput')[0];
    const sourceCode = sourceCodeInput.value;

    $.ajax({
        type: 'post',
        url: 'api/processor/compile',
        context: document.body,
        contentType: 'application/json',
        data: JSON.stringify({
            sourceCode: sourceCode,
        }),
        success: (data, status) => {
            $('#compiledCodeOutput')
                .val(data.data)
                .trigger('change');
            applyMemoryState(data.memoryState);

            if (data.registers) {
                updateRegistersFromBackend(data.registers);
            }
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
}

$(document).ready(() => {
    $('#compiledCodeOutput').change(function (event) {
        if (event.target.value) {
            $('#executeCodeBtn')[0].disabled = false;
        }
    });
    $('#inputField')[0].disabled = true;
    $('#sendInputBtn')[0].disabled = true;

    initializeEmptyRegisters();

    $.ajax({
        type: 'get',
        url: 'api/processor/state',
        context: document.body,
        contentType: 'application/json',
        success: (data, status) => {
            $('#outputField').val(data.data);
            applyMemoryState(data.memoryState);
            // Atualiza os registradores com os dados recebidos
            if (data.registers) {
                updateRegistersFromBackend(data.registers);
            }
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
});

function executeCode() {

    $.ajax({
        type: 'post',
        url: 'api/processor/execute',
        context: document.body,
        contentType: 'application/json',
        success: (data, status) => {
            resolveResponse(data, status);
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
}

function applyMemoryState(memoryState) {
    memoryState.forEach((row, rowIdx) => {
        row.forEach((col, colIdx) => {
            const memoryRef = $(`#table-row-${rowIdx}-col-${colIdx}`);
            memoryRef.empty().append(col ?? '0000');
        });
    });
}

function initializeEmptyRegisters() {
    const registerNames = ['RA', 'RB', 'RC', 'RD'];
    registerNames.forEach(registerName => {
        registerData[registerName] = [];
    });
    updateRegisters();
}

function updateRegisters() {
    Object.keys(registerData).forEach(registerName => {
        const registerElement = document.getElementById(`register-${registerName}`);
        const values = registerData[registerName];

        registerElement.innerHTML = '';

        if (values.length === 0) {
            const emptyDiv = document.createElement('div');
            emptyDiv.className = 'register-value';
            emptyDiv.style.fontStyle = 'italic';
            emptyDiv.style.color = '#6c757d';
            emptyDiv.textContent = 'Empty';
            registerElement.appendChild(emptyDiv);
        } else {
            values.forEach(value => {
                const valueDiv = document.createElement('div');
                valueDiv.className = 'register-value';
                valueDiv.textContent = value;
                registerElement.appendChild(valueDiv);
            });
        }
    });
}

function updateRegistersFromBackend(registers) {
    if (registers) {
        // Atualiza a variável global com os dados recebidos
        registerData = { ...registers };
        updateRegisters();
    }
}

function sendInput() {

    const inputField = $('#inputField')[0];
    const inputValue = inputField.value;

    $.ajax({
        type: 'post',
        url: 'api/processor/input',
        context: document.body,
        contentType: 'application/json',
        data: JSON.stringify({
            sourceCode: inputValue,
        }),
        success: (data, status) => {
            const inputField =$('#inputField');
            inputField[0].disabled = true;
            inputField.val('');
            $('#sendInputBtn')[0].disabled = true;
            resolveResponse(data, status);
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
}

function resolveResponse(data, status) {
    if (data.needsInput) {
        alert("Necessário input do usuário")
        $('#inputField')[0].disabled = false;
        $('#sendInputBtn')[0].disabled = false;
    } else {
        $('#outputField').val(data.data);
    }
    applyMemoryState(data.memoryState);

    if (data.registers) {
        updateRegistersFromBackend(data.registers);
    }
}