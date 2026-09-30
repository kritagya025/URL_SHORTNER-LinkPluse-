/* ==========================================================================
   LinkPulse — Dashboard client
   Handles theme, short-link creation, live polling, sorting, filtering,
   the details panel and deletion.
   ========================================================================== */

// Configuration: Backend API Base URL
const API_BASE_URL = (window.location.origin.includes('5500') || window.location.origin.includes('8080'))
    ? 'http://localhost:8080'
    : (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
        ? window.location.origin
        : 'https://linkpulse-backend-thkr.onrender.com';

const POLL_INTERVAL_MS = 3000;
const THEME_STORAGE_KEY = 'linkpulse-theme';
const COPIED_RESET_MS = 1500;

const NETWORK_ERROR_MESSAGE = "Can't reach the server. Check your connection and try again.";

// State management
let allUrls = [];
let sortKey = 'createdAt';
let sortDir = 'desc';
let statusFilter = 'all';
let lastCreatedCode = null;
let highlightCode = null;
let hasLoadedOnce = false;
let apiState = 'connecting';
let lastSyncAt = null;
let loadInFlight = null;
let mutationVersion = 0;

let detailsCode = null;
let detailsTrigger = null;
let detailsHasData = false;

let pendingDeleteCode = null;
let deleteTrigger = null;
let isDeleting = false;

// Rows are keyed by short code and reused between renders so focus survives polling
const rowCache = new Map();

// DOM References
const shortenForm = document.getElementById('shorten-form');
const createBar = document.getElementById('create-bar');
const originalUrlInput = document.getElementById('original-url');
const expiresAtInput = document.getElementById('expires-at');
const shortenBtn = document.getElementById('shorten-btn');
const shortenBtnText = shortenBtn.querySelector('.btn-text');
const shortenSpinner = shortenBtn.querySelector('.spinner');
const createError = document.getElementById('create-error');

const expiryTrigger = document.getElementById('expiry-trigger');
const expiryPanel = document.getElementById('expiry-panel');
const expiryLabel = document.getElementById('expiry-label');
const expirySummary = document.getElementById('expiry-summary');
const expiryDone = document.getElementById('expiry-done');
const presetBtns = document.querySelectorAll('.preset-btn');

const resultBox = document.getElementById('result-box');
const resultShortUrl = document.getElementById('result-short-url');
const copyBtn = document.getElementById('copy-btn');
const inspectResultBtn = document.getElementById('inspect-result-btn');
const resultDismiss = document.getElementById('result-dismiss');

const siteHeader = document.getElementById('site-header');
const lookupForm = document.getElementById('lookup-form');
const lookupToggle = document.getElementById('lookup-toggle');
const statsShortCodeInput = document.getElementById('stats-short-code');

const workspace = document.getElementById('workspace');
const details = document.getElementById('details');
const detailsScrim = document.getElementById('details-scrim');
const detailsClose = document.getElementById('details-close');
const detailsLoading = document.getElementById('details-loading');
const detailsLoadingText = document.getElementById('details-loading-text');
const detailsError = document.getElementById('details-error');
const detailsErrorTitle = document.getElementById('details-error-title');
const detailsErrorText = document.getElementById('details-error-text');
const detailsRetry = document.getElementById('details-retry');
const detailsNotice = document.getElementById('details-notice');
const detailsCopyShort = document.getElementById('details-copy-short');
const detailsCopyDest = document.getElementById('details-copy-dest');
const detailsDelete = document.getElementById('details-delete');
const statsDisplay = document.getElementById('stats-display');
const statShortUrl = document.getElementById('stat-short-url');
const statOriginalUrl = document.getElementById('stat-original-url');
const statShortCode = document.getElementById('stat-short-code');
const statStatus = document.getElementById('stat-status');
const statClicks = document.getElementById('stat-clicks');
const statRedirect = document.getElementById('stat-redirect');
const statCreated = document.getElementById('stat-created');
const statExpires = document.getElementById('stat-expires');

const linksTable = document.getElementById('links-table');
const urlTableBody = document.getElementById('url-table-body');
const tableEmpty = document.getElementById('table-empty');
const tableError = document.getElementById('table-error');
const tableSkeleton = document.getElementById('table-skeleton');
const emptyTitle = document.getElementById('empty-title');
const emptyText = document.getElementById('empty-text');
const emptyAction = document.getElementById('empty-action');
const refreshBtn = document.getElementById('refresh-btn');
const refreshLabel = refreshBtn.querySelector('.btn-label');
const tableSearch = document.getElementById('table-search');
const searchKbd = document.getElementById('search-kbd');
const clearTableSearchBtn = document.getElementById('clear-table-search');
const tableUrlCount = document.getElementById('table-url-count');
const statusBtns = document.querySelectorAll('.seg-btn');
const mobileSort = document.getElementById('mobile-sort');
const connNotice = document.getElementById('conn-notice');
const connNoticeText = document.getElementById('conn-notice-text');
const syncStatus = document.getElementById('sync-status');
const syncText = document.getElementById('sync-text');

const overviewTotalUrls = document.getElementById('overview-total-urls');
const overviewUrlsWord = document.getElementById('overview-urls-word');
const overviewTotalClicks = document.getElementById('overview-total-clicks');
const overviewClicksWord = document.getElementById('overview-clicks-word');
const overviewActiveUrls = document.getElementById('overview-active-urls');
const overviewExpiredUrls = document.getElementById('overview-expired-urls');

const themeToggle = document.getElementById('theme-toggle');
const scrollTopBtn = document.getElementById('scroll-top-btn');
const apiStatus = document.getElementById('api-status');
const apiStatusText = document.getElementById('api-status-text');
const apiLive = document.getElementById('api-live');
const apiLatency = document.getElementById('api-latency');

const toastContainer = document.getElementById('toast-container');
const srPolite = document.getElementById('sr-polite');
const srAssertive = document.getElementById('sr-assertive');

const confirmModal = document.getElementById('confirm-modal');
const confirmText = document.getElementById('confirm-text');
const confirmError = document.getElementById('confirm-error');
const confirmAccept = document.getElementById('confirm-accept');
const confirmAcceptText = confirmAccept.querySelector('.btn-text');
const confirmAcceptSpinner = confirmAccept.querySelector('.spinner');
const confirmCancel = document.getElementById('confirm-cancel');

// Details dock beside the table on wide screens and overlay the page otherwise
const dockedQuery = window.matchMedia('(min-width: 1280px)');

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
    if (meta) meta.setAttribute('content', theme === 'light' ? '#F7F8FA' : '#0D0F12');
    syncThemeToggleLabel();
    try {
        localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch (e) {
        /* Storage unavailable (private mode) — theme stays for this session only */
    }
}

function syncThemeToggleLabel() {
    const isLight = document.documentElement.getAttribute('data-theme') === 'light';
    themeToggle.setAttribute('aria-label', isLight ? 'Switch to dark theme' : 'Switch to light theme');
    themeToggle.title = isLight ? 'Switch to dark theme' : 'Switch to light theme';
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
    scrollTopBtn.classList.toggle('is-visible', window.scrollY > 420);
}, { passive: true });

