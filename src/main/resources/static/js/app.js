/* ==========================================================================
   LinkPulse — Dashboard client
   Handles theme, short-link creation, live polling, sorting and inspection.
   ========================================================================== */

// Configuration: Backend API Base URL
const API_BASE_URL = (window.location.origin.includes('5500') || window.location.origin.includes('8080'))
    ? 'http://localhost:8080'
    : (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
        ? window.location.origin
        : 'https://linkpulse-backend-thkr.onrender.com';

const POLL_INTERVAL_MS = 3000;
const THEME_STORAGE_KEY = 'linkpulse-theme';

// State management
let allUrls = [];
let sortKey = 'createdAt';
let sortDir = 'desc';
let lastCreatedCode = null;
let hasLoadedOnce = false;
let isApiOnline = true;

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
const inspectResultBtn = document.getElementById('inspect-result-btn');

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
const tableSkeleton = document.getElementById('table-skeleton');
const emptyTitle = document.getElementById('empty-title');
const emptyText = document.getElementById('empty-text');
const refreshBtn = document.getElementById('refresh-btn');
const tableSearch = document.getElementById('table-search');
const tableUrlCount = document.getElementById('table-url-count');
const clearTableSearchBtn = document.getElementById('clear-table-search');
const clearStatsInputBtn = document.getElementById('clear-stats-input');
const toastContainer = document.getElementById('toast-container');

const overviewTotalUrls = document.getElementById('overview-total-urls');
const overviewTotalClicks = document.getElementById('overview-total-clicks');
const overviewActiveUrls = document.getElementById('overview-active-urls');

const siteHeader = document.getElementById('site-header');
const themeToggle = document.getElementById('theme-toggle');
const scrollTopBtn = document.getElementById('scroll-top-btn');
const apiStatus = document.getElementById('api-status');
const apiStatusText = document.getElementById('api-status-text');

const confirmModal = document.getElementById('confirm-modal');
const confirmText = document.getElementById('confirm-text');
const confirmAccept = document.getElementById('confirm-accept');
const confirmCancel = document.getElementById('confirm-cancel');

/* --------------------------------------------------------------------------
   Theme
   -------------------------------------------------------------------------- */

/**
 * Persists and applies the given colour theme to the document root.
 *
 * @param {'dark'|'light'} theme Theme identifier to activate
 */
function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', theme === 'light' ? '#f6f7f9' : '#08080b');
    try {
        localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch (e) {
        /* Storage unavailable (private mode) — theme stays for this session only */
    }
}

themeToggle.addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
    applyTheme(next);
});

/* --------------------------------------------------------------------------
   Initialisation
   -------------------------------------------------------------------------- */

/**
 * Bootstraps the dashboard: loads the link list and starts the live poll that
 * keeps click counts and metrics current.
 */
document.addEventListener('DOMContentLoaded', () => {
    loadAllUrls();
    setInterval(() => {
        // Skip polling while the tab is in the background
        if (!document.hidden) loadAllUrls();
    }, POLL_INTERVAL_MS);
});

window.addEventListener('focus', loadAllUrls);

window.addEventListener('scroll', () => {
    const scrolled = window.scrollY > 12;
    siteHeader.classList.toggle('is-stuck', scrolled);
    scrollTopBtn.classList.toggle('is-visible', window.scrollY > 420);
}, { passive: true });

scrollTopBtn.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

shortenForm.addEventListener('submit', handleCreateShortUrl);
copyBtn.addEventListener('click', () => copyToClipboard(resultShortUrl.textContent, copyBtn));
getStatsBtn.addEventListener('click', () => handleGetStats());
refreshBtn.addEventListener('click', () => {
    loadAllUrls();
    showToast('Dashboard refreshed', 'success');
});

inspectResultBtn.addEventListener('click', () => {
    if (lastCreatedCode) inspectStats(lastCreatedCode);
});

statsShortCodeInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') handleGetStats();
});

// Filter input with a clear affordance
tableSearch.addEventListener('input', () => {
    clearTableSearchBtn.classList.toggle('hidden', !tableSearch.value);
    renderUrlTable();
});

clearTableSearchBtn.addEventListener('click', () => {
    tableSearch.value = '';
    clearTableSearchBtn.classList.add('hidden');
    renderUrlTable();
    tableSearch.focus();
});

statsShortCodeInput.addEventListener('input', () => {
    clearStatsInputBtn.classList.toggle('hidden', !statsShortCodeInput.value);
});

