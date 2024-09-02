document.addEventListener("DOMContentLoaded", (event) => {
    Handlebars.fetchTemplate("./device-state.handlebars")
        .then(([template]) => {
            function render(response) {
                const html = response.instances.map(it => template(it)).join('')
                const instances = document.querySelector("#instances")
                instances.innerHTML = html
                M.Collapsible.init(instances, {});

                document.querySelectorAll("select").forEach(it => M.FormSelect.init(it, {}))
                document.querySelectorAll(".device-state input[type=radio]").forEach(it => {
                    it.addEventListener("change", (e) => onInstanceStateChanged(e))
                })
                document.querySelectorAll(".device-state .save-button").forEach(it => {
                    it.addEventListener("click", (e) => onSave(e))
                })
            }

            function onInstanceStateChanged(evt) {
                let instanceName = evt.target.closest(".collection-item").dataset["instance"]
                let deviceState = document.querySelector(`input[name="device-state-${instanceName}"]:checked`).value
                
                document.querySelector(`#countdown-${instanceName}`).style.visibility = ["APP_DISABLED", "ACTIVE"].includes(deviceState) ? "hidden" : "visible"
            }

            function onSave(evt) {        
                let instanceName = evt.target.closest(".collection-item").dataset["instance"]
                let deviceState = document.querySelector(`input[name="device-state-${instanceName}"]:checked`).value
                let countdown = document.querySelector(`input[name=countdown-${instanceName}]`).checked ? 60 : 0

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

            function update() {
                let date = today()
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(response => render(response))
            }

            update()
    })
});