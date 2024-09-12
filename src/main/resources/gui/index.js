document.addEventListener("DOMContentLoaded", (event) => {
    Handlebars.fetchTemplate("./index.handlebars")
        .then(([template]) => {
            M.Datepicker.init(document.querySelector("#datepicker"), {
                defaultDate: new Date(),
                setDefaultDate: true,
                format: "yyyy-mm-dd",
                onClose: onDateChanged
            })

            function render(response) {
                const html = response.instances.map(it => template(it)).join('')
                const instances = document.querySelector("#instances")
                instances.innerHTML = html
                instances.querySelectorAll(".instance-buttons a").forEach(it => {
                    it.addEventListener('click', onChangeStateClicked)
                })
                M.Collapsible.init(instances, {});
            }

            function onDateChanged() {
                console.log('date changed')
                update()
            }

            function onChangeStateClicked(e) {
                console.log('clicked')
                let instanceId = e.target.closest('.instance-details').dataset["instanceid"]
                console.log(instanceId)
                let div = document.querySelector("#instance-state-modal")
                let content = div.querySelector(".modal-content")
                content.innerHTML = '<center>... loading ...</center>'
                let modal = M.Modal.getInstance(div)
                modal.open()
                let templatePromise = Handlebars.fetchTemplate("./index-set-state.handlebars").then(([it]) => it)
                let dataPromise = ServerRequest.fetch(`/bff/instance-state?instanceId=${instanceId}`).then(it => it.json())
                Promise.all([templatePromise, dataPromise])
                    .then(([setStateTemplate, data]) => {
                        data.availableStates.unshift({
                            title: "Automatic",
                            deviceState: null,
                            icon: "<path d=\"m354-287 126-76 126 77-33-144 111-96-146-13-58-136-58 135-146 13 111 97-33 143ZM233-120l65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Zm247-350Z\"/>",
                            description: "Automatic - based on schedule."
                        })
                        content.innerHTML = setStateTemplate(data)
                        content.querySelectorAll('a').forEach(it => {
                            it.addEventListener('click', (e) => {
                                let deviceState = e.target.closest('a').dataset["devicestate"]
                                console.log(`Setting state of ${instanceId} to ${deviceState}`)
                                ServerRequest.fetch(`/bff/instance-state?instanceId=${instanceId}`, {
                                    method: 'POST',
                                    body: JSON.stringify({forcedDeviceState: deviceState})
                                }).then(response => {
                                    M.toast({text: "Saved"})
                                    modal.close()
                                    update()
                                })
                            })
                        })
                    })
            }

            function update() {
                document.querySelector("#instances").innerHTML = "<center>... fetching data ...</center>"
                let date = document.querySelector("#datepicker").value
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(response => render(response))
            }

            update()
    })
});