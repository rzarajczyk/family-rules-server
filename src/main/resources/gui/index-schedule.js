function formatTime(t) {
    return `${String(t.hour).padStart(2, '0')}:${String(t.minute).padStart(2, '0')}`
}

function timeId(t) {
    return formatTime(t).replace(':', '')
}

function parseTime(t) {
    const [hour, minute] = t.split(":").map(Number);
    return  { hour, minute };
}

function createTimetableData() {
    return {
        hours: Array.from({ length: 96 }, (_, i) => {
                   const hour = Math.floor(i / 4);  // There are 4 slots per hour (00, 15, 30, 45)
                   const minute = (i % 4) * 15;     // Minutes are in increments of 15
                   const timeFormatted =  formatTime({hour, minute});
                   const t = timeId({hour, minute})
                   return { hour, minute, timeFormatted, timeId: t };
                 }),
        days: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
    }
}

function renderTimetable(tpl, content) {
    let templateData = createTimetableData()
    content.innerHTML = tpl(templateData)
    content.querySelectorAll(".schedule-cell").forEach(it => {
        it.addEventListener('mouseover', (e) => {
            e.target.classList.add('add-period')
        })
        it.addEventListener('mouseout', (e) => {
            e.target.classList.remove('add-period')
        })
        it.addEventListener('click', (e) => {
            let day = e.target.dataset.day
            let time = { hour: e.target.dataset.hour, minute: e.target.dataset.minute }
            document.querySelector('#add-period-modal-start').value = formatTime(time)
            document.querySelector('#add-period-modal-start').disabled = false
            document.querySelector('#add-period-modal-end').value = formatTime({hour: parseInt(time.hour) + 1, minute: time.minute})
            document.querySelector('#add-period-modal-end').disabled = false
            document.querySelector('#add-period-modal-day').selectedIndex = templateData.days.indexOf(day)
            document.querySelector('#add-period-modal-day').disabled = false
            document.querySelector('#add-period-modal-ok').innerHTML = 'Add period'
            M.FormSelect.init(document.querySelector('#add-period-modal-day'), {})
            M.Modal.getInstance(document.querySelector('#add-period-modal')).open()
        })
    })
    let daysSelector = document.querySelector('#add-period-modal-day')
    daysSelector.innerHTML = ''
    templateData.days.forEach(day => {
        daysSelector.innerHTML += `<option value="${day}">${day}</option>`
    })
    M.FormSelect.init(daysSelector, {})
}

function renderSchedule(data, content) {
    Object.keys(data.schedules).forEach(day => {
        data.schedules[day].periods.forEach(period => {
            if (period.state.deviceState != 'ACTIVE') {
                let d = day.toLowerCase()
                let fromId = `schedule-${d}-${timeId(period.from)}`
                let toId = `schedule-${d}-${timeId(period.to)}`
                if (period.to.minute == 0) {
                    toId = `schedule-${d}-${timeId({hour: period.to.hour - 1, minute: 45})}`
                }

                let fromElement = document.querySelector(`#${fromId}`)
                let toElement = document.querySelector(`#${toId}`)

                let top = fromElement.offsetTop + 24
                let left = fromElement.offsetLeft + 24
                let height = toElement.offsetTop + toElement.clientHeight - fromElement.offsetTop
                let width = toElement.offsetLeft + toElement.clientWidth - fromElement.offsetLeft

                let div = document.createElement('div')
                div.style.left = `${left}px`
                div.style.top = `${top}px`
                div.style.width = `${width}px`
                div.style.height = `${height}px`
                div.title = `${period.state.title}\n${formatTime(period.from)}-${formatTime(period.to)}`
                div.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" fill="var(--md-sys-color-on-background)" style="width: 100%; max-width: 48px; height: 100%; max-height: 48px;">${period.state.icon}</svg>`
                div.classList.add('period')

                div.addEventListener('click', () => {
                    document.querySelector('#add-period-modal-start').value = formatTime(period.from)
                    document.querySelector('#add-period-modal-start').disabled = true
                    document.querySelector('#add-period-modal-end').value = formatTime(period.to)
                    document.querySelector('#add-period-modal-end').disabled = true
                    let index = Array.from(document.querySelectorAll('#add-period-modal-day option'))
                        .map(it => it.value.toUpperCase())
                        .indexOf(day)
                    document.querySelector('#add-period-modal-day').disabled = true
                    document.querySelector('#add-period-modal-day').selectedIndex = index
                    M.FormSelect.init(document.querySelector('#add-period-modal-day'), {})
                    document.querySelector('#add-period-modal-ok').innerHTML = 'Update'
                    M.Modal.getInstance(document.querySelector('#add-period-modal')).open()
                })

                content.appendChild(div)
            }
        })
    })
    document.querySelector("#add-period-modal-states").innerHTML = ''
    data.availableStates.forEach(state => {
        document.querySelector("#add-period-modal-states").innerHTML += `
        <label>
            <input name="add-period-modal-device-state" value="${state.deviceState}" type="radio" />
            <span>
                <span class="format-state">
                    <svg xmlns="http://www.w3.org/2000/svg" height="12px" viewBox="0 -960 960 960" width="12px" fill="#5f6368">${state.icon}</svg>
                    ${state.title}
                </span>
                ${state.description ?  `<br><i>${state.description}</i>` : ""}
            </span>
        </label>
        `
    })
    document.querySelector(`input[name="add-period-modal-device-state"]:first-child`).checked = true
    document.querySelector('#add-period-modal-ok').addEventListener('click', addPeriod)
}

function addPeriod() {
    let instanceId = document.querySelector("#instance-schedule-modal .modal-content").dataset['instanceid']
    let from = document.querySelector('#add-period-modal-start').value
    let to = document.querySelector('#add-period-modal-end').value
    let days = M.FormSelect.getInstance(document.querySelector('#add-period-modal-day')).getSelectedValues()
    let state = document.querySelector(`input[name="add-period-modal-device-state"]:checked`).value

    ServerRequest.fetch(`/bff/instance-schedule/add-period?instanceId=${instanceId}`, {
        method: 'POST',
        body: JSON.stringify({
            days: days.map(it => it.toUpperCase()),
            from: parseTime(from),
            to: parseTime(to),
            state
        })
    }).then(response => {
        M.toast({text: "Saved"})
        M.Modal.getInstance(document.querySelector("#add-period-modal")).close()
        refresh(instanceId)
    })
}

function refresh(instanceId) {
    ServerRequest
        .fetch(`/bff/instance-schedule?instanceId=${instanceId}`)
        .then(it => it.json())
        .then(data => {
            document.querySelectorAll('.period').forEach(it => it.remove())
            let content = document.querySelector("#instance-schedule-modal .modal-content")
            renderSchedule(data, content)
        })
}