clearStatsInputBtn.addEventListener('click', () => {
    statsShortCodeInput.value = '';
    clearStatsInputBtn.classList.add('hidden');
    statsShortCodeInput.focus();
});

// Expiration presets
document.querySelectorAll('.preset-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const preset = btn.dataset.preset;
        document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('is-active'));

        if (preset === 'clear') {
            expiresAtInput.value = '';
            return;
        }

        const target = new Date();
        if (preset === '1h') target.setHours(target.getHours() + 1);
        else if (preset === '1d') target.setDate(target.getDate() + 1);
        else if (preset === '7d') target.setDate(target.getDate() + 7);
        else if (preset === '30d') target.setDate(target.getDate() + 30);

        expiresAtInput.value = toLocalInputValue(target);
        btn.classList.add('is-active');
    });
});

// Column sorting (click or keyboard)
document.querySelectorAll('.th-sortable').forEach(th => {
    const activate = () => {
        const key = th.dataset.sort;
        if (sortKey === key) {
            sortDir = sortDir === 'asc' ? 'desc' : 'asc';
        } else {
            sortKey = key;
            sortDir = key === 'clickCount' || key === 'createdAt' ? 'desc' : 'asc';
        }
        updateSortIndicators();
        renderUrlTable();
    };

    th.addEventListener('click', activate);
    th.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            activate();
        }
    });
});

// Row actions via delegation — keeps generated markup free of inline handlers
urlTableBody.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;

    const { action, code, url } = btn.dataset;
    if (action === 'copy') copyToClipboard(url, btn);
    else if (action === 'stats') inspectStats(code);
    else if (action === 'delete') requestDelete(code);
});

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    const typing = /^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName);

    if (e.key === '/' && !typing) {
        e.preventDefault();
        tableSearch.focus();
        tableSearch.select();
    } else if (e.key === 'Escape') {
        if (!confirmModal.classList.contains('hidden')) {
            closeConfirm();
        } else if (document.activeElement === tableSearch && tableSearch.value) {
            tableSearch.value = '';
            clearTableSearchBtn.classList.add('hidden');
            renderUrlTable();
        }
    }
});

/* --------------------------------------------------------------------------
   Create
   -------------------------------------------------------------------------- */

/**
 * Submits the shorten form, creating a new short link for the destination URL.
 *
 * @param {SubmitEvent} e Form submission event
 */
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
        const expiryDate = new Date(expiresAtRaw);
        if (expiryDate <= new Date()) {
            showToast('Expiration date must be in the future', 'error');
            return;
        }
        expiresAt = expiresAtRaw.length === 16 ? `${expiresAtRaw}:00` : expiresAtRaw;
    }

    setLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ originalUrl, expiresAt })
        });

        const data = await response.json();
        if (!response.ok) throw new Error(data.message || 'Failed to shorten URL');

        lastCreatedCode = data.shortCode;
        resultShortUrl.textContent = data.shortUrl;
        resultShortUrl.href = data.shortUrl;
        resultBox.classList.remove('hidden');

        showToast('Short URL generated successfully', 'success');
        shortenForm.reset();
        document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('is-active'));
        loadAllUrls();
    } catch (err) {
        showToast(err.message, 'error');
    } finally {
        setLoading(false);
    }
}

/* --------------------------------------------------------------------------
   Inspect
   -------------------------------------------------------------------------- */

/**
 * Fetches and renders analytics for the short code currently in the inspector.
 *
 * @param {boolean} isSilent When true, suppresses toasts during background refresh
 */
