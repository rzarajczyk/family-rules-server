document.addEventListener("DOMContentLoaded", (event) => {
    Handlebars.fetchTemplate("./index.handlebars")
        .then(([template]) => {
            const LOADING = '<center>... loading ...</center>'

            M.Datepicker.init(document.querySelector("#datepicker"), {
                defaultDate: new Date(),
                setDefaultDate: true,
                format: "yyyy-mm-dd",
                onClose: onDateChanged
            })

            M.Timepicker.init(document.querySelectorAll(".timepicker"), {
                twelveHour: false,
                container: "body",
                autoClose: true
            })

            function render(response) {
                const html = response.instances.map(it => template(it)).join('')
                const instances = document.querySelector("#instances")
                instances.innerHTML = html
                instances.querySelectorAll(".instance-buttons a.change-state").forEach(it => {
                    it.addEventListener('click', onChangeStateClicked)
                })
                instances.querySelectorAll(".instance-buttons a.client-info").forEach(it => {
                    it.addEventListener('click', onClientInfoClicked)
                })
                instances.querySelectorAll(".instance-buttons a.client-edit").forEach(it => {
                    it.addEventListener('click', onClientEditClicked)
                })
                document.querySelectorAll("#instance-edit-save").forEach(it => {
                    it.addEventListener('click', onClientEditSaveClicked)
                })
                instances.querySelectorAll("a.edit-schedule").forEach(it => {
                    it.addEventListener('click', onEditScheduleClicked)
                })
                M.Collapsible.init(instances, {});
            }

            function onDateChanged() {
                console.log('date changed')
                update()
            }

            function onChangeStateClicked(e) {
                openModal({
                    e: e,
                    selector: "#instance-state-modal",
                    templateUrl: "./index-set-state.handlebars",
                    detailsUrlBuilder: instanceId => `/bff/instance-state?instanceId=${instanceId}`
                })
                    .then(([setStateTemplate, data]) => {
                        let content = document.querySelector("#instance-state-modal .modal-content")
                        let instanceId = content.dataset['instanceid']
                        data.availableStates.unshift({
                            title: "Automatic",
                            deviceState: null,
                            icon: "<path d=\"m354-287 126-76 126 77-33-144 111-96-146-13-58-136-58 135-146 13 111 97-33 143ZM233-120l65-281L80-590l288-25 112-265 112 265 288 25-218 189 65 281-247-149-247 149Zm247-350Z\"/>",
                            description: "Based on schedule."
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
                                    M.Modal.getInstance(document.querySelector("#instance-state-modal")).close()
                                    update()
                                })
                            })
                        })
                    })
            }

            function onClientInfoClicked(e) {
                openModal({
                    e: e,
                    selector: "#instance-info-modal",
                    templateUrl: "./index-info.handlebars",
                    detailsUrlBuilder: instanceId => `/bff/instance-info?instanceId=${instanceId}`
                })
                    .then(([tpl, data]) => {
                        let content = document.querySelector("#instance-info-modal .modal-content")
                        content.innerHTML = tpl(data)
                    })
            }

            function onClientEditClicked(e) {
                openModal({
                    e: e,
                    selector: "#instance-edit-modal",
                    templateUrl: "./index-edit.handlebars",
                    detailsUrlBuilder: instanceId => `/bff/instance-edit-info?instanceId=${instanceId}`
                })
                    .then(([tpl, data]) => {
                        let content = document.querySelector("#instance-edit-modal .modal-content")
                        content.innerHTML = tpl(data)
                    })
            }

            function onClientEditSaveClicked() {
                let content = document.querySelector("#instance-edit-modal .modal-content")
                let instanceId = content.dataset['instanceid']
                let instanceName = document.querySelector("#instance-name").value
                ServerRequest.fetch(`/bff/instance-edit-info?instanceId=${instanceId}`, {
                    method: 'POST',
                    body: JSON.stringify({ instanceName })
                }).then(response => {
                    M.toast({text: "Saved"})
                    M.Modal.getInstance(document.querySelector("#instance-edit-modal")).close()
                    update()
                })
            }

            function onEditScheduleClicked(e) {
                openModal({
                    e: e,
                    selector: "#instance-schedule-modal",
                    templateUrl: "./index-schedule.handlebars",
                    detailsUrlBuilder: instanceId => `/bff/instance-schedule?instanceId=${instanceId}`
                })
                    .then(([tpl, data]) => {
                        let content = document.querySelector("#instance-schedule-modal .modal-content")
                        renderTimetable(tpl, content)
                        renderSchedule(data, content)
                    })
            }

            function openModal(options) {
                const { e, selector, templateUrl, detailsUrlBuilder } = options
                let instanceId = e.target.closest('.instance-details').dataset["instanceid"]
                let div = document.querySelector(selector)
                let content = div.querySelector(".modal-content")
                content.dataset['instanceid'] = instanceId
                content.innerHTML = LOADING
                let modal = M.Modal.getInstance(div)
                modal.open()
                let templatePromise = Handlebars.fetchTemplate(templateUrl).then(([it]) => it)
                let dataPromise = ServerRequest.fetch(detailsUrlBuilder(instanceId)).then(it => it.json())
                return Promise.all([templatePromise, dataPromise])
            }

            function update() {
                document.querySelector("#instances").innerHTML = LOADING
                let date = document.querySelector("#datepicker").value
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(response => render(response))
            }

            update()

            window.update = update
    })
});