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
                // Store instance data globally for app group functionality
                window.currentInstanceData = response.instances;
                
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
                instances.querySelectorAll(".instance-buttons a.client-delete").forEach(it => {
                    it.addEventListener('click', onClientDeleteClicked)
                })
                instances.querySelectorAll(".instance-buttons a.associate-group").forEach(it => {
                    it.addEventListener('click', onAssociateGroupClicked)
                })
                document.querySelectorAll("#instance-edit-save").forEach(it => {
                    it.addEventListener('click', onClientEditSaveClicked)
                })
                instances.querySelectorAll("a.edit-schedule").forEach(it => {
                    it.addEventListener('click', onEditScheduleClicked)
                })
                M.Collapsible.init(instances, {});
                
                // Initialize tooltips for info icons (excluding app names)
                M.Tooltip.init(document.querySelectorAll('.tooltipped:not(.clickable-app-name)'), {
                    position: 'left',
                    enterDelay: 200,
                    exitDelay: 0
                });
                
                // Initialize app group functionality
                initializeAppGroupHandlers();
                
                // Initialize clickable app names
                initializeClickableAppNames();
                
                // Show content after successful render
                showContent();
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
                                    Toast.info("Saved")
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
                        document.querySelectorAll('.file-field input[type="file"]').forEach((fileInput) => {
                            M.Forms.InitFileInputPath(fileInput);
                        });
                        
                        // Setup remove icon functionality
                        setupRemoveIconFunctionality();
                    })
            }

            function onClientDeleteClicked(e) {
                let instanceId = e.target.closest('.instance-details').dataset["instanceid"]
                if (confirm("Are you sure?")) {
                    ServerRequest.fetch(`/bff/delete-instance?instanceId=${instanceId}`, {
                        method: 'POST',
                    }).then(response => {
                        Toast.info("Deleted")
                        update()
                    })
                }
            }

            function submitClientEdit(instanceId, instanceName, iconType, iconData) {
                ServerRequest.fetch(`/bff/instance-edit-info?instanceId=${instanceId}`, {
                    method: 'POST',
                    body: JSON.stringify({
                        instanceName,
                        icon: iconType && iconData ? {
                            type: iconType,
                            data: iconData
                        } : null
                    })
                }).then(response => {
                    Toast.info("Saved")
                    M.Modal.getInstance(document.querySelector("#instance-edit-modal")).close()
                    update()
                })

            }

            function setupRemoveIconFunctionality() {
                const removeIconContainer = document.querySelector('.remove-icon-container');
                const removeIconBtn = document.querySelector('#remove-icon-btn');
                const iconImg = document.querySelector('.edit-instance-data img');
                const fileInput = document.querySelector('#instance-icon');
                
                // Check if current icon is the default icon
                function isDefaultIcon() {
                    const currentIconType = iconImg.dataset['type'];
                    const currentIconData = iconImg.dataset['icon'];
                    return !currentIconType || !currentIconData;
                }
                
                // Show/hide remove button based on whether icon is default
                function updateRemoveButtonVisibility() {
                    if (isDefaultIcon()) {
                        removeIconContainer.style.display = 'none';
                    } else {
                        removeIconContainer.style.display = 'block';
                    }
                }
                
                // Handle remove icon button click
                removeIconBtn.addEventListener('click', function() {
                    // Reset to default icon
                    iconImg.src = 'default-icon.png';
                    iconImg.dataset['type'] = '';
                    iconImg.dataset['icon'] = '';
                    
                    // Clear file input
                    fileInput.value = '';
                    const filePathInput = document.querySelector('.file-path');
                    if (filePathInput) {
                        filePathInput.value = '';
                    }
                    
                    // Hide remove button
                    removeIconContainer.style.display = 'none';
                });
                
                // Handle file input change
                fileInput.addEventListener('change', function() {
                    if (this.files.length > 0) {
                        const file = this.files[0];
                        
                        // Preview the selected image
                        const reader = new FileReader();
                        reader.onload = function(e) {
                            iconImg.src = e.target.result;
                            // Update data attributes with the new file info
                            iconImg.dataset['type'] = file.type;
                            // For preview, we'll store the full data URL, but for saving we'll convert to base64
                            iconImg.dataset['icon'] = e.target.result.split(',')[1]; // Extract base64 part
                        };
                        reader.readAsDataURL(file);
                        
                        // Show remove button when a file is selected
                        removeIconContainer.style.display = 'block';
                    }
                });
                
                // Initial visibility check
                updateRemoveButtonVisibility();
            }

            function onClientEditSaveClicked() {
                let content = document.querySelector("#instance-edit-modal .modal-content")
                let instanceId = content.dataset['instanceid']
                let instanceName = document.querySelector("#instance-name").value
                let iconFile = document.querySelector("#instance-icon").files[0]
                let iconImg = document.querySelector('.edit-instance-data img')
                
                if (iconFile) {
                    resizeImage(iconFile, (iconData) => {
                        submitClientEdit(instanceId, instanceName, iconFile.type, iconData)
                    })
                } else {
                    let iconType = iconImg.dataset['type']
                    let iconData = iconImg.dataset['icon']
                    
                    // If the icon is the default icon (empty or null), send null values to clear the custom icon
                    if (!iconType || !iconData) {
                        submitClientEdit(instanceId, instanceName, null, null)
                    } else {
                        submitClientEdit(instanceId, instanceName, iconType, iconData)
                    }
                }
            }

