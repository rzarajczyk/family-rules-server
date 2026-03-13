document.addEventListener("DOMContentLoaded", (event) => {

    Loading.init(document.getElementById('loading'))

    Handlebars.fetchTemplate("./index.handlebars")
        .then(([template]) => {
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

            function renderDevices(response) {
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
                document.querySelectorAll("#instance-edit-save").forEach(it => {
                    it.addEventListener('click', onClientEditSaveClicked)
                })
                instances.querySelectorAll("a.view-usage").forEach(it => {
                    it.addEventListener('click', onViewUsageClicked)
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
                            description: null
                        })
                        content.innerHTML = setStateTemplate(data)
                        content.querySelectorAll('a').forEach(it => {
                            it.addEventListener('click', (e) => {
                                let deviceState = e.target.closest('a').dataset["devicestate"]
                                let extra = e.target.closest('a').dataset["extra"]
                                console.log(`Setting state of ${instanceId} to ${deviceState}`)
                                ServerRequest.fetch(`/bff/instance-state?instanceId=${instanceId}`, {
                                    method: 'POST',
                                    body: JSON.stringify({forcedDeviceState: deviceState, extra: extra})
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
                        
                        // Initialize app groups selects
                        setupAppGroupsSelect(data.appGroups);
                        setupAppGroupsBlockSelect(data.appGroups);
                    })
            }

            function onClientDeleteClicked(e) {
                let instanceId = e.target.closest('.fr-collapsible').dataset["instanceid"]
                if (confirm("Are you sure?")) {
                    ServerRequest.fetch(`/bff/delete-instance?instanceId=${instanceId}`, {
                        method: 'POST',
                    }).then(response => {
                        Toast.info("Deleted")
                        update()
                    })
                }
            }

            function submitClientEdit(instanceId, instanceName, iconType, iconData, appGroups) {
                ServerRequest.fetch(`/bff/instance-edit-info?instanceId=${instanceId}`, {
                    method: 'POST',
                    body: JSON.stringify({
                        instanceName,
                        icon: iconType && iconData ? {
                            type: iconType,
                            data: iconData
                        } : null,
                        appGroups: appGroups
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
                
                // Get selected app groups for show
                let appGroupsSelect = document.querySelector('#app-groups-select');
                let selectedShowGroups = Array.from(appGroupsSelect.selectedOptions).map(option => option.value);
                
                // Get selected app groups for block
                let appGroupsBlockSelect = document.querySelector('#app-groups-block-select');
                let selectedBlockGroups = Array.from(appGroupsBlockSelect.selectedOptions).map(option => option.value);
                
                let appGroupsObj = { show: selectedShowGroups, block: selectedBlockGroups };
                
                if (iconFile) {
                    resizeImage(iconFile, (iconData) => {
                        submitClientEdit(instanceId, instanceName, iconFile.type, iconData, appGroupsObj)
                    })
                } else {
                    let iconType = iconImg.dataset['type']
                    let iconData = iconImg.dataset['icon']
                    
                    // If the icon is the default icon (empty or null), send null values to clear the custom icon
                    if (!iconType || !iconData) {
                        submitClientEdit(instanceId, instanceName, null, null, appGroupsObj)
                    } else {
                        submitClientEdit(instanceId, instanceName, iconType, iconData, appGroupsObj)
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

            function onViewUsageClicked(e) {
                let instanceId = e.target.closest('.fr-collapsible').dataset["instanceid"]
                let instanceName = e.target.closest('.fr-collapsible').querySelector('.fr-name-text').textContent

                let instanceData = window.currentInstanceData?.find(inst => inst.instanceId === instanceId)

                if (!instanceData || !instanceData.onlinePeriods) {
                    Toast.info("No usage data available for this date")
                    return
                }

                showClockModal(instanceName, instanceData.onlinePeriods)
            }

            function showClockModal(instanceName, onlinePeriods) {
                document.getElementById('usage-histogram-title').textContent = `Screen Time Usage - ${instanceName}`

                drawClock(onlinePeriods)

                let modal = M.Modal.getInstance(document.querySelector("#usage-histogram-modal"))
                modal.open()
            }

            function drawClock(onlinePeriods) {
                const svg = document.getElementById('usage-clock')
                // Clear previous drawing
                while (svg.firstChild) svg.removeChild(svg.firstChild)

                const ns = 'http://www.w3.org/2000/svg'
                const cx = 250, cy = 250
                const outerR = 220   // outer edge of usage ring
                const innerR = 150   // inner edge of usage ring (donut)
                const faceR  = 145   // clock face (filled circle inside)
                const tickOuter = 230
                const labelR = 242

                // Helper: angle in radians for a given fractional time (0=midnight, 1=midnight again)
                // Clock goes clockwise, midnight at top → angle 0 = -π/2 (12 o'clock)
                function timeToAngle(hours) {
                    return (hours / 24) * 2 * Math.PI - Math.PI / 2
                }

                function polarToCartesian(r, angle) {
                    return { x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) }
                }

                function arcPath(r1, r2, startAngle, endAngle) {
                    const p1 = polarToCartesian(r2, startAngle)
                    const p2 = polarToCartesian(r2, endAngle)
                    const p3 = polarToCartesian(r1, endAngle)
                    const p4 = polarToCartesian(r1, startAngle)
                    const large = (endAngle - startAngle) > Math.PI ? 1 : 0
                    return [
                        `M ${p1.x} ${p1.y}`,
                        `A ${r2} ${r2} 0 ${large} 1 ${p2.x} ${p2.y}`,
                        `L ${p3.x} ${p3.y}`,
                        `A ${r1} ${r1} 0 ${large} 0 ${p4.x} ${p4.y}`,
                        'Z'
                    ].join(' ')
                }

                function makePath(d, fill, stroke, strokeWidth) {
                    const el = document.createElementNS(ns, 'path')
                    el.setAttribute('d', d)
                    el.setAttribute('fill', fill)
                    if (stroke) { el.setAttribute('stroke', stroke); el.setAttribute('stroke-width', strokeWidth || 1) }
                    return el
                }

                function makeCircle(r, fill, stroke, strokeWidth) {
                    const el = document.createElementNS(ns, 'circle')
                    el.setAttribute('cx', cx); el.setAttribute('cy', cy); el.setAttribute('r', r)
                    el.setAttribute('fill', fill)
                    if (stroke) { el.setAttribute('stroke', stroke); el.setAttribute('stroke-width', strokeWidth || 1) }
                    return el
                }

                // Background ring (inactive)
                svg.appendChild(makePath(arcPath(innerR, outerR, -Math.PI / 2, 3 * Math.PI / 2), '#e0e0e0', null))

                // Usage arcs — merge contiguous 10-min buckets into single arcs
                const activeSet = new Set(onlinePeriods)
                // Build sorted list of minutes-since-midnight for all active buckets
                const activeMins = []
                for (let h = 0; h < 24; h++) {
                    for (let m = 0; m < 60; m += 10) {
                        const key = `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}`
                        if (activeSet.has(key)) activeMins.push(h * 60 + m)
                    }
                }

                // Group into contiguous runs (gap > 10 min = new segment)
                const segments = []
                let segStart = null, segEnd = null
                for (const mins of activeMins) {
                    if (segStart === null) {
                        segStart = mins; segEnd = mins
                    } else if (mins === segEnd + 10) {
                        segEnd = mins
                    } else {
                        segments.push([segStart, segEnd])
                        segStart = mins; segEnd = mins
                    }
                }
                if (segStart !== null) segments.push([segStart, segEnd])

                // Draw each segment as an arc (bucket covers start → start+10min)
                const accentColor = '#1565C0'
                const accentHover = '#1E88E5'

                // Tooltip element (reused across all arcs)
                const tooltip = document.createElementNS(ns, 'g')
                tooltip.setAttribute('visibility', 'hidden')
                tooltip.setAttribute('pointer-events', 'none')

                for (const [start, end] of segments) {
                    const a1 = timeToAngle(start / 60)
                    const a2 = timeToAngle((end + 10) / 60)
                    const arcEl = makePath(arcPath(innerR, outerR, a1, a2), accentColor, null)
                    arcEl.style.cursor = 'pointer'

                    const startLabel = `${String(Math.floor(start / 60)).padStart(2,'0')}:${String(start % 60).padStart(2,'0')}`
                    const endMins = end + 10
                    const endLabel   = `${String(Math.floor(endMins / 60) % 24).padStart(2,'0')}:${String(endMins % 60).padStart(2,'0')}`
                    const tipText = `${startLabel} – ${endLabel}`

                    arcEl.addEventListener('mouseenter', (evt) => {
                        arcEl.setAttribute('fill', accentHover)
                        // Position tooltip near the midpoint of the arc, outside the ring
                        const midAngle = (a1 + a2) / 2
                        const tipR = outerR + 28
                        const tp = polarToCartesian(tipR, midAngle)

                        // Clamp so the box stays inside the 500×500 viewBox
                        const boxW = 90, boxH = 24, pad = 4
                        let bx = tp.x - boxW / 2
                        let by = tp.y - boxH / 2
                        bx = Math.max(pad, Math.min(500 - boxW - pad, bx))
                        by = Math.max(pad, Math.min(500 - boxH - pad, by))

                        tooltip.querySelector('rect').setAttribute('x', bx)
                        tooltip.querySelector('rect').setAttribute('y', by)
                        tooltip.querySelector('rect').setAttribute('width', boxW)
                        tooltip.querySelector('rect').setAttribute('height', boxH)
                        tooltip.querySelector('text').setAttribute('x', bx + boxW / 2)
                        tooltip.querySelector('text').setAttribute('y', by + boxH / 2)
                        tooltip.querySelector('text').textContent = tipText
                        tooltip.setAttribute('visibility', 'visible')
                    })
                    arcEl.addEventListener('mouseleave', () => {
                        arcEl.setAttribute('fill', accentColor)
                        tooltip.setAttribute('visibility', 'hidden')
                    })

                    svg.appendChild(arcEl)
                }

                // Build tooltip DOM (appended last so it renders on top)
                const tipRect = document.createElementNS(ns, 'rect')
                tipRect.setAttribute('rx', 4)
                tipRect.setAttribute('fill', '#212121')
                tipRect.setAttribute('opacity', '0.85')
                const tipText = document.createElementNS(ns, 'text')
                tipText.setAttribute('text-anchor', 'middle')
                tipText.setAttribute('dominant-baseline', 'middle')
                tipText.setAttribute('font-size', '12')
                tipText.setAttribute('font-family', 'sans-serif')
                tipText.setAttribute('fill', '#ffffff')
                tooltip.appendChild(tipRect)
                tooltip.appendChild(tipText)

                // Clock face circle
                svg.appendChild(makeCircle(faceR, '#fafafa', '#bdbdbd', 1))

                // Hour tick marks + labels
                for (let h = 0; h < 24; h++) {
                    const angle = timeToAngle(h)
                    const isMajor = (h % 6 === 0)
                    const tickInner = isMajor ? tickOuter - 14 : tickOuter - 7
                    const p1 = polarToCartesian(tickInner, angle)
                    const p2 = polarToCartesian(tickOuter, angle)
                    const line = document.createElementNS(ns, 'line')
                    line.setAttribute('x1', p1.x); line.setAttribute('y1', p1.y)
                    line.setAttribute('x2', p2.x); line.setAttribute('y2', p2.y)
                    line.setAttribute('stroke', isMajor ? '#424242' : '#9e9e9e')
                    line.setAttribute('stroke-width', isMajor ? 2 : 1)
                    svg.appendChild(line)

                    // Label every 6 hours
                    if (isMajor) {
                        const lp = polarToCartesian(labelR, angle)
                        const text = document.createElementNS(ns, 'text')
                        text.setAttribute('x', lp.x); text.setAttribute('y', lp.y)
                        text.setAttribute('text-anchor', 'middle')
                        text.setAttribute('dominant-baseline', 'middle')
                        text.setAttribute('font-size', '14')
                        text.setAttribute('font-family', 'sans-serif')
                        text.setAttribute('fill', '#424242')
                        text.textContent = `${String(h).padStart(2,'0')}:00`
                        svg.appendChild(text)
                    }
                }

                // Center label — total screen time
                const totalMins = activeMins.length * 10  // each bucket = 10 min
                const hours = Math.floor(totalMins / 60)
                const mins  = totalMins % 60
                const label = hours > 0 ? `${hours}h ${mins}m` : `${mins}m`

                const centerLabel = document.createElementNS(ns, 'text')
                centerLabel.setAttribute('x', cx); centerLabel.setAttribute('y', cy - 10)
                centerLabel.setAttribute('text-anchor', 'middle')
                centerLabel.setAttribute('dominant-baseline', 'middle')
                centerLabel.setAttribute('font-size', '28')
                centerLabel.setAttribute('font-weight', 'bold')
                centerLabel.setAttribute('font-family', 'sans-serif')
                centerLabel.setAttribute('fill', '#1565C0')
                centerLabel.textContent = label
                svg.appendChild(centerLabel)

                const centerSub = document.createElementNS(ns, 'text')
                centerSub.setAttribute('x', cx); centerSub.setAttribute('y', cy + 22)
                centerSub.setAttribute('text-anchor', 'middle')
                centerSub.setAttribute('dominant-baseline', 'middle')
                centerSub.setAttribute('font-size', '13')
                centerSub.setAttribute('font-family', 'sans-serif')
                centerSub.setAttribute('fill', '#757575')
                centerSub.textContent = 'screen time'
                svg.appendChild(centerSub)

                // Tooltip renders on top of everything
                svg.appendChild(tooltip)
            }

            function openModal(options) {
                const { e, selector, templateUrl, detailsUrlBuilder } = options
                let instanceId = e.target.closest('.fr-collapsible').dataset["instanceid"]
                let div = document.querySelector(selector)
                let content = div.querySelector(".modal-content")
                content.dataset['instanceid'] = instanceId
                content.innerHTML = '<center>... loading ...</center>'
                let modal = M.Modal.getInstance(div)
                modal.open()
                let templatePromise = Handlebars.fetchTemplate(templateUrl).then(([it]) => it)
                let dataPromise = ServerRequest.fetch(detailsUrlBuilder(instanceId)).then(it => it.json())
                return Promise.all([templatePromise, dataPromise])
            }

            function showLoading() {
                Loading.show()
                document.getElementById('instances').style.display = 'none';
            }

            function showError(message) {
                Loading.error(message)

                const container = document.getElementById('instances');
                container.style.display = 'none';
            }

            function showContent() {
                Loading.hide()
                document.getElementById('instances').style.display = 'block';
            }

            function update() {
                showLoading();
                let date = document.querySelector("#datepicker").value
                ServerRequest.fetch(`/bff/status?date=${date}`)
                    .then(response => response.json())
                    .then(renderDevices)
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
            const instanceId = appItem.closest('.fr-collapsible').dataset.instanceid;
            
            removeAppFromGroup(instanceId, appPath, groupId);
        });
    });
    
    // Handle add group button clicks
    document.querySelectorAll('.add-group-btn').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.stopPropagation();
            const appItem = this.closest('.app-usage-item');
            const appPath = appItem.dataset.appPath;
            const instanceId = appItem.closest('.fr-collapsible').dataset.instanceid;
            
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
    const instanceDetails = appItem.closest('.fr-collapsible');
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

// Setup app groups multi-select
function setupAppGroupsSelect(appGroupsData) {
    const selectElement = document.querySelector('#app-groups-select');
    if (!selectElement) return;
    
    // Get selected groups from the AppGroupsDto object
    const selectedGroups = appGroupsData?.show || [];
    
    // Set selected options
    Array.from(selectElement.options).forEach(option => {
        option.selected = selectedGroups.includes(option.value);
    });
    
    // Initialize Materialize select
    M.FormSelect.init(selectElement, {});
}

// Setup app groups block multi-select
function setupAppGroupsBlockSelect(appGroupsData) {
    const selectElement = document.querySelector('#app-groups-block-select');
    if (!selectElement) return;
    
    // Get selected groups from the AppGroupsDto object
    const selectedGroups = appGroupsData?.block || [];
    
    // Set selected options
    Array.from(selectElement.options).forEach(option => {
        option.selected = selectedGroups.includes(option.value);
    });
    
    // Initialize Materialize select
    M.FormSelect.init(selectElement, {});
}
