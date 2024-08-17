document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

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

    let datepicker = M.Datepicker.init(document.querySelector("#datepicker"), {
        defaultDate: new Date(),
        setDefaultDate: true,
        format: "yyyy-mm-dd",
        onClose: onDateChanged
    })

    function initialRenderer(response) {
        let html = ''
        response.instances.forEach(it => {
            let li = `<li id="report-${it.instanceName}">
            <div class="collapsible-header">
                <i class="material-icons">devices</i>
                <div class="instance-name"><span class="new badge total-screen-time" data-badge-caption="">${formatScreenTime(it.screenTimeSeconds)}</span>${it.instanceName}</div>
            </div>
            <div class="collapsible-body">
                <div class="instance-report">
                <ul class="collection">
                    <li class="collection-item">
                        Device state
                        <span class="secondary-content device-state">
                                <label>
                                    <input data-instance="${it.instanceName}" name="${it.instanceName}" value="ACTIVE" type="radio" ${it.state.deviceState == "ACTIVE" ? "checked" : ""} />
                                    <span>Active</span>
                                </label>
                                <label>
                                    <input data-instance="${it.instanceName}" name="${it.instanceName}" value="LOCKED" type="radio" ${it.state.deviceState == "LOCKED" ? "checked" : ""} />
                                    <span>Locked down</span>
                                </label>
                                <label>
                                    <input data-instance="${it.instanceName}" name="${it.instanceName}" value="LOGGED_OUT" type="radio" ${it.state.deviceState == "LOGGED_OUT" ? "checked" : ""} />
                                    <span>Logged out</span>
                                </label>
                                <span id="countdown-${it.instanceName}" style="visibility: ${it.state.deviceState == "ACTIVE" ? "hidden" : "visible"}">
                                    <hr />
                                    <label>
                                        <input name="countdown-${it.instanceName}" data-instance="${it.instanceName}" type="checkbox" ${it.state.deviceStateCountdown > 0 ? "checked" : ""} />
                                        <span>With countdown</span>
                                    </label>
                                </span>
                        </span>
                    </li>
                    <li class="collection-item"></li> 
                `
            li += Object.keys(it.appUsageSeconds).map(app => {
                    return `<li class="collection-item">
                        ${app}
                        <span class="secondary-content">${formatScreenTime(it.appUsageSeconds[app])}</span>
                    </li>`
            }).join("")
            li += `
                </ul>
                </div>
            </div>
        </li>`
            html += li
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
});