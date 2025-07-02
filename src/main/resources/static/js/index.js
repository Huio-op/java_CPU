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
            setCurrentState(data);
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
    const registerData = {};
    const registerNames = ['RA', 'RB', 'RC', 'RD', 'PC', 'SP'];
    registerNames.forEach(registerName => {
        registerData[registerName] = [];
    });
    updateRegisters(registerData);
}

function updateRegisters(registerData) {
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
        const registerData = {...registers};
        updateRegisters(registerData);
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
                } else if (data.executionFlag === 'NEEDS_INPUT_C') {
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

function clearContext() {
    $.ajax({
        type: 'get',
        url: 'api/processor/clear',
        context: document.body,
        contentType: 'application/json',
        success: (data, status) => {
            this.setCurrentState(data);
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    });
}

const opcodesData = [
    ["NOOP", "PARA A EXECUÇÃO POR 1 MS", "0x00", "NENHUM"],
    ["MOV", "COLOCA UM VALOR EM UM REGISTRADOR", "0x01", "1- VALOR NUMÉRICO\n2- REGISTRADOR"],
    ["LOAD", "LÊ UM VALOR DA MEMÓRIA RAM E COLOCA EM UM REGISTRADOR", "0x02", "1- ENDEREÇO DE MEMÓRIA PARA LER\n2- REGISTRADOR PARA SALVAR O VALOR"],
    ["SAVE", "TIRA UM VALOR DE UM REGISTRADOR E SALVA NA MEMÓRIA RAM", "0x03", "1- REGISTRADOR PARA LER\n2- ENDEREÇO DE MEMÓRIA PARA SALVAR O VALOR"],
    ["INP", "ENTRADA DO TECLADO DE NÚMERO", "0x04", "1- REGISTRADOR ONDE SERÁ SALVO O VALOR RECEBIDO"],
    ["INP_C", "ENTRADA DO TECLADO DE TEXTO", "0x13", "1- REGISTRADOR ONDE SERÁ SALVO O VALOR RECEBIDO"],
    ["OUT", "SAÍDA NA TELA DE NÚMEROS", "0x05", "1- REGISTRADOR QUE SERÁ LIDO PARA SER EXIBIDO"],
    ["OUT_C", "SAÍDA NA TELA DE CARACTERES", "0x14", "1- REGISTRADOR QUE SERÁ LIDO PARA SER EXIBIDO"],
    ["ADD", "SOMA O VALOR DE DOIS REGISTRADORES", "0x06", "1- REGISTRADOR COM PRIMEIRO VALOR, RESULTADO SERÁ SALVO NESTE REGISTRADOR\n2- REGISTRADOR COM SEGUNDO VALOR"],
    ["SUB", "DIMINUI O VALOR DE DOIS REGISTRADORES", "0x07", "1- REGISTRADOR COM PRIMEIRO VALOR, RESULTADO SERÁ SALVO NESTE REGISTRADOR\n2- REGISTRADOR COM SEGUNDO VALOR"],
    ["MUL", "MULTIPLICA O VALOR DE DOIS REGISTRADORES", "0x08", "1- REGISTRADOR COM PRIMEIRO VALOR, RESULTADO SERÁ SALVO NESTE REGISTRADOR\n2- REGISTRADOR COM SEGUNDO VALOR"],
    ["DIV", "DIVIDE O VALOR DE DOIS REGISTRADORES", "0x09", "1- REGISTRADOR COM PRIMEIRO VALOR, RESULTADO SERÁ SALVO NESTE REGISTRADOR\n2- REGISTRADOR COM SEGUNDO VALOR"],
    ["JGT", "COMPARA O VALOR DE DOIS REGISTRADORES E SALTA PARA UMA FUNÇÃO QUANDO O PRIMEIRO É MAIOR QUE O SEGUNDO, NÃO TIRA OS VALORES DOS REGISTRADORES, CASO ALGUM DOS REGISTRADORES ESTEJA VAZIO O SALTO NÃO É EXECUTADO", "0x0A", "1- REGISTRADOR PARA COMPARAÇÃO\n2- REGISTRADOR PARA COMPARAÇÃO\n3- NOME DA FUNÇÃO PARA O SALTO"],
    ["JLT", "COMPARA O VALOR DE DOIS REGISTRADORES E SALTA PARA UMA FUNÇÃO QUANDO O PRIMEIRO É MENOR QUE O SEGUNDO, NÃO TIRA OS VALORES DOS REGISTRADORES, CASO ALGUM DOS REGISTRADORES ESTEJA VAZIO O SALTO NÃO É EXECUTADO", "0x0B", "1- REGISTRADOR PARA COMPARAÇÃO\n2- REGISTRADOR PARA COMPARAÇÃO\n3- NOME DA FUNÇÃO PARA O SALTO"],
    ["JGE", "COMPARA O VALOR DE DOIS REGISTRADORES E SALTA PARA UMA FUNÇÃO QUANDO O PRIMEIRO É MAIOR OU IGUAL AO SEGUNDO, NÃO TIRA OS VALORES DOS REGISTRADORES, CASO ALGUM DOS REGISTRADORES ESTEJA VAZIO O SALTO NÃO É EXECUTADO", "0x0C", "1- REGISTRADOR PARA COMPARAÇÃO\n2- REGISTRADOR PARA COMPARAÇÃO\n3- NOME DA FUNÇÃO PARA O SALTO"],
    ["JLE", "COMPARA O VALOR DE DOIS REGISTRADORES E SALTA PARA UMA FUNÇÃO QUANDO O PRIMEIRO É MENOR OU IGUAL AO SEGUNDO, NÃO TIRA OS VALORES DOS REGISTRADORES, CASO ALGUM DOS REGISTRADORES ESTEJA VAZIO O SALTO NÃO É EXECUTADO", "0x0D", "1- REGISTRADOR PARA COMPARAÇÃO\n2- REGISTRADOR PARA COMPARAÇÃO\n3- NOME DA FUNÇÃO PARA O SALTO"],
    ["JEQ", "COMPARA O VALOR DE DOIS REGISTRADORES E SALTA PARA UMA FUNÇÃO QUANDO O PRIMEIRO É IGUAL AO SEGUNDO, NÃO TIRA OS VALORES DOS REGISTRADORES", "0x0E", "1- REGISTRADOR PARA COMPARAÇÃO\n2- REGISTRADOR PARA COMPARAÇÃO\n3- NOME DA FUNÇÃO PARA O SALTO"],
    ["JNN", "SALTA PARA UMA FUNÇÃO QUANDO O VALOR DE UM REGISTRADOR NÃO É NULO, NÃO TIRA OS VALORES DOS REGISTRADORES", "0x15", "1- REGISTRADOR PARA VERIFICAÇÃO\n2- NOME DA FUNÇÃO PARA O SALTO"],
    ["JMP", "SALTA PARA UMA FUNÇÃO SEM CONDIÇÕES", "0x0F", "1- NOME DA FUNÇÃO PARA O SALTO"],
    ["CPY", "COPIA O VALOR DE UM REGISTRADOR PARA OUTRO", "0x10", "1- REGISTRADOR COM VALOR PARA SER COPIADO\n2- REGISTRADOR QUE RECEBERÁ A CÓPIA DO VALOR"],
    ["CUT", "RETIRA O VALOR DE UM REGISTRADOR E COLOCA EM OUTRO", "0x16", "1- REGISTRADOR DE ONDE SERÁ RETIRADO O VALOR\n2 - REGISTRADOR ONDE SERÁ COLOCADO O VALOR"],
    ["DEL", "RETIRA O VALOR DE UM REGISTRADOR", "0x17", "1- REGISTRADOR DE ONDE SERÁ RETIRADO O VALOR"],
    ["DEF", "DEFINE UMA FUNÇÃO, SALTOS APENAS PODEM SER EXECUTADOS PARA FUNÇÕES DEFINIDAS", "0x11", "1- NOME DA FUNÇÃO EM TEXTO SEM CARACTERES ESPECIAIS"],
    ["RET", "RETORNA APÒS UM RETORNA DE UMA FUNÇÃO PARA O LOCAL ONDE FOI CHAMADA", "0x12", "NENHUM"],
    ["HALT", "ENCERRA EXECUÇÃO", "0xFF", "NENHUM"]
];

const prefixesData = [
    ["Prefixo R", "Utilizado em todos os 4 registradores disponíveis para utilização do usuário (A, B, C , D)", "OUT RA -> Realiza a leitura e saída em tela do valor presente no Registrador A"],
    ["Prefixo @", "Utilizado para sinalizar uma posição na memória RAM (apenas disponível utilizar a partir do @8,X, pois os primeiros registros são utilizados para manter o código compilado), o primeiro número representa a linha e o segundo a coluna da memória a ser utilizada, podendo ir de 8 até 15 nas linhas e 0 até 15 nas colunas.", "SAVE RA @8,0 -> Salva o valor presente no Registrador A na posição de memória 8,0"],
    ["Número sem prefixo", "Assim se declara um valor numérico", "MOV 5 RA -> Coloca o valor 5 no Registrador A"]
];

function populateOpcodesTable() {
    const tbody = document.querySelector('#opcodesTable tbody');
    tbody.innerHTML = '';

    opcodesData.forEach(row => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
                    <td class="opcode-cell">${row[0]}</td>
                    <td>${row[1]}</td>
                    <td class="hex-cell">${row[2]}</td>
                    <td class="params-cell">${row[3]}</td>
                `;
        tbody.appendChild(tr);
    });
}

function populatePrefixesTable() {
    const tbody = document.querySelector('#prefixesTable tbody');
    tbody.innerHTML = '';

    prefixesData.forEach(row => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
                    <td class="prefix-cell">${row[0]}</td>
                    <td>${row[1]}</td>
                    <td class="example-cell">${row[2]}</td>
                `;
        tbody.appendChild(tr);
    });
}

function setCurrentState(data) {
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
    } else {
        $('#compiledCodeOutput')
            .val("")
            .trigger('change');
    }
}

function openModal() {
    document.getElementById('infoModal').style.display = 'block';
}

function closeModal() {
    document.getElementById('infoModal').style.display = 'none';
}

window.onclick = function (event) {
    const modal = document.getElementById('infoModal');
    if (event.target === modal) {
        closeModal();
    }
}

document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
        closeModal();
    }
});

document.addEventListener('DOMContentLoaded', function () {
    populateOpcodesTable();
    populatePrefixesTable();
});