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

function today() {
    let date = new Date()
    const yyyy = date.getFullYear();
    const mm = String(date.getMonth() + 1).padStart(2, '0');
    const dd = String(date.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

function formatState(state) {
    if (state == "ACTIVE") {
        return `<i class="material-icons tiny">check_circle</i> Active`
    } else if (state == "LOCKED") {
        return `<i class="material-icons tiny">logout</i> Logged out`
    } else if (state == "LOGGED_OUT") {
        return `<i class="material-icons tiny">lock</i> Locked`
    } else if (state == "APP_DISABLED") {
        return `<i class="material-icons tiny">warning</i> App disabled!`
    } else if (state == "") {
        return `<i class="material-icons tiny">star</i> <span style="font-style: italic">Automatic</span>`
    } else {
        return state
    }
}

document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

    Handlebars.registerHelper('ifEquals', function(a, b, options) {
        if (a == b) {
            return options.fn(this);
        } else {
            return options.inverse(this);
        }
    })

    Handlebars.registerHelper('ifNotEquals', function(a, b, options) {
        if (a != b) {
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

    Handlebars.registerHelper('formatState', function(options) {
        let state = options.fn(this);
        return formatState(state)
    })

    Handlebars.registerHelper('toLowerCase', function(str) {
      return str.toLowerCase();
    });

    Handlebars.registerHelper('br', function(text) {
        text = Handlebars.Utils.escapeExpression(text);
        text = text.replace(/(\r\n|\n|\r)/gm, '<br>');
        return new Handlebars.SafeString(text);
    });

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

    Handlebars.fetchTemplate = function (...urls) {
        const fetchPromises = urls.map(url =>
            fetch(url)
                .then(response => response.text())
                .then(template => Handlebars.compile(template))
        );

        return Promise.all(fetchPromises);
    }

    document.querySelectorAll('span.format-state').forEach(it => {
        it.innerHTML = formatState(it.innerHTML)
    })

    const elements = Array.from(document.querySelectorAll('*[data-source]'))
    const promises = elements.map(element => {
        const source = element.dataset['source']
        return Handlebars.fetchTemplate(source)
            .then(([template]) => {
                element.outerHTML = template({})
                return template
            })
    })
    Promise.all(promises).then(() => {
        M.AutoInit()
        document.querySelectorAll('header ul').forEach(ul => {
            ul.querySelectorAll('li a').forEach(link => {
                if (link.getAttribute('href') === window.location.pathname.split('/').pop()) {
                    link.parentElement.classList.add('active');
                }
            })
        })

    })

    fetch("/bff/time")
        .then(it => it.json())
        .then(time => {
            document.querySelectorAll(".clock").forEach(it => {
                it.innerHTML = time.time
            })
        })
})