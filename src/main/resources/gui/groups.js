document.addEventListener("DOMContentLoaded", (event) => {
    const LOADING = '<center>... loading ...</center>'
    let currentStatisticsData = null; // Store the statistics data for modal use

    M.Datepicker.init(document.querySelector("#datepicker"), {
        defaultDate: new Date(),
        setDefaultDate: true,
        format: "yyyy-mm-dd",
        onClose: onDateChanged
    })

    // Initialize modal
    const modal = document.getElementById('all-apps-modal');
    M.Modal.init(modal, {});

    function onDateChanged() {
        console.log('date changed')
        update()
    }

    function renderAppGroups(statisticsResponse) {
        try {
            // Store the data for modal use
            currentStatisticsData = statisticsResponse;
            
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

        document.querySelectorAll('.app-group-all-apps-btn').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const groupId = this.dataset.groupId;
                showAllAppsModal(groupId);
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
            if (data.success) {
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

    function showAllAppsModal(groupId) {
        if (!currentStatisticsData) {
            console.error('No statistics data available');
            return;
        }

        const group = currentStatisticsData.groups.find(g => g.id === groupId);
        if (!group) {
            console.error('Group not found:', groupId);
            return;
        }

        // Set modal title
        document.getElementById('modal-group-name').textContent = group.name;

        // Group apps by device
        const appsByDevice = {};
        group.apps.forEach(app => {
            if (!appsByDevice[app.deviceId]) {
                appsByDevice[app.deviceId] = {
                    deviceName: app.deviceName,
                    apps: []
                };
            }
            appsByDevice[app.deviceId].apps.push(app);
        });

        // Render apps grouped by device
        const modalContent = document.getElementById('modal-apps-content');
        let html = '';

        if (Object.keys(appsByDevice).length === 0) {
            html = '<p class="center-align" style="padding: 2rem; color: var(--md-sys-color-on-surface-variant);">No apps in this group</p>';
        } else {
            Object.entries(appsByDevice).forEach(([deviceId, deviceData]) => {
                html += `
                    <div class="modal-device-section">
                        <h6>${deviceData.deviceName}</h6>
                `;
                
                deviceData.apps.forEach(app => {
                    const screenTimeFormatted = formatScreenTime(app.screenTime);
                    html += `
                        <div class="modal-app-item">
                            <div class="modal-app-info">
                                <img src="${app.iconBase64 ? 'data:image/png;base64,' + app.iconBase64 : 'default-icon.png'}" 
                                     alt="${app.name}" 
                                     class="modal-app-icon circle">
                                <div class="modal-app-details">
                                    <span class="modal-app-name">${app.name}</span>
                                    <span class="modal-app-path">${app.packageName}</span>
                                </div>
                            </div>
                            ${app.screenTime > 0 ? `<span class="modal-app-usage">${screenTimeFormatted}</span>` : ''}
                            <button class="modal-app-remove-btn" 
                                    data-group-id="${groupId}" 
                                    data-device-id="${deviceId}" 
                                    data-app-path="${app.packageName}"
                                    title="Remove app from group">
                                <i class="material-icons">delete</i>
                            </button>
                        </div>
                    `;
                });
                
                html += '</div>';
            });
        }

        modalContent.innerHTML = html;

        // Add event listeners for remove buttons
        modalContent.querySelectorAll('.modal-app-remove-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                const groupId = this.dataset.groupId;
                const deviceId = this.dataset.deviceId;
                const appPath = this.dataset.appPath;
                
                if (confirm('Are you sure you want to remove this app from the group?')) {
                    removeAppFromGroup(groupId, deviceId, appPath);
                }
            });
        });

        // Open modal
        const modalInstance = M.Modal.getInstance(document.getElementById('all-apps-modal'));
        modalInstance.open();
    }

    function removeAppFromGroup(groupId, deviceId, appPath) {
        ServerRequest.fetch(`/bff/app-groups/${groupId}/apps/${encodeURIComponent(appPath)}?instanceId=${deviceId}`, {
            method: 'DELETE'
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // Close modal and reload data
                const modalInstance = M.Modal.getInstance(document.getElementById('all-apps-modal'));
                modalInstance.close();
                window.update();
            } else {
                console.error('Failed to remove app from group');
                alert('Failed to remove app from group');
            }
        })
        .catch(error => {
            console.error('Error removing app from group:', error);
            alert('Error removing app from group');
        });
    }

    function formatScreenTime(seconds) {
        if (seconds < 60) {
            return `${seconds}s`;
        } else if (seconds < 3600) {
            const minutes = Math.floor(seconds / 60);
            const secs = seconds % 60;
            return secs > 0 ? `${minutes}m ${secs}s` : `${minutes}m`;
        } else {
            const hours = Math.floor(seconds / 3600);
            const minutes = Math.floor((seconds % 3600) / 60);
            return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
        }
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
