DAYS = Array.from(['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN',])

class ScheduleRegistry {
    constructor() {
        this.schedules = new Map()
        this.transaction = null
    }

    get(instanceName, day) {
        let schedulesPerInstance = this.schedules.get(instanceName) ?? new Map()
        return schedulesPerInstance.get(day)
    }

    getForInstance(instanceName) {
        let schedulesPerInstance = this.schedules.get(instanceName) ?? new Map()
        return schedulesPerInstance
    }

    set(instanceName, day, schedule) {
        let schedulesPerInstance = this.schedules.get(instanceName) ?? new Map()
        schedulesPerInstance.set(day, schedule)
        this.schedules.set(instanceName, schedulesPerInstance)
    }

    getTransaction() {
        return this.transaction.schedule
    }

    getTransactionInstanceName() {
            return this.transaction.instanceName
    }

    beginTransaction(instanceName, day) {
        this.transaction = {
            schedule: this.get(instanceName, day).clone(),
            instanceName: instanceName
        }
    }

    commitTransaction(days) {
        console.log(days)
        days.forEach(day => {
            this.set(this.transaction.instanceName, day, this.transaction.schedule.clone())
        })
    }

    revertTransaction() {
        this.transaction = null
    }
}

class DeviceSchedule {
    constructor(defValue = 'ACTIVE') {
        this.schedule = Array(24 * 60).fill(defValue)
        this.defValue = defValue
    }

    clone() {
        let result = new DeviceSchedule(this.defValue)
        result.schedule = [...this.schedule]
        return result
    }

    // Helper method to convert "HH:MM:SS" to minutes since midnight
    parse(time) {
        const [hours, minutes, seconds] = time.split(":").map(Number);
        return hours * 60 + minutes;
    }

    // Helper method to convert minutes since midnight to "HH:MM:00"
    render(minutes) {
        const hours = String(Math.floor(minutes / 60)).padStart(2, '0')
        const mins = String(minutes % 60).padStart(2, '0')
        let result = `${hours}:${mins}`
        if (result == "24:00") {
            return "00:00"
        } else {
            return result
        }
    }

    // Get all periods
    getPeriods() {
        let periods = []
        let current = ""
        let start = -1
        for (let i=0; i<this.schedule.length-1; i++) {
            let state = this.schedule[i]
            if (current != state) {
                if (i > 0) {
                    periods.push({
                        state: current,
                        id: start,
                        start: this.render(start),
                        end: this.render(i)
                    })
                }
                current = state
                start = i
            }
        }
        periods.push({
            state: current,
            id: start,
            start: this.render(start),
            end: this.render(this.schedule.length)
        })
        return periods;
    }

    // Add a period
    addPeriod(startTime, endTime, state) {
        const start = this.parse(startTime)
        const end = this.parse(endTime)
        if (start < end) {
            this.schedule = this.schedule.fill(state, start, end)
        } else if (start > end) {
            this.schedule = this.schedule.fill(state, start, this.parse("24:00:00"))
            this.schedule = this.schedule.fill(state, this.parse("00:00:00"), end)
        }
    }

    // Delete a period by id
    deletePeriod(id) {
        let state = this.schedule[id]
        for (let i=id; i<this.schedule.length-1; i++) {
            if (this.schedule[i] == state) {
                this.schedule[i] = this.defValue
            } else {
                return
            }
        }
    }
}

