// Configuration: Backend API Base URL
// When running inside Docker (Nginx container on port 80), API requests use relative origin (Nginx proxies /api/ -> backend:8080).
// When running served by Spring Boot directly on 8080, API requests target localhost:8080.
// When running from any static dev server (Live Server, http-server, etc.), target http://localhost:8080 directly.
// When running deployed on Render, target the Render backend URL.
const SPRING_BOOT_LOCAL_URL = 'http://localhost:8080';

const API_BASE_URL = (() => {
    const origin = window.location.origin;
    const hostname = window.location.hostname;
    const port = window.location.port;

    // Deployed on Render (not localhost/local network)
    if (hostname !== 'localhost' && hostname !== '127.0.0.1' && !hostname.startsWith('192.168.')) {
        return 'https://linkpulse-backend-thkr.onrender.com';
    }

    // Docker Nginx proxy on port 80, or Spring Boot embedded static server on port 8080
    if (port === '' || port === '80' || port === '8080') {
        return origin;
    }

    // Served from any other local port (e.g. 5500, 8090, 8091) or file:// protocol
    return SPRING_BOOT_LOCAL_URL;
})();

// State management
let allUrls = [];

// DOM References
const shortenForm = document.getElementById('shorten-form');
const originalUrlInput = document.getElementById('original-url');
const expiresAtInput = document.getElementById('expires-at');
const shortenBtn = document.getElementById('shorten-btn');
const shortenBtnText = shortenBtn.querySelector('.btn-text');
const shortenSpinner = shortenBtn.querySelector('.spinner');

const resultBox = document.getElementById('result-box');
const resultShortUrl = document.getElementById('result-short-url');
const copyBtn = document.getElementById('copy-btn');

const statsShortCodeInput = document.getElementById('stats-short-code');
const getStatsBtn = document.getElementById('get-stats-btn');
const statsDisplay = document.getElementById('stats-display');
const statsPlaceholder = document.getElementById('stats-placeholder');
const statOriginalUrl = document.getElementById('stat-original-url');
const statShortCode = document.getElementById('stat-short-code');
const statStatus = document.getElementById('stat-status');
const statClicks = document.getElementById('stat-clicks');
const statCreated = document.getElementById('stat-created');
const statExpires = document.getElementById('stat-expires');

const urlTableBody = document.getElementById('url-table-body');
const tableEmpty = document.getElementById('table-empty');
const refreshBtn = document.getElementById('refresh-btn');
const tableSearch = document.getElementById('table-search');
const tableUrlCount = document.getElementById('table-url-count');
const toastContainer = document.getElementById('toast-container');

// Backend Status Badge Elements
const backendStatusBadge = document.getElementById('backend-status-badge');
const backendStatusDot = document.getElementById('backend-status-dot');
const backendStatusText = document.getElementById('backend-status-text');

// Overview Counters
const overviewTotalUrls = document.getElementById('overview-total-urls');
const overviewTotalClicks = document.getElementById('overview-total-clicks');
const overviewActiveUrls = document.getElementById('overview-active-urls');

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    checkBackendStatus();
    loadAllUrls();
    // Refresh URLs every 3s
    setInterval(loadAllUrls, 3000);
    // Periodically check backend health every 10s
    setInterval(checkBackendStatus, 10000);
});

// Auto-refresh when switching back to the dashboard tab
window.addEventListener('focus', () => {
    checkBackendStatus();
    loadAllUrls();
});

shortenForm.addEventListener('submit', handleCreateShortUrl);
copyBtn.addEventListener('click', () => copyToClipboard(resultShortUrl.textContent));
getStatsBtn.addEventListener('click', handleGetStats);
refreshBtn.addEventListener('click', loadAllUrls);
tableSearch.addEventListener('input', handleTableSearch);