function resizeImage(file, onResize, width = 64, height = 64) {
    let reader = new FileReader();
    reader.onload = function(event) {
        let img = new Image();
        img.onload = function() {
            let canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            let ctx = canvas.getContext('2d');

            // Improve image quality by using a higher quality interpolation algorithm
            ctx.imageSmoothingEnabled = true;
            ctx.imageSmoothingQuality = 'high';

            ctx.drawImage(img, 0, 0, width, height);
            let resizedBase64 = canvas.toDataURL(file.type).split(',')[1];
            onResize(resizedBase64);
        }
        img.src = event.target.result;
    }
    reader.readAsDataURL(file);
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

            function showLoading() {
                document.getElementById('loading').style.display = 'block';
                document.getElementById('error').style.display = 'none';
                document.getElementById('instances').style.display = 'none';
            }

            function showError(message) {
                const loading = document.getElementById('loading');
                const error = document.getElementById('error');
                const container = document.getElementById('instances');
                
                loading.style.display = 'none';
                container.style.display = 'none';
                
                document.getElementById('error-message').textContent = message;
                error.style.display = 'block';
            }

            function showContent() {
                document.getElementById('loading').style.display = 'none';
                document.getElementById('error').style.display = 'none';
                document.getElementById('instances').style.display = 'block';
            }

            function update() {
                showLoading();
                let date = document.querySelector("#datepicker").value
                ServerRequest.fetch(`/bff/status?date=${date}`)
                .then(response => response.json())
                .then(render)
                .catch(error => {
                    console.error('Error in update function:', error);
                    showError('Failed to load devices data. Please try again.');
                })
            }

            update()

            window.update = update
    })
});

// App Group functionality
function initializeAppGroupHandlers() {
    // Handle remove group button clicks
    document.querySelectorAll('.remove-group-btn').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation();
            const label = this.closest('.app-group-label');
            const appItem = this.closest('.app-usage-item');
            const appPath = appItem.dataset.appPath;
            const groupId = label.dataset.groupId;
            const instanceId = appItem.closest('.instance-details').dataset.instanceid;
            
            removeAppFromGroup(instanceId, appPath, groupId);
        });
    });
    
    // Handle add group button clicks
    document.querySelectorAll('.add-group-btn').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation();
            const appItem = this.closest('.app-usage-item');
            const appPath = appItem.dataset.appPath;
            const instanceId = appItem.closest('.instance-details').dataset.instanceid;
            
            showAddGroupDropdown(appItem, instanceId, appPath);
        });
    });
}