scrollTopBtn.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));

shortenForm.addEventListener('submit', handleCreateShortUrl);
originalUrlInput.addEventListener('input', clearCreateError);

copyBtn.addEventListener('click', () => copyToClipboard(resultShortUrl.textContent, copyBtn));
inspectResultBtn.addEventListener('click', () => {
    if (lastCreatedCode) inspectStats(lastCreatedCode, inspectResultBtn);
});
resultDismiss.addEventListener('click', () => {
    resultBox.classList.add('hidden');
    originalUrlInput.focus();
});

refreshBtn.addEventListener('click', async () => {
    refreshBtn.disabled = true;
    refreshLabel.textContent = 'Refreshing…';
    const ok = await loadAllUrls();
    refreshBtn.disabled = false;
    refreshLabel.textContent = ok ? 'Refreshed' : 'Refresh';
    if (ok) {
        announce('Links refreshed');
        setTimeout(() => { refreshLabel.textContent = 'Refresh'; }, 1200);
    }
});

document.querySelectorAll('[data-retry]').forEach(btn => btn.addEventListener('click', () => loadAllUrls()));

// Lookup by short code (header)
lookupForm.addEventListener('submit', (e) => {
    e.preventDefault();
    handleGetStats();
});

lookupToggle.addEventListener('click', () => {
    const open = !siteHeader.classList.contains('lookup-open');
    setMobileLookup(open);
});

function setMobileLookup(open) {
    siteHeader.classList.toggle('lookup-open', open);
    lookupToggle.setAttribute('aria-expanded', String(open));
    if (open) statsShortCodeInput.focus();
}

// Filter input with a clear affordance
tableSearch.addEventListener('input', () => {
    syncSearchAffordances();
    renderUrlTable();
});

clearTableSearchBtn.addEventListener('click', () => {
    tableSearch.value = '';
    syncSearchAffordances();
    renderUrlTable();
    tableSearch.focus();
});

function syncSearchAffordances() {
    const hasValue = Boolean(tableSearch.value);
    clearTableSearchBtn.classList.toggle('hidden', !hasValue);
    searchKbd.classList.toggle('hidden', hasValue);
}

// Status filter
statusBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        statusFilter = btn.dataset.status;
        statusBtns.forEach(b => b.setAttribute('aria-pressed', String(b === btn)));
        renderUrlTable();
    });
});

emptyAction.addEventListener('click', () => {
    tableSearch.value = '';
    syncSearchAffordances();
    statusFilter = 'all';
    statusBtns.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.status === 'all')));
    renderUrlTable();
    tableSearch.focus();
});

// Expiry: presets, custom date and the disclosure panel that holds them
presetBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        applyPreset(btn.dataset.preset);
        closeExpiryPanel(true);
    });
});

expiresAtInput.addEventListener('input', () => {
    activePreset = expiresAtInput.value ? 'custom' : 'clear';
    syncExpiryUi();
});

expiryTrigger.addEventListener('click', () => {
    if (expiryPanel.hidden) openExpiryPanel();
    else closeExpiryPanel(true);
});

expiryDone.addEventListener('click', () => closeExpiryPanel(true));

document.addEventListener('click', (e) => {
    if (!expiryPanel.hidden && !expiryPanel.contains(e.target) && !expiryTrigger.contains(e.target)) {
        closeExpiryPanel(false);
    }
});

expiryPanel.addEventListener('focusout', (e) => {
    if (e.relatedTarget && !expiryPanel.contains(e.relatedTarget) && e.relatedTarget !== expiryTrigger) {
        closeExpiryPanel(false);
    }
});

// Column sorting (header buttons, or the select on phones)
document.querySelectorAll('.sort-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const key = btn.dataset.sort;
        if (sortKey === key) {
            sortDir = sortDir === 'asc' ? 'desc' : 'asc';
        } else {
            sortKey = key;
            sortDir = key === 'clickCount' || key === 'createdAt' ? 'desc' : 'asc';
        }
        updateSortIndicators();
        renderUrlTable();
    });
});

mobileSort.addEventListener('change', () => {
    const [key, dir] = mobileSort.value.split(':');
    sortKey = key;
    sortDir = dir;
    updateSortIndicators();
    renderUrlTable();
});

