const $ = (selector) => document.querySelector(selector);
let isSubmitting = false;

class LoginHttpError extends Error {
    constructor(status, code, request) {
        super('Login request failed.');
        this.status = status;
        this.code = code;
        this.request = request;
    }
}

function showToast(message) { const toast = $('#toast'); toast.textContent = message; toast.hidden = false; window.clearTimeout(showToast.timer); showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 3200); }
function setMessage(message) { const target = $('#login-form-message'); target.textContent = message; if (message) target.focus(); }
function setSubmitting(submitting) { const button = $('#login-submit'); button.disabled = submitting; button.textContent = submitting ? '로그인 확인 중…' : '로그인'; }
function clearPassword() { $('#login-password').value = ''; }

function validateLogin() {
    const email = $('#login-email');
    const password = $('#login-password');
    if (!email.value.trim()) { setMessage('이메일을 입력해 주세요.'); email.focus(); return false; }
    if (!email.validity.valid) { setMessage('이메일 형식을 확인해 주세요.'); email.focus(); return false; }
    if (!password.value) { setMessage('비밀번호를 입력해 주세요.'); password.focus(); return false; }
    setMessage('');
    return true;
}

async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}

async function requestLogin(email, password) {
    const auth = window.BokdeongiAuth;
    if (!auth) throw new LoginHttpError(0, null, 'configuration');

    let csrf;
    try {
        csrf = await auth.getCsrfToken();
    } catch (error) {
        throw new LoginHttpError(0, null, error && error.type === 'configuration' ? 'configuration' : 'csrf');
    }

    let response;
    try {
        response = await fetch(auth.getApiUrl('/api/auth/login'), {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
            body: JSON.stringify({ email, password })
        });
    } catch {
        throw new LoginHttpError(0, null, 'login');
    }

    const payload = await readJson(response);
    if (!response.ok) throw new LoginHttpError(response.status, payload && payload.code, 'login');
    if (!auth.completeLogin(payload)) throw new LoginHttpError(response.status, null, 'response');
}

function showLoginError(error) {
    if (window.BokdeongiAuth) window.BokdeongiAuth.clearAuthentication();
    clearPassword();
    if (!(error instanceof LoginHttpError)) { setMessage('로그인 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.'); return; }
    if (error.request === 'configuration') { setMessage('인증 서버 설정을 확인해 주세요.'); return; }
    if (error.request === 'csrf') { setMessage(error.status === 0 ? '서버에 연결할 수 없습니다. 연결 상태를 확인해 주세요.' : '보안 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.'); return; }
    if (error.status === 0) setMessage('서버에 연결할 수 없습니다. 연결 상태를 확인해 주세요.');
    else if (error.status === 401) setMessage('이메일 또는 비밀번호를 확인해 주세요.');
    else if (error.status === 423 || error.code === 'AUTH_005') setMessage('로그인을 잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    else if (error.status === 400) setMessage('입력한 이메일과 비밀번호를 확인해 주세요.');
    else if (error.status === 403) setMessage('보안 확인에 실패했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.');
    else if (error.status >= 500) setMessage('서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
    else setMessage('로그인 처리 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.');
}

function setupLoginForm() {
    $('#login-form').addEventListener('input', () => setMessage(''));
    $('#login-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (isSubmitting || !validateLogin()) return;

        const email = $('#login-email').value.trim();
        const password = $('#login-password').value;
        isSubmitting = true;
        setSubmitting(true);
        if (window.BokdeongiAuth) window.BokdeongiAuth.clearAuthentication();
        try {
            await requestLogin(email, password);
            window.location.assign('index.html');
        } catch (error) {
            showLoginError(error);
        } finally {
            isSubmitting = false;
            setSubmitting(false);
        }
    });
    document.addEventListener('click', (event) => { const toggle = event.target.closest('[data-password-toggle]'); if (toggle) { const input = $('#' + toggle.dataset.passwordToggle); const visible = input.type === 'text'; input.type = visible ? 'password' : 'text'; toggle.setAttribute('aria-pressed', String(!visible)); toggle.setAttribute('aria-label', visible ? '비밀번호 표시' : '비밀번호 숨기기'); toggle.textContent = visible ? '◉' : '◌'; return; } if (event.target.closest('[data-password-help]')) { showToast('비밀번호 재설정은 P1 기능으로 준비 중입니다.'); } if (event.target.closest('[data-kakao-login]')) { showToast('카카오 로그인은 OAuth 연동 후 사용할 수 있어요.'); } });
}
function setupHeaderInteractions() {}
document.addEventListener('DOMContentLoaded', () => { setupHeaderInteractions(); setupLoginForm(); });
