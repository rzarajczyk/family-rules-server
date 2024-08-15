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
            let locked = it.state.lockedSince != null
            let lockedSince = locked ? it.state.lockedSince : ""
            let loggedOut = it.state.loggedOutSince != null
            let loggedOutSince = loggedOut ? it.state.loggedOutSince : ""
            let li = `<li id="report-${it.instanceName}">
            <div class="collapsible-header">
                <i class="material-icons">devices</i>
                <div class="instance-name"><span class="new badge total-screen-time" data-badge-caption="">${formatScreenTime(it.screenTimeSeconds)}</span>${it.instanceName}</div>
            </div>
            <div class="collapsible-body">
                <div class="instance-report">
                    <ul class="collection">
                        <li class="collection-item">
                            Lock device
                            <span class="secondary-content">
                                <div class="switch">
                                    <label>
                                        Off
                                        <input type="checkbox" ${locked ? "checked" : ""}
                                            class="lock-checkbox" data-instance-name="${it.instanceName}" data-since="${lockedSince}">
                                        <span class="lever"></span>
                                        On
                                    </label>
                                </div>
                            </span>
                        </li>
                        <li class="collection-item">
                            Logout
                            <span class="secondary-content">
                                <div class="switch">
                                    <label>
                                        Off
                                        <input type="checkbox" ${loggedOut ? "checked" : ""}
                                            class="logout-checkbox" data-instance-name="${it.instanceName}" data-since="${loggedOutSince}">
                                        <span class="lever"></span>
                                        On
                                    </label>
                                </div>
                            </span>
                        </li>
                        <li class="collection-item">
                            <label>
                                <input type="checkbox" checked class="filled-in countdown-checkbox" />
                                <span>Enable countdown</span>
                            </label>
                            <div class="countdown-text pl-6 pt-2">
                                This settings enables 60 seconds countdown every time I turn on "Lock" or "Logout"
                            </div>
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
        document.querySelectorAll(".lock-checkbox, .logout-checkbox").forEach(it => {
            it.addEventListener("change", (e) => onInstanceStateChanged(e))
        })
    }

    function onInstanceStateChanged(evt) {        
        let instanceName = evt.target.dataset["instanceName"]
        let countdown = document.querySelector(`#report-${instanceName} .countdown-checkbox`).checked

        let modifyStateAndFetchSince = (name) => {
            let checkbox = document.querySelector(`#report-${instanceName} ${name}`)
            if (checkbox.checked) {
                let since = checkbox.dataset["since"]
                if (!since) {
                    since = Date.now()
                    if (countdown) {
                        since += 60 * 1000
                    
                    }
                    checkbox.dataset["since"] = new Date(since).toISOString()
                }
            } else {
                checkbox.dataset["since"] = ""
            }
            return checkbox.dataset["since"] ? checkbox.dataset["since"] : null
        }
        

        updateState(instanceName, {
            "lockedSince": modifyStateAndFetchSince('.lock-checkbox'),
            "loggedOutSince": modifyStateAndFetchSince('.logout-checkbox')
        })
    }

    function updateState(instanceName, state) {
        let headers = new Headers()
        headers.set('Authorization', 'Basic ' + btoa(getCookie("fr_username") + ":" + getCookie("fr_token")))
        headers.set('x-seed', getCookie("fr_seed"))
        headers.set('Content-Type', "application/json");
        document.querySelectorAll(".switch input[type=checkbox]").forEach(it => { it.disabled = true })
        fetch(`/bff/state?instanceName=${instanceName}`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(state)
        }).then(response => {
            document.querySelectorAll(".switch input[type=checkbox]").forEach(it => { it.disabled = false })
            M.toast({text: "Saved!"})
        })
    }

    function onDateChanged() {
        console.log('date changed')
        update(initialRenderer)
    }

    // function intervalRefresher(response) {
    //     response.instances.forEach(it => {
    //         document.querySelector(`#report-${it.instanceName} .total-screen-time`).innerHTML = formatScreenTime(it.screenTimeSeconds)
    //     })
    // }

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