// Row actions via delegation — keeps generated markup free of inline handlers
urlTableBody.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-action]');
    if (!btn) return;

    const { action, code, url } = btn.dataset;
    if (action === 'copy') copyToClipboard(url, btn);
    else if (action === 'stats') inspectStats(code, btn);
    else if (action === 'delete') requestDelete(code, btn);
});

// Details panel
detailsClose.addEventListener('click', () => closeDetails());
detailsScrim.addEventListener('click', () => closeDetails());
detailsRetry.addEventListener('click', () => {
    if (detailsCode) fetchDetails(detailsCode, false);
});
detailsCopyShort.addEventListener('click', () => copyToClipboard(statShortUrl.textContent, detailsCopyShort));
detailsCopyDest.addEventListener('click', () => copyToClipboard(statOriginalUrl.textContent, detailsCopyDest));
detailsDelete.addEventListener('click', () => {
    if (detailsCode) requestDelete(detailsCode, detailsDelete);
});
details.addEventListener('keydown', (e) => {
    if (e.key === 'Tab' && !dockedQuery.matches) trapFocus(details, e);
});

dockedQuery.addEventListener('change', applyDetailsMode);

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName);

    if (e.key === '/' && !typing && !e.ctrlKey && !e.metaKey && !e.altKey && !isOverlayOpen()) {
        e.preventDefault();
        tableSearch.focus();
        tableSearch.select();
    } else if (e.key === 'Escape') {
        if (!confirmModal.classList.contains('hidden')) {
            if (!isDeleting) closeConfirm();
        } else if (!expiryPanel.hidden) {
            closeExpiryPanel(true);
        } else if (!details.hidden) {
            closeDetails();
        } else if (siteHeader.classList.contains('lookup-open')) {
            setMobileLookup(false);
            lookupToggle.focus();
        } else if (document.activeElement === tableSearch && tableSearch.value) {
            tableSearch.value = '';
            syncSearchAffordances();
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
    clearCreateError();
    let originalUrl = originalUrlInput.value.trim();
    const expiresAtRaw = expiresAtInput.value;

    if (!originalUrl) {
        showCreateError('Enter a URL to shorten.');
        return;
    }

    // Auto-prepend https:// if URL scheme is missing
    if (!/^https?:\/\//i.test(originalUrl)) {
        originalUrl = 'https://' + originalUrl;
    }

    if (!hasValidHost(originalUrl)) {
        showCreateError("That doesn't look like a valid URL. Check it and try again.");
        return;
    }

    let expiresAt = null;
    if (expiresAtRaw) {
        const expiryDate = new Date(expiresAtRaw);
        if (expiryDate <= new Date()) {
            showCreateError('Expiration date must be in the future.');
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

        const data = await readJson(response);
        if (!response.ok) throw new ApiError(data.message || 'Failed to shorten URL', response.status);

        lastCreatedCode = data.shortCode;
        highlightCode = data.shortCode;
        resultShortUrl.textContent = data.shortUrl;
        resultShortUrl.href = data.shortUrl;
        resetCopied(copyBtn);
        resultBox.classList.remove('hidden');
        announce(`Short URL created: ${data.shortUrl}`);

        shortenForm.reset();
        applyPreset('clear');
        reloadAfterMutation();
    } catch (err) {
        showCreateError(humanizeError(err));
    } finally {
        setLoading(false);
    }
}

/**
 * Mirrors the backend's structural check (http/https scheme plus a host) so obvious
 * typos are caught before the request.
 *
 * @param {string} value Candidate URL with a scheme
 * @returns {boolean} True when the URL parses and has a host
 */
function hasValidHost(value) {
    try {
        return Boolean(new URL(value).hostname);
    } catch (e) {
        return false;
    }
}

function showCreateError(message) {
    createError.textContent = message;
    createBar.classList.add('has-error');
    originalUrlInput.setAttribute('aria-invalid', 'true');
    originalUrlInput.focus();
}

function clearCreateError() {
    if (!createError.textContent) return;
    createError.textContent = '';
    createBar.classList.remove('has-error');
    originalUrlInput.removeAttribute('aria-invalid');
}

/* --------------------------------------------------------------------------
   Expiry
   -------------------------------------------------------------------------- */

const PRESET_LABELS = { clear: 'No expiry', '1h': '+1 hour', '1d': '+1 day', '7d': '+7 days', '30d': '+30 days' };
let activePreset = 'clear';

/**
 * Applies an expiration preset relative to the current time.
 *
 * @param {'clear'|'1h'|'1d'|'7d'|'30d'} preset Preset identifier
 */
function applyPreset(preset) {
    activePreset = preset;

    if (preset === 'clear') {
        expiresAtInput.value = '';
    } else {
        const target = new Date();
        if (preset === '1h') target.setHours(target.getHours() + 1);
        else if (preset === '1d') target.setDate(target.getDate() + 1);
        else if (preset === '7d') target.setDate(target.getDate() + 7);
        else if (preset === '30d') target.setDate(target.getDate() + 30);
        expiresAtInput.value = toLocalInputValue(target);
    }

    syncExpiryUi();
}

function syncExpiryUi() {
    const value = expiresAtInput.value;

    presetBtns.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.preset === activePreset)));

    if (!value) {
        expiryLabel.textContent = 'No expiry';
    } else if (activePreset !== 'custom') {
        expiryLabel.textContent = PRESET_LABELS[activePreset];
    } else {
        expiryLabel.textContent = formatDateShort(value);
    }

    expiryTrigger.classList.toggle('has-value', Boolean(value));
    expirySummary.textContent = value ? `Expires ${formatDate(value)}` : '';
}

