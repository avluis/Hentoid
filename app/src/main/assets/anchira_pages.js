function processAnchiraData(a) {
    if (typeof a !== 'undefined' && typeof a.id !== 'undefined') {
        anchiraJsInterface.transmit(JSON.stringify(a))
    }
}