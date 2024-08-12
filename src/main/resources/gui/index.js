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
            let li = `<li>
            <div class="collapsible-header">
                <i class="material-icons">devices</i>
                <div class="instance-name"><span class="new badge" data-badge-caption="">${formatScreenTime(it.screenTimeSeconds)}</span>${it.instanceName}</div>
            </div>
            <div class="collapsible-body">
                <div class="instance-report">
                <ul class="collection">`
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
    }

    function onDateChanged() {
        console.log('date changed')
        update(intervalRefresher)
    }

    function intervalRefresher(response) {
        response.instances.forEach(it => {
            document.querySelector(`#screen-time-${it.instanceName}`).innerHTML = formatScreenTime(it.screenTimeSeconds)
        })
    }

    function update(handler) {
        let date = document.querySelector("#datepicker").value
        let headers = new Headers();
        headers.set('Authorization', 'Basic ' + btoa(getCookie("fr_username") + ":" + getCookie("fr_token")));
        headers.set('x-seed', getCookie("fr_seed"));
        fetch(`/bff/dailyAppUsage?date=${date}`, { headers: headers })
            .then(response => response.json())
            .then(response => handler(response))
    }

    update(initialRenderer)
    setInterval(() => { update(intervalRefresher) }, 15000)
});