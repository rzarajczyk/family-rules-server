document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

    const urlParams = new URLSearchParams(window.location.search)
    if (urlParams.has("logout")) {
        document.querySelector("#logged-out").style.display = 'block'
    }
    if (urlParams.has("error")) {
        document.querySelector("#error").style.display = 'block'
    }

    M.Modal.init(document.querySelector("#insiders-modal"), {
        dismissible: false,
        inDuration: 0
    })
    M.Modal.getInstance(document.querySelector("#insiders-modal")).open()
});