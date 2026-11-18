const $ = (selector) => document.querySelector(selector);
function showToast(message) { const toast = $('#toast'); toast.textContent = message; toast.hidden = false; window.clearTimeout(showToast.timer); showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 3200); }
function setMessage(message) { const target = $('#login-form-message'); target.textContent = message; if (message) target.focus(); }
function setSubmitting(submitting) { const button = $('#login-submit'); button.disabled = submitting; button.textContent = submitting ? '로그인 확인 중…' : '로그인'; }
function validateLogin() { const email = $('#login-email'); const password = $('#login-password'); if (!email.value.trim()) { setMessage('이메일을 입력해 주세요.'); email.focus(); return false; } if (!email.validity.valid) { setMessage('이메일 형식을 확인해 주세요.'); email.focus(); return false; } if (!password.value) { setMessage('비밀번호를 입력해 주세요.'); password.focus(); return false; } setMessage(''); return true; }
function handleLoginResult(result) {
    if (result.status === 'SUCCESS') { window.location.assign(result.profileStep && result.profileStep < 4 ? 'signup-profile.html' : 'index.html'); return; }
    if (result.status === 'LOCKED') { setMessage('로그인을 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.'); return; }
    setMessage('이메일 또는 비밀번호를 확인해 주세요.');
}
function setupLoginForm() {
    $('#login-form').addEventListener('input', () => setMessage(''));
    $('#login-form').addEventListener('submit', (event) => { event.preventDefault(); if (!validateLogin()) return; /* AUTH-01 API 연동 후 이 위치에서 서버 인증 결과를 handleLoginResult에 전달한다. */ showToast('이메일 로그인은 인증 API 연동 후 사용할 수 있어요.'); });
    document.addEventListener('click', (event) => { const toggle = event.target.closest('[data-password-toggle]'); if (toggle) { const input = $('#' + toggle.dataset.passwordToggle); const visible = input.type === 'text'; input.type = visible ? 'password' : 'text'; toggle.setAttribute('aria-pressed', String(!visible)); toggle.setAttribute('aria-label', visible ? '비밀번호 표시' : '비밀번호 숨기기'); toggle.textContent = visible ? '◉' : '◌'; return; } if (event.target.closest('[data-password-help]')) { showToast('비밀번호 재설정은 P1 기능으로 준비 중입니다.'); } if (event.target.closest('[data-kakao-login]')) { showToast('카카오 로그인은 OAuth 연동 후 사용할 수 있어요.'); } });
}
function setupHeaderInteractions() {}
document.addEventListener('DOMContentLoaded', () => { setupHeaderInteractions(); setupLoginForm(); });
