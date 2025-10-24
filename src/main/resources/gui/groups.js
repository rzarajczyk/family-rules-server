document.addEventListener("DOMContentLoaded", (event) => {
    const LOADING = '<center>... loading ...</center>'

    M.Datepicker.init(document.querySelector("#datepicker"), {
        defaultDate: new Date(),
        setDefaultDate: true,
        format: "yyyy-mm-dd",
        onClose: onDateChanged
    })

    function onDateChanged() {
        console.log('date changed')
        update()
    }

    function renderAppGroupCarousel(statisticsResponse) {
        try {
            const carousel = document.querySelector('#app-groups-carousel');
            
            if (!carousel) {
                console.error('Carousel element not found');
                return;
            }
            
            if (!statisticsResponse || !statisticsResponse.groups || statisticsResponse.groups.length === 0) {
                carousel.innerHTML = '<div class="carousel-item"><div class="center-align" style="padding: 20px; color: var(--md-sys-color-outline);">No app groups created yet</div></div>';
                // Initialize carousel with empty state
                try {
                    M.Carousel.init(carousel, {
                        numVisible: 1,
                        shift: 0,
                        padding: 20,
                        dist: 0,
                        indicators: true
                    });
                } catch (carouselError) {
                    console.error('Error initializing empty carousel:', carouselError);
                }
                return;
            }
            
            // Load the template
            Handlebars.fetchTemplate('./app-group-tile.handlebars')
                .then(([template]) => {
                    const html = statisticsResponse.groups.map(group => template(group)).join('');
                    carousel.innerHTML = html;
                    
                    // Initialize Materialize carousel
                    try {
                        M.Carousel.init(carousel, {
                            numVisible: 3,
                            shift: 0,
                            padding: 20,
                            dist: 0,
                            indicators: true
                        });
                    } catch (carouselError) {
                        console.error('Error initializing carousel:', carouselError);
                    }
                    
                    // Initialize remove group button handlers
                    initializeAppGroupCarouselHandlers();
                })
                .catch(templateError => {
                    console.error('Error loading template:', templateError);
                    carousel.innerHTML = '<div class="center-align" style="padding: 20px; color: red;">Error loading app group tiles</div>';
                });
        } catch (error) {
            console.error('Error in renderAppGroupCarousel:', error);
        }
    }

    function initializeAppGroupCarouselHandlers() {
        document.querySelectorAll('.app-group-tile-remove-btn').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const groupId = this.dataset.groupId;
                if (confirm('Are you sure you want to delete this app group? This will remove all apps from this group.')) {
                    deleteAppGroup(groupId);
                }
            });
        });
    }

    function deleteAppGroup(groupId) {
        ServerRequest.fetch(`/bff/app-groups/${groupId}`, {
            method: 'DELETE'
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Reload the page to show updated groups
                window.update();
            } else {
                console.error('Failed to delete app group');
            }
        })
        .catch(error => {
            console.error('Error deleting app group:', error);
        });
    }

    function update() {
        let date = document.querySelector("#datepicker").value
        ServerRequest.fetch(`/bff/app-groups/statistics?date=${date}`)
        .then(response => response.json())
        .then(renderAppGroupCarousel)
        .catch(error => {
            console.error('Error in update function:', error);
            document.querySelector("#app-groups-carousel").innerHTML = '<div class="center-align" style="padding: 20px; color: red;">Error loading data</div>';
        })
    }

    update()

    window.update = update
});
