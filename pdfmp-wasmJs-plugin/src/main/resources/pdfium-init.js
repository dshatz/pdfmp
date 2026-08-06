var Module = typeof Module != "undefined" ? Module : {};
window.awaitPdfium = new Promise((resolve) => {
    if (typeof Module !== 'undefined' && typeof Module._FPDF_InitLibrary === 'function') {
        resolve();
        return;
    }

    var oldInit = Module.onRuntimeInitialized;
    Module.onRuntimeInitialized = function() {
        if (oldInit) oldInit();
        resolve();
    };

    // Fallback polling safety net in case the hook was missed
    var checkInterval = setInterval(() => {
        if (typeof Module !== 'undefined' && typeof Module._FPDF_InitLibrary === 'function') {
            clearInterval(checkInterval);
            resolve();
        }
    }, 50);
});