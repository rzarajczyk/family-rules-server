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

    function renderAppGroups(statisticsResponse) {
        try {
            const appGroupsContainer = document.querySelector('#app-groups');
            
            if (!appGroupsContainer) {
                console.error('App groups container not found');
                return;
            }
            
            if (!statisticsResponse || !statisticsResponse.groups || statisticsResponse.groups.length === 0) {
                appGroupsContainer.innerHTML = '<li><div class="center-align" style="padding: 20px; color: var(--md-sys-color-outline);">No app groups created yet</div></li>';
                return;
            }
            
            // Load the template
            Handlebars.fetchTemplate('./app-group-collapsible.handlebars')
                .then(([template]) => {
                    // Sort apps by screen time (usage) in descending order for each group
                    const sortedGroups = statisticsResponse.groups.map(group => {
                        if (group.apps && Array.isArray(group.apps)) {
                            group.apps = group.apps.sort((a, b) => (b.screenTime || 0) - (a.screenTime || 0));
                        }
                        return group;
                    });
                    
                    const html = sortedGroups.map(group => template(group)).join('');
                    appGroupsContainer.innerHTML = html;
                    
                    // Initialize Materialize collapsibles
                    try {
                        M.Collapsible.init(appGroupsContainer, {});
                    } catch (collapsibleError) {
                        console.error('Error initializing collapsibles:', collapsibleError);
                    }
                    
                    // Initialize remove group button handlers
                    initializeAppGroupHandlers();
                })
                .catch(templateError => {
                    console.error('Error loading template:', templateError);
                    appGroupsContainer.innerHTML = '<li><div class="center-align" style="padding: 20px; color: red;">Error loading app groups</div></li>';
                });
        } catch (error) {
            console.error('Error in renderAppGroups:', error);
        }
    }

    function initializeAppGroupHandlers() {
        document.querySelectorAll('.app-group-remove-btn').forEach(btn => {
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
        .then(renderAppGroups)
        .catch(error => {
            console.error('Error in update function:', error);
            document.querySelector("#app-groups").innerHTML = '<li><div class="center-align" style="padding: 20px; color: red;">Error loading data</div></li>';
        })
    }

    update()

    window.update = update
});
