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

            function onEditScheduleClicked(e) {
                openModal({
                    e: e,
                    selector: "#instance-schedule-modal",
                    templateUrl: "./index-schedule.handlebars",
                    detailsUrlBuilder: instanceId => `/bff/instance-schedule?instanceId=${instanceId}`
                })
                    .then(([tpl, data]) => {
                        let content = document.querySelector("#instance-schedule-modal .modal-content")
                        let templateData = createTimetableData()
                        content.innerHTML = tpl(templateData)

                        console.log(data)
                        Object.keys(data.schedules).forEach(day => {
                            data.schedules[day].periods.forEach(period => {
                                if (period.state.deviceState != 'ACTIVE') {
                                    let d = day.toLowerCase()
//                                    sequence(period.from, period.to).forEach(time => {
//                                        let t = timeId(time)
//                                        let id = `schedule-${d}-${t}`
//                                        let cell = document.querySelector(`#${id}`)
//                                        cell.style.backgroundColor = 'red'
//                                        cell.classList.add('tooltipped')
//                                        cell.dataset['tooltip'] = period.state
//                                    })
                                    let fromId = `schedule-${d}-${timeId(period.from)}`
                                    let toId = `schedule-${d}-${timeId(period.to)}`

                                    let fromElement = document.querySelector(`#${fromId}`)
                                    let toElement = document.querySelector(`#${toId}`)

                                    let top = fromElement.offsetTop + 24
                                    let left = fromElement.offsetLeft + 24
                                    let height = toElement.offsetTop + toElement.clientHeight - fromElement.offsetTop
                                    let width = toElement.offsetLeft + toElement.clientWidth - fromElement.offsetLeft

                                    let div = document.createElement('div')
                                    div.style.position = 'absolute'
                                    div.style.left = `${left}px`
                                    div.style.top = `${top}px`
                                    div.style.padding = `0.5rem`
                                    div.style.backgroundColor = `var(--md-ref-palette-primary80)`
                                    div.style.width = `${width}px`
                                    div.style.height = `${height}px`
                                    div.style.borderRadius = '10px'
                                    div.style.textAlign = 'center'
                                    div.title = `${period.state.title}\n${formatTime(period.from)}-${formatTime(period.to)}`
                                    div.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" fill="var(--md-sys-color-on-background)" style="width: 100%; max-width: 48px; height: 100%; max-height: 48px;">${period.state.icon}</svg>`

                                    content.appendChild(div)
                                }
                            })
                        })
                        M.Tooltip.init(document.querySelectorAll('#instance-schedule-modal .tooltipped'), {})
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
    })
});