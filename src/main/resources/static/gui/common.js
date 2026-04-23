// ============================================================
// common.js — shared utilities for all pages (Alpine + DaisyUI)
// ============================================================

const ServerRequest = {
    fetch(url, options) {
        const headers = new Headers();
        headers.set('Content-Type', 'application/json');

        return fetch(url, {
            method: options?.method ?? 'GET',
            headers: headers,
            body: options?.body,
            credentials: 'include',
            redirect: 'manual'
        }).then(response => {
            if (response.type === 'opaqueredirect') {
                window.location.href = '/logout';
                return null;
            }
            if (response.redirected) {
                window.location.href = response.url;
                return null;
            }
            if (response.status !== 200) {
                throw new Error('Received HTTP status code: ' + response.status);
            }
            return response;
        });
    }
};

// ---- Utility helpers ----

function today() {
    const d = new Date();
    return d.getFullYear() + '-' +
        String(d.getMonth() + 1).padStart(2, '0') + '-' +
        String(d.getDate()).padStart(2, '0');
}

function formatScreenTime(seconds) {
    if (seconds == null || seconds === 0) return '0m 00s';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    const fh = h > 0 ? h + 'h ' : '';
    const fm = (m < 10 ? '0' : '') + m;
    const fs = (s < 10 ? '0' : '') + s;
    return fh + fm + 'm ' + fs + 's';
}

function formatScreenTimeShort(seconds) {
    if (seconds == null || seconds === 0) return '0s';
    if (seconds < 60) return seconds + 's';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h === 0) return m + 'm';
    return m > 0 ? h + 'h ' + m + 'm' : h + 'h';
}

function stateIcon(state) {
    if (state === 'ACTIVE') return 'check_circle';
    if (state === 'LOCKED') return 'lock';
    if (state === 'LOGGED_OUT') return 'logout';
    if (state === 'APP_DISABLED') return 'warning';
    return 'star';
}

function stateLabel(state) {
    if (state === 'ACTIVE') return 'Active';
    if (state === 'LOCKED') return 'Locked';
    if (state === 'LOGGED_OUT') return 'Logged out';
    if (state === 'APP_DISABLED') return 'App disabled!';
    if (!state || state === '') return 'Automatic';
    return state;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function iconSrc(icon) {
    if (icon && icon.type && icon.data) return 'data:' + icon.type + ';base64,' + icon.data;
    return 'default-icon.png';
}

function appIconSrc(base64) {
    if (base64) return 'data:image/webp;base64,' + base64;
    return 'default-icon.png';
}

// ---- Toast (Alpine store) ----

document.addEventListener('alpine:init', () => {
    Alpine.store('toast', {
        items: [],
        _counter: 0,

        info(message, duration = 5000) {
            this._show(message, 'alert-success', duration);
        },
        error(message, duration = 5000) {
            this._show(message, 'alert-error', duration);
        },
        _show(message, cls, duration) {
            const id = ++this._counter;
            this.items.push({ id, message, cls });
            setTimeout(() => this.dismiss(id), duration);
        },
        dismiss(id) {
            this.items = this.items.filter(t => t.id !== id);
        }
    });

    Alpine.store('user', {
        username: '',
        accessLevel: '',
        loaded: false,
        async load() {
            if (this.loaded) return;
            try {
                const resp = await ServerRequest.fetch('/bff/current-user');
                if (resp && resp.ok) {
                    const data = await resp.json();
                    this.username = data.username;
                    this.accessLevel = data.accessLevel;
                }
            } catch (_) { /* ignore */ }
            this.loaded = true;
        }
    });
});

// ---- Header injection ----

document.addEventListener('DOMContentLoaded', () => {
    const headerEl = document.getElementById('app-header');
    if (headerEl) {
        fetch('./header.html')
            .then(r => r.text())
            .then(html => {
                headerEl.innerHTML = html;
                // Allow Alpine to pick up the new DOM
                Alpine.initTree(headerEl);
                // Highlight active nav item
                headerEl.querySelectorAll('a[href]').forEach(link => {
                    if (link.getAttribute('href') === '/gui/' + window.location.pathname.split('/').pop()) {
                        link.classList.add('active');
                    }
                });
            });
    }
});

// Backward-compat Toast global
const Toast = {
    info(msg, dur) { Alpine.store('toast').info(msg, dur); },
    error(msg, dur) { Alpine.store('toast').error(msg, dur); }
};
