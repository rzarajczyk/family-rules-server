document.addEventListener("DOMContentLoaded", (event) => {
    // Password change form elements
    const passwordForm = document.getElementById('password-change-form');
    const successMessage = document.getElementById('success-message');
    const errorMessage = document.getElementById('error-message');
    const errorText = document.getElementById('error-text');

    // Webhook settings form elements
    const webhookForm = document.getElementById('webhook-settings-form');
    const webhookEnabledCheckbox = document.getElementById('webhook-enabled');
    const webhookUrlInput = document.getElementById('webhook-url');
    const webhookSuccessMessage = document.getElementById('webhook-success-message');
    const webhookErrorMessage = document.getElementById('webhook-error-message');
    const webhookErrorText = document.getElementById('webhook-error-text');
    const viewWebhookHistoryBtn = document.getElementById('view-webhook-history-btn');

    // Initialize modal with options
    const webhookHistoryModalElem = document.getElementById('webhook-history-modal');
    const webhookHistoryModal = M.Modal.init(webhookHistoryModalElem, {
        dismissible: true,
        onCloseEnd: function() {
            // Modal closed
        }
    });

    // Initialize other Materialize components
    M.AutoInit();

    // Load webhook settings on page load
    loadWebhookSettings();

    async function loadWebhookSettings() {
        try {
            const response = await ServerRequest.fetch('/bff/webhook-settings', {
                method: 'GET'
            });

            if (response.ok) {
                const data = await response.json();
                if (data.success) {
                    webhookEnabledCheckbox.checked = data.webhookEnabled;
                    if (data.webhookUrl) {
                        webhookUrlInput.value = data.webhookUrl;
                        // Activate the label for Materialize
                        const label = webhookUrlInput.nextElementSibling;
                        if (label && label.tagName === 'LABEL') {
                            label.classList.add('active');
                        }
                    }
                }
            }
        } catch (error) {
            console.error('Error loading webhook settings:', error);
        }
    }

    function hideMessages() {
        successMessage.style.display = 'none';
        errorMessage.style.display = 'none';
    }

    function showSuccess() {
        hideMessages();
        successMessage.style.display = 'block';
        // Clear form
        document.getElementById('current-password').value = '';
        document.getElementById('new-password').value = '';
        document.getElementById('confirm-password').value = '';
    }

    function showError(message) {
        hideMessages();
        errorText.textContent = message;
        errorMessage.style.display = 'block';
    }

    function hideWebhookMessages() {
        webhookSuccessMessage.style.display = 'none';
        webhookErrorMessage.style.display = 'none';
    }

    function showWebhookSuccess() {
        hideWebhookMessages();
        webhookSuccessMessage.style.display = 'block';
    }

    function showWebhookError(message) {
        hideWebhookMessages();
        webhookErrorText.textContent = message;
        webhookErrorMessage.style.display = 'block';
    }

    // Webhook settings form submission
    webhookForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const webhookEnabled = webhookEnabledCheckbox.checked;
        const webhookUrl = webhookUrlInput.value.trim();

        // Validate webhook URL if enabled
        if (webhookEnabled && !webhookUrl) {
            showWebhookError('Webhook URL is required when webhook is enabled');
            return;
        }

        try {
            const response = await ServerRequest.fetch('/bff/webhook-settings', {
                method: 'POST',
                body: JSON.stringify({
                    webhookEnabled: webhookEnabled,
                    webhookUrl: webhookUrl || null
                })
            });

            if (response.ok) {
                const result = await response.json();
                if (result.success) {
                    showWebhookSuccess();
                } else {
                    showWebhookError(result.message || 'Failed to save webhook settings');
                }
            } else {
                const errorData = await response.json();
                showWebhookError(errorData.message || 'Failed to save webhook settings');
            }
        } catch (error) {
            console.error('Error saving webhook settings:', error);
            showWebhookError('An error occurred while saving webhook settings');
        }
    });

    // Password change form submission
    passwordForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const currentPassword = document.getElementById('current-password').value;
        const newPassword = document.getElementById('new-password').value;
        const confirmPassword = document.getElementById('confirm-password').value;

        // Validate passwords match
        if (newPassword !== confirmPassword) {
            showError('New passwords do not match');
            return;
        }

        // Validate password length
        if (newPassword.length < 6) {
            showError('New password must be at least 6 characters long');
            return;
        }

        try {
            const response = await ServerRequest.fetch('/bff/change-password', {
                method: 'POST',
                body: JSON.stringify({
                    currentPassword: currentPassword,
                    newPassword: newPassword
                })
            });

            if (response.ok) {
                showSuccess();
            } else {
                const errorData = await response.json();
                showError(errorData.message || 'Failed to change password');
            }
        } catch (error) {
            console.error('Error changing password:', error);
            showError('An error occurred while changing password');
        }
    });

    // Webhook history button click
    viewWebhookHistoryBtn.addEventListener('click', async () => {
        webhookHistoryModal.open();
        await loadWebhookCallHistory();
    });

    // Webhook history close button click
    const webhookHistoryCloseBtn = document.getElementById('webhook-history-close-btn');
    webhookHistoryCloseBtn.addEventListener('click', (e) => {
        e.preventDefault();
        webhookHistoryModal.close();
    });

    async function loadWebhookCallHistory() {
        const contentDiv = document.getElementById('webhook-history-content');
        contentDiv.innerHTML = '<div class="progress"><div class="indeterminate"></div></div>';

        try {
            const response = await ServerRequest.fetch('/bff/webhook-call-history', {
                method: 'GET'
            });

            if (response.ok) {
                const data = await response.json();
                if (data.success && data.calls.length > 0) {
                    const table = document.createElement('table');
                    table.className = 'webhook-history-table striped';
                    
                    // Table header
                    const thead = document.createElement('thead');
                    thead.innerHTML = `
                        <tr>
                            <th>Timestamp</th>
                            <th>Status</th>
                            <th>Status Code</th>
                            <th>Error Message</th>
                        </tr>
                    `;
                    table.appendChild(thead);
                    
                    // Table body
                    const tbody = document.createElement('tbody');
                    data.calls.forEach(call => {
                        const tr = document.createElement('tr');
                        const timestamp = new Date(call.timestamp);
                        const statusClass = call.status === 'success' ? 'status-success' : 'status-error';
                        
                        tr.innerHTML = `
                            <td>${timestamp.toLocaleString()}</td>
                            <td class="${statusClass}">${call.status}</td>
                            <td>${call.statusCode || '-'}</td>
                            <td>${call.errorMessage || '-'}</td>
                        `;
                        tbody.appendChild(tr);
                    });
                    table.appendChild(tbody);
                    
                    contentDiv.innerHTML = '';
                    contentDiv.appendChild(table);
                } else {
                    contentDiv.innerHTML = '<p>No webhook call history found.</p>';
                }
            } else {
                contentDiv.innerHTML = '<p class="red-text">Failed to load webhook call history.</p>';
            }
        } catch (error) {
            console.error('Error loading webhook call history:', error);
            contentDiv.innerHTML = '<p class="red-text">An error occurred while loading webhook call history.</p>';
        }
    }
});