function openExpiryPanel() {
    expiryPanel.hidden = false;
    expiryTrigger.setAttribute('aria-expanded', 'true');
    const current = expiryPanel.querySelector('.expiry-opt[aria-pressed="true"]') || expiresAtInput;
    current.focus();
}

function closeExpiryPanel(returnFocus) {
    if (expiryPanel.hidden) return;
    expiryPanel.hidden = true;
    expiryTrigger.setAttribute('aria-expanded', 'false');
    if (returnFocus) expiryTrigger.focus();
}

/* --------------------------------------------------------------------------
   Details (inspect)
   -------------------------------------------------------------------------- */

/**
 * Looks up the short code typed into the header lookup and opens its details.
 * Accepts a bare code or a full short URL.
 *
 * @param {boolean} isSilent When true, suppresses feedback during background refresh
 */
function handleGetStats(isSilent = false) {
    const shortCode = extractShortCode(statsShortCodeInput.value);
    if (!shortCode) {
        if (!isSilent) statsShortCodeInput.focus();
        return;
    }
    inspectStats(shortCode, statsShortCodeInput);
}

/**
 * Opens the details panel for a short code and loads its statistics.
 *
 * @param {string} shortCode Short code to inspect
 * @param {HTMLElement} trigger Element that opened the panel; focus returns here on close
 */
function inspectStats(shortCode, trigger) {
    detailsCode = shortCode;
    if (trigger) detailsTrigger = trigger;
    detailsNotice.classList.add('hidden');

    // Show what the list already knows immediately; the stats call then confirms it
    const known = allUrls.find(u => u.shortCode === shortCode);
    if (known) {
        fillDetails({ ...known, status: isExpired(known) ? 'EXPIRED' : 'ACTIVE' });
        showDetailsState('content');
    } else {
        detailsHasData = false;
        detailsLoadingText.textContent = `Loading ${shortCode}…`;
        showDetailsState('loading');
    }

    openDetails();
    markSelectedRow();
    fetchDetails(shortCode, false);
}

/**
 * Fetches statistics for the open details panel.
 *
 * @param {string} shortCode Short code to fetch
 * @param {boolean} isSilent When true (background refresh), failures keep the last good data
 */
