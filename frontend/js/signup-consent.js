const $ = (selector) => document.querySelector(selector);
const CONSENT_STORAGE_KEY = 'bokdeongi-signup-consents';
const REQUIRED_CONSENTS = ['TERMS', 'PRIVACY'];
const CONSENT_DOCUMENTS = {
    TERMS: { title: '서비스 이용약관', version: 'pending' },
    PRIVACY: { title: '개인정보 수집·이용 동의', version: 'pending' },
    OPTIONAL_INFO: { title: '선택 정보 수집·이용 동의', version: 'pending' },
    SENSITIVE: { title: '민감정보 수집·이용 동의', version: 'pending' },
    DOC_RECORD: { title: '서류 기록 보관 동의', version: 'pending' }
};

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
        doc_version: input.dataset.docVersion,
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
    const labels = missing.map((type) => CONSENT_DOCUMENTS[type].title).join(', ');
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
            const documentInfo = CONSENT_DOCUMENTS[viewButton.dataset.consentView];
            opener = viewButton;
            title.textContent = documentInfo.title;
            content.replaceChildren();
            const version = document.createElement('p');
            version.textContent = `문서 버전: ${documentInfo.version}`;
            const pending = document.createElement('p');
            pending.textContent = '정식 약관 문서가 아직 연결되지 않았습니다. 실제 문서가 등록되면 이 화면에서 해당 버전의 내용을 확인할 수 있습니다.';
            content.append(version, pending);
            dialog.showModal();
            return;
        }
        if (event.target.closest('[data-dialog-close]')) dialog.close();
    });
    dialog.addEventListener('close', () => { if (opener) opener.focus(); });
}

function setupHeaderInteractions() {
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

document.addEventListener('DOMContentLoaded', () => { setupHeaderInteractions(); setupConsents(); setupConsentDialog(); });
