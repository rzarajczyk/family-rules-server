const Toast = {
    container: null,
    
    init() {
        // Create toast container if it doesn't exist
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.className = 'toast-container';
            document.body.appendChild(this.container);
        }
    },
    
    show(message, type = 'info', duration = 5000) {
        this.init();
        
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        
        const content = document.createElement('div');
        content.className = 'toast-content';
        content.textContent = message;
        
        const closeBtn = document.createElement('button');
        closeBtn.className = 'toast-close';
        closeBtn.innerHTML = '<i class="material-icons">close</i>';
        closeBtn.onclick = () => this.hide(toast);
        
        toast.appendChild(content);
        toast.appendChild(closeBtn);
        this.container.appendChild(toast);
        
        // Trigger animation
        setTimeout(() => toast.classList.add('show'), 10);
        
        // Auto-hide after duration
        setTimeout(() => this.hide(toast), duration);
        
        return toast;
    },
    
    hide(toast) {
        if (toast && toast.parentNode) {
            toast.classList.add('hide');
            setTimeout(() => {
                if (toast.parentNode) {
                    toast.parentNode.removeChild(toast);
                }
            }, 300);
        }
    },
    
    info(message, duration = 5000) {
        return this.show(message, 'info', duration);
    },
    
    error(message, duration = 5000) {
        return this.show(message, 'error', duration);
    }
};

const Loading = {

    init(container, text='Loading...') {
        container.classList.add('loading-container')
        container.innerHTML = `
                <div class="preloader-wrapper active">
                    <div class="spinner-layer spinner-blue-only">
                        <div class="circle-clipper left">
                            <div class="circle"></div>
                        </div>
                        <div class="circle-clipper right">
                            <div class="circle"></div>
                        </div>
                    </div>
                </div>
                <p>${text}</p>
        `
    }

}