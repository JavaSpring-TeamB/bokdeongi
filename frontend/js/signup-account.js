const $ = (selector) => document.querySelector(selector);
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,20}$/;
const phonePattern = /^01[016789]-?\d{3,4}-?\d{4}$/;

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 3200);
}

function setFieldMessage(fieldName, message, valid = false) {
    const input = $('#signup-' + fieldName);
    const messageTarget = $('#' + fieldName + '-message');
    if (!input || !messageTarget) return;
    input.setAttribute('aria-invalid', String(Boolean(message) && !valid));
    input.classList.toggle('is-valid', valid);
    messageTarget.textContent = message;
    messageTarget.classList.toggle('is-valid', valid);
}

function validateEmail(showEmpty = true) {
    const input = $('#signup-email');
    const value = input.value.trim();
    if (!value) { setFieldMessage('email', showEmpty ? '이메일을 입력해 주세요.' : ''); return false; }
    if (!input.validity.valid) { setFieldMessage('email', '이메일 형식을 확인해 주세요.'); return false; }
    setFieldMessage('email', '이메일 형식이 올바릅니다.', true);
    return true;
}

function validatePassword(showEmpty = true) {
    const value = $('#signup-password').value;
    if (!value) { setFieldMessage('password', showEmpty ? '비밀번호를 입력해 주세요.' : ''); return false; }
    if (!passwordPattern.test(value)) { setFieldMessage('password', '8~20자의 영문, 숫자, 특수문자를 모두 포함해 주세요.'); return false; }
    setFieldMessage('password', '사용 가능한 비밀번호 형식입니다.', true);
    return true;
}

function validatePasswordConfirm(showEmpty = true) {
    const value = $('#signup-password-confirm').value;
    const password = $('#signup-password').value;
    if (!value) { setFieldMessage('password-confirm', showEmpty ? '비밀번호를 다시 입력해 주세요.' : ''); return false; }
    if (value !== password) { setFieldMessage('password-confirm', '비밀번호가 일치하지 않습니다.'); return false; }
    setFieldMessage('password-confirm', '비밀번호가 일치합니다.', true);
    return true;
}

function validateName(showEmpty = true) {
    const input = $('#signup-name');
    input.value = input.value.trim();
    if (!input.value) { setFieldMessage('name', showEmpty ? '이름을 입력해 주세요.' : ''); return false; }
    setFieldMessage('name', '', true);
    return true;
}

function validatePhone() {
    const input = $('#signup-phone');
    const value = input.value.trim();
    if (!value) { setFieldMessage('phone', ''); return true; }
    if (!phonePattern.test(value)) { setFieldMessage('phone', '전화번호 형식을 확인해 주세요. 예: 010-1234-5678'); return false; }
    setFieldMessage('phone', '입력한 연락처 형식이 올바릅니다.', true);
    return true;
}

function validateAll() {
    return [validateEmail(), validatePassword(), validatePasswordConfirm(), validateName(), validatePhone()].every(Boolean);
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

function setupForm() {
    const form = $('#signup-account-form');
    const inputValidators = { email: validateEmail, password: validatePassword, passwordConfirm: validatePasswordConfirm, name: validateName, phone: validatePhone };
    form.addEventListener('input', (event) => {
        const validator = inputValidators[event.target.name];
        if (!validator) return;
        if (event.target.name === 'email') setFieldMessage('email', '');
        validator(false);
        if (event.target.name === 'password' && $('#signup-password-confirm').value) validatePasswordConfirm(false);
    });
    form.addEventListener('blur', (event) => { const validator = inputValidators[event.target.name]; if (validator) validator(true); }, true);
    form.addEventListener('submit', (event) => { event.preventDefault(); if (!validateAll()) { showToast('필수 입력 항목과 입력 형식을 확인해 주세요.'); return; } showToast('입력 형식을 확인했어요. 약관 동의 화면은 구현 후 연결됩니다.'); });
    $('[data-email-check]').addEventListener('click', () => { if (!validateEmail()) return; showToast('이메일 중복 확인은 회원가입 API 연동 후 사용할 수 있어요.'); });
    document.addEventListener('click', (event) => {
        const toggle = event.target.closest('[data-password-toggle]');
        if (!toggle) return;
        const input = $('#' + toggle.dataset.passwordToggle);
        const visible = input.type === 'text';
        input.type = visible ? 'password' : 'text';
        toggle.setAttribute('aria-pressed', String(!visible));
        toggle.setAttribute('aria-label', visible ? '비밀번호 표시' : '비밀번호 숨기기');
        toggle.textContent = visible ? '◉' : '◌';
    });
}

document.addEventListener('DOMContentLoaded', () => { setupHeaderInteractions(); setupForm(); });