// Create Short URL Handler
async function handleCreateShortUrl(e) {
    e.preventDefault();
    let originalUrl = originalUrlInput.value.trim();
    const expiresAtRaw = expiresAtInput.value;

    if (!originalUrl) {
        showToast('Please enter a destination URL', 'error');
        return;
    }

    // Auto-prepend https:// if URL scheme is missing
    if (!/^https?:\/\//i.test(originalUrl)) {
        originalUrl = 'https://' + originalUrl;
    }

    let expiresAt = null;
    if (expiresAtRaw) {
        expiresAt = new Date(expiresAtRaw).toISOString().split('.')[0];
    }

    setLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ originalUrl, expiresAt })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Failed to shorten URL');
        }

        // Render result box
        resultShortUrl.textContent = data.shortUrl;
        resultShortUrl.href = data.shortUrl;
        resultBox.classList.remove('hidden');

        showToast('Short URL generated successfully', 'success');
        shortenForm.reset();
        loadAllUrls();

    } catch (err) {
        showToast(getErrorMessage(err), 'error');
    } finally {
        setLoading(false);
    }
}

// Get Stats Handler
async function handleGetStats(isSilent = false) {
    const shortCode = statsShortCodeInput.value.trim();
    if (!shortCode) {
        if (!isSilent) showToast('Please enter a short code', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls/${encodeURIComponent(shortCode)}/stats`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Failed to fetch statistics');
        }

        statOriginalUrl.textContent = data.originalUrl;
        statShortCode.textContent = data.shortCode;
        statClicks.textContent = data.clickCount;
        statCreated.textContent = formatDate(data.createdAt);
        statExpires.textContent = data.expiresAt ? formatDate(data.expiresAt) : 'Never';

        // Status badge
        statStatus.textContent = data.status;
        statStatus.className = `badge ${data.status === 'ACTIVE' ? 'badge-active' : 'badge-expired'}`;

        statsPlaceholder.classList.add('hidden');
        statsDisplay.classList.remove('hidden');
        if (!isSilent) showToast(`Loaded analytics for '${shortCode}'`, 'success');

    } catch (err) {
        if (!isSilent) {
            statsDisplay.classList.add('hidden');
            statsPlaceholder.classList.remove('hidden');
            showToast(getErrorMessage(err), 'error');
        }
    }
}

// Load All Created URLs
async function loadAllUrls() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/urls`);
        if (!response.ok) throw new Error('Failed to fetch URLs');

        allUrls = await response.json();
        updateOverviewMetrics(allUrls);
        renderUrlTable(allUrls);

        // Silent background refresh for inspector if active
        if (statsShortCodeInput.value.trim() && !statsDisplay.classList.contains('hidden')) {
            handleGetStats(true);
        }
    } catch (err) {
        // Silently handle background polling connectivity glitches
    }
}

// Update Top Overview Cards
function updateOverviewMetrics(urls) {
    let totalClicks = 0;
    let activeCount = 0;

    urls.forEach(url => {
        totalClicks += (url.clickCount || 0);
        const isExpired = url.expiresAt && new Date(url.expiresAt) < new Date();
        if (!isExpired) activeCount++;
    });

    overviewTotalUrls.textContent = urls.length;
    overviewTotalClicks.textContent = totalClicks;
    overviewActiveUrls.textContent = activeCount;
    tableUrlCount.textContent = `${urls.length} Links`;
}