async function fetchDetails(shortCode, isSilent) {
    if (!isSilent && !detailsHasData) showDetailsState('loading');

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls/${encodeURIComponent(shortCode)}/stats`);
        const data = await readJson(response);
        if (!response.ok) throw new ApiError(data.message || 'Failed to fetch statistics', response.status);
        if (shortCode !== detailsCode || details.hidden) return;

        fillDetails(data);
        detailsNotice.classList.add('hidden');
        showDetailsState('content');
    } catch (err) {
        if (shortCode !== detailsCode || details.hidden || isSilent) return;

        if (err.status === 404) {
            detailsHasData = false;
            detailsErrorTitle.textContent = 'Link not found';
            detailsErrorText.textContent = `There is no short link with the code “${shortCode}”. Check the code, or it may have been deleted.`;
            detailsRetry.classList.add('hidden');
            showDetailsState('error');
        } else if (detailsHasData) {
            detailsNotice.classList.remove('hidden');
        } else {
            detailsErrorTitle.textContent = "Couldn't load this link";
            detailsErrorText.textContent = humanizeError(err);
            detailsRetry.classList.remove('hidden');
            showDetailsState('error');
        }
    }
}

/**
 * Renders a stats payload into the details panel.
 *
 * @param {Object} data UrlStatsResponse (or a list row with a derived status)
 */
function fillDetails(data) {
    detailsHasData = true;
    const active = data.status === 'ACTIVE';

    statShortUrl.textContent = data.shortUrl;
    statShortUrl.href = data.shortUrl;
    statOriginalUrl.textContent = data.originalUrl;
    statShortCode.textContent = data.shortCode;
    statClicks.textContent = formatNumber(data.clickCount || 0);
    statStatus.textContent = active ? 'Active' : 'Expired';
    statStatus.className = `badge ${active ? 'badge-active' : 'badge-expired'}`;
    statRedirect.textContent = active ? '302 Found' : '410 Gone';
    statCreated.innerHTML = formatDetailDate(data.createdAt);
    statExpires.innerHTML = data.expiresAt ? formatDetailDate(data.expiresAt) : 'Never';
}

function formatDetailDate(value) {
    return `${escapeHtml(formatDate(value))}<span class="date-rel">${escapeHtml(formatRelative(value))}</span>`;
}

/**
 * @param {'loading'|'error'|'content'} state Which details view to show
 */
function showDetailsState(state) {
    detailsLoading.classList.toggle('hidden', state !== 'loading');
    detailsError.classList.toggle('hidden', state !== 'error');
    statsDisplay.classList.toggle('hidden', state !== 'content');
}

function openDetails() {
    const wasHidden = details.hidden;
    details.hidden = false;
    workspace.classList.add('has-details');
    applyDetailsMode();
    if (wasHidden || !dockedQuery.matches) details.focus({ preventScroll: true });
}

/**
 * Switches the panel between a docked, non-modal column and a modal drawer / sheet.
 */
function applyDetailsMode() {
    releaseBackground('details');
    document.body.classList.remove('details-docked');
    details.removeAttribute('role');
    details.removeAttribute('aria-modal');
    detailsScrim.hidden = true;
    document.documentElement.style.overflow = '';

    if (details.hidden) return;

    if (dockedQuery.matches) {
        document.body.classList.add('details-docked');
    } else {
        details.setAttribute('role', 'dialog');
        details.setAttribute('aria-modal', 'true');
        detailsScrim.hidden = false;
        document.documentElement.style.overflow = 'hidden';
        isolateBackground(details, 'details');
    }
}

function closeDetails({ restoreFocus = true } = {}) {
    if (details.hidden) return;
    details.hidden = true;
    workspace.classList.remove('has-details');
    applyDetailsMode();
    detailsCode = null;
    detailsHasData = false;
    markSelectedRow();

    if (restoreFocus && detailsTrigger && detailsTrigger.isConnected) {
        detailsTrigger.focus({ preventScroll: true });
    }
    detailsTrigger = null;
}

/* --------------------------------------------------------------------------
   Load & render
   -------------------------------------------------------------------------- */

/**
 * Fetches every short link, refreshes the summary and repaints the table.
 * Concurrent callers share one in-flight request.
 *
 * @returns {Promise<boolean>} Whether the load succeeded
 */
function loadAllUrls() {
    if (loadInFlight) return loadInFlight;

    loadInFlight = (async () => {
        const started = performance.now();
        const version = mutationVersion;
        try {
            const response = await fetch(`${API_BASE_URL}/api/urls`);
            if (!response.ok) throw new Error('Failed to fetch URLs');

            const urls = await response.json();
            // A create or delete finished while this request was in flight; its data is stale
            if (version !== mutationVersion) return true;

            allUrls = urls;
            apiLatency.textContent = `API ${Math.round(performance.now() - started)} ms`;
            lastSyncAt = new Date();
            hasLoadedOnce = true;
            setApiStatus('online');

            updateOverviewMetrics(allUrls);
            renderUrlTable();

            // Silent background refresh for the details panel when it is open
            if (detailsCode && !details.hidden) fetchDetails(detailsCode, true);
            return true;
        } catch (err) {
            apiLatency.textContent = 'API unreachable';
            setApiStatus('offline');
            renderUrlTable();
            return false;
        } finally {
            loadInFlight = null;
        }
    })();

    return loadInFlight;
}

/**
 * Reloads after a create or delete, waiting out any poll that started before the change.
 *
 * @returns {Promise<boolean>} Whether the reload succeeded
 */
async function reloadAfterMutation() {
    mutationVersion++;
    if (loadInFlight) await loadInFlight;
    return loadAllUrls();
}

/**
 * Reflects backend reachability in the header and the table.
 *
 * @param {'connecting'|'online'|'offline'} state Result of the last API call
 */
function setApiStatus(state) {
    const previous = apiState;
    apiState = state;
    updateSyncStatus();
    if (previous === state) return;

    apiStatus.dataset.state = state;
    apiStatusText.textContent = state === 'online' ? 'Connected' : state === 'offline' ? 'Offline' : 'Connecting…';
    apiLive.textContent = state === 'online' ? `· Live ${POLL_INTERVAL_MS / 1000}s` : state === 'offline' ? '· Retrying' : '';

    if (state === 'offline') {
        announce("Can't reach the server. Retrying automatically.", 'assertive');
    } else if (state === 'online' && previous === 'offline') {
        showToast('Connection restored', 'success');
    }
}

function updateSyncStatus() {
    syncStatus.dataset.state = apiState;
    const stale = apiState === 'offline' && hasLoadedOnce;

    if (apiState === 'online' && lastSyncAt) {
        syncText.textContent = `Updated ${formatTime(lastSyncAt)}`;
    } else if (stale) {
        syncText.textContent = `Offline · last updated ${formatTime(lastSyncAt)}`;
    } else if (apiState === 'offline') {
        syncText.textContent = 'Offline';
    } else {
        syncText.textContent = 'Waiting for data';
    }

    connNotice.classList.toggle('hidden', !stale);
    if (stale) {
        connNoticeText.textContent = `Can't reach the server. Showing links as of ${formatTime(lastSyncAt)} — retrying every ${POLL_INTERVAL_MS / 1000} seconds.`;
    }
}

/**
 * Recomputes the summary line from the current link collection.
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

    document.getElementById('links-summary').classList.remove('hidden');
    overviewTotalUrls.textContent = formatNumber(urls.length);
    overviewUrlsWord.textContent = urls.length === 1 ? 'link' : 'links';
    overviewTotalClicks.textContent = formatNumber(totalClicks);
    overviewClicksWord.textContent = totalClicks === 1 ? 'click' : 'clicks';
    overviewActiveUrls.textContent = formatNumber(activeCount);
    overviewExpiredUrls.textContent = formatNumber(urls.length - activeCount);
}

/**
 * Applies the active filter and sort, then reconciles the table rows. Existing
 * rows are reused and only changed cells are rewritten, so keyboard focus and
 * text selection survive the live poll.
 */
