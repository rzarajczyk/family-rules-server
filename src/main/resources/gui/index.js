document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();


    function formatScreenTime(seconds) {
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
    }

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
        return formatScreenTime(options.fn(this));
    })


    fetch("./index.handlebars")
        .then(it => it.text())
        .then(template => {
            template = Handlebars.compile(template)

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

            let datepicker = M.Datepicker.init(document.querySelector("#datepicker"), {
                defaultDate: new Date(),
                setDefaultDate: true,
                format: "yyyy-mm-dd",
                onClose: onDateChanged
            })

            function initialRenderer(response) {
                let html = ''
                response.instances.forEach(it => {
                    html += template(it)
                })
                let instances = document.querySelector("#instances")
                instances.innerHTML = html
                M.Collapsible.init(instances, {});
                document.querySelectorAll("select").forEach(it => M.FormSelect.init(it, {}))
                document.querySelectorAll(".device-state input").forEach(it => {
                    it.addEventListener("change", (e) => onInstanceStateChanged(e))
                })
            }

            function onInstanceStateChanged(evt) {        
                let instanceName = evt.target.dataset["instance"]
                let deviceState = document.querySelector(`input[name="${instanceName}"]:checked`).value
                let countdown = document.querySelector(`input[name=countdown-${instanceName}]`).checked ? 60 : 0

                document.querySelector(`#countdown-${instanceName}`).style.visibility = deviceState == "ACTIVE" ? "hidden" : "visible"

                updateState(instanceName, {
                    "deviceState": deviceState,
                    "deviceStateCountdown": countdown
                })
            }

            function updateState(instanceName, state) {
                let headers = new Headers()
                headers.set('Authorization', 'Basic ' + btoa(getCookie("fr_username") + ":" + getCookie("fr_token")))
                headers.set('x-seed', getCookie("fr_seed"))
                headers.set('Content-Type', "application/json");
                fetch(`/bff/state?instanceName=${instanceName}`, {
                    method: 'POST',
                    headers: headers,
                    body: JSON.stringify(state)
                }).then(response => {
                    M.toast({text: "Saved"})
                })
            }

            function onDateChanged() {
                console.log('date changed')
                update(intervalRefresher)
            }

            function intervalRefresher(response) {
                response.instances.forEach(it => {
                    document.querySelector(`#report-${it.instanceName} .total-screen-time`).innerHTML = formatScreenTime(it.screenTimeSeconds)
                })
            }

            function update(handler) {
                let date = document.querySelector("#datepicker").value
                let headers = new Headers();
                headers.set('Authorization', 'Basic ' + btoa(getCookie("fr_username") + ":" + getCookie("fr_token")));
                headers.set('x-seed', getCookie("fr_seed"));
                fetch(`/bff/status?date=${date}`, { headers: headers })
                    .then(response => response.json())
                    .then(response => handler(response))
            }

            update(initialRenderer)
            // setInterval(() => { update(intervalRefresher) }, 15000)

    })
});