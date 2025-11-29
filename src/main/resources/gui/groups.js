document.addEventListener("DOMContentLoaded", (event) => {
    Loading.init(document.getElementById('loading'))

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
    
    const groupStatesModal = document.getElementById('group-states-modal');
    M.Modal.init(groupStatesModal, {});
    
    const editStateModal = document.getElementById('edit-state-modal');
    M.Modal.init(editStateModal, {});
    
    const applyGroupStateModal = document.getElementById('apply-group-state-modal');
    M.Modal.init(applyGroupStateModal, {});

    function onDateChanged() {
        console.log('date changed')
        update()
    }

    function renderAppGroups(statisticsResponse) {
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

        // Fetch group states count for each group
        const statePromises = statisticsResponse.groups.map(group => 
            ServerRequest.fetch(`/bff/app-groups/${group.id}/states`)
                .then(response => response.json())
                .then(data => {
                    group.hasStates = data.states && data.states.length > 0;
                    return group;
                })
                .catch(error => {
                    console.error(`Error fetching states for group ${group.id}:`, error);
                    group.hasStates = false;
                    return group;
                })
        );

        Promise.all(statePromises).then(() => {
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
        });
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
                const currentName = this.closest('.fr-collapsible').querySelector('.fr-name-text').textContent;
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
        
        document.querySelectorAll('.app-group-states-btn').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const groupId = this.dataset.groupId;
                showGroupStatesModal(groupId);
            });
        });
        
        document.querySelectorAll('.change-group-state').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const groupId = this.dataset.groupId;
                showApplyGroupStateModal(groupId);
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

        // Show loading state
        document.getElementById('modal-loading').style.display = 'block';
        document.getElementById('modal-apps-content').style.display = 'none';

        // Open modal
        const modalInstance = M.Modal.getInstance(document.getElementById('all-apps-modal'));
        modalInstance.open();

        // Fetch all apps for this group
        ServerRequest.fetch(`/bff/app-groups/${groupId}/all-apps`)
            .then(response => response.json())
            .then(data => {
                renderAllAppsModal(groupId, data);
            })
            .catch(error => {
                console.error('Error loading apps:', error);
                document.getElementById('modal-loading').style.display = 'none';
                document.getElementById('modal-apps-content').innerHTML = 
                    '<p class="modal-no-apps">Failed to load apps. Please try again.</p>';
                document.getElementById('modal-apps-content').style.display = 'block';
            });
    }

    function renderAllAppsModal(groupId, allAppsData) {
        const modalContent = document.getElementById('modal-apps-content');
        let html = '';

        if (!allAppsData.devices || allAppsData.devices.length === 0) {
            html = '<p class="modal-no-apps">No apps found on any device</p>';
        } else {
            allAppsData.devices.forEach(device => {
                html += `
                    <div class="modal-device-section">
                        <h6>
                            <i class="material-icons">phone_android</i>
                            ${device.deviceName}
                        </h6>
                `;
                
                if (device.apps.length === 0) {
                    html += '<p style="padding: 1rem; color: var(--md-sys-color-on-surface-variant);">No apps on this device</p>';
                } else {
                    device.apps.forEach(app => {
                        const checkboxId = `app-${device.deviceId}-${app.packageName.replace(/[^a-zA-Z0-9]/g, '_')}`;
                        html += `
                            <div class="modal-app-item">
                                <label for="${checkboxId}">
                                    <input type="checkbox" 
                                           id="${checkboxId}"
                                           class="filled-in app-checkbox" 
                                           data-device-id="${device.deviceId}"
                                           data-app-path="${app.packageName}"
                                           ${app.inGroup ? 'checked' : ''} />
                                    <span class="modal-app-info">
                                        <img src="${app.iconBase64 ? 'data:image/png;base64,' + app.iconBase64 : 'default-icon.png'}" 
                                             alt="${app.name}" 
                                             class="modal-app-icon">
                                        <div class="modal-app-details">
                                            <span class="modal-app-name">${app.name}</span>
                                            <span class="modal-app-path">${app.packageName}</span>
                                        </div>
                                    </span>
                                </label>
                            </div>
                        `;
                    });
                }
                
                html += '</div>';
            });
        }

        modalContent.innerHTML = html;
        document.getElementById('modal-loading').style.display = 'none';
        modalContent.style.display = 'block';

        // Store initial state for comparison
        const initialState = new Map();
        document.querySelectorAll('.app-checkbox').forEach(checkbox => {
            const key = `${checkbox.dataset.deviceId}:${checkbox.dataset.appPath}`;
            initialState.set(key, checkbox.checked);
        });

        // Setup save button handler
        const saveBtn = document.getElementById('modal-save-btn');
        saveBtn.onclick = () => saveGroupChanges(groupId, initialState);
    }

    function saveGroupChanges(groupId, initialState) {
        const changes = {
            devices: new Map()
        };

        // Collect all changes
        document.querySelectorAll('.app-checkbox').forEach(checkbox => {
            const deviceId = checkbox.dataset.deviceId;
            const appPath = checkbox.dataset.appPath;
            const key = `${deviceId}:${appPath}`;
            const wasChecked = initialState.get(key);
            const isChecked = checkbox.checked;

            if (wasChecked !== isChecked) {
                if (!changes.devices.has(deviceId)) {
                    changes.devices.set(deviceId, {
                        deviceId: deviceId,
                        appsToAdd: [],
                        appsToRemove: []
                    });
                }

                if (isChecked) {
                    changes.devices.get(deviceId).appsToAdd.push(appPath);
                } else {
                    changes.devices.get(deviceId).appsToRemove.push(appPath);
                }
            }
        });

        // If no changes, just close the modal
        if (changes.devices.size === 0) {
            const modalInstance = M.Modal.getInstance(document.getElementById('all-apps-modal'));
            modalInstance.close();
            return;
        }

        // Disable save button during save
        const saveBtn = document.getElementById('modal-save-btn');
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving...';

        // Send changes to server
        const requestBody = {
            devices: Array.from(changes.devices.values())
        };

        ServerRequest.fetch(`/bff/app-groups/${groupId}/members`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestBody)
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const modalInstance = M.Modal.getInstance(document.getElementById('all-apps-modal'));
                modalInstance.close();
                window.update();
            } else {
                console.error('Failed to save group changes');
                alert('Failed to save changes');
                saveBtn.disabled = false;
                saveBtn.textContent = 'Save Changes';
            }
        })
        .catch(error => {
            console.error('Error saving group changes:', error);
            alert('Error saving changes');
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save Changes';
        });
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
        Loading.show()
        document.getElementById('app-groups').style.display = 'none';
    }

    function showError(message) {
        Loading.error(message)
        const container = document.getElementById('app-groups');
        container.style.display = 'none';
    }

    function showContent() {
        Loading.hide()
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

// Group States functionality
let currentGroupIdForStates = null;
let currentDevicesForStates = null;
let currentStates = null;
let currentEditingStateId = null;

function showGroupStatesModal(groupId) {
    currentGroupIdForStates = groupId;
    
    // Show loading state
    document.getElementById('states-modal-loading').style.display = 'block';
    document.getElementById('states-modal-content').style.display = 'none';
    
    // Open modal
    const modalInstance = M.Modal.getInstance(document.getElementById('group-states-modal'));
    modalInstance.open();
    
    // Load states and devices in parallel
    Promise.all([
        ServerRequest.fetch(`/bff/app-groups/${groupId}/states`).then(r => r.json()),
        ServerRequest.fetch(`/bff/app-groups/${groupId}/devices-for-states`).then(r => r.json())
    ])
    .then(([statesData, devicesData]) => {
        currentStates = statesData.states;
        currentDevicesForStates = devicesData.devices;
        renderGroupStatesModal(statesData.states, devicesData.devices);
    })
    .catch(error => {
        console.error('Error loading group states:', error);
        document.getElementById('states-modal-loading').style.display = 'none';
        document.getElementById('states-modal-content').innerHTML = 
            '<p class="no-states-message">Failed to load group states. Please try again.</p>';
        document.getElementById('states-modal-content').style.display = 'block';
    });
}

function renderGroupStatesModal(states, devices) {
    const content = document.getElementById('states-modal-content');
    let html = '';
    
    if (states.length === 0) {
        html = '<p class="no-states-message">No group states defined yet. Click "Add New State" to create one.</p>';
    } else {
        states.forEach(state => {
            html += `
                <div class="group-state-item">
                    <div class="group-state-info">
                        <div class="group-state-name">${state.name}</div>
                        <div class="group-state-devices">
                            ${formatStateDevices(state, devices)}
                        </div>
                    </div>
                    <div class="group-state-actions">
                        <a class="btn-small" onclick="editGroupState('${state.id}')">
                            <i class="material-icons">edit</i>
                        </a>
                        <a class="btn-small red" onclick="deleteGroupState('${state.id}')">
                            <i class="material-icons">delete</i>
                        </a>
                    </div>
                </div>
            `;
        });
    }
    
    content.innerHTML = html;
    document.getElementById('states-modal-loading').style.display = 'none';
    content.style.display = 'block';
    
    // Setup add button handler
    document.getElementById('states-add-btn').onclick = () => addNewGroupState();
}

function formatStateDevices(state, devices) {
    const deviceEntries = Object.entries(state.deviceStates);
    if (deviceEntries.length === 0) {
        return '<em>No devices configured</em>';
    }
    
    return deviceEntries.map(([deviceId, deviceState]) => {
        const device = devices.find(d => d.deviceId === deviceId);
        const deviceName = device ? device.deviceName : 'Unknown Device';
        return `<span class="group-state-device"><strong>${deviceName}:</strong> ${deviceState.deviceState}</span>`;
    }).join('');
}

function addNewGroupState() {
    currentEditingStateId = null;
    showEditStateModal('New Group State', '', {});
}

function editGroupState(stateId) {
    currentEditingStateId = stateId;
    const state = currentStates.find(s => s.id === stateId);
    if (state) {
        showEditStateModal('Edit Group State', state.name, state.deviceStates);
    }
}

function showEditStateModal(title, stateName, deviceStates) {
    document.getElementById('edit-state-modal-title').textContent = title;
    const nameInput = document.getElementById('state-name-input');
    nameInput.value = stateName;
    
    // Update label
    const label = nameInput.nextElementSibling;
    if (stateName) {
        label.classList.add('active');
    } else {
        label.classList.remove('active');
    }
    
    // Render device state selectors
    renderDeviceStateSelectors(deviceStates);
    
    // Setup save button
    document.getElementById('edit-state-save-btn').onclick = () => saveGroupState();
    
    // Open modal
    const modalInstance = M.Modal.getInstance(document.getElementById('edit-state-modal'));
    modalInstance.open();
}

function renderDeviceStateSelectors(deviceStates) {
    const container = document.getElementById('device-states-list');
    let html = '';
    
    if (!currentDevicesForStates || currentDevicesForStates.length === 0) {
        html = '<p style="color: var(--md-sys-color-on-surface-variant);">No devices available</p>';
    } else {
        currentDevicesForStates.forEach(device => {
            const currentState = deviceStates[device.deviceId];
            const currentStateValue = currentState ? currentState.deviceState : '';
            
            html += `
                <div class="device-state-selector">
                    <h6>${device.deviceName}</h6>
                    <select class="browser-default device-state-select" data-device-id="${device.deviceId}">
                        <option value="">-- Not Set --</option>
            `;
            
            device.availableStates.forEach(state => {
                const selected = state.deviceState === currentStateValue ? 'selected' : '';
                html += `<option value="${state.deviceState}" ${selected}>${state.title || state.deviceState}</option>`;
            });
            
            html += `
                    </select>
                </div>
            `;
        });
    }
    
    container.innerHTML = html;
}

function saveGroupState() {
    const name = document.getElementById('state-name-input').value.trim();
    if (!name) {
        alert('Please enter a state name');
        return;
    }
    
    // Collect device states
    const deviceStates = {};
    document.querySelectorAll('.device-state-select').forEach(select => {
        const deviceId = select.dataset.deviceId;
        const stateValue = select.value;
        if (stateValue) {
            deviceStates[deviceId] = {
                deviceState: stateValue,
                extra: null
            };
        }
    });
    
    const requestBody = {
        name: name,
        deviceStates: deviceStates
    };
    
    let promise;
    if (currentEditingStateId) {
        // Update existing state
        promise = ServerRequest.fetch(`/bff/app-groups/${currentGroupIdForStates}/states/${currentEditingStateId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });
    } else {
        // Create new state
        promise = ServerRequest.fetch(`/bff/app-groups/${currentGroupIdForStates}/states`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });
    }
    
    promise
        .then(response => response.json())
        .then(data => {
            // Close edit modal
            const editModalInstance = M.Modal.getInstance(document.getElementById('edit-state-modal'));
            editModalInstance.close();
            
            // Refresh the states list
            showGroupStatesModal(currentGroupIdForStates);
        })
        .catch(error => {
            console.error('Error saving group state:', error);
            alert('Failed to save group state');
        });
}

function deleteGroupState(stateId) {
    if (!confirm('Are you sure you want to delete this group state?')) {
        return;
    }
    
    ServerRequest.fetch(`/bff/app-groups/${currentGroupIdForStates}/states/${stateId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Refresh the states list
            showGroupStatesModal(currentGroupIdForStates);
        } else {
            console.error('Failed to delete group state');
            alert('Failed to delete group state');
        }
    })
    .catch(error => {
        console.error('Error deleting group state:', error);
        alert('Error deleting group state');
    });
}

function showApplyGroupStateModal(groupId) {
    // Get group name from the DOM
    const groupElement = document.querySelector(`[data-group-id="${groupId}"]`);
    const groupName = groupElement ? groupElement.querySelector('.fr-name-text').textContent : 'App Group';
    
    // Fetch group states
    ServerRequest.fetch(`/bff/app-groups/${groupId}/states`)
        .then(response => response.json())
        .then(data => {
            // Load the template
            return Handlebars.fetchTemplate('./apply-group-state.handlebars')
                .then(([template]) => {
                    const modalContent = document.querySelector('#apply-group-state-modal .modal-content');
                    modalContent.innerHTML = template({
                        groupId: groupId,
                        groupName: groupName,
                        states: data.states
                    });
                    
                    // Setup click handlers for each state
                    modalContent.querySelectorAll('a[data-state-id]').forEach(link => {
                        link.addEventListener('click', (e) => {
                            e.preventDefault();
                            const stateId = link.dataset.stateId;
                            const groupId = link.dataset.groupId;
                            applyGroupState(groupId, stateId);
                        });
                    });
                    
                    // Open modal
                    const modalInstance = M.Modal.getInstance(document.getElementById('apply-group-state-modal'));
                    modalInstance.open();
                });
        })
        .catch(error => {
            console.error('Error loading group states:', error);
            alert('Failed to load group states');
        });
}

function applyGroupState(groupId, stateId) {
    ServerRequest.fetch(`/bff/app-groups/${groupId}/apply-state/${stateId}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            Toast.info(`Group state applied to ${data.appliedDevices} device(s)`);
            // Close modal
            const modalInstance = M.Modal.getInstance(document.getElementById('apply-group-state-modal'));
            modalInstance.close();
            // Optionally refresh the devices page or provide feedback
        } else {
            console.error('Failed to apply group state');
            alert('Failed to apply group state');
        }
    })
    .catch(error => {
        console.error('Error applying group state:', error);
        alert('Error applying group state');
    });
}
