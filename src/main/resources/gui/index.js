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
                M.Collapsible.init(instances, {});
            }

            function onDateChanged() {
                console.log('date changed')
                update()
            }

            function update() {
                document.querySelector("#instances").innerHTML = ""
                let date = document.querySelector("#datepicker").value
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(response => render(response))
            }

            update()
    })
});