// Render Table Rows
function renderUrlTable(urls) {
    urlTableBody.innerHTML = '';

    if (!urls || urls.length === 0) {
        tableEmpty.classList.remove('hidden');
        return;
    }

    tableEmpty.classList.add('hidden');

    urls.forEach(url => {
        const isExpired = url.expiresAt && new Date(url.expiresAt) < new Date();
        const statusText = isExpired ? 'EXPIRED' : 'ACTIVE';
        const statusClass = isExpired ? 'badge-expired' : 'badge-active';

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><span class="text-break" title="${escapeHtml(url.originalUrl)}">${escapeHtml(url.originalUrl)}</span></td>
            <td><a href="${url.shortUrl}" target="_blank" class="table-link" title="${escapeHtml(url.shortUrl)}">${escapeHtml(url.shortUrl)}</a></td>
            <td class="align-center"><strong>${url.clickCount}</strong></td>
            <td>${formatDateShort(url.createdAt)}</td>
            <td>${url.expiresAt ? formatDateShort(url.expiresAt) : 'Never'}</td>
            <td><span class="badge ${statusClass}">${statusText}</span></td>
            <td class="align-right">
                <div class="action-group">
                    <button class="action-btn" title="Copy Link" onclick="copyToClipboard('${url.shortUrl}')">Copy</button>
                    <button class="action-btn" title="Inspect Stats" onclick="inspectStatsFromTable('${url.shortCode}')">Stats</button>
                    <button class="action-btn action-btn-del" title="Delete URL" onclick="deleteUrl('${url.shortCode}')">Delete</button>
                </div>
            </td>
        `;
        urlTableBody.appendChild(tr);
    });
}

// Table Search Filtering
function handleTableSearch() {
    const query = tableSearch.value.toLowerCase().trim();
    if (!query) {
        renderUrlTable(allUrls);
        return;
    }

    const filtered = allUrls.filter(url => 
        url.originalUrl.toLowerCase().includes(query) ||
        url.shortCode.toLowerCase().includes(query) ||
        url.shortUrl.toLowerCase().includes(query)
    );

    renderUrlTable(filtered);
}

// Delete Short URL
async function deleteUrl(shortCode) {
    if (!confirm(`Delete short code '${shortCode}'?`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls/${encodeURIComponent(shortCode)}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const data = await response.json();
            throw new Error(data.message || 'Failed to delete URL');
        }

        showToast(`Deleted link '${shortCode}'`, 'success');
        loadAllUrls();
    } catch (err) {
        showToast(getErrorMessage(err), 'error');
    }
}

// Helper: Inspect Stats from Table
function inspectStatsFromTable(shortCode) {
    statsShortCodeInput.value = shortCode;
    handleGetStats();
    statsDisplay.scrollIntoView({ behavior: 'smooth' });
}

// Helper: Copy to Clipboard
function copyToClipboard(text) {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
        showToast('Short URL copied to clipboard', 'success');
    }).catch(() => {
        showToast('Failed to copy to clipboard', 'error');
    });
}

// UI State Helpers
function setLoading(isLoading) {
    if (isLoading) {
        shortenBtnText.textContent = 'Shortening...';
        shortenSpinner.classList.remove('hidden');
        shortenBtn.disabled = true;
    } else {
        shortenBtnText.textContent = 'Shorten URL';
        shortenSpinner.classList.add('hidden');
        shortenBtn.disabled = false;
    }
}

function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `<span>${escapeHtml(message)}</span>`;

    toastContainer.appendChild(toast);
    setTimeout(() => {
        toast.remove();
    }, 3500);
}

function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatDateShort(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric'
    });
}

function escapeHtml(str) {
    return str.replace(/[&<>'"]/g, 
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}

// Detect network/backend-down errors and return a professional message
function getErrorMessage(err) {
    if (err instanceof TypeError && err.message === 'Failed to fetch') {
        return 'Backend is currently unavailable. Please try again later.';
    }
    return err.message || 'An unexpected error occurred.';
}

// Dynamic Backend Status Check
function updateBackendBadge(isOnline) {
    if (isOnline) {
        backendStatusBadge.className = 'tag tag-status-online';
        backendStatusBadge.title = 'Spring Boot backend is reachable.';
        backendStatusDot.className = 'status-dot status-dot-online';
        backendStatusText.textContent = 'Backend Online';
    } else {
        backendStatusBadge.className = 'tag tag-status-warn';
        backendStatusBadge.title = 'Backend is currently unavailable. Backend-dependent features cannot be used right now.';
        backendStatusDot.className = 'status-dot status-dot-warn';
        backendStatusText.textContent = 'Backend Offline';
    }
}

async function checkBackendStatus() {
    const healthUrl = `${API_BASE_URL}/api/urls`;
    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 3000);

        const response = await fetch(healthUrl, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            signal: controller.signal
        });
        clearTimeout(timeoutId);

        if (!response.ok) {
            updateBackendBadge(false);
            return;
        }

        // Must be JSON response from Spring Boot REST API
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            updateBackendBadge(false);
            return;
        }

        // Verify JSON array can be parsed
        const data = await response.json();
        if (Array.isArray(data)) {
            updateBackendBadge(true);
        } else {
            updateBackendBadge(false);
        }
    } catch (err) {
        updateBackendBadge(false);
    }
}
