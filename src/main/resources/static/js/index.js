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

})

function executeCode() {

    $.ajax({
        type: 'get',
        url: 'api/processor/execute',
        context: document.body,
        contentType: 'application/json',
        success: (data, status) => {
            $('#outputField').val(data.data)
            applyMemoryState(data.memoryState);
        },
        error: (error, type) => {
            console.error("error:", error);
            alert(`Error: ${error.responseJSON?.message ?? type}`);
        }
    })
}

function applyMemoryState(memoryState) {
    memoryState.forEach((row, rowIdx) => {
        row.forEach((col, colIdx) => {
            const memoryRef = $(`#table-row-${rowIdx}-col-${colIdx}`);
            memoryRef.empty().append(col ?? '0000')
        })
    })
}