async function handleGetStats(isSilent = false) {
    const shortCode = statsShortCodeInput.value.trim();
    if (!shortCode) {
        if (!isSilent) showToast('Please enter a short code', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls/${encodeURIComponent(shortCode)}/stats`);
        const data = await response.json();
        if (!response.ok) throw new Error(data.message || 'Failed to fetch statistics');

        statOriginalUrl.textContent = data.originalUrl;
        statOriginalUrl.title = data.originalUrl;
        statShortCode.textContent = data.shortCode;
        statClicks.textContent = data.clickCount;
        statCreated.textContent = formatDate(data.createdAt);
        statExpires.textContent = data.expiresAt ? formatDate(data.expiresAt) : 'Never';

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

/**
 * Loads a short code into the inspector and scrolls it into view.
 *
 * @param {string} shortCode Short code to inspect
 */
function inspectStats(shortCode) {
    statsShortCodeInput.value = shortCode;
    clearStatsInputBtn.classList.remove('hidden');
    handleGetStats();
    document.querySelector('.inspector-panel').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/* --------------------------------------------------------------------------
   Load & render
   -------------------------------------------------------------------------- */

/**
 * Fetches every short link, refreshes the overview metrics and repaints the table.
 */
async function loadAllUrls() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/urls`);
        if (!response.ok) throw new Error('Failed to fetch URLs');

        allUrls = await response.json();
        setApiStatus(true);

        hasLoadedOnce = true;
        tableSkeleton.classList.add('hidden');

        updateOverviewMetrics(allUrls);
        renderUrlTable();

        // Silent background refresh for the inspector when it is open
        if (statsShortCodeInput.value.trim() && !statsDisplay.classList.contains('hidden')) {
            handleGetStats(true);
        }
    } catch (err) {
        setApiStatus(false);
        if (!hasLoadedOnce) tableSkeleton.classList.add('hidden');
    }
}

/**
 * Reflects backend reachability in the header status badge.
 *
 * @param {boolean} online Whether the last API call succeeded
 */
function setApiStatus(online) {
    if (online === isApiOnline && apiStatusText.textContent !== 'Connecting…') return;
    isApiOnline = online;
    apiStatus.dataset.state = online ? 'online' : 'offline';
    apiStatusText.textContent = online ? 'System Ready' : 'Backend Offline';
}

/**
 * Recomputes the hero metrics from the current link collection.
 *
 * @param {Array<Object>} urls Link records returned by the API
 */
function updateOverviewMetrics(urls) {
    let totalClicks = 0;
    let activeCount = 0;

    urls.forEach(url => {
        totalClicks += (url.clickCount || 0);
        if (!isExpired(url)) activeCount++;
    });

    overviewTotalUrls.textContent = urls.length;
    overviewTotalClicks.textContent = totalClicks;
    overviewActiveUrls.textContent = activeCount;
    tableUrlCount.textContent = `${urls.length} ${urls.length === 1 ? 'Link' : 'Links'}`;
}

/**
 * Applies the active filter and sort, then paints the dashboard table.
 */