function renderUrlTable() {
    const query = tableSearch.value.toLowerCase().trim();
    const rows = sortUrls(allUrls.filter(url => (!query || matchesQuery(url, query)) && matchesStatus(url)));

    if (!hasLoadedOnce) {
        const failed = apiState === 'offline';
        tableUrlCount.textContent = failed ? 'No data yet' : 'Loading links…';
        tableSkeleton.classList.toggle('hidden', failed);
        tableError.classList.toggle('hidden', !failed);
        tableEmpty.classList.add('hidden');
        linksTable.classList.add('is-empty');
        return;
    }

    tableUrlCount.innerHTML = `Showing <strong>${rows.length}</strong> of <strong>${allUrls.length}</strong> ${allUrls.length === 1 ? 'link' : 'links'}`;
    tableSkeleton.classList.add('hidden');
    tableError.classList.add('hidden');

    // Forget rows for links that no longer exist
    const liveCodes = new Set(allUrls.map(u => u.shortCode));
    rowCache.forEach((tr, code) => {
        if (!liveCodes.has(code)) {
            tr.remove();
            rowCache.delete(code);
        }
    });

    if (!rows.length) {
        urlTableBody.replaceChildren();
        linksTable.classList.add('is-empty');
        tableEmpty.classList.remove('hidden');
        showEmptyState(query);
        return;
    }

    tableEmpty.classList.add('hidden');
    linksTable.classList.remove('is-empty');

    const focused = document.activeElement;
    const hadFocus = urlTableBody.contains(focused);

    // Scale each click bar against the busiest link on screen
    const maxClicks = Math.max(...rows.map(u => u.clickCount || 0), 1);
    const wanted = rows.map(url => {
        let tr = rowCache.get(url.shortCode);
        if (!tr) {
            tr = createRow();
            rowCache.set(url.shortCode, tr);
        }
        updateRow(tr, url, maxClicks);
        return tr;
    });

    const wantedSet = new Set(wanted);
    Array.from(urlTableBody.children).forEach(tr => {
        if (!wantedSet.has(tr)) tr.remove();
    });

    // Move only rows that are out of place
    wanted.forEach((tr, i) => {
        const current = urlTableBody.children[i];
        if (current !== tr) urlTableBody.insertBefore(tr, current || null);
    });

    if (hadFocus && focused.isConnected && document.activeElement !== focused) {
        focused.focus({ preventScroll: true });
    }
}

function showEmptyState(query) {
    const filtered = Boolean(query) || statusFilter !== 'all';
    emptyAction.classList.toggle('hidden', !filtered);

    if (!allUrls.length) {
        emptyTitle.textContent = 'No links yet';
        emptyText.textContent = 'Paste a long URL above to create your first short link.';
    } else if (query) {
        emptyTitle.textContent = 'No matching links';
        const scope = statusFilter === 'all' ? '' : ` among ${statusFilter} links`;
        emptyText.textContent = `Nothing matches “${tableSearch.value.trim()}”${scope}. Try a different search term.`;
    } else {
        emptyTitle.textContent = `No ${statusFilter} links`;
        emptyText.textContent = statusFilter === 'expired'
            ? 'No links have passed their expiration date.'
            : 'Every link has passed its expiration date.';
    }
}

const COLUMNS = ['col-short', 'col-dest', 'col-clicks', 'col-status', 'col-created', 'col-expires', 'col-actions'];

function createRow() {
    const tr = document.createElement('tr');
    COLUMNS.forEach(col => {
        const td = document.createElement('td');
        td.className = col;
        tr.appendChild(td);
    });
    tr._html = [];
    return tr;
}

/**
 * Writes a link's data into its row, touching only cells whose markup changed.
 *
 * @param {HTMLTableRowElement} tr Row created by createRow
 * @param {Object} url Link record
 * @param {number} maxClicks Highest click count among visible rows
 */
function updateRow(tr, url, maxClicks) {
    const expired = isExpired(url);
    const clicks = url.clickCount || 0;
    const barWidth = Math.round((clicks / maxClicks) * 100);
    const shortParts = splitShortUrl(url.shortUrl, url.shortCode);
    const { host, path } = splitUrl(url.originalUrl);
    const code = escapeHtml(url.shortCode);

    tr.dataset.code = url.shortCode;
    tr.classList.toggle('is-expired', expired);
    tr.classList.toggle('is-selected', url.shortCode === detailsCode && !details.hidden);

    if (url.shortCode === highlightCode) {
        highlightCode = null;
        tr.classList.add('is-new');
        setTimeout(() => tr.classList.remove('is-new'), 2400);
    }

    const cells = [
        `<a href="${escapeHtml(url.shortUrl)}" target="_blank" rel="noopener" class="short-link"
            title="Open ${escapeHtml(url.shortUrl)} in a new tab"><span class="short-host">${escapeHtml(shortParts.prefix)}</span><span class="short-code">${code}</span></a>`,

        `<span class="dest" title="${escapeHtml(url.originalUrl)}"><span class="dest-host">${escapeHtml(host)}</span>${path ? `<span class="dest-path">${escapeHtml(path)}</span>` : ''}</span>`,

        `<span class="clicks"><span class="clicks-num">${formatNumber(clicks)}<span class="clicks-unit"> ${clicks === 1 ? 'click' : 'clicks'}</span></span>
            <span class="click-bar" aria-hidden="true"><i style="width:${barWidth}%"></i></span></span>`,

        `<span class="badge ${expired ? 'badge-expired' : 'badge-active'}">${expired ? 'Expired' : 'Active'}</span>`,

        dateCell(url.createdAt),

        url.expiresAt ? dateCell(url.expiresAt) : '<span class="date-never">Never</span>',

        `<div class="row-actions">
            <button type="button" class="row-action copy-icon-btn" data-action="copy" data-url="${escapeHtml(url.shortUrl)}"
                    title="Copy short link" aria-label="Copy short link ${code}">
                <svg class="i-copy" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect x="9" y="9" width="12" height="12" rx="2"></rect>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                </svg>
                <svg class="i-check" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="m20 6-11 11-5-5"></path>
                </svg>
            </button>
            <button type="button" class="row-action" data-action="stats" data-code="${code}"
                    title="View details" aria-label="View details for ${code}">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="9"></circle>
                    <path d="M12 16v-5M12 8h.01"></path>
                </svg>
            </button>
            <button type="button" class="row-action row-action-del" data-action="delete" data-code="${code}"
                    title="Delete link" aria-label="Delete ${code}">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path>
                </svg>
            </button>
        </div>`
    ];

    cells.forEach((html, i) => {
        if (tr._html[i] !== html) {
            tr.children[i].innerHTML = html;
            tr._html[i] = html;
        }
    });
}

