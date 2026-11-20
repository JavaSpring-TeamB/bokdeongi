const $ = (selector) => document.querySelector(selector);
const CONSENT_STORAGE_KEY = 'bokdeongi-signup-consents';
const ACCOUNT_DRAFT_STORAGE_KEY = 'bokdeongi-signup-account-draft';
const REQUIRED_CONSENTS = ['TERMS', 'PRIVACY'];
const consentDocument = (type) => window.BokdeongiConsentDocuments?.get(type) || null;

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 3200);
}

function readStoredConsents() {
    try { return JSON.parse(sessionStorage.getItem(CONSENT_STORAGE_KEY)) || []; } catch (error) { return []; }
}

function saveConsents() {
    const actedAt = new Date().toISOString();
    const consents = Array.from(document.querySelectorAll('[data-consent-type]')).map((input) => ({
        consent_type: input.dataset.consentType,
        doc_version: window.BokdeongiConsentDocuments?.versionFor(input.dataset.consentType) || null,
        agreed: input.checked,
        acted_at: actedAt
    }));
    try { sessionStorage.setItem(CONSENT_STORAGE_KEY, JSON.stringify(consents)); } catch (error) { /* 브라우저 저장소를 사용할 수 없는 경우 현재 화면 상태만 유지한다. */ }
    return consents;
}

function restoreConsents() {
    const saved = new Map(readStoredConsents().map((consent) => [consent.consent_type, consent.agreed]));
    document.querySelectorAll('[data-consent-type]').forEach((input) => { input.checked = saved.get(input.dataset.consentType) === true; });
}

function syncAllConsent() {
    const inputs = Array.from(document.querySelectorAll('[data-consent-type]'));
    const all = $('#consent-all');
    all.checked = inputs.length > 0 && inputs.every((input) => input.checked);
    all.indeterminate = !all.checked && inputs.some((input) => input.checked);
}

function clearError() {
    const error = $('#consent-error');
    error.textContent = '';
    document.querySelectorAll('[data-consent-type]').forEach((input) => input.removeAttribute('aria-invalid'));
}

function validateRequiredConsents() {
    const missing = REQUIRED_CONSENTS.filter((type) => !document.querySelector(`[data-consent-type="${type}"]`).checked);
    if (!missing.length) { clearError(); return true; }
    const labels = missing.map((type) => consentDocument(type)?.title || '필수 약관').join(', ');
    const error = $('#consent-error');
    error.textContent = `필수 약관에 동의해 주세요: ${labels}.`;
    missing.forEach((type) => document.querySelector(`[data-consent-type="${type}"]`).setAttribute('aria-invalid', 'true'));
    error.focus();
    return false;
}

function setupConsents() {
    const form = $('#signup-consent-form');
    const all = $('#consent-all');
    restoreConsents();
    syncAllConsent();
    all.addEventListener('change', () => {
        document.querySelectorAll('[data-consent-type]').forEach((input) => { input.checked = all.checked; });
        clearError();
        saveConsents();
    });
    document.querySelectorAll('[data-consent-type]').forEach((input) => input.addEventListener('change', () => { syncAllConsent(); clearError(); saveConsents(); }));
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!validateRequiredConsents()) { showToast('필수 약관 동의 여부를 확인해 주세요.'); return; }
        saveConsents();
        window.location.assign('signup-profile.html');
    });
}

function setupConsentDialog() {
    const dialog = $('#consent-dialog');
    const title = $('#consent-dialog-title');
    const content = $('#consent-dialog-content');
    let opener = null;
    document.addEventListener('click', (event) => {
        const viewButton = event.target.closest('[data-consent-view]');
        if (viewButton) {
            const type = viewButton.dataset.consentView;
            const documentInfo = consentDocument(type);
            if (!documentInfo) { showToast('동의 문서를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.'); return; }
            opener = viewButton;
            title.textContent = documentInfo.title;
            window.BokdeongiConsentDocuments.renderInto(content, type);
            dialog.showModal();
            return;
        }
        if (event.target.closest('[data-dialog-close]')) dialog.close();
    });
    dialog.addEventListener('close', () => { if (opener) opener.focus(); });
}

function setupHeaderInteractions() {
    return;
    let theme = localStorage.getItem('bokdeongi-theme') || 'light';
    const themeButton = $('[data-theme-toggle]');
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1);
    themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
    themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
    document.addEventListener('click', (event) => {
        const unavailable = event.target.closest('[data-unavailable-page]');
        if (unavailable) { event.preventDefault(); showToast('이 페이지는 현재 준비 중입니다.'); return; }
        const fontSize = event.target.closest('[data-font-size]');
        if (fontSize) { const current = Number(localStorage.getItem('bokdeongi-font-scale') || 1); const change = fontSize.dataset.fontSize === 'increase' ? .1 : -.1; const scale = Math.min(1.2, Math.max(.9, Number((current + change).toFixed(2)))); localStorage.setItem('bokdeongi-font-scale', scale); document.documentElement.style.setProperty('--font-scale', scale); return; }
        if (event.target.closest('[data-theme-toggle]')) { theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; document.documentElement.dataset.theme = theme; localStorage.setItem('bokdeongi-theme', theme); themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드'; themeButton.setAttribute('aria-pressed', String(theme === 'dark')); return; }
        const menu = event.target.closest('.menu-toggle');
        if (menu) { const navigation = $('#site-navigation'); const isOpen = navigation.classList.toggle('is-open'); menu.setAttribute('aria-expanded', String(isOpen)); menu.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기'); }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    window.location.replace('signup-account.html');
    return;
    if (!sessionStorage.getItem(ACCOUNT_DRAFT_STORAGE_KEY)) {
        window.location.replace('signup-account.html');
        return;
    }
    setupHeaderInteractions();
    setupConsents();
    setupConsentDialog();
});
