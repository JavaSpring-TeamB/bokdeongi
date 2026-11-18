const $ = (selector) => document.querySelector(selector);
const COMPLETION_STORAGE_KEY = 'bokdeongi-signup-completion';
const PROFILE_STORAGE_KEY = 'bokdeongi-signup-profile';
const CONSENT_STORAGE_KEY = 'bokdeongi-signup-consents';

function showToast(message) { const toast = $('#toast'); toast.textContent = message; toast.hidden = false; window.clearTimeout(showToast.timer); showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 3200); }
function readSession(key) { try { return JSON.parse(sessionStorage.getItem(key)); } catch (error) { return null; } }
function signedUpSuccessfully() {
    const completion = readSession(COMPLETION_STORAGE_KEY);
    // 회원가입 API가 성공한 뒤 서버가 발급·검증한 완료 상태를 이 값으로 대체한다.
    return completion?.completed === true;
}
function isLocalPreview() {
    return window.location.protocol === 'file:' || ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname);
}
function incompleteSignupDestination() {
    if (readSession(PROFILE_STORAGE_KEY)) return 'signup-profile.html';
    const consents = readSession(CONSENT_STORAGE_KEY) || [];
    if (consents.some((consent) => consent.consent_type === 'TERMS' && consent.agreed) && consents.some((consent) => consent.consent_type === 'PRIVACY' && consent.agreed)) return 'signup-profile.html';
    return 'signup-account.html';
}
function setupHeaderInteractions() {
    return;
    let theme = localStorage.getItem('bokdeongi-theme') || 'light'; const themeButton = $('[data-theme-toggle]'); document.documentElement.dataset.theme = theme; document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1); themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드'; themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
    document.addEventListener('click', (event) => { const unavailable = event.target.closest('[data-unavailable-page]'); if (unavailable) { event.preventDefault(); showToast('이 페이지는 현재 준비 중입니다.'); return; } const fontSize = event.target.closest('[data-font-size]'); if (fontSize) { const current = Number(localStorage.getItem('bokdeongi-font-scale') || 1); const change = fontSize.dataset.fontSize === 'increase' ? .1 : -.1; const scale = Math.min(1.2, Math.max(.9, Number((current + change).toFixed(2)))); localStorage.setItem('bokdeongi-font-scale', scale); document.documentElement.style.setProperty('--font-scale', scale); return; } if (event.target.closest('[data-theme-toggle]')) { theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; document.documentElement.dataset.theme = theme; localStorage.setItem('bokdeongi-theme', theme); themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드'; themeButton.setAttribute('aria-pressed', String(theme === 'dark')); return; } const menu = event.target.closest('.menu-toggle'); if (menu) { const navigation = $('#site-navigation'); const isOpen = navigation.classList.toggle('is-open'); menu.setAttribute('aria-expanded', String(isOpen)); menu.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기'); } });
}
document.addEventListener('DOMContentLoaded', () => {
    setupHeaderInteractions();
    // 로컬 파일/개발 서버에서는 화면 QA만 허용하며, 가입 완료 상태를 만들거나 저장하지 않는다.
    if (!isLocalPreview() && !signedUpSuccessfully()) { window.location.replace(incompleteSignupDestination()); return; }
    $('#signup-complete-content').hidden = false;
});