function dateCell(value) {
    return `<span class="date" title="${escapeHtml(formatDate(value))}">
        <span class="date-abs">${escapeHtml(formatDateShort(value))}</span>
        <span class="date-rel">${escapeHtml(formatRelative(value))}</span></span>`;
}

function markSelectedRow() {
    rowCache.forEach((tr, code) => {
        tr.classList.toggle('is-selected', code === detailsCode && !details.hidden);
    });
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
 * Syncs aria-sort on the headers and the phone sort select with the active sort.
 */
function updateSortIndicators() {
    document.querySelectorAll('.sort-btn').forEach(btn => {
        btn.closest('th').setAttribute('aria-sort',
            btn.dataset.sort === sortKey ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none');
    });
    const value = `${sortKey}:${sortDir}`;
    if (Array.from(mobileSort.options).some(o => o.value === value)) mobileSort.value = value;
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

function matchesStatus(url) {
    if (statusFilter === 'all') return true;
    return statusFilter === 'expired' ? isExpired(url) : !isExpired(url);
}

/* --------------------------------------------------------------------------
   Delete
   -------------------------------------------------------------------------- */

/**
 * Opens the confirmation dialog for deleting a short link.
 *
 * @param {string} shortCode Short code queued for deletion
 * @param {HTMLElement} trigger Element that requested the delete; focus returns here on cancel
 */
function requestDelete(shortCode, trigger) {
    pendingDeleteCode = shortCode;
    deleteTrigger = trigger || document.activeElement;
    confirmText.innerHTML = `This will permanently remove the short link <code>${escapeHtml(shortCode)}</code> and its click count.`;
    confirmError.textContent = '';
    setDeleting(false);
    confirmModal.classList.remove('hidden');
    isolateBackground(confirmModal, 'modal');
    confirmCancel.focus();
}

function closeConfirm({ restoreFocus = true } = {}) {
    confirmModal.classList.add('hidden');
    releaseBackground('modal');
    pendingDeleteCode = null;
    if (restoreFocus && deleteTrigger && deleteTrigger.isConnected) deleteTrigger.focus({ preventScroll: true });
    deleteTrigger = null;
}

function setDeleting(pending) {
    isDeleting = pending;
    confirmAccept.disabled = pending;
    confirmCancel.disabled = pending;
    confirmAcceptSpinner.classList.toggle('hidden', !pending);
    confirmAcceptText.textContent = pending ? 'Deleting…' : 'Delete';
    confirmModal.querySelector('.modal').setAttribute('aria-busy', String(pending));
}

confirmCancel.addEventListener('click', () => closeConfirm());
confirmModal.addEventListener('click', (e) => {
    if (e.target === confirmModal && !isDeleting) closeConfirm();
});
confirmModal.addEventListener('keydown', (e) => {
    if (e.key === 'Tab') trapFocus(confirmModal, e);
});

confirmAccept.addEventListener('click', async () => {
    const shortCode = pendingDeleteCode;
    if (!shortCode || isDeleting) return;

    setDeleting(true);
    confirmError.textContent = '';

    try {
        const response = await fetch(`${API_BASE_URL}/api/urls/${encodeURIComponent(shortCode)}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new ApiError(data.message || 'Failed to delete URL', response.status);
        }

        const focusTarget = focusTargetAfterDelete(shortCode);
        setDeleting(false);
        closeConfirm({ restoreFocus: false });

        // Close the details panel and result strip when they show the link that was just removed
        if (detailsCode === shortCode) closeDetails({ restoreFocus: false });
        if (lastCreatedCode === shortCode) {
            resultBox.classList.add('hidden');
            lastCreatedCode = null;
        }

        mutationVersion++;
        allUrls = allUrls.filter(u => u.shortCode !== shortCode);
        updateOverviewMetrics(allUrls);
        renderUrlTable();

        showToast(`Deleted ${shortCode}`, 'success');
        (focusTarget && focusTarget.isConnected ? focusTarget : tableSearch).focus({ preventScroll: true });
        reloadAfterMutation();
    } catch (err) {
        setDeleting(false);
        confirmError.textContent = humanizeError(err);
        confirmCancel.focus();
    }
});

/**
 * Picks where focus should land once a row disappears: the details button of the
 * row that takes its place, or of the row above it.
 *
 * @param {string} shortCode Code of the row being deleted
 * @returns {HTMLElement|null} Element to focus
 */
function focusTargetAfterDelete(shortCode) {
    const tr = rowCache.get(shortCode);
    if (!tr || !tr.isConnected) return null;
    const neighbour = tr.nextElementSibling || tr.previousElementSibling;
    return neighbour ? neighbour.querySelector('[data-action="stats"]') : null;
}

/* --------------------------------------------------------------------------
   Overlays: background isolation & focus trap
   -------------------------------------------------------------------------- */

const inertOwners = { details: [], modal: [] };

/**
 * Makes everything outside `keep` inert, walking up to <body>. Only elements that
 * were not already inert are recorded, so stacked overlays release cleanly.
 *
 * @param {HTMLElement} keep Element that stays interactive
 * @param {'details'|'modal'} owner Overlay that owns this isolation
 */
function isolateBackground(keep, owner) {
    releaseBackground(owner);
    let node = keep;
    while (node && node !== document.body) {
        const parent = node.parentElement;
        Array.from(parent.children).forEach(sibling => {
            if (sibling === node || sibling.tagName === 'SCRIPT' || sibling.inert
                || sibling.hasAttribute('data-keep-interactive')) return;
            sibling.inert = true;
            inertOwners[owner].push(sibling);
        });
        node = parent;
    }
}

function releaseBackground(owner) {
    inertOwners[owner].forEach(el => { el.inert = false; });
    inertOwners[owner] = [];
}

function isOverlayOpen() {
    return !confirmModal.classList.contains('hidden') || (!details.hidden && !dockedQuery.matches);
}

/**
 * Keeps Tab / Shift+Tab cycling inside a container.
 *
 * @param {HTMLElement} container Overlay root
 * @param {KeyboardEvent} e Tab keydown event
 */
function trapFocus(container, e) {
    const focusable = Array.from(container.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )).filter(el => el.offsetParent !== null);
    if (!focusable.length) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (e.shiftKey && (document.activeElement === first || document.activeElement === container)) {
        e.preventDefault();
        last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
    }
}

/* --------------------------------------------------------------------------
   Helpers
   -------------------------------------------------------------------------- */

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

/**
 * Parses a JSON body, tolerating empty or non-JSON responses (e.g. a proxy error page).
 *
 * @param {Response} response Fetch response
 * @returns {Promise<Object>} Parsed body, or an empty object
 */
async function readJson(response) {
    try {
        return await response.json();
    } catch (e) {
        return response.ok ? {} : { message: `The server returned an error (${response.status}). Try again in a moment.` };
    }
}

/**
 * Turns an error into a message suitable for people rather than developers.
 *
 * @param {Error} err Error thrown by a request
 * @returns {string} User-facing message
 */
function humanizeError(err) {
    if (err instanceof TypeError) return NETWORK_ERROR_MESSAGE;
    return err.message || 'Something went wrong. Try again.';
}

/**
 * Copies text to the clipboard and flashes confirmation on the source button.
 *
 * @param {string} text Text to place on the clipboard
 * @param {HTMLElement|null} btnElement Button to flash, if any
 */
async function copyToClipboard(text, btnElement = null) {
    if (!text) return;

    try {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
        } else {
            legacyCopy(text);
        }
        announce('Copied to clipboard');
        if (btnElement) flashCopied(btnElement);
    } catch (e) {
        showToast("Couldn't copy to the clipboard. Select the link and copy it manually.", 'error');
    }
}

/** Fallback for non-secure origins where the async clipboard API is unavailable. */
function legacyCopy(text) {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand('copy');
    area.remove();
    if (!ok) throw new Error('copy failed');
}

function flashCopied(btn) {
    const label = btn.querySelector('.btn-label');
    if (!btn.dataset.label) btn.dataset.label = btn.getAttribute('aria-label') || '';

    btn.classList.add('is-copied');
    if (label) label.textContent = 'Copied';
    if (btn.dataset.label) btn.setAttribute('aria-label', 'Copied');

    clearTimeout(btn._copiedTimer);
    btn._copiedTimer = setTimeout(() => resetCopied(btn), COPIED_RESET_MS);
}

function resetCopied(btn) {
    const label = btn.querySelector('.btn-label');
    btn.classList.remove('is-copied');
    if (label) label.textContent = 'Copy';
    if (btn.dataset.label) btn.setAttribute('aria-label', btn.dataset.label);
}

/**
 * Toggles the submit button between idle and in-flight states.
 *
 * @param {boolean} isLoading Whether a request is in flight
 */
function setLoading(isLoading) {
    shortenBtnText.textContent = isLoading ? 'Shortening…' : 'Shorten';
    shortenSpinner.classList.toggle('hidden', !isLoading);
    shortenBtn.disabled = isLoading;
}

/**
 * Sends a message to screen readers without showing anything.
 *
 * @param {string} message Text to announce
 * @param {'polite'|'assertive'} mode Urgency
 */
function announce(message, mode = 'polite') {
    const region = mode === 'assertive' ? srAssertive : srPolite;
    region.textContent = '';
    setTimeout(() => { region.textContent = message; }, 40);
}

/**
 * Renders a transient toast notification. Errors stay longer and can be dismissed.
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
                 stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icon}</svg>
        </span>
        <span class="toast-msg">${escapeHtml(message)}</span>
        ${type === 'error' ? `<button type="button" class="icon-btn toast-close" aria-label="Dismiss notification">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"></path></svg>
        </button>` : ''}
    `;

    const remove = () => {
        toast.classList.add('is-leaving');
        setTimeout(() => toast.remove(), 160);
    };
    const closeBtn = toast.querySelector('.toast-close');
    if (closeBtn) closeBtn.addEventListener('click', remove);

    toastContainer.appendChild(toast);
    announce(message, type === 'error' ? 'assertive' : 'polite');
    setTimeout(remove, type === 'error' ? 6000 : 3200);
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
 * Splits a URL into a host label and a shortened path for display.
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
 * Splits a short URL into its scheme-less prefix and code, e.g. "localhost:8080/" + "aB72xQ".
 *
 * @param {string} shortUrl Absolute short URL
 * @param {string} shortCode Code at the end of the URL
 * @returns {{prefix: string}} Display prefix
 */
function splitShortUrl(shortUrl, shortCode) {
    const bare = String(shortUrl || '').replace(/^https?:\/\//i, '');
    const prefix = bare.endsWith(shortCode) ? bare.slice(0, bare.length - shortCode.length) : `${bare}/`;
    return { prefix };
}

/**
 * Accepts either a bare code or a pasted short URL and returns the code.
 *
 * @param {string} value Raw input
 * @returns {string} Short code, or an empty string
 */
function extractShortCode(value) {
    const trimmed = String(value || '').trim();
    if (!trimmed.includes('/')) return trimmed;
    const segments = trimmed.split(/[?#]/)[0].split('/').filter(Boolean);
    return segments.length ? segments[segments.length - 1] : '';
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

function formatTime(date) {
    return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString();
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

// Reflect the initial sort, theme and expiry state
updateSortIndicators();
syncThemeToggleLabel();
syncExpiryUi();
renderUrlTable();
