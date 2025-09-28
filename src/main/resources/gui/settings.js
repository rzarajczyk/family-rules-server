document.addEventListener("DOMContentLoaded", (event) => {
    M.AutoInit();

    const passwordForm = document.getElementById('password-change-form');
    const successMessage = document.getElementById('success-message');
    const errorMessage = document.getElementById('error-message');
    const errorText = document.getElementById('error-text');

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
});
