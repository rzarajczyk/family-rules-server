document.addEventListener("DOMContentLoaded", (event) => {
    Handlebars.fetchTemplate("./device-state.handlebars")
        .then(template => {
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
                ServerRequest.fetch(`/bff/state?instanceName=${instanceName}`, {
                    method: 'POST',
                    body: JSON.stringify(state)
                }).then(response => {
                    M.toast({text: "Saved"})
                })
            }

            function today() {
                let date = new Date()
                const yyyy = date.getFullYear();
                const mm = String(date.getMonth() + 1).padStart(2, '0');
                const dd = String(date.getDate()).padStart(2, '0');
                return `${yyyy}-${mm}-${dd}`;
              };

            function update(handler) {
                let date = today()
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(response => handler(response))
            }

            update(initialRenderer)
    })
});