// Configuration: Backend API Base URL
const API_BASE_URL = (window.location.origin.includes('5500') || window.location.origin.includes('8080'))
    ? 'http://localhost:8080'
    : (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
        ? window.location.origin
        : 'https://linkpulse-backend-thkr.onrender.com';

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

// Overview Counters
const overviewTotalUrls = document.getElementById('overview-total-urls');
const overviewTotalClicks = document.getElementById('overview-total-clicks');
const overviewActiveUrls = document.getElementById('overview-active-urls');

/**
 * Global Initialization: Listens for DOMContentLoaded event to fetch initial links list
 * and establishes a 3-second recurring interval for live click count updates.
 */
document.addEventListener('DOMContentLoaded', () => {
    loadAllUrls();
    // Real-Time Polling: Refresh click counts and metrics every 3 seconds
    setInterval(loadAllUrls, 3000);
});

// Auto-refresh when switching back to the dashboard tab
window.addEventListener('focus', loadAllUrls);

shortenForm.addEventListener('submit', handleCreateShortUrl);
copyBtn.addEventListener('click', () => copyToClipboard(resultShortUrl.textContent));
getStatsBtn.addEventListener('click', handleGetStats);
refreshBtn.addEventListener('click', loadAllUrls);
tableSearch.addEventListener('input', handleTableSearch);

const clearTableSearchBtn = document.getElementById('clear-table-search');
const clearStatsInputBtn = document.getElementById('clear-stats-input');

if (tableSearch && clearTableSearchBtn) {
    tableSearch.addEventListener('input', () => {
        if (tableSearch.value) {
            clearTableSearchBtn.classList.remove('hidden');
        } else {
            clearTableSearchBtn.classList.add('hidden');
        }
    });
    clearTableSearchBtn.addEventListener('click', () => {
        tableSearch.value = '';
        clearTableSearchBtn.classList.add('hidden');
        handleTableSearch();
    });
}

if (statsShortCodeInput && clearStatsInputBtn) {
    statsShortCodeInput.addEventListener('input', () => {
        if (statsShortCodeInput.value) {
            clearStatsInputBtn.classList.remove('hidden');
        } else {
            clearStatsInputBtn.classList.add('hidden');
        }
    });
    clearStatsInputBtn.addEventListener('click', () => {
        statsShortCodeInput.value = '';
        clearStatsInputBtn.classList.add('hidden');
    });
}

// Expiration preset buttons handler
document.querySelectorAll('.preset-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
        const preset = e.target.getAttribute('data-preset');
        if (preset === 'clear') {
            expiresAtInput.value = '';
            return;
        }

        const now = new Date();
        if (preset === '1h') now.setHours(now.getHours() + 1);
        else if (preset === '1d') now.setDate(now.getDate() + 1);
        else if (preset === '7d') now.setDate(now.getDate() + 7);

        // Format to ISO string format suitable for datetime-local input (YYYY-MM-DDTHH:mm)
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');

        expiresAtInput.value = `${year}-${month}-${day}T${hours}:${minutes}`;
    });
});

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
        showToast(err.message, 'error');
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
            showToast(err.message, 'error');
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
        showToast(err.message, 'error');
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