function removeAppFromGroup(instanceId, appPath, groupId) {
    ServerRequest.fetch(`/bff/app-groups/${groupId}/apps/${encodeURIComponent(appPath)}?instanceId=${instanceId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Reload the page to show updated groups
            window.update();
        } else {
            console.error('Failed to remove app from group');
        }
    })
    .catch(error => {
        console.error('Error removing app from group:', error);
    });
}

function showAddGroupDropdown(appItem, instanceId, appPath) {
    // Get available app groups from the instance data
    const instanceDetails = appItem.closest('.instance-details');
    const instanceData = window.currentInstanceData?.find(inst => inst.instanceId === instanceId);
    
    if (!instanceData) {
        console.error('Instance data not found');
        return;
    }
    
    const availableGroups = instanceData.availableAppGroups || [];
    
    // Create dropdown menu
    const dropdown = document.createElement('div');
    dropdown.className = 'app-group-dropdown';
    dropdown.style.cssText = `
        background: white;
        border: 1px solid #ccc;
        border-radius: 4px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        min-width: 150px;
    `;
    
    // Add existing groups
    availableGroups.forEach(group => {
        const item = document.createElement('div');
        item.className = 'dropdown-item';
        item.style.cssText = `
            padding: 8px 12px;
            cursor: pointer;
            border-bottom: 1px solid #eee;
        `;
        item.textContent = group.name;
        item.addEventListener('click', () => {
            addAppToGroup(instanceId, appPath, group.id);
            dropdown.remove();
        });
        dropdown.appendChild(item);
    });
    
    // Add "New app group" option
    const newGroupItem = document.createElement('div');
    newGroupItem.className = 'dropdown-item new-group-item';
    newGroupItem.style.cssText = `
        padding: 8px 12px;
        cursor: pointer;
        background-color: #f5f5f5;
        font-style: italic;
    `;
    newGroupItem.textContent = 'New app group...';
    newGroupItem.addEventListener('click', () => {
        showCreateGroupDialog(instanceId, appPath);
        dropdown.remove();
    });
    dropdown.appendChild(newGroupItem);
    
    // Position dropdown with fixed positioning (relative to viewport)
    const rect = appItem.getBoundingClientRect();
    
    // Calculate position relative to viewport (for fixed positioning)
    let left = rect.left;
    let top = rect.bottom + 5;
    
    // Ensure dropdown doesn't go off-screen horizontally
    const dropdownWidth = 150; // min-width from CSS
    const viewportWidth = window.innerWidth;
    const adjustedLeft = left + dropdownWidth > viewportWidth 
        ? viewportWidth - dropdownWidth - 10 
        : left;
    
    // Ensure dropdown doesn't go off-screen vertically
    const dropdownHeight = Math.min(200, availableGroups.length * 40 + 50); // Estimate height
    const viewportHeight = window.innerHeight;
    const adjustedTop = top + dropdownHeight > viewportHeight
        ? rect.top - dropdownHeight - 5 // Position above the button
        : top;
    
    dropdown.style.left = adjustedLeft + 'px';
    dropdown.style.top = adjustedTop + 'px';
    
    document.body.appendChild(dropdown);
    
    // Close dropdown when clicking outside
    const closeDropdown = (e) => {
        if (!dropdown.contains(e.target)) {
            dropdown.remove();
            document.removeEventListener('click', closeDropdown);
        }
    };
    setTimeout(() => document.addEventListener('click', closeDropdown), 0);
}

function addAppToGroup(instanceId, appPath, groupId) {
    ServerRequest.fetch(`/bff/app-groups/${groupId}/apps`, {
        method: 'POST',
        body: JSON.stringify({
            instanceId: instanceId,
            appPath: appPath
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Reload the page to show updated groups
            window.update();
        } else {
            console.error('Failed to add app to group');
        }
    })
    .catch(error => {
        console.error('Error adding app to group:', error);
    });
}

function showCreateGroupDialog(instanceId, appPath) {
    const groupName = prompt('Enter name for new app group:');
    if (groupName && groupName.trim()) {
        createAppGroup(groupName.trim(), instanceId, appPath);
    }
}

function onAssociateGroupClicked(event) {
    const instanceElement = event.target.closest('.instance-details')
    const instanceId = instanceElement?.dataset?.instanceid
    if (!instanceId) return

    const instanceData = (window.currentInstanceData || []).find(i => i.instanceId === instanceId)
    const groups = instanceData?.availableAppGroups || []
    const current = instanceData?.associatedAppGroupId

    if (groups.length === 0) {
        M.toast({html: 'No app groups available. Create one first in Groups tab.'})
        return
    }

    const choices = groups.map((g, idx) => `${idx + 1}. ${g.name}`).join('\n')
    const defaultText = current ? `Current: ${groups.find(g => g.id === current)?.name || current}` : 'Current: none'
    const answer = prompt(`${defaultText}\nSelect app group number (empty to clear):\n\n${choices}`)
    if (answer === null) return

    const trimmed = answer.trim()
    const index = trimmed === '' ? null : (parseInt(trimmed, 10) - 1)
    let selectedId = null
    if (index !== null) {
        if (isNaN(index) || index < 0 || index >= groups.length) {
            M.toast({html: 'Invalid selection'})
            return
        }
        selectedId = groups[index].id
    }

    fetch(`/bff/instance-associated-group?instanceId=${encodeURIComponent(instanceId)}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ groupId: selectedId })
    }).then(r => {
        if (!r.ok) throw new Error('Failed')
        return r.json().catch(() => ({}))
    }).then(() => {
        M.toast({html: 'Associated app group updated'})
        update()
    }).catch(() => {
        M.toast({html: 'Failed to update associated app group'})
    })
}

function createAppGroup(groupName, instanceId, appPath) {
    ServerRequest.fetch('/bff/app-groups', {
        method: 'POST',
        body: JSON.stringify({
            name: groupName
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.group) {
            // Add the app to the newly created group
            addAppToGroup(instanceId, appPath, data.group.id);
        } else {
            console.error('Failed to create app group');
        }
    })
    .catch(error => {
        console.error('Error creating app group:', error);
    });
}

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
