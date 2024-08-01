document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

    function setSessionCookie(name, value) {
        // Set the cookie without an expiration date to make it a session cookie
        document.cookie = `${name}=${encodeURIComponent(value)}; path=/;`;
    }

    document.querySelector("#login").addEventListener("click", () => {
        let username = document.querySelector("#username").value
        let password = document.querySelector("#password").value
        console.log(username)
        console.log(password)

        let headers = new Headers();
        headers.set('Authorization', 'Basic ' + btoa(username + ":" + password));
        fetch("/bff/login", {
            method: 'POST',
            headers: headers
        })
        .then(response => response.json())
        .then(response => {
            if (response.success) {
                setSessionCookie("fr_username", username)
                setSessionCookie("fr_token", response.token)
                setSessionCookie("fr_seed", response.seed)
                location.href = "/gui/index.html"
            } else {
                alert('Invalid username/password')
            }
        })
    })
});