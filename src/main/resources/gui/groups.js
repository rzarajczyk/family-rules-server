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
                showError('App groups container not found');
                return;
            }
            
            if (!statisticsResponse || !statisticsResponse.groups || statisticsResponse.groups.length === 0) {
                appGroupsContainer.innerHTML = '<li><div class="center-align" style="padding: 20px; color: var(--md-sys-color-outline);">No app groups created yet</div></li>';
                showContent();
                return;
            }
            
            // Load the template
            Handlebars.fetchTemplate('./app-group-collapsible.handlebars')
                .then(([template]) => {
                    // Apps are already sorted by the server, so we can use them directly
                    const html = statisticsResponse.groups.map(group => template(group)).join('');
                    appGroupsContainer.innerHTML = html;
                    
                    // Initialize Materialize collapsibles
                    try {
                        M.Collapsible.init(appGroupsContainer, {});
                    } catch (collapsibleError) {
                        console.error('Error initializing collapsibles:', collapsibleError);
                    }
                    
                    // Initialize remove group button handlers
                    initializeAppGroupHandlers();
                    
                    // Initialize clickable app names
                    initializeClickableAppNames();
                    
                    // Show content after successful render
                    showContent();
                })
                .catch(templateError => {
                    console.error('Error loading template:', templateError);
                    showError('Failed to load app groups template. Please try again.');
                });
        } catch (error) {
            console.error('Error in renderAppGroups:', error);
            showError('An error occurred while rendering app groups. Please try again.');
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

        document.querySelectorAll('.app-group-rename-btn').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const groupId = this.dataset.groupId;
                const currentName = this.closest('.app-group-item').querySelector('.app-group-name').textContent;
                const newName = prompt('Enter new name for the group:', currentName);
                if (newName && newName.trim() !== '' && newName !== currentName) {
                    renameAppGroup(groupId, newName.trim());
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

    function renameAppGroup(groupId, newName) {
        ServerRequest.fetch(`/bff/app-groups/${groupId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ newName: newName })
        })
        .then(response => response.json())
        .then(data => {
            if (data.group) {
                // Reload the page to show updated groups
                window.update();
            } else {
                console.error('Failed to rename app group');
            }
        })
        .catch(error => {
            console.error('Error renaming app group:', error);
        });
    }

    function showLoading() {
        document.getElementById('loading').style.display = 'block';
        document.getElementById('error').style.display = 'none';
        document.getElementById('app-groups').style.display = 'none';
    }

    function showError(message) {
        const loading = document.getElementById('loading');
        const error = document.getElementById('error');
        const container = document.getElementById('app-groups');
        
        loading.style.display = 'none';
        container.style.display = 'none';
        
        document.getElementById('error-message').textContent = message;
        error.style.display = 'block';
    }

    function showContent() {
        document.getElementById('loading').style.display = 'none';
        document.getElementById('error').style.display = 'none';
        document.getElementById('app-groups').style.display = 'block';
    }

    function update() {
        showLoading();
        let date = document.querySelector("#datepicker").value
        ServerRequest.fetch(`/bff/app-groups/statistics?date=${date}`)
        .then(response => response.json())
        .then(renderAppGroups)
        .catch(error => {
            console.error('Error in update function:', error);
            showError('Failed to load app groups data. Please try again.');
        })
    }

    update()

    window.update = update
});

// Clickable app names functionality
function initializeClickableAppNames() {
    document.querySelectorAll('.clickable-app-name').forEach(appNameElement => {
        appNameElement.addEventListener('click', function(e) {
            e.stopPropagation();
            toggleAppNameDisplay(this);
        });
    });
}

function toggleAppNameDisplay(element) {
    const appName = element.dataset.appName;
    const appPath = element.dataset.appPath;
    const isShowingPath = element.classList.contains('showing-path');
    
    if (isShowingPath) {
        // Switch to showing app name
        element.textContent = appName;
        element.classList.remove('showing-path');
    } else {
        // Switch to showing app path
        element.textContent = appPath;
        element.classList.add('showing-path');
    }
}
