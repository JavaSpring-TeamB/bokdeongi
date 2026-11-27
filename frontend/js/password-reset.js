(() => {
    const $ = (selector) => document.querySelector(selector);
    const state = { email: '', resetToken: null, resetExpiresAt: 0, codeExpiresAt: 0, resendAt: 0, timer: null, sending: false, verifying: false, resetting: false };
    const API_PATH = '/api/auth/password-reset/';

    function apiUrl(path) {
        const configured = window.BOKDEONGI_AUTH_API_BASE_URL;
        if (typeof configured !== 'string' || !configured.trim()) throw new Error('인증 서버 설정을 확인해 주세요.');
        const base = new URL(configured);
        if (!['http:', 'https:'].includes(base.protocol)) throw new Error('인증 서버 설정을 확인해 주세요.');
        return new URL(path, base.origin).toString();
    }
    function showStep(step) {
        document.querySelectorAll('[data-reset-step]').forEach((section) => { section.hidden = section.dataset.resetStep !== step; });
        const heading = $('#reset-title');
        heading.textContent = step === 'password' ? '새 비밀번호 설정' : step === 'complete' ? '변경 완료' : '비밀번호 찾기';
        $('#reset-intro').hidden = step !== 'email';
    }
    function message(text, field) {
        const target = field ? $('[data-field-error="' + field + '"]') : $('[data-step="email"] [data-step-message]');
        if (target) target.textContent = text;
    }
    function clearMessages() {
        document.querySelectorAll('[data-field-error], [data-step-message], [data-password-match]').forEach((node) => { node.textContent = ''; });
    }
    function responseMessage(payload, fallback) {
        return typeof payload?.message === 'string' && payload.message.trim() ? payload.message.trim() : fallback;
    }
    async function readJson(response) { try { return await response.json(); } catch { return null; } }
    async function post(endpoint, body) {
        let csrfResponse;
        try { csrfResponse = await fetch(apiUrl('/api/auth/csrf'), { credentials: 'include' }); }
        catch { throw new Error('보안 확인 서버에 연결할 수 없습니다. 연결 상태를 확인해 주세요.'); }
        const csrf = await readJson(csrfResponse);
        if (!csrfResponse.ok || typeof csrf?.headerName !== 'string' || typeof csrf?.token !== 'string') {
            throw new Error(responseMessage(csrf, '보안 확인에 실패했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.'));
        }
        let response;
        try {
            response = await fetch(apiUrl(API_PATH + endpoint), {
                method: 'POST', credentials: 'include',
                headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
                body: JSON.stringify(body)
            });
        } catch { throw new Error('서버에 연결할 수 없습니다. 연결 상태를 확인해 주세요.'); }
        if (response.status === 204) return null;
        const payload = await readJson(response);
        if (!response.ok) throw new Error(responseMessage(payload, response.status >= 500 ? '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.' : '입력 정보를 확인하고 다시 시도해 주세요.'));
        return payload;
    }
    function setBusy(button, busy, idle, working) {
        button.disabled = busy;
        button.setAttribute('aria-busy', String(busy));
        button.textContent = busy ? working : idle;
    }
    function clearResetToken() { state.resetToken = null; state.resetExpiresAt = 0; }
    function resetAuthorization() {
        clearResetToken();
        state.email = '';
        state.codeExpiresAt = 0;
        state.resendAt = 0;
        if (state.timer !== null) window.clearInterval(state.timer);
        state.timer = null;
    }
    function remaining(until) { return Math.max(0, Math.ceil((until - Date.now()) / 1000)); }
    function formatTime(seconds) { return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0'); }
    function updateTimer() {
        const codeSeconds = remaining(state.codeExpiresAt);
        const resendSeconds = remaining(state.resendAt);
        const timer = $('[data-code-timer]');
        const resend = $('[data-resend-code]');
        timer.textContent = codeSeconds ? '인증번호 유효시간 ' + formatTime(codeSeconds) : '인증번호가 만료되었습니다. 새 인증번호를 요청해 주세요.';
        resend.disabled = state.sending || resendSeconds > 0;
        resend.textContent = resendSeconds ? '인증번호 재발송 (' + formatTime(resendSeconds) + ')' : '인증번호 재발송';
        if (!codeSeconds && state.codeExpiresAt) $('#verify-code-form').querySelector('[data-verify-code]').disabled = true;
        if (state.resetToken && remaining(state.resetExpiresAt) === 0) {
            clearResetToken();
            showStep('email');
            $('#verify-code-form').hidden = false;
            $('#reset-code').value = '';
            $('[data-step="password"] [data-step-message]').textContent = '인증이 만료되었습니다. 인증번호를 다시 확인해 주세요.';
        }
        if (!codeSeconds && !resendSeconds && state.timer !== null && !state.resetToken) {
            window.clearInterval(state.timer); state.timer = null;
        }
    }
    function startTimer() {
        if (state.timer !== null) window.clearInterval(state.timer);
        updateTimer();
        state.timer = window.setInterval(updateTimer, 250);
    }
    function validateEmail() {
        const input = $('#reset-email');
        const email = input.value.trim();
        if (!email) { message('이메일을 입력해 주세요.', 'email'); input.focus(); return null; }
        if (!input.validity.valid) { message('이메일 형식을 확인해 주세요.', 'email'); input.focus(); return null; }
        return email;
    }
    async function sendCode() {
        if (state.sending) return;
        clearMessages();
        const email = validateEmail();
        if (!email) return;
        state.sending = true;
        const button = $('[data-send-code]'); setBusy(button, true, '인증번호 발송', '발송 중…');
        try {
            const payload = await post('send-code', { email });
            if (!Number.isFinite(payload?.expiresIn) || payload.expiresIn <= 0) throw new Error('인증번호 발송 응답을 확인하지 못했습니다.');
            if (state.email !== email) resetAuthorization();
            state.email = email;
            state.codeExpiresAt = Date.now() + payload.expiresIn * 1000;
            state.resendAt = Date.now() + 60_000;
            $('#verify-code-form').hidden = false;
            $('#reset-code').disabled = false;
            $('#reset-code').focus();
            $('[data-step="email"] [data-step-message]').textContent = '가입된 이메일인 경우 인증번호가 발송됩니다.';
            startTimer();
        } catch (error) {
            $('[data-step="email"] [data-step-message]').textContent = error.message || '인증번호를 발송하지 못했습니다.';
        } finally { state.sending = false; setBusy(button, false, '인증번호 발송', '발송 중…'); updateTimer(); }
    }
    async function verifyCode() {
        if (state.verifying) return;
        clearMessages();
        if (remaining(state.codeExpiresAt) === 0) { message('인증번호가 만료되었습니다. 새 인증번호를 요청해 주세요.', 'code'); updateTimer(); return; }
        const input = $('#reset-code');
        const code = input.value;
        if (!/^[0-9]{6}$/.test(code)) { message('인증번호는 숫자 6자리로 입력해 주세요.', 'code'); input.focus(); return; }
        if (!state.email || state.email !== $('#reset-email').value.trim()) { resetAuthorization(); showStep('email'); message('이메일이 변경되어 인증을 초기화했습니다.', 'email'); return; }
        state.verifying = true;
        const button = $('[data-verify-code]'); setBusy(button, true, '인증 확인', '확인 중…');
        try {
            const payload = await post('verify-code', { email: state.email, code });
            if (typeof payload?.resetToken !== 'string' || !payload.resetToken || !Number.isFinite(payload.expiresIn) || payload.expiresIn <= 0) throw new Error('인증 응답을 확인하지 못했습니다. 다시 인증해 주세요.');
            state.resetToken = payload.resetToken;
            state.resetExpiresAt = Date.now() + payload.expiresIn * 1000;
            $('#new-password').value = ''; $('#confirm-password').value = '';
            showStep('password'); startTimer(); $('#new-password').focus();
        } catch (error) {
            message((error.message || '인증번호를 확인하지 못했습니다.') + ' 인증번호가 만료되었거나 여러 번 틀렸다면 새 인증번호를 요청해 주세요.', 'code');
        } finally { state.verifying = false; setBusy(button, false, '인증 확인', '확인 중…'); }
    }
    function validatePassword(password, confirm) {
        if (password.length < 8 || password.length > 64) return '비밀번호는 8~64자로 입력해 주세요.';
        if (new TextEncoder().encode(password).length > 72) return '비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.';
        if (!password.trim()) return '비밀번호를 입력해 주세요.';
        if (password !== confirm) return '새 비밀번호와 확인 입력값이 일치하지 않습니다.';
        return '';
    }
    function updateMatch() {
        const password = $('#new-password').value;
        const confirm = $('#confirm-password').value;
        const target = $('[data-password-match]');
        target.textContent = confirm ? (password === confirm ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.') : '';
        target.classList.toggle('is-valid', Boolean(confirm) && password === confirm);
    }
    async function resetPassword(event) {
        event.preventDefault();
        if (state.resetting) return;
        clearMessages();
        if (!state.resetToken || remaining(state.resetExpiresAt) === 0) { clearResetToken(); showStep('email'); $('[data-step="password"] [data-step-message]').textContent = '인증이 만료되었습니다. 인증번호를 다시 확인해 주세요.'; return; }
        const validationError = validatePassword($('#new-password').value, $('#confirm-password').value);
        if (validationError) { $('[data-field-error="password"]').textContent = validationError; return; }
        state.resetting = true;
        const button = $('[data-reset-submit]'); setBusy(button, true, '비밀번호 변경', '변경 중…');
        const password = $('#new-password').value;
        try {
            await post('reset', { resetToken: state.resetToken, newPassword: password });
            resetAuthorization();
            $('#new-password').value = ''; $('#confirm-password').value = ''; $('#reset-code').value = ''; $('#reset-email').value = '';
            showStep('complete');
            window.setTimeout(() => { window.location.assign('login.html'); }, 2400);
        } catch (error) {
            const text = error.message || '비밀번호를 변경하지 못했습니다.';
            $('[data-step="password"] [data-step-message]').textContent = text;
            if (/재설정 토큰|만료|유효하지/i.test(text)) {
                clearResetToken(); showStep('email'); $('[data-step="email"] [data-step-message]').textContent = text + ' 인증번호를 다시 확인해 주세요.';
            }
        } finally { state.resetting = false; setBusy(button, false, '비밀번호 변경', '변경 중…'); }
    }
    function setup() {
        $('#send-code-form').addEventListener('submit', (event) => { event.preventDefault(); void sendCode(); });
        $('#verify-code-form').addEventListener('submit', (event) => { event.preventDefault(); void verifyCode(); });
        $('#reset-password-form').addEventListener('submit', resetPassword);
        $('[data-resend-code]').addEventListener('click', () => { if (!state.sending && remaining(state.resendAt) === 0) void sendCode(); });
        $('#reset-email').addEventListener('input', () => {
            clearMessages();
            if (state.email && $('#reset-email').value.trim() !== state.email) {
                resetAuthorization(); $('#verify-code-form').hidden = true; $('#reset-code').value = ''; showStep('email');
            }
        });
        $('#reset-code').addEventListener('input', (event) => { event.target.value = event.target.value.replace(/[^0-9]/g, '').slice(0, 6); message('', 'code'); });
        ['new-password', 'confirm-password'].forEach((id) => $('#' + id).addEventListener('input', updateMatch));
        document.addEventListener('click', (event) => {
            const toggle = event.target.closest('[data-password-toggle]');
            if (!toggle) return;
            const input = document.getElementById(toggle.dataset.passwordToggle);
            const visible = input.type === 'text';
            input.type = visible ? 'password' : 'text';
            toggle.setAttribute('aria-pressed', String(!visible));
            toggle.setAttribute('aria-label', visible ? '비밀번호 표시' : '비밀번호 숨기기');
            toggle.textContent = visible ? '◉' : '◌';
        });
        window.addEventListener('pagehide', resetAuthorization);
    }
    document.addEventListener('DOMContentLoaded', setup, { once: true });
    window.BokdeongiPasswordReset = Object.freeze({ validatePassword, formatTime, getState: () => ({ email: state.email, hasResetToken: Boolean(state.resetToken), resetExpiresAt: state.resetExpiresAt }) });
})();
