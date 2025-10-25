// User management functionality
let users = [];

// Load users from the backend
async function loadUsers() {
    try {
        showLoading();
        
        const response = await ServerRequest.fetch('/bff/users');
        
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('Access denied. Admin privileges required.');
            }
            throw new Error(`Failed to load users: ${response.status} ${response.statusText}`);
        }
        
        const data = await response.json();
        users = data.users;
        displayUsers();
        
    } catch (error) {
        console.error('Error loading users:', error);
        showError(error.message);
    }
}

// Display users in the UI
function displayUsers() {
    const container = document.getElementById('users-container');
    const loading = document.getElementById('loading');
    const error = document.getElementById('error');
    
    // Hide loading and error
    loading.style.display = 'none';
    error.style.display = 'none';
    
    if (users.length === 0) {
        container.innerHTML = `
            <div class="card">
                <div class="card-content">
                    <div class="center-align">
                        <i class="material-icons large">people</i>
                        <h5>No Users Found</h5>
                        <p>No users are currently registered in the system.</p>
                    </div>
                </div>
            </div>
        `;
    } else {
        container.innerHTML = users.map(user => createUserCard(user)).join('');
    }
    
    container.style.display = 'block';
}

// Create a user card element
function createUserCard(user) {
    const accessLevelClass = getAccessLevelClass(user.accessLevel);
    const accessLevelText = getAccessLevelText(user.accessLevel);
    const userInitial = user.username.charAt(0).toUpperCase();
    
    return `
        <div class="card user-card" data-username="${escapeHtml(user.username)}">
            <div class="card-content">
                <div class="user-info">
                    <div class="user-avatar">
                        ${userInitial}
                    </div>
                    <div class="user-details">
                        <h5 class="user-name">${escapeHtml(user.username)}</h5>
                        <div class="user-access-level">
                            <span class="access-level-badge ${accessLevelClass}">
                                ${accessLevelText}
                            </span>
                        </div>
                    </div>
                    <div class="user-actions">
                        <button class="btn waves-effect waves-light orange" onclick="resetPassword('${escapeHtml(user.username)}')">
                            <i class="material-icons left">lock_reset</i>
                            Reset Password
                        </button>
                        <button class="btn-floating red waves-effect waves-light" onclick="deleteUser('${escapeHtml(user.username)}')">
                            <i class="material-icons">delete</i>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;
}

// Get CSS class for access level badge
function getAccessLevelClass(accessLevel) {
    switch (accessLevel) {
        case 'ADMIN':
            return 'access-level-admin';
        case 'PARENT':
            return 'access-level-parent';
        default:
            return 'access-level-admin';
    }
}

// Get display text for access level
function getAccessLevelText(accessLevel) {
    switch (accessLevel) {
        case 'ADMIN':
            return 'Admin';
        case 'PARENT':
            return 'Parent';
        default:
            return 'Admin';
    }
}

// Show loading state
function showLoading() {
    document.getElementById('loading').style.display = 'block';
    document.getElementById('error').style.display = 'none';
    document.getElementById('users-container').style.display = 'none';
}

// Show error state
function showError(message) {
    const loading = document.getElementById('loading');
    const error = document.getElementById('error');
    const container = document.getElementById('users-container');
    
    loading.style.display = 'none';
    container.style.display = 'none';
    
    document.getElementById('error-message').textContent = message;
    error.style.display = 'block';
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Delete user function
async function deleteUser(username) {
    if (!confirm(`Are you sure you want to delete user "${username}"? This action cannot be undone.`)) {
        return;
    }
    
    try {
        const response = await ServerRequest.fetch(`/bff/users/${encodeURIComponent(username)}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || `Failed to delete user: ${response.status}`);
        }
        
        const result = await response.json();
        Toast.info(result.message);
        
        // Reload users list
        loadUsers();
        
    } catch (error) {
        console.error('Error deleting user:', error);
        Toast.error(`Error: ${error.message}`);
    }
}

// Reset password function
async function resetPassword(username) {
    const newPassword = prompt(`Enter new password for user "${username}":`);
    if (!newPassword) {
        return;
    }
    
    try {
        const response = await ServerRequest.fetch(`/bff/users/${encodeURIComponent(username)}/reset-password`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                newPassword: newPassword
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || `Failed to reset password: ${response.status}`);
        }
        
        const result = await response.json();
        Toast.info(result.message);
        
    } catch (error) {
        console.error('Error resetting password:', error);
        Toast.error(`Error: ${error.message}`);
    }
}

// Modal management
let addUserModal;

// Initialize the page
document.addEventListener('DOMContentLoaded', function() {
    loadUsers();
    
    // Initialize modal
    addUserModal = M.Modal.init(document.getElementById('addUserModal'), {
        onCloseEnd: function() {
            // Reset form when modal closes
            document.getElementById('addUserForm').reset();
        }
    });
});

// Open add user modal
function openAddUserModal() {
    addUserModal.open();
}

// Close add user modal
function closeAddUserModal() {
    addUserModal.close();
}

// Submit add user form
async function submitAddUser() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const adminRights = document.getElementById('adminRights').checked;
    
    // Validation
    if (!username) {
        Toast.error('Username is required');
        return;
    }
    
    if (!password) {
        Toast.error('Password is required');
        return;
    }
    
    try {
        const response = await ServerRequest.fetch('/bff/users', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: username,
                password: password,
                accessLevel: adminRights ? 'ADMIN' : 'PARENT'
            })
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || `Failed to create user: ${response.status}`);
        }
        
        const result = await response.json();
        Toast.info(result.message);
        
        // Close modal and reload users
        closeAddUserModal();
        loadUsers();
        
    } catch (error) {
        console.error('Error creating user:', error);
        Toast.error(`Error: ${error.message}`);
    }
}