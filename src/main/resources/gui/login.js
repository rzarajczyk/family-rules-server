document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

    function setSessionCookie(name, value) {
        // Set the cookie without an expiration date to make it a session cookie
        document.cookie = `${name}=${encodeURIComponent(value)}; path=/;`;
    }

    function setOneWeekCookie(name, value) {
        const expirationDate = new Date();
        expirationDate.setDate(expirationDate.getDate() + 7); // Add 7 days for one week

        document.cookie = `${name}=${encodeURIComponent(value)}; path=/; expires=${expirationDate.toUTCString()}`;
    }

    function deleteCookie(name) {
            document.cookie = `${name}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 UTC`;
    }

    function getCookie(name) {
        let cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            let [cookieName, cookieValue] = cookie.trim().split('=');
            if (cookieName === name) {
                return decodeURIComponent(cookieValue);
            }
        }
        return null;
    }

    let logout = new URLSearchParams(window.location.search).get('logout')
    if (logout) {
            deleteCookie("fr_username");
            deleteCookie("fr_token");
            deleteCookie("fr_seed");
    }


    let username = getCookie("fr_username")
    let token = getCookie("fr_token")
    let seed = getCookie("fr_seed")

    if (username && token && seed) {
        location.href = "/gui/index.html"
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
                setOneWeekCookie("fr_username", username)
                setOneWeekCookie("fr_token", response.token)
                setOneWeekCookie("fr_seed", response.seed)
                location.href = "/gui/index.html"
            } else {
                alert('Invalid username/password')
            }
        })
    })
});