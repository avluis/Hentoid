function processAnchiraData(a) {
    if (typeof a !== 'undefined' && typeof a.published_at !== 'undefined') {
        anchiraJsInterface.transmit(JSON.stringify(a))
    }
}