const $ = (selector) => document.querySelector(selector);
const ACCOUNT_DRAFT_STORAGE_KEY = 'bokdeongi-signup-account-draft';
const CONSENT_STORAGE_KEY = 'bokdeongi-signup-consents';
const COMPLETION_STORAGE_KEY = 'bokdeongi-signup-completion';
const PROFILE_DRAFT_STORAGE_KEY = 'bokdeongi-signup-profile';
let isSubmitting = false;

function readSignupSession(key) {
    try { return JSON.parse(sessionStorage.getItem(key)); } catch { return null; }
}

function setSignupRequestMessage(message) {
    const target = $('#signup-request-message');
    target.textContent = message;
    if (message) target.focus();
}

function disableUnauthenticatedProfileInputs() {
    document.querySelectorAll('.signup-profile__form-scroll input, .signup-profile__form-scroll select').forEach((input) => {
        input.disabled = true;
    });
}

function getSignupConsents() {
    const stored = readSignupSession(CONSENT_STORAGE_KEY);
    if (!Array.isArray(stored)) return null;
    const types = ['TERMS', 'PRIVACY', 'OPTIONAL_INFO', 'SENSITIVE'];
    const byType = new Map(stored.filter((consent) => consent && types.includes(consent.consent_type)).map((consent) => [consent.consent_type, consent]));
    if (byType.size !== types.length) return null;
    return types.map((consentType) => {
        const consent = byType.get(consentType);
        return { consentType, docVersion: consent.doc_version, agreed: consent.agreed === true };
    });
}

function getSignupRequest() {
    const account = readSignupSession(ACCOUNT_DRAFT_STORAGE_KEY);
    const consents = getSignupConsents();
    const password = $('#signup-final-password').value;
    const validAccount = account && typeof account.email === 'string' && typeof account.name === 'string' && typeof account.phoneNumber === 'string' && typeof account.birthDate === 'string';
    const validConsents = consents && consents.every((consent) => typeof consent.docVersion === 'string' && consent.docVersion);
    const requiredAgreed = consents && consents.some((consent) => consent.consentType === 'TERMS' && consent.agreed) && consents.some((consent) => consent.consentType === 'PRIVACY' && consent.agreed);
    if (!validAccount || !validConsents || !requiredAgreed || password.length < 8 || password.length > 64) return null;
    return { email: account.email, password, name: account.name, phoneNumber: account.phoneNumber, birthDate: account.birthDate, consents };
}

async function readJson(response) {
    try { return await response.json(); } catch { return null; }
}

function setSignupSubmitting(submitting) {
    isSubmitting = submitting;
    const button = $('#signup-complete-submit');
    button.disabled = submitting;
    button.textContent = submitting ? '회원가입 처리 중…' : '회원가입 완료 ›';
}

async function completeSignup() {
    if (isSubmitting) return;
    const request = getSignupRequest();
    if (!request) {
        $('#signup-final-password').value = '';
        $('#signup-final-password-message').textContent = '이전 단계의 입력값과 필수 약관 동의, 비밀번호를 확인해 주세요.';
        return;
    }
    const auth = window.BokdeongiAuth;
    if (!auth) {
        setSignupRequestMessage('회원가입 서버 설정을 불러오지 못했어요.');
        return;
    }

    setSignupRequestMessage('');
    setSignupSubmitting(true);
    try {
        const response = await fetch(auth.getApiUrl('/api/auth/signup'), {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        const payload = await readJson(response);
        if (!response.ok) {
            $('#signup-final-password').value = '';
            if ((payload && payload.code === 'AUTH_001') || response.status === 409 || (payload && payload.message === '이미 사용 중인 이메일입니다.')) {
                setSignupRequestMessage('이미 사용 중인 이메일입니다. 이전 단계에서 다른 이메일을 입력해 주세요.');
            } else if (response.status === 403) {
                setSignupRequestMessage('보안 검증에 실패했어요. 페이지를 새로고침한 뒤 다시 시도해 주세요.');
            } else {
                setSignupRequestMessage((payload && payload.message) || '회원가입 정보를 확인해 주세요.');
            }
            return;
        }
        if (!payload || typeof payload.userId !== 'number' || typeof payload.email !== 'string') {
            setSignupRequestMessage('회원가입 응답을 확인하지 못했어요. 로그인 화면에서 가입 여부를 확인해 주세요.');
            return;
        }
        sessionStorage.setItem(COMPLETION_STORAGE_KEY, JSON.stringify({ completed: true }));
        sessionStorage.removeItem(ACCOUNT_DRAFT_STORAGE_KEY);
        sessionStorage.removeItem(CONSENT_STORAGE_KEY);
        sessionStorage.removeItem(PROFILE_DRAFT_STORAGE_KEY);
        $('#signup-final-password').value = '';
        window.location.assign('signup-complete.html');
    } catch {
        setSignupRequestMessage('회원가입 서버에 연결할 수 없어요. 네트워크 상태를 확인해 주세요.');
    } finally {
        setSignupSubmitting(false);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.location.replace('signup-account.html');
    return;
    if (!sessionStorage.getItem(ACCOUNT_DRAFT_STORAGE_KEY) || !sessionStorage.getItem(CONSENT_STORAGE_KEY)) {
        window.location.replace('signup-account.html');
        return;
    }
    sessionStorage.removeItem(PROFILE_DRAFT_STORAGE_KEY);
    disableUnauthenticatedProfileInputs();
    $('#signup-profile-form').addEventListener('submit', (event) => {
        event.preventDefault();
        completeSignup();
    });
});
