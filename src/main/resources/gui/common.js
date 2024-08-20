document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

    Handlebars.registerHelper('ifEquals', function(a, b, options) {
        if (a === b) {
            return options.fn(this);
        } else {
            return options.inverse(this);
        }
    })

    Handlebars.registerHelper('ifGt', function(a, b, options) {
        if (a > b) {
            return options.fn(this);
        } else {
            return options.inverse(this);
        }
    })

    Handlebars.registerHelper('formatScreenTime', function(options) {
        let seconds = options.fn(this);

        // Calculate the hours, minutes, and remaining seconds
        var hours = Math.floor(seconds / 3600);
        var minutes = Math.floor((seconds % 3600) / 60);
        var remainingSeconds = seconds % 60;

        // Pad the minutes and seconds with leading zeros if necessary
        var formattedHours = hours > 0 ? hours + ":" : "";
        var formattedMinutes = (minutes < 10 ? "0" : "") + minutes;
        var formattedSeconds = (remainingSeconds < 10 ? "0" : "") + remainingSeconds;

        // Combine the parts into the final formatted string
        return formattedHours + formattedMinutes + ":" + formattedSeconds;
    })

    Handlebars.fetchTemplate = function (url) {
        return fetch(url)
            .then(it => it.text())
            .then(it => Handlebars.compile(it))
    }
})

const ServerRequest = {
    getCookie: function(name) {
        let cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            let [cookieName, cookieValue] = cookie.trim().split('=');
            if (cookieName === name) {
                return decodeURIComponent(cookieValue);
            }
        }
        return null;
    },

    fetch: function(url, options) {
        let headers = new Headers()
        headers.set('Authorization', 'Basic ' + btoa(ServerRequest.getCookie("fr_username") + ":" + ServerRequest.getCookie("fr_token")))
        headers.set('x-seed', ServerRequest.getCookie("fr_seed"))
        headers.set('Content-Type', "application/json");

        return fetch(url, {
            method: options?.method ?? "GET",
            headers: headers,
            body: options?.body
        })
    }
}