document.addEventListener("DOMContentLoaded", (event) => {

    M.Timepicker.init(document.querySelectorAll(".timepicker"), {
        twelveHour: false,
        container: "body",
        autoClose: true
    })

    window.schedules = new ScheduleRegistry()

    Handlebars.fetchTemplate("./schedule.handlebars", "./schedule-row.handlebars")
        .then(([template, rowTemplate]) => {
            function parse(response) {
                response.instances.forEach(it => {
                    let instanceName = it['instanceName']
                    DAYS.forEach(day => window.schedules.set(instanceName, day, new DeviceSchedule()))
                    Object.keys(it.weeklySchedule.schedules).forEach(day => {
                        let schedule = new DeviceSchedule()
                        it.weeklySchedule.schedules[day].periods.forEach(period => {
                            schedule.addPeriod(period.from, period.to, period.state)
                        })
                        window.schedules.set(instanceName, day, schedule)
                    })
                })
                return response
            }

            function render(response) {
                const html = response.instances.map(it => template(it)).join('')
                const instances = document.querySelector("#instances")
                instances.innerHTML = html
                M.Collapsible.init(instances, {});

                response.instances.forEach(it => {
                    let instanceName = it['instanceName']

                    DAYS.forEach(day => {
                        let scheduleContainer = document.querySelector(`#schedule-${instanceName}-${day}`)
                        scheduleContainer.innerHTML = `
                           <div id="schedule-${instanceName}-${day}">
                               <table class="highlight">
                                   <thead>
                                   <tr>
                                       <th>Time</th>
                                       <th>State</th>
                                   </tr>
                                   </thead>
                                   <tbody>
                                   </tbody>
                               </table>
                           </div>
                       `
                       let schedule = window.schedules.get(instanceName, day)
                       renderSchedule(schedule, scheduleContainer)
                    })

                    let tabs = M.Tabs.init(document.querySelector(`#schedule-${instanceName} .tabs`), { swipeable: false })
                    tabs.select(`schedule-${instanceName}-MON`)
                })

                document.querySelectorAll('.edit-button').forEach(it => {
                    it.addEventListener('click', it => {
                        let instanceName = it.target.closest('.schedule').dataset['instance']
                        let div = document.querySelector("#edit-schedule-modal")
                        let modal = M.Modal.getInstance(div)
                        let selectedTabIndex = M.Tabs.getInstance(document.querySelector(`#schedule-${instanceName} .tabs`)).index
                        let day = DAYS[selectedTabIndex]
                        div.querySelector('input[type=checkbox]').checked = false
                        div.querySelector(`#${day}`).checked = true

                        window.schedules.beginTransaction(instanceName, day)

                        renderSchedule(window.schedules.getTransaction(), div, true)
                        modal.open()
                    })
                })
            }

            function renderSchedule(schedule, div, editMode = false) {
                let html = schedule.getPeriods().map(it => rowTemplate(it)).join('')
                if (editMode) {
                    html += `<tr>
                                <td>
                                    <a class="btn add-period-button"><i class="material-icons">add</i> Add period</a>
                                </td>
                                <td></td>
                            </tr>`
                }
                div.querySelector("tbody").innerHTML = html
                M.AutoInit(div)

                if (editMode) {
                    div.querySelector(".add-period-button").addEventListener("click", () => {
                        let modal = M.Modal.getInstance(document.querySelector('#add-period-modal'))
                        modal.open()
                    })
                }
            }

            document.querySelector("#add-period-modal-ok").addEventListener('click', () => {
                let modal = M.Modal.getInstance(document.querySelector('#add-period-modal'))
                let start = document.querySelector("#add-period-modal-start").value
                let end = document.querySelector("#add-period-modal-end").value
                let state = document.querySelector(`input[name="add-period-modal-device-state"]:checked`).value
                let schedule = window.schedules.getTransaction()
                schedule.addPeriod(start, end, state)
                renderSchedule(schedule, document.querySelector("#edit-schedule-modal"), true)
                modal.close()
            })

            document.querySelector("#edit-schedule-modal-ok").addEventListener('click', () => {
                let instanceName = window.schedules.getTransactionInstanceName()
                let selectedDays = DAYS.filter(day => document.querySelector(`#${day}`).checked)
                window.schedules.commitTransaction(selectedDays)
                M.Modal.getInstance(document.querySelector('#edit-schedule-modal')).close()
                save(instanceName, window.schedules.getForInstance(instanceName))
            })

            function save(instanceName, schedules) {
                let state = {schedules: {}}
                for (const [day, schedule] of schedules) {
                    state.schedules[day] = {
                        periods: schedule.getPeriods()
                            .map(it => ({state: it.state, from: it.start, to: it.end}) )
                    }
                }

                ServerRequest.fetch(`/bff/schedule?instanceName=${instanceName}`, {
                    method: 'POST',
                    body: JSON.stringify(state)
                }).then(response => {
//                    M.toast({text: "Saved"})
                    update()
                })
            }

            function update() {
                let date = today()
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(response => parse(response))
                    .then(response => render(response))
            }

            update()
    })
});