function renderUrlTable() {
    const query = tableSearch.value.toLowerCase().trim();
    const rows = sortUrls(query ? allUrls.filter(url => matchesQuery(url, query)) : allUrls.slice());

    urlTableBody.innerHTML = '';

    if (!rows.length) {
        tableEmpty.classList.remove('hidden');
        if (query) {
            emptyTitle.textContent = 'No matching links';
            emptyText.textContent = `Nothing matches "${tableSearch.value.trim()}". Try a different search term.`;
        } else {
            emptyTitle.textContent = 'No links yet';
            emptyText.textContent = 'Enter a destination URL above to shorten your first link.';
        }
        return;
    }

    tableEmpty.classList.add('hidden');

    // Scale each click bar against the busiest link on screen
    const maxClicks = Math.max(...rows.map(u => u.clickCount || 0), 1);
    const fragment = document.createDocumentFragment();

    rows.forEach(url => {
        const expired = isExpired(url);
        const { host, path } = splitUrl(url.originalUrl);
        const clicks = url.clickCount || 0;
        const barWidth = Math.round((clicks / maxClicks) * 100);

        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td data-label="Original">
                <span class="cell-original" title="${escapeHtml(url.originalUrl)}">
                    <span class="origin-host text-break">${escapeHtml(host)}</span>
                    ${path ? `<span class="origin-path text-break">${escapeHtml(path)}</span>` : ''}
                </span>
            </td>
            <td data-label="Short link">
                <a href="${escapeHtml(url.shortUrl)}" target="_blank" rel="noopener" class="table-link"
                   title="${escapeHtml(url.shortUrl)}">/${escapeHtml(url.shortCode)}</a>
            </td>
            <td data-label="Clicks" class="col-clicks align-center">
                <span class="click-cell">
                    <span class="click-num">${clicks}</span>
                    <span class="click-bar"><i style="width:${barWidth}%"></i></span>
                </span>
            </td>
            <td data-label="Created" class="col-date">
                <span class="date-cell" title="${escapeHtml(formatDate(url.createdAt))}">
                    <span>${escapeHtml(formatDateShort(url.createdAt))}</span>
                    <span class="date-rel">${escapeHtml(formatRelative(url.createdAt))}</span>
                </span>
            </td>
            <td data-label="Expires" class="col-date">
                ${url.expiresAt
                    ? `<span class="date-cell" title="${escapeHtml(formatDate(url.expiresAt))}">
                           <span>${escapeHtml(formatDateShort(url.expiresAt))}</span>
                           <span class="date-rel">${escapeHtml(formatRelative(url.expiresAt))}</span>
                       </span>`
                    : '<span class="date-rel">Never</span>'}
            </td>
            <td data-label="Status" class="col-status">
                <span class="badge ${expired ? 'badge-expired' : 'badge-active'}">${expired ? 'Expired' : 'Active'}</span>
            </td>
            <td data-label="Actions" class="col-actions align-right">
                <span class="action-group">
                    <button class="action-btn" data-action="copy" data-url="${escapeHtml(url.shortUrl)}"
                            title="Copy short link" aria-label="Copy short link">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                        </svg>
                    </button>
                    <button class="action-btn" data-action="stats" data-code="${escapeHtml(url.shortCode)}"
                            title="Inspect analytics" aria-label="Inspect analytics">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M3 3v18h18"></path>
                            <path d="m7 14 4-4 3 3 5-6"></path>
                        </svg>
                    </button>
                    <button class="action-btn action-btn-del" data-action="delete" data-code="${escapeHtml(url.shortCode)}"
                            title="Delete link" aria-label="Delete link">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M3 6h18"></path>
                            <path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"></path>
                            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path>
                        </svg>
                    </button>
                </span>
            </td>
        `;
        fragment.appendChild(tr);
    });

    urlTableBody.appendChild(fragment);
}

/**
 * Sorts links by the active column and direction.
 *
 * @param {Array<Object>} urls Links to sort in place
 * @returns {Array<Object>} The sorted collection
 */
function sortUrls(urls) {
    const dir = sortDir === 'asc' ? 1 : -1;

    return urls.sort((a, b) => {
        let av = a[sortKey];
        let bv = b[sortKey];

        // Links that never expire sort last regardless of direction
        if (sortKey === 'expiresAt') {
            if (!av && !bv) return 0;
            if (!av) return 1;
            if (!bv) return -1;
        }

        if (sortKey === 'clickCount') {
            return ((av || 0) - (bv || 0)) * dir;
        }

        if (sortKey === 'createdAt' || sortKey === 'expiresAt') {
            return (new Date(av) - new Date(bv)) * dir;
        }

        return String(av || '').localeCompare(String(bv || ''), undefined, { sensitivity: 'base' }) * dir;
    });
}

/**
 * Syncs the aria-sort attributes so the active column arrow renders correctly.
 */
function updateSortIndicators() {
    document.querySelectorAll('.th-sortable').forEach(th => {
        th.setAttribute('aria-sort',
            th.dataset.sort === sortKey ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none');
    });
}

/**
 * Tests whether a link matches the dashboard filter query.
 *
 * @param {Object} url Link record
 * @param {string} query Lower-cased search term
 * @returns {boolean} True when any searchable field contains the query
 */
function matchesQuery(url, query) {
    return (url.originalUrl || '').toLowerCase().includes(query)
        || (url.shortCode || '').toLowerCase().includes(query)
        || (url.shortUrl || '').toLowerCase().includes(query);
}

/* --------------------------------------------------------------------------
   Delete
   -------------------------------------------------------------------------- */

let pendingDeleteCode = null;

/**
 * Opens the confirmation dialog for deleting a short link.
 *
 * @param {string} shortCode Short code queued for deletion
 */
function requestDelete(shortCode) {
    pendingDeleteCode = shortCode;
    confirmText.innerHTML = `This permanently removes <code>${escapeHtml(shortCode)}</code> and its click history. This cannot be undone.`;
    confirmModal.classList.remove('hidden');
    confirmAccept.focus();
}

function closeConfirm() {
    confirmModal.classList.add('hidden');
    pendingDeleteCode = null;
}

confirmCancel.addEventListener('click', closeConfirm);
confirmModal.addEventListener('click', (e) => {
    if (e.target === confirmModal) closeConfirm();
});

confirmAccept.addEventListener('click', async () => {
    const shortCode = pendingDeleteCode;
    closeConfirm();
    if (!shortCode) return;

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls/${encodeURIComponent(shortCode)}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.message || 'Failed to delete URL');
        }

        showToast(`Deleted link '${shortCode}'`, 'success');

        // Clear the inspector when it is showing the link that was just removed
        if (statsShortCodeInput.value.trim() === shortCode) {
            statsShortCodeInput.value = '';
            clearStatsInputBtn.classList.add('hidden');
            statsDisplay.classList.add('hidden');
            statsPlaceholder.classList.remove('hidden');
        }

        loadAllUrls();
    } catch (err) {
        showToast(err.message, 'error');
    }
});

/* --------------------------------------------------------------------------
   Helpers
   -------------------------------------------------------------------------- */

/**
 * Copies text to the clipboard and flashes confirmation on the source button.
 *
 * @param {string} text Text to place on the clipboard
 * @param {HTMLElement|null} btnElement Button to flash, if any
 */
function copyToClipboard(text, btnElement = null) {
    if (!text) return;

    navigator.clipboard.writeText(text).then(() => {
        showToast('Short URL copied to clipboard', 'success');
        if (!btnElement) return;

        btnElement.classList.add('is-copied');
        setTimeout(() => btnElement.classList.remove('is-copied'), 1400);
    }).catch(() => {
        showToast('Failed to copy to clipboard', 'error');
    });
}

/**
 * Toggles the submit button between idle and in-flight states.
 *
 * @param {boolean} isLoading Whether a request is in flight
 */
function setLoading(isLoading) {
    shortenBtnText.textContent = isLoading ? 'Shortening...' : 'Shorten URL';
    shortenSpinner.classList.toggle('hidden', !isLoading);
    shortenBtn.disabled = isLoading;
}

/**
 * Renders a transient toast notification.
 *
 * @param {string} message Text to display
 * @param {'success'|'error'} type Visual style of the toast
 */
function showToast(message, type = 'success') {
    const icon = type === 'success'
        ? '<path d="m20 6-11 11-5-5"></path>'
        : '<circle cx="12" cy="12" r="9"></circle><path d="M12 8v5M12 16.5v.01"></path>';

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <span class="toast-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">${icon}</svg>
        </span>
        <span class="toast-msg">${escapeHtml(message)}</span>
    `;

    toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.classList.add('is-leaving');
        setTimeout(() => toast.remove(), 200);
    }, 3200);
}

