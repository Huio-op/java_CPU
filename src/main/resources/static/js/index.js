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
            $('#stepCodeBtn')[0].disabled = false;
        }
    });
    $('#inputField')[0].disabled = true;
    $('#sendInputBtn')[0].disabled = true;
    $('#sendInputStepBtn')[0].disabled = true;
    $('#executeCodeBtn')[0].disabled = false;
    $('#stepCodeBtn')[0].disabled = false;

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

            if (data.compiledCode) {
                $('#compiledCodeOutput')
                    .val(data.compiledCode)
                    .trigger('change');
            }
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
});

function executeCode(step = false) {
    $.ajax({
        type: 'post',
        url: 'api/processor/execute',
        data: JSON.stringify({
            step
        }),
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
            memoryRef.removeClass('bold');
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
        registerData = {...registers};
        updateRegisters();
    }
}

function sendInput(step = false) {

    const inputField = $('#inputField')[0];
    const inputValue = inputField.value;

    $.ajax({
        type: 'post',
        url: 'api/processor/input',
        context: document.body,
        contentType: 'application/json',
        data: JSON.stringify({
            data: inputValue,
            step
        }),
        success: (data, status) => {
            const inputField = $('#inputField');
            inputField[0].disabled = true;
            inputField.val('');
            $('#sendInputBtn')[0].disabled = true;
            $('#sendInputStepBtn')[0].disabled = true;
            $('#executeCodeBtn')[0].disabled = false;
            $('#stepCodeBtn')[0].disabled = false;
            resolveResponse(data, status);
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
}

function resolveResponse(data, status) {
    if (data.output) {
        $('#outputField').val(data.output);
    }
    applyMemoryState(data.memoryState);

    $('#compiledCodeOutput')
        .val(data.compiledCode)
        .trigger('change');

    if (data.executionFlag) {
        switch (data.executionFlag) {
            case 'ENDED':
                break;
            case 'EXECUTING':
                if (data.hasOwnProperty('executionY') && data.hasOwnProperty('executionX')) {
                    const memoryRef = $(`#table-row-${data.executionY}-col-${data.executionX}`);
                    memoryRef.addClass('bold');
                }
                break;
            case 'NEEDS_INPUT_I':
            case 'NEEDS_INPUT_C':
                if (data.executionFlag === 'NEEDS_INPUT_I') {
                    alert("Number input needed");
                } else if (data.executionFlag === 'NEEDS_INPUT_I') {
                    alert("Text input needed");
                }
                $('#inputField')[0].disabled = false;
                $('#sendInputBtn')[0].disabled = false;
                $('#sendInputStepBtn')[0].disabled = false;

                $('#executeCodeBtn')[0].disabled = true;
                $('#stepCodeBtn')[0].disabled = true;

                break;
            default:
        }
    }

    if (data.registers) {
        updateRegistersFromBackend(data.registers);
    }
}