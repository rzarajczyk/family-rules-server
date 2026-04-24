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

// ---- SVG Clock drawing (shared by devices + groups pages) ----
function drawClock(onlinePeriods, selectedDate) {
    const svg = document.getElementById('usage-clock');
    while (svg.firstChild) svg.removeChild(svg.firstChild);

    const ns = 'http://www.w3.org/2000/svg';
    const cx = 250, cy = 250;
    const centerR = 96;
    const chartInnerR = 100, chartOuterR = 176;
    const hourBandInnerR = 190, hourBandOuterR = 228, hourLabelR = 209;
    const tickInnerR = 180, tickOuterR = 188;

    function timeToAngle(hours) { return (hours / 24) * 2 * Math.PI - Math.PI / 2; }
    function polar(r, a) { return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) }; }

    function arcPath(r1, r2, a1, a2) {
        const p1 = polar(r2, a1), p2 = polar(r2, a2), p3 = polar(r1, a2), p4 = polar(r1, a1);
        const lg = (a2 - a1) > Math.PI ? 1 : 0;
        return `M ${p1.x} ${p1.y} A ${r2} ${r2} 0 ${lg} 1 ${p2.x} ${p2.y} L ${p3.x} ${p3.y} A ${r1} ${r1} 0 ${lg} 0 ${p4.x} ${p4.y} Z`;
    }

    function el(tag, attrs) {
        const e = document.createElementNS(ns, tag);
        for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
        return e;
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function normalizeAngleHours(hours) {
        return ((hours % 24) + 24) % 24;
    }

    function smoothstep(edge0, edge1, value) {
        const t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    function mixColor(from, to, amount) {
        const r = Math.round(from[0] + (to[0] - from[0]) * amount);
        const g = Math.round(from[1] + (to[1] - from[1]) * amount);
        const b = Math.round(from[2] + (to[2] - from[2]) * amount);
        return `rgb(${r}, ${g}, ${b})`;
    }

    function parseDateParts(dateString) {
        const [year, month, day] = (dateString || today()).split('-').map(Number);
        return { year, month, day };
    }

    function getTimeZoneOffsetHours(date, timeZone) {
        const formatter = new Intl.DateTimeFormat('en-CA', {
            timeZone,
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hourCycle: 'h23'
        });
        const parts = Object.fromEntries(
            formatter
                .formatToParts(date)
                .filter(part => part.type !== 'literal')
                .map(part => [part.type, Number(part.value)])
        );
        const zonedUtc = Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
        return (zonedUtc - date.getTime()) / 3600000;
    }

    function calculateWarsawSunTimes(dateString) {
        const { year, month, day } = parseDateParts(dateString);
        const latitude = 52.2297;
        const longitude = 21.0122;
        const zenith = 90.833;
        const targetDate = new Date(Date.UTC(year, month - 1, day, 12, 0, 0));
        const startOfYear = Date.UTC(year, 0, 1);
        const dayOfYear = Math.floor((Date.UTC(year, month - 1, day) - startOfYear) / 86400000) + 1;
        const longitudeHour = longitude / 15;
        const offsetHours = getTimeZoneOffsetHours(targetDate, 'Europe/Warsaw');

        function normalizeDegrees(degrees) {
            return ((degrees % 360) + 360) % 360;
        }

        function degreesToRadians(degrees) {
            return degrees * Math.PI / 180;
        }

        function radiansToDegrees(radians) {
            return radians * 180 / Math.PI;
        }

        function calculate(isSunrise) {
            const approxTime = dayOfYear + ((isSunrise ? 6 : 18) - longitudeHour) / 24;
            const meanAnomaly = 0.9856 * approxTime - 3.289;
            const trueLongitude = normalizeDegrees(
                meanAnomaly
                + 1.916 * Math.sin(degreesToRadians(meanAnomaly))
                + 0.020 * Math.sin(degreesToRadians(2 * meanAnomaly))
                + 282.634
            );

            let rightAscension = normalizeDegrees(radiansToDegrees(Math.atan(0.91764 * Math.tan(degreesToRadians(trueLongitude)))));
            const longitudeQuadrant = Math.floor(trueLongitude / 90) * 90;
            const ascensionQuadrant = Math.floor(rightAscension / 90) * 90;
            rightAscension = (rightAscension + longitudeQuadrant - ascensionQuadrant) / 15;

            const sinDeclination = 0.39782 * Math.sin(degreesToRadians(trueLongitude));
            const cosDeclination = Math.cos(Math.asin(sinDeclination));
            const cosLocalHour = (
                Math.cos(degreesToRadians(zenith))
                - sinDeclination * Math.sin(degreesToRadians(latitude))
            ) / (cosDeclination * Math.cos(degreesToRadians(latitude)));

            if (cosLocalHour < -1 || cosLocalHour > 1) {
                return isSunrise ? 7 : 21;
            }

            const localHourAngle = isSunrise
                ? 360 - radiansToDegrees(Math.acos(cosLocalHour))
                : radiansToDegrees(Math.acos(cosLocalHour));
            const hour = localHourAngle / 15;
            const localMeanTime = hour + rightAscension - 0.06571 * approxTime - 6.622;
            const utcHours = normalizeAngleHours(localMeanTime - longitudeHour);
            return normalizeAngleHours(utcHours + offsetHours);
        }

        return {
            sunrise: calculate(true),
            sunset: calculate(false)
        };
    }

    function hourBandTone(hour, sunrise, sunset) {
        const transitionHours = 1.5;
        const sunriseAmount = smoothstep(sunrise - transitionHours, sunrise + transitionHours, hour);
        const sunsetAmount = 1 - smoothstep(sunset - transitionHours, sunset + transitionHours, hour);
        return sunriseAmount * sunsetAmount;
    }

    const nightColor = [16, 32, 78];
    const dayColor = [135, 206, 235];
    const { sunrise, sunset } = calculateWarsawSunTimes(selectedDate);

    // Background ring for usage segments.
    svg.appendChild(el('path', { d: arcPath(chartInnerR, chartOuterR, -Math.PI / 2, 3 * Math.PI / 2), fill: '#e0e0e0' }));

    // Paint the outer hour band in small slices to simulate a smooth day/night cycle.
    const bandSlices = 96;
    for (let i = 0; i < bandSlices; i++) {
        const fromHour = i * 24 / bandSlices;
        const toHour = (i + 1) * 24 / bandSlices;
        const tone = hourBandTone((fromHour + toHour) / 2, sunrise, sunset);
        svg.appendChild(el('path', {
            d: arcPath(hourBandInnerR, hourBandOuterR, timeToAngle(fromHour), timeToAngle(toHour)),
            fill: mixColor(nightColor, dayColor, tone)
        }));
    }

    // Active arcs
    const activeSet = new Set(onlinePeriods);
    const activeMins = [];
    for (let h = 0; h < 24; h++) for (let m = 0; m < 60; m += 10) {
        const key = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0');
        if (activeSet.has(key)) activeMins.push(h * 60 + m);
    }

    const segments = [];
    let ss = null, se = null;
    for (const mins of activeMins) {
        if (ss === null) { ss = mins; se = mins; }
        else if (mins === se + 10) se = mins;
        else { segments.push([ss, se]); ss = mins; se = mins; }
    }
    if (ss !== null) segments.push([ss, se]);

    const accent = '#1565C0', accentHover = '#1E88E5';
    const tooltip = el('g', { visibility: 'hidden', 'pointer-events': 'none' });

    for (const [start, end] of segments) {
        const a1 = timeToAngle(start / 60), a2 = timeToAngle((end + 10) / 60);
        const arc = el('path', { d: arcPath(chartInnerR, chartOuterR, a1, a2), fill: accent, style: 'cursor:pointer' });
        const startL = String(Math.floor(start / 60)).padStart(2, '0') + ':' + String(start % 60).padStart(2, '0');
        const endM = end + 10;
        const endL = String(Math.floor(endM / 60) % 24).padStart(2, '0') + ':' + String(endM % 60).padStart(2, '0');
        const tip = startL + ' – ' + endL;

        arc.addEventListener('mouseenter', () => {
            arc.setAttribute('fill', accentHover);
            const mid = (a1 + a2) / 2, tp = polar(chartOuterR + 22, mid);
            const bw = 90, bh = 24;
            let bx = Math.max(4, Math.min(500 - bw - 4, tp.x - bw / 2));
            let by = Math.max(4, Math.min(500 - bh - 4, tp.y - bh / 2));
            tooltip.querySelector('rect').setAttribute('x', bx);
            tooltip.querySelector('rect').setAttribute('y', by);
            tooltip.querySelector('rect').setAttribute('width', bw);
            tooltip.querySelector('rect').setAttribute('height', bh);
            tooltip.querySelector('text').setAttribute('x', bx + bw / 2);
            tooltip.querySelector('text').setAttribute('y', by + bh / 2);
            tooltip.querySelector('text').textContent = tip;
            tooltip.setAttribute('visibility', 'visible');
        });
        arc.addEventListener('mouseleave', () => {
            arc.setAttribute('fill', accent);
            tooltip.setAttribute('visibility', 'hidden');
        });
        svg.appendChild(arc);
    }

    const tipRect = el('rect', { rx: 4, fill: '#212121', opacity: '0.85' });
    const tipText = el('text', { 'text-anchor': 'middle', 'dominant-baseline': 'middle', 'font-size': '12', 'font-family': 'sans-serif', fill: '#ffffff' });
    tooltip.appendChild(tipRect);
    tooltip.appendChild(tipText);

    svg.appendChild(el('circle', { cx, cy, r: centerR, fill: '#fafafa', stroke: '#bdbdbd', 'stroke-width': 1 }));
    svg.appendChild(el('circle', { cx, cy, r: chartOuterR, fill: 'none', stroke: '#d0d0d0', 'stroke-width': 1 }));
    svg.appendChild(el('circle', { cx, cy, r: hourBandInnerR, fill: 'none', stroke: '#e0e0e0', 'stroke-width': 1 }));
    svg.appendChild(el('circle', { cx, cy, r: hourBandOuterR, fill: 'none', stroke: '#d0d0d0', 'stroke-width': 1 }));

    for (let h = 0; h < 24; h++) {
        const a = timeToAngle(h);
        const p1 = polar(tickInnerR, a), p2 = polar(tickOuterR, a);
        const tone = hourBandTone(h, sunrise, sunset);
        const labelColor = tone < 0.45 ? '#f8fafc' : '#1f2937';
        const tickColor = tone < 0.45 ? '#dbe4ff' : '#6b7280';
        svg.appendChild(el('line', { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y, stroke: tickColor, 'stroke-width': 1 }));

        const lp = polar(hourLabelR, a);
        const t = el('text', { x: lp.x, y: lp.y, 'text-anchor': 'middle', 'dominant-baseline': 'middle', 'font-size': '11', 'font-family': 'sans-serif', fill: labelColor, 'font-weight': '600' });
        t.textContent = String(h);
        svg.appendChild(t);
    }

    svg.appendChild(tooltip);
}