/**
 * Determines whether a link has passed its expiration timestamp.
 *
 * @param {Object} url Link record
 * @returns {boolean} True when the link is expired
 */
function isExpired(url) {
    return Boolean(url.expiresAt) && new Date(url.expiresAt) < new Date();
}

/**
 * Splits a URL into a host label and a shortened path for two-line display.
 *
 * @param {string} rawUrl Absolute URL
 * @returns {{host: string, path: string}} Display parts
 */
function splitUrl(rawUrl) {
    try {
        const parsed = new URL(rawUrl);
        const path = parsed.pathname === '/' ? '' : parsed.pathname;
        return { host: parsed.hostname.replace(/^www\./, ''), path: path + parsed.search };
    } catch (e) {
        return { host: rawUrl, path: '' };
    }
}

/**
 * Converts a Date into the value format expected by datetime-local inputs.
 *
 * @param {Date} date Date to format
 * @returns {string} Value in YYYY-MM-DDTHH:mm form
 */
function toLocalInputValue(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatDate(dateString) {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString(undefined, {
        month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
}

function formatDateShort(dateString) {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleDateString(undefined, {
        month: 'short', day: 'numeric', year: 'numeric'
    });
}

/**
 * Renders a timestamp as a coarse relative label such as "3h ago" or "in 2d".
 *
 * @param {string} dateString ISO-like timestamp
 * @returns {string} Human-readable relative label
 */
function formatRelative(dateString) {
    if (!dateString) return '';

    const diffMs = new Date(dateString).getTime() - Date.now();
    const future = diffMs > 0;
    const abs = Math.abs(diffMs);

    const minutes = Math.round(abs / 60000);
    const hours = Math.round(abs / 3600000);
    const days = Math.round(abs / 86400000);

    let label;
    if (minutes < 1) label = 'just now';
    else if (minutes < 60) label = `${minutes}m`;
    else if (hours < 24) label = `${hours}h`;
    else if (days < 30) label = `${days}d`;
    else label = `${Math.round(days / 30)}mo`;

    if (label === 'just now') return label;
    return future ? `in ${label}` : `${label} ago`;
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>'"]/g, tag => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        "'": '&#39;',
        '"': '&quot;'
    }[tag] || tag));
}

// Reflect the initial sort state in the table header
updateSortIndicators();
