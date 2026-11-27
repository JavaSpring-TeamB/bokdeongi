const myPageData = {
    profile: { basics: [], settings: [] },
    faqs: [
        ['복지 혜택은 어떤 기준으로 추천되나요?', '입력된 프로필 정보와 각 혜택의 공고 조건을 비교해 안내합니다. 실제 신청 가능 여부와 심사는 해당 기관의 최신 기준을 확인해 주세요.'],
        ['신청 현황은 어떻게 확인하나요?', '상단의 신청 현황 메뉴에서 진행 중인 신청의 단계와 다음 할 일을 확인할 수 있어요.'],
        ['쉬운 말 번역기는 어떤 파일을 지원하나요?', '현재 화면에서는 텍스트 입력을 이용할 수 있으며, 이미지와 PDF 분석은 추후 연동 예정입니다.'],
        ['정보는 안전하게 보관되나요?', '개인정보 처리 기준과 보관 방식은 확정된 개인정보 처리방침이 연결된 후 확인할 수 있어요.']
    ]
};

const $ = (selector) => document.querySelector(selector);
let currentSection = 'profile';
let profileMode = 'view';
let passwordChangeSubmitting = false;
let withdrawalSubmitting = false;
let withdrawalOpener = null;
let profileLoadState = 'checking';
let profileLoadErrorStatus = null;
let profileCompleted = false;
let profileResponse = null;
let profileSaving = false;
let profileLoadRequestId = 0;
let profileSessionId = 0;
let lastObservedAuthStatus = 'checking';
let bookmarkPage = 1;
const BOOKMARK_PAGE_SIZE = 4;
let favoriteSort = 'DEADLINE';
const favoriteListState = {
    screen: 'IDLE',
    response: null,
    errorKind: null,
    errorMessage: '',
    requestPromise: null
};
let favoriteListRequestId = 0;
let favoriteListController = null;
let favoriteRemovalId = null;
let favoriteRemovalRequestId = 0;
let regionLoadState = 'idle';

function getMyPageRegions() {
    return window.BokdeongiRegions?.getData() || null;
}

function myPageRegionMessage() {
    if (regionLoadState === 'loading' || regionLoadState === 'idle') return '지역 목록을 불러오고 있어요.';
    if (regionLoadState === 'error') return window.BokdeongiRegions?.getError()?.code === 'empty' ? '현재 선택 가능한 지역 정보가 없습니다.' : '지역 목록을 불러오지 못했습니다. 다시 시도해 주세요.';
    const current = getMyPageRegions()?.findByCode(profileEditorState.regionCode);
    return profileEditorState.regionCode && !current ? '현재 저장된 지역 정보가 목록에 없습니다. 현재 지역을 다시 선택해 주세요.' : '상세 주소와 우편번호는 수집하지 않아요.';
}

async function ensureMyPageRegions(retry = false) {
    const provider = window.BokdeongiRegions;
    if (!provider) { regionLoadState = 'error'; if (profileMode === 'edit') renderSection(); return; }
    regionLoadState = 'loading';
    if (profileMode === 'edit') renderSection();
    try {
        await (retry ? provider.retry() : provider.load());
        regionLoadState = 'ready';
    } catch {
        regionLoadState = 'error';
    }
    if (profileMode === 'edit') renderSection();
}
const profileAccordion = { basic: true, household: false, income: false, additional: false, sensitive: false };
function emptyProfileEditorState() {
    return {
        gender: '', sido: '', sigunguName: '', regionCode: '', householdSize: '',
        housingType: '', hasHouse: '', incomeInputType: '', incomeAmount: '', incomeBaseYear: '',
        maritalStatus: '', childrenCount: '', childrenExact: '', youngestYear: '',
        householdTypes: null, statusCodes: null, welfareStatus: ''
    };
}
let profileEditorState = emptyProfileEditorState();
const SENSITIVE_OPTIONS = [
    ['JA0301', '예비부모·난임', '임신·출산 관련 지원 안내에 활용합니다.'], ['JA0302', '임산부', '임신 중 받을 수 있는 지원 안내에 활용합니다.'],
    ['JA0303', '출산·입양', '출산·입양 관련 지원 안내에 활용합니다.'], ['JA0328', '장애 관련', '장애 관련 지원 안내에 활용합니다.'],
    ['JA0329', '보훈 관련', '보훈 관련 지원 안내에 활용합니다.'], ['JA0330', '질병 관련', '질병 관련 지원 안내에 활용합니다.'],
    ['JA0401', '다문화 가구', '다문화 가구 지원 안내에 활용합니다.'], ['JA0402', '북한이탈주민', '북한이탈주민 지원 안내에 활용합니다.']
];
const STATUS_GROUPS = [
    ['education-status', '학력·재학 상태', [['JA0317', '초등학생'], ['JA0318', '중학생'], ['JA0319', '고등학생'], ['JA0320', '대학생']]],
    ['employment-status', '경제활동(고용 상태)', [['JA0326', '재직자'], ['JA0327', '구직자'], ['SELF_EMPLOYED', '자영업자']]],
    ['occupation-industry', '직업·산업 분야', [['JA0313', '농업 종사자'], ['JA0314', '어업 종사자'], ['JA0315', '축산업 종사자'], ['JA0316', '임업 종사자']]]
];
const CONSENT_TYPES = ['OPTIONAL_INFO', 'SENSITIVE', 'DOC_PROCESS', 'DOC_RECORD'];
let consentLoadState = 'checking';
let consentLoadErrorStatus = null;
let consentLoadRequestId = 0;
let consentSubmittingType = null;
let sensitiveCodesSaving = false;
let sensitiveWithdrawalOpener = null;
let sensitiveConsentOpener = null;
let consentState = {
    OPTIONAL_INFO: null,
    SENSITIVE: null,
    DOC_PROCESS: null,
    DOC_RECORD: null
};
let sensitiveEditState = { codes: null };

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;
    window.setTimeout(() => { toast.hidden = true; }, 2800);
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[character]));
}

function profileValue(group, label) {
    const item = (myPageData.profile[group] || []).find(([, itemLabel]) => itemLabel === label);
    return item ? item[2] : '';
}

function displayValue(value, fallback = '미입력') {
    return value === null || value === undefined || value === '' ? fallback : String(value);
}

function formatBirthDate(value) {
    return value ? String(value).replaceAll('-', '.') : '미입력';
}

function formatRegion(profile) {
    return [profile.sidoName, profile.sigunguName].filter(Boolean).join(' ') || '미입력';
}

function formatIncome(profile) {
    if (profile.incomeInputType === 'UNKNOWN') return '잘 모르겠어요';
    if (profile.incomeInputType === 'BRACKET') {
        return profile.medianIncomePct === null || profile.medianIncomePct === undefined
            ? '중위소득 구간 입력'
            : '중위소득 ' + profile.medianIncomePct + '%';
    }
    if (profile.incomeInputType === 'ANNUAL' || profile.incomeInputType === 'MONTHLY') {
        const type = profile.incomeInputType === 'ANNUAL' ? '연소득' : '월소득';
        const percentage = profile.medianIncomePct === null || profile.medianIncomePct === undefined
            ? ''
            : ' · 기준 중위소득 ' + profile.medianIncomePct + '%' + (profile.incomeBaseYear ? ' (' + profile.incomeBaseYear + '년)' : '');
        return type + ' 입력됨' + percentage;
    }
    return '미입력';
}

function incomeDigits(value) {
    return String(value ?? '').replace(/\D/g, '');
}

function formatIncomeAmount(input) {
    const caret = input.selectionStart ?? input.value.length;
    const digitCount = incomeDigits(input.value.slice(0, caret)).length;
    const formatted = incomeDigits(input.value).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    input.value = formatted;

    let nextCaret = 0;
    let seenDigits = 0;
    while (nextCaret < formatted.length && seenDigits < digitCount) {
        if (/\d/.test(formatted[nextCaret])) seenDigits += 1;
        nextCaret += 1;
    }
    input.setSelectionRange?.(nextCaret, nextCaret);
}

function applyProfileResponse(profile) {
    profileResponse = {
        ...profile,
        householdTypeCodes: Array.isArray(profile.householdTypeCodes) ? [...profile.householdTypeCodes] : null,
        statusCodes: Array.isArray(profile.statusCodes) ? [...profile.statusCodes] : null
    };
    myPageData.profile = {
        basics: [
            ['♙', '이름', displayValue(profile.name)],
            ['▣', '생년월일', formatBirthDate(profile.birthDate)],
            ['⌖', '거주지역', formatRegion(profile)],
            ['✉', '이메일', displayValue(profile.email)]
        ],
        settings: [
            ['♧', '가구 형태', profile.householdSize === null || profile.householdSize === undefined ? '미입력' : profile.householdSize + '인 가구'],
            ['⌂', '주거 형태', { OWN: '자가', JEONSE: '전세', MONTHLY: '월세', ETC: '기타' }[profile.housingType] || '미입력'],
            ['▤', '소득 정보', formatIncome(profile)]
        ]
    };

    // GET ProfileResponse는 hasHouse를 반환한다. noHouse 반전은 다음 단계의 step2 저장 요청에서만 적용한다.
    profileEditorState = {
        gender: displayValue(profile.gender, ''),
        sido: displayValue(profile.sidoName, ''),
        sigunguName: displayValue(profile.sigunguName, ''),
        regionCode: displayValue(profile.regionCode, ''),
        householdSize: profile.householdSize === null || profile.householdSize === undefined ? '' : String(profile.householdSize),
        housingType: displayValue(profile.housingType, ''),
        hasHouse: profile.hasHouse === null || profile.hasHouse === undefined ? '' : String(profile.hasHouse),
        incomeInputType: displayValue(profile.incomeInputType, ''),
        incomeAmount: '',
        incomeBaseYear: profile.incomeBaseYear === null || profile.incomeBaseYear === undefined ? '' : String(profile.incomeBaseYear),
        medianIncomePct: profile.medianIncomePct === null || profile.medianIncomePct === undefined ? '' : String(profile.medianIncomePct),
        maritalStatus: displayValue(profile.maritalStatus, ''),
        childrenCount: profile.childrenCount === null || profile.childrenCount === undefined ? '' : profile.childrenCount >= 4 ? '4+' : String(profile.childrenCount),
        childrenExact: profile.childrenCount !== null && profile.childrenCount !== undefined && profile.childrenCount >= 4 ? String(profile.childrenCount) : '',
        youngestYear: profile.youngestChildBirthYear === null || profile.youngestChildBirthYear === undefined ? '' : String(profile.youngestChildBirthYear),
        householdTypes: Array.isArray(profile.householdTypeCodes) ? [...profile.householdTypeCodes] : null,
        statusCodes: Array.isArray(profile.statusCodes) ? [...profile.statusCodes] : null,
        welfareStatus: displayValue(profile.welfareStatus, '')
    };
    profileCompleted = profile.profileCompleted === true;
}

function isConsentItem(value) {
    return value && typeof value === 'object' && !Array.isArray(value);
}

function applyConsentResponse(payload) {
    if (!payload || !Array.isArray(payload.consents)) return false;

    const byType = new Map(payload.consents
        .filter((item) => isConsentItem(item) && CONSENT_TYPES.includes(item.consentType))
        .map((item) => [item.consentType, item]));

    consentState = Object.fromEntries(CONSENT_TYPES.map((type) => {
        const item = byType.get(type);
        return [type, {
            agreed: item && typeof item.agreed === 'boolean' ? item.agreed : null,
            docVersion: item && typeof item.docVersion === 'string' && item.docVersion ? item.docVersion : null,
            consentId: item && Number.isSafeInteger(item.consentId) && item.consentId > 0 ? item.consentId : null
        }];
    }));

    // 서버는 민감 코드 목록을 반환하지 않는다. 이전 브라우저 값으로 복원하지 않는다.
    sensitiveEditState.codes = null;
    return true;
}

function hasVerifiedSensitiveConsent() {
    return consentLoadState === 'loaded' && consentState.SENSITIVE && consentState.SENSITIVE.agreed === true;
}

async function loadConsentStatus({ render = true } = {}) {
    const requestId = ++consentLoadRequestId;
    const auth = window.BokdeongiAuth;
    if (!auth || auth.getAuthenticationState() !== 'authenticated') {
        consentLoadState = 'error';
        consentLoadErrorStatus = null;
        if (render && currentSection === 'profile') renderSection();
        return false;
    }

    consentLoadState = 'checking';
    consentLoadErrorStatus = null;
    if (render && currentSection === 'profile') renderSection();

    try {
        const response = await auth.authenticatedFetch('/api/auth/consents');
        if (requestId !== consentLoadRequestId) return false;
        if (response.status === 401) {
            auth.clearAuthentication();
            if (requestId !== consentLoadRequestId) return false;
            profileLoadState = 'unauthenticated';
            consentLoadState = 'error';
            consentLoadErrorStatus = 401;
            if (render && currentSection === 'profile') renderSection();
            return false;
        }
        if (!response.ok) {
            consentLoadState = 'error';
            consentLoadErrorStatus = response.status;
            if (render && currentSection === 'profile') renderSection();
            return false;
        }

        const payload = await response.json();
        if (requestId !== consentLoadRequestId) return false;
        if (!applyConsentResponse(payload)) {
            consentLoadState = 'error';
            if (render && currentSection === 'profile') renderSection();
            return false;
        }

        consentLoadState = 'loaded';
        if (render && currentSection === 'profile') renderSection();
        return true;
    } catch (error) {
        if (requestId !== consentLoadRequestId) return false;
        consentLoadState = 'error';
        consentLoadErrorStatus = null;
        if (render && currentSection === 'profile') renderSection();
        return false;
    }
}

function resetMyPageUserData(profileState) {
    profileSessionId += 1;
    profileLoadRequestId += 1;
    consentLoadRequestId += 1;
    profileLoadState = profileState;
    profileLoadErrorStatus = null;
    profileCompleted = false;
    profileResponse = null;
    profileSaving = false;
    profileMode = 'view';
    myPageData.profile = { basics: [], settings: [] };
    profileEditorState = emptyProfileEditorState();
    sensitiveEditState = { codes: null };
    consentState = Object.fromEntries(CONSENT_TYPES.map((type) => [type, null]));
    consentLoadState = profileState === 'unauthenticated' ? 'error' : 'checking';
    consentLoadErrorStatus = profileState === 'unauthenticated' ? 401 : null;
    consentSubmittingType = null;
    sensitiveCodesSaving = false;

    favoriteListRequestId += 1;
    if (favoriteListController) favoriteListController.abort();
    favoriteListController = null;
    favoriteListState.screen = profileState === 'unauthenticated'
        ? 'UNAUTHENTICATED'
        : profileState === 'error' ? 'ERROR' : 'IDLE';
    favoriteListState.response = null;
    favoriteListState.errorKind = profileState === 'error' ? 'NETWORK' : null;
    favoriteListState.errorMessage = '';
    favoriteListState.requestPromise = null;
    favoriteRemovalRequestId += 1;
    favoriteRemovalId = null;
    bookmarkPage = 1;

    if (currentSection === 'profile' || currentSection === 'bookmarks') renderSection();
}

function handleMyPageAuthenticationChange(event) {
    const status = event.detail && event.detail.status;
    if (!['checking', 'authenticated', 'unauthenticated', 'error'].includes(status)) return;
    const previousStatus = lastObservedAuthStatus;
    lastObservedAuthStatus = status;

    if (status !== 'authenticated') {
        if (status === 'unauthenticated' || status === 'error'
            || previousStatus === 'authenticated' || profileResponse) {
            resetMyPageUserData(status === 'unauthenticated' ? 'unauthenticated' : status === 'error' ? 'error' : 'checking');
        }
        return;
    }

    if (previousStatus === 'unauthenticated' || previousStatus === 'error') {
        resetMyPageUserData('checking');
        void loadProfile();
        if (currentSection === 'bookmarks') void loadFavorites();
    }
}

function renderProfileLoadState() {
    if (profileLoadState === 'checking') {
        return '<header class="my-page__content-header"><h2>내 정보</h2></header><div class="my-page__empty" role="status">로그인 정보를 확인하고 프로필을 불러오는 중이에요.</div>';
    }
    if (profileLoadState === 'unauthenticated') {
        return '<header class="my-page__content-header"><h2>내 정보</h2></header><div class="my-page__empty"><p>마이페이지는 로그인 후 이용할 수 있어요.</p><a class="bk-btn bk-btn--primary bk-btn--md" href="login.html">로그인하기</a></div>';
    }
    const message = profileLoadErrorStatus === 403
        ? '프로필을 조회할 권한이 없어요. 다시 로그인한 뒤 시도해 주세요.'
        : profileLoadErrorStatus >= 500
            ? '프로필 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
            : '프로필 정보를 불러오지 못했어요. 네트워크와 로그인 상태를 확인해 주세요.';
    return '<header class="my-page__content-header"><h2>내 정보</h2></header><div class="my-page__empty" role="alert">' + message + '</div>';
}

function infoItems(items) {
    return items.map(([, label, value]) => '<div class="my-page__info-item"><span class="my-page__info-label">' + escapeHtml(label) + '</span><strong class="my-page__info-value">' + escapeHtml(value) + '</strong></div>').join('');
}

function renderProfileView() {
    const profileStatus = profileCompleted ? '프로필 등록 완료' : '프로필 입력 중';
    return '<header class="my-page__content-header"><h2>내 정보</h2><span class="my-page__status">' + profileStatus + '</span></header>' +
        '<section class="my-page__section"><h3>기본 정보</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.basics) + '</div></section>' +
        '<section class="my-page__section"><h3>맞춤 혜택 프로필</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.settings) + '</div><div class="my-page__profile-action"><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-profile-edit>프로필 수정하기 <span aria-hidden="true">›</span></button></div></section>' +
        '<section class="my-page__section"><h3>계정 및 개인정보 관리</h3><div class="my-page__account-grid">' +
        '<button class="my-page__account-button" type="button" data-account-action="개인정보 동의 관리"><span><strong>개인정보 동의 관리</strong><small>현재 동의 내역을 확인하고 변경해요.</small></span><span aria-hidden="true">›</span></button>' +
        '<button class="my-page__account-button" type="button" data-account-action="비밀번호 변경"><span><strong>비밀번호 변경</strong><small>비밀번호를 안전하게 변경해요.</small></span><span aria-hidden="true">›</span></button>' +
        '<button class="my-page__account-button" type="button" data-account-action="회원 탈퇴"><span><strong>회원 탈퇴</strong><small>복덩이 서비스를 탈퇴할 수 있어요.</small></span><span aria-hidden="true">›</span></button></div></section>';
}

function renderPasswordChange() {
    return '<header class="my-page__content-header"><div><h2>비밀번호 변경</h2><p class="my-page__content-intro">변경 후 모든 기기에서 다시 로그인해야 해요.</p></div></header>' +
        '<form class="my-page__password-form" data-password-change-form novalidate>' +
        '<div class="my-page__form-control"><label for="current-password"><span>현재 비밀번호 <em aria-hidden="true">*</em></span></label><input id="current-password" name="currentPassword" type="password" autocomplete="current-password" maxlength="64" required aria-describedby="current-password-error"><p id="current-password-error" class="my-page__form-error" data-password-error="currentPassword" aria-live="polite"></p></div>' +
        '<div class="my-page__form-control"><label for="new-password"><span>새 비밀번호 <em aria-hidden="true">*</em></span></label><input id="new-password" name="newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="64" required aria-describedby="new-password-help new-password-error"><p id="new-password-help" class="my-page__form-help">8~64자로 입력해 주세요.</p><p id="new-password-error" class="my-page__form-error" data-password-error="newPassword" aria-live="polite"></p></div>' +
        '<div class="my-page__form-control"><label for="new-password-confirm"><span>새 비밀번호 확인 <em aria-hidden="true">*</em></span></label><input id="new-password-confirm" name="newPasswordConfirm" type="password" autocomplete="new-password" maxlength="64" required aria-describedby="new-password-confirm-error"><p id="new-password-confirm-error" class="my-page__form-error" data-password-error="newPasswordConfirm" aria-live="polite"></p></div>' +
        '<p class="my-page__form-error my-page__password-form-status" data-password-form-status role="alert" tabindex="-1"></p>' +
        '<div class="my-page__profile-form-actions"><button class="bk-btn bk-btn--outline bk-btn--md" type="button" data-password-change-cancel>취소</button><button class="bk-btn bk-btn--primary bk-btn--md" type="submit" data-password-change-submit>비밀번호 변경</button></div></form>';
}

function renderConsentManagement() {
    if (consentLoadState === 'checking') {
        return '<header class="my-page__content-header"><h2>개인정보 동의 관리</h2></header><div class="my-page__empty" role="status">서버에서 현재 동의 상태를 확인하고 있어요.</div>';
    }
    if (consentLoadState === 'error') {
        const message = consentLoadErrorStatus === 401
            ? '로그인 상태가 만료되어 동의 상태를 확인할 수 없어요. 다시 로그인해 주세요.'
            : consentLoadErrorStatus === 403
                ? '보안 확인에 실패해 동의 상태를 불러올 수 없어요. 잠시 후 다시 시도해 주세요.'
                : '동의 상태를 확인하지 못했어요. 확인 전에는 변경할 수 없어요.';
        return '<header class="my-page__content-header"><h2>개인정보 동의 관리</h2></header><div class="my-page__empty" role="alert"><p>' + message + '</p><div class="my-page__profile-form-actions"><button class="bk-btn bk-btn--outline bk-btn--md" type="button" data-consent-back>돌아가기</button><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-consent-retry>다시 확인</button></div></div>';
    }

    const item = (type, title, description) => {
        const consent = consentState[type];
        const agreed = consent && consent.agreed === true;
        const hasHistory = consent && typeof consent.agreed === 'boolean';
        const submitting = consentSubmittingType === type;
        const status = agreed ? '현재 상태: 동의' : hasHistory ? '현재 상태: 미동의' : '현재 상태: 동의 이력 없음';
        const action = type === 'SENSITIVE'
            ? agreed
                ? '<button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-sensitive-withdraw-start' + (submitting ? ' disabled' : '') + '>' + (submitting ? '변경 중…' : '동의 철회') + '</button>'
                : '<button class="bk-btn bk-btn--primary bk-btn--sm" type="button" data-sensitive-consent-start' + (submitting ? ' disabled' : '') + '>동의하기</button>'
            : '<button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-optional-consent-change="' + (agreed ? 'false' : 'true') + '"' + (submitting ? ' disabled' : '') + '>' + (submitting ? '변경 중…' : agreed ? '동의 철회' : '동의하기') + '</button>';
        const documentActions = ['DOC_PROCESS', 'DOC_RECORD'].includes(type)
            ? '<div class="my-page__consent-actions"><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-document-consent-view="' + type + '">내용 보기</button><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-document-consent-change="' + type + ':' + (!agreed) + '"' + (submitting ? ' disabled' : '') + '>' + (submitting ? '변경 중…' : agreed ? '동의 철회' : '동의하기') + '</button></div>' : '';
        return '<section class="my-page__section my-page__consent-item"><div class="my-page__section-heading"><div><h3>' + title + '</h3><p>' + description + '</p><p class="my-page__form-help">' + status + '</p></div>' + (documentActions || action) + '</div></section>';
    };

    return '<header class="my-page__content-header"><div><h2>개인정보 동의 관리</h2><p class="my-page__content-intro">서버에 저장된 최신 동의 상태를 기준으로 변경합니다.</p></div></header>' +
        item('OPTIONAL_INFO', '선택정보 수집·이용', '선택정보를 제공하면 맞춤 안내를 더 정확하게 받을 수 있어요.') +
        item('SENSITIVE', '민감정보 수집·이용', '민감정보는 선택 항목이며, 철회하면 관련 정보가 삭제될 수 있어요.') +
        item('DOC_PROCESS', '서류 처리 및 자동 검토', '동의하면 OCR 서류 업로드와 자동 검토를 이용할 수 있어요.') +
        item('DOC_RECORD', '서류 유효기간 기억', '동의하면 서류 발급일을 기억해 유효기간과 만료 여부를 확인해요.') +
        '<p class="my-page__form-help">민감정보 철회 뒤 맞춤 혜택 판정 결과가 달라질 수 있으며, 재판정 완료 시점은 즉시 확정되지 않아요.</p><div class="my-page__profile-form-actions"><button class="bk-btn bk-btn--outline bk-btn--md" type="button" data-consent-back>돌아가기</button></div>';
}

function renderProfileEditor() {
    const birthDate = profileValue('basics', '생년월일').replaceAll('.', '-');
    const field = (label, name, value, options = '') => '<label class="my-page__form-control"><span>' + label + (options.includes('required') ? ' <em aria-hidden="true">*</em>' : '') + '</span><input name="' + name + '" value="' + escapeHtml(value) + '" ' + options + '></label>';
    const selected = (value, expected) => value === expected ? ' selected' : '';
    const radio = (name, value, label, state) => '<label><input type="radio" name="' + name + '" value="' + value + '"' + (state === value ? ' checked' : '') + '> ' + label + '</label>';
    const checks = (group, items, values) => items.map(([value, label, none]) => '<label><input type="checkbox" value="' + value + '"' + (none ? ' data-none' : '') + (values && (none ? values.length === 0 : values.includes(value)) ? ' checked' : '') + '> ' + label + '</label>').join('');
    const accordion = (key, title, content) => '<section class="my-page__profile-accordion" data-profile-accordion="' + key + '"><button class="my-page__profile-accordion-button" type="button" aria-expanded="' + profileAccordion[key] + '" aria-controls="profile-panel-' + key + '" data-profile-accordion-toggle="' + key + '"><span>' + title + '</span><span aria-hidden="true">⌄</span></button><div class="my-page__profile-accordion-panel" id="profile-panel-' + key + '"' + (profileAccordion[key] ? '' : ' hidden') + '>' + content + '</div></section>';
    const regionData = getMyPageRegions();
    const matchedRegion = regionData?.findByCode(profileEditorState.regionCode);
    const selectedSido = matchedRegion?.sidoName || profileEditorState.sido;
    const regionNames = regionData ? [...regionData.sidos] : [];
    if (selectedSido && !regionNames.includes(selectedSido)) regionNames.push(selectedSido);
    const regionOptions = regionNames.map((name) => '<option value="' + escapeHtml(name) + '"' + selected(selectedSido, name) + '>' + escapeHtml(name) + '</option>').join('');
    const sigunguRegions = regionData ? [...regionData.selectableFor(selectedSido)] : [];
    if (profileEditorState.regionCode && !sigunguRegions.some((region) => region.regionCode === profileEditorState.regionCode)) {
        sigunguRegions.push({ regionCode: profileEditorState.regionCode, sidoName: selectedSido, sigunguName: profileEditorState.sigunguName || profileEditorState.regionCode });
    }
    const sigunguOptions = sigunguRegions.map((region) => '<option value="' + escapeHtml(region.regionCode) + '"' + selected(profileEditorState.regionCode, region.regionCode) + '>' + escapeHtml(region.sigunguName || region.sidoName) + '</option>').join('');
    const regionControlsDisabled = regionLoadState !== 'ready';
    const regionMessage = myPageRegionMessage();
    const currentYear = new Date().getFullYear();
    const incomeAmountVisible = ['ANNUAL', 'MONTHLY'].includes(profileEditorState.incomeInputType);
    const incomeBracketVisible = profileEditorState.incomeInputType === 'BRACKET';
    const childVisible = profileEditorState.childrenCount && profileEditorState.childrenCount !== '0';
    const exactChildrenVisible = profileEditorState.childrenCount === '4+';
    const sensitiveCodes = sensitiveEditState.codes;
    const sensitiveEnabled = hasVerifiedSensitiveConsent();
    const sensitiveOptions = SENSITIVE_OPTIONS.map(([code, label, description]) => '<label><input type="checkbox" name="sensitiveCodes" value="' + code + '"' + (sensitiveCodes && sensitiveCodes.includes(code) ? ' checked' : '') + (sensitiveEnabled ? '' : ' disabled') + '><span><strong>' + label + '</strong><small>' + description + '</small></span></label>').join('') + '<label class="my-page__sensitive-none"><input type="checkbox" name="sensitiveCodes" value="NONE" data-none' + (Array.isArray(sensitiveCodes) && sensitiveCodes.length === 0 ? ' checked' : '') + (sensitiveEnabled ? '' : ' disabled') + '><span><strong>해당 없음</strong><small>관련 항목이 없음을 명시합니다.</small></span></label>';
    const statusGroupFields = STATUS_GROUPS.map(([group, title, items]) => '<fieldset class="my-page__multi-choice" data-exclusive-group="' + group + '"><legend>' + title + '</legend><div>' + checks(group, [...items, ['NONE', '해당 없음', true]], profileEditorState.statusCodes) + '</div></fieldset>').join('');
    const sensitiveMessage = consentLoadState === 'checking'
        ? '민감정보 동의 상태를 서버에서 확인하고 있어요.'
        : consentLoadState === 'error'
            ? '민감정보 동의 상태를 확인하지 못했어요. 동의 상태를 확인하기 전에는 입력할 수 없어요.'
            : sensitiveEnabled
                ? '서버에서 동의 상태를 확인했어요. 저장된 민감 코드 목록은 보안상 다시 표시하지 않아요.'
                : '동의하지 않은 상태예요. 정식 동의 문서가 연결된 뒤 동의 관리에서 동의하면 입력할 수 있어요.';
    const sensitiveContent = '<p class="my-page__sensitive-intro">민감정보는 관련 복지 혜택을 더 정확하게 안내하기 위해 사용됩니다. 입력은 선택 사항이며, 동의 여부와 입력 내용은 변경할 수 있습니다.</p><div class="my-page__sensitive-consent"><span><strong>민감정보 수집·이용 동의</strong> <small>(선택)</small></span><button class="my-page__sensitive-view" type="button" data-open-consent-management>동의 관리</button></div><p class="my-page__sensitive-notice" data-sensitive-reason>' + sensitiveMessage + '</p><div class="my-page__sensitive-options" data-exclusive-group="sensitiveCodes"' + (sensitiveEnabled ? '' : ' hidden') + '>' + sensitiveOptions + '</div><div class="my-page__profile-form-actions"' + (sensitiveEnabled ? '' : ' hidden') + '><button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-sensitive-codes-save' + (sensitiveCodesSaving ? ' disabled' : '') + '>' + (sensitiveCodesSaving ? '저장 중…' : '민감정보 코드 저장') + '</button></div>';
    return '<header class="my-page__content-header"><div><h2>프로필 수정</h2><p class="my-page__content-intro">변경한 정보는 서버에 저장되며, 저장 후 최신 프로필을 다시 불러옵니다.</p></div><span class="my-page__status">' + (profileCompleted ? '프로필 등록 완료' : '프로필 입력 중') + '</span></header>' +
        '<form class="my-page__profile-form" data-profile-form novalidate>' +
        '<section class="my-page__profile-form-section"><div class="my-page__section-heading"><h3>계정 고유 정보</h3><p>계정 정보는 이 화면에서 변경할 수 없어요.</p></div><div class="my-page__profile-form-grid">' +
        field('이름', 'name', profileValue('basics', '이름'), 'type="text" disabled') +
        field('이메일', 'email', profileValue('basics', '이메일'), 'type="email" disabled') +
        '</div></section>' +
        accordion('basic', '기본 정보', '<div class="my-page__profile-form-grid">' + field('생년월일', 'birthDate', birthDate, 'type="date" required max="' + new Date().toISOString().slice(0, 10) + '"') + '<fieldset class="my-page__choice-field"><legend>성별 <em aria-hidden="true">*</em></legend><div>' + radio('gender', 'M', '남성', profileEditorState.gender) + radio('gender', 'F', '여성', profileEditorState.gender) + '</div></fieldset><label class="my-page__form-control"><span>시·도 <em aria-hidden="true">*</em></span><select name="sido" required' + (regionControlsDisabled ? ' disabled' : '') + '><option value="">시·도 선택</option>' + regionOptions + '</select></label><label class="my-page__form-control"><span>시·군·구 <em aria-hidden="true">*</em></span><select name="regionCode" required' + (regionControlsDisabled || !selectedSido || !sigunguRegions.length ? ' disabled' : '') + '><option value="">' + (selectedSido ? '시·군·구 선택' : '시·도를 먼저 선택해 주세요.') + '</option>' + sigunguOptions + '</select></label></div><p class="my-page__form-help" data-profile-region-message>' + escapeHtml(regionMessage) + '</p>' + (regionLoadState === 'error' ? '<button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-profile-region-retry>지역 목록 다시 불러오기</button>' : '') + '</div>') +
        accordion('household', '가구 및 주거 정보', '<div class="my-page__profile-form-grid"><label class="my-page__form-control"><span>가구원 수 <em aria-hidden="true">*</em></span><select name="householdSize" required><option value="">선택해 주세요.</option>' + ['1', '2', '3', '4', '5'].map((value) => '<option value="' + value + '"' + selected(profileEditorState.householdSize, value) + '>' + (value === '5' ? '5명 이상' : value + '명') + '</option>').join('') + '</select></label><fieldset class="my-page__choice-field"><legend>주거 형태 <em aria-hidden="true">*</em></legend><div>' + radio('housingType', 'OWN', '자가', profileEditorState.housingType) + radio('housingType', 'JEONSE', '전세', profileEditorState.housingType) + radio('housingType', 'MONTHLY', '월세', profileEditorState.housingType) + radio('housingType', 'ETC', '기타', profileEditorState.housingType) + '</div></fieldset><fieldset class="my-page__choice-field"><legend>주택 보유 여부 <em aria-hidden="true">*</em></legend><div>' + radio('hasHouse', 'true', '보유', profileEditorState.hasHouse) + radio('hasHouse', 'false', '미보유', profileEditorState.hasHouse) + '</div></fieldset></div>') +
        accordion('income', '소득 정보', '<p class="my-page__form-help">금액 입력 시 서버가 기준 중위소득 비율과 기준 연도를 계산합니다. 중위소득 비율을 알고 있다면 직접 입력할 수 있어요.</p><fieldset class="my-page__choice-field"><legend>입력 방식 <em aria-hidden="true">*</em></legend><div>' + radio('incomeInputType', 'ANNUAL', '연소득', profileEditorState.incomeInputType) + radio('incomeInputType', 'MONTHLY', '월소득', profileEditorState.incomeInputType) + radio('incomeInputType', 'BRACKET', '중위소득 비율', profileEditorState.incomeInputType) + radio('incomeInputType', 'UNKNOWN', '잘 모르겠어요', profileEditorState.incomeInputType) + '</div></fieldset><div class="my-page__profile-form-grid" data-income-amount-fields' + (incomeAmountVisible ? '' : ' hidden') + '>' + field('소득 금액', 'incomeAmount', profileEditorState.incomeAmount, 'type="text" inputmode="numeric" autocomplete="off" placeholder="예: 2,500,000"' + (incomeAmountVisible ? ' required' : ' disabled')) + '<label class="my-page__form-control"><span>현재 계산 비율</span><input value="' + escapeHtml(profileEditorState.medianIncomePct ? profileEditorState.medianIncomePct + '%' : '저장 후 표시') + '" type="text" readonly></label><label class="my-page__form-control"><span>서버 기준 연도</span><input value="' + escapeHtml(profileEditorState.incomeBaseYear || '저장 후 표시') + '" type="text" readonly></label></div><div class="my-page__profile-form-grid" data-income-bracket-fields' + (incomeBracketVisible ? '' : ' hidden') + '>' + field('중위소득 비율', 'medianIncomePct', profileEditorState.medianIncomePct, 'type="number" min="0" max="9999.9" step="0.1" inputmode="decimal"' + (incomeBracketVisible ? ' required' : ' disabled')) + '</div>') +
        accordion('additional', '추가 정보', '<div class="my-page__profile-form-grid"><fieldset class="my-page__choice-field"><legend>혼인 여부</legend><div>' + radio('maritalStatus', 'SINGLE', '미혼', profileEditorState.maritalStatus) + radio('maritalStatus', 'MARRIED', '기혼', profileEditorState.maritalStatus) + radio('maritalStatus', 'ETC', '기타', profileEditorState.maritalStatus) + '</div></fieldset><label class="my-page__form-control"><span>자녀 수</span><select name="childrenCount"><option value="">선택해 주세요.</option><option value="0"' + selected(profileEditorState.childrenCount, '0') + '>없음</option><option value="1"' + selected(profileEditorState.childrenCount, '1') + '>1명</option><option value="2"' + selected(profileEditorState.childrenCount, '2') + '>2명</option><option value="3"' + selected(profileEditorState.childrenCount, '3') + '>3명</option><option value="4+"' + selected(profileEditorState.childrenCount, '4+') + '>4명 이상</option></select></label><label class="my-page__form-control" data-exact-children-field' + (exactChildrenVisible ? '' : ' hidden') + '><span>정확한 자녀 수</span><input name="childrenExact" type="number" min="4" inputmode="numeric" value="' + escapeHtml(profileEditorState.childrenExact) + '"' + (exactChildrenVisible ? ' required' : ' disabled') + '></label><label class="my-page__form-control" data-youngest-field' + (childVisible ? '' : ' hidden') + '><span>막내 자녀 출생연도</span><input name="youngestYear" type="number" min="1900" max="' + currentYear + '" inputmode="numeric" value="' + escapeHtml(profileEditorState.youngestYear) + '"' + (childVisible ? ' required' : ' disabled') + '></label></div><fieldset class="my-page__multi-choice" data-exclusive-group="householdTypes"><legend>가구 유형 <em aria-hidden="true">*</em></legend><div>' + checks('householdTypes', [['JA0404', '1인 가구'], ['JA0403', '한부모 가구'], ['JA0403', '조손 가구'], ['JA0411', '다자녀 가구'], ['JA0413', '신규 전입 가구'], ['JA0414', '확대가족'], ['NONE', '해당 없음', true]], profileEditorState.householdTypes) + '</div></fieldset>' + statusGroupFields + '<fieldset class="my-page__choice-field"><legend>복지 수급 상태</legend><div>' + radio('welfareStatus', 'BASIC', '기초생활수급자', profileEditorState.welfareStatus) + radio('welfareStatus', 'NEAR_POOR', '차상위계층', profileEditorState.welfareStatus) + radio('welfareStatus', 'NONE', '해당 없음', profileEditorState.welfareStatus) + radio('welfareStatus', 'UNKNOWN', '잘 모르겠어요', profileEditorState.welfareStatus) + '</div></fieldset>') +
        accordion('sensitive', '민감정보 관리 (선택)', sensitiveContent) +
        '<p class="my-page__form-error" data-profile-form-error role="alert" tabindex="-1"></p><div class="my-page__profile-form-actions"><button class="bk-btn bk-btn--outline bk-btn--md" type="button" data-profile-cancel' + (profileSaving ? ' disabled' : '') + '>취소</button><button class="bk-btn bk-btn--primary bk-btn--md" type="submit" data-profile-save' + (profileSaving ? ' disabled' : '') + '>' + (profileSaving ? '저장 중…' : '저장') + '</button></div></form>';
}

function renderProfile() {
    if (profileLoadState !== 'loaded') return renderProfileLoadState();
    if (profileMode === 'consents') return renderConsentManagement();
    if (profileMode === 'password') return renderPasswordChange();
    return profileMode === 'edit' ? renderProfileEditor() : renderProfileView();
}

class FavoriteListRequestError extends Error {
    constructor(kind, message) {
        super(message);
        this.kind = kind;
    }
}

function favoriteBenefitId(value) {
    const id = typeof value === 'number' || typeof value === 'string' ? Number(value) : NaN;
    return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function isFavoriteItem(item) {
    return item && typeof item === 'object' && favoriteBenefitId(item.benefitId) !== null;
}

function isFavoriteListResponse(payload) {
    return payload && typeof payload === 'object' && !Array.isArray(payload)
        && Array.isArray(payload.items) && payload.items.every(isFavoriteItem)
        && Array.isArray(payload.ended) && payload.ended.every(isFavoriteItem)
        && (payload.count === undefined || (Number.isSafeInteger(payload.count) && payload.count >= 0));
}

function isFavoriteToggleResponse(payload, benefitId) {
    return payload && typeof payload === 'object' && typeof payload.bookmarked === 'boolean'
        && favoriteBenefitId(payload.benefitId) === benefitId;
}

function optionalFavoriteText(value) {
    return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function favoriteStatusText(status) {
    const labels = { ELIGIBLE: '해당', CONDITIONAL: '조건부', INELIGIBLE: '해당 없음' };
    const value = optionalFavoriteText(status);
    return labels[value] || value;
}

function favoriteCard(item) {
    const benefitId = favoriteBenefitId(item.benefitId);
    if (benefitId === null) return '';
    const status = favoriteStatusText(item.status);
    const category = optionalFavoriteText(item.category);
    const deadline = optionalFavoriteText(item.period?.text);
    const title = optionalFavoriteText(item.title);
    const orgName = optionalFavoriteText(item.orgName);
    const hashtags = Array.isArray(item.hashtags) ? item.hashtags.map(optionalFavoriteText).filter(Boolean) : [];
    const statusClass = status === '조건부' ? ' my-page__benefit-status--conditional' : '';
    const metadata = [
        status ? '<span class="my-page__benefit-status' + statusClass + '">' + escapeHtml(status) + '</span>' : '',
        category ? '<span class="my-page__benefit-category">' + escapeHtml(category) + '</span>' : '',
        deadline ? '<span class="my-page__benefit-deadline">마감 ' + escapeHtml(deadline) + '</span>' : ''
    ].join('');
    const details = [
        orgName ? '<p>기관: ' + escapeHtml(orgName) + '</p>' : '',
        hashtags.length ? '<p>' + hashtags.map((tag) => '#' + escapeHtml(tag)).join(' ') + '</p>' : ''
    ].join('');
    const removing = favoriteRemovalId === benefitId;
    return '<article class="my-page__benefit-card"><div class="my-page__benefit-content">'
        + (metadata ? '<div class="my-page__benefit-meta">' + metadata + '</div>' : '')
        + (title ? '<h3>' + escapeHtml(title) + '</h3>' : '')
        + details
        + '</div><div class="my-page__benefit-actions"><a class="bk-btn bk-btn--outline bk-btn--sm" href="benefit-detail.html?id=' + encodeURIComponent(benefitId) + '">상세 보기</a><button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-remove-bookmark="' + benefitId + '"' + (removing ? ' disabled' : '') + '>' + (removing ? '해제 중…' : '관심 해제') + '</button></div></article>';
}

function renderBookmarks() {
    const header = '<header class="my-page__content-header"><h2>저장한 관심 혜택</h2></header>';
    if (favoriteListState.screen === 'IDLE' || favoriteListState.screen === 'LOADING') {
        if (!favoriteListState.response) return header + '<div class="my-page__empty" role="status">관심 혜택을 불러오고 있어요.</div>';
    }
    if (favoriteListState.screen === 'UNAUTHENTICATED') {
        return header + '<div class="my-page__empty">관심 혜택은 로그인 후 확인할 수 있어요. <a href="login.html">로그인하기</a></div>';
    }
    if (favoriteListState.screen === 'ERROR' && !favoriteListState.response) {
        const message = favoriteListState.errorKind === 'NETWORK'
            ? '네트워크 연결을 확인한 뒤 다시 시도해 주세요.'
            : favoriteListState.errorKind === 'INVALID'
                ? '관심 혜택 응답 형식이 올바르지 않습니다. 잠시 후 다시 시도해 주세요.'
                : '관심 혜택을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
        return header + '<div class="my-page__empty" role="alert">' + message + '<div><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-retry-favorites>다시 시도</button></div></div>';
    }

    const response = favoriteListState.response;
    const items = response?.items || [];
    const ended = response?.ended || [];
    const count = Number.isSafeInteger(response?.count) ? response.count : items.length + ended.length;
    bookmarkPage = window.BokdeongiPagination.clampPage(items.length, bookmarkPage, BOOKMARK_PAGE_SIZE);
    const start = (bookmarkPage - 1) * BOOKMARK_PAGE_SIZE;
    const activeCards = items.slice(start, start + BOOKMARK_PAGE_SIZE).map(favoriteCard).join('');
    const activeList = activeCards || (ended.length ? '<div class="my-page__empty">신청 가능한 관심 혜택이 없어요.</div>' : '<div class="my-page__empty">저장한 관심 혜택이 없어요. 복지 혜택 찾기에서 관심 있는 혜택을 저장해 보세요.</div>');
    const pagination = window.BokdeongiPagination.markup({ totalCount: items.length, currentPage: bookmarkPage, pageSize: BOOKMARK_PAGE_SIZE, label: '저장한 관심 혜택' });
    const endedCards = ended.map(favoriteCard).join('');
    const endedSection = endedCards ? '<section class="my-page__section"><h3>종료된 혜택</h3><div class="my-page__benefit-list">' + endedCards + '</div></section>' : '';
    const statusNote = favoriteListState.screen === 'LOADING'
        ? '<p class="my-page__favorites-message" role="status">정렬된 목록을 불러오는 중이에요.</p>'
        : favoriteListState.screen === 'ERROR'
            ? '<div class="my-page__favorites-message" role="alert">목록을 새로 불러오지 못해 이전 결과를 표시하고 있어요. <button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-retry-favorites>다시 시도</button></div>'
            : '';
    const sortControl = '<label class="my-page__favorite-sort"><span>정렬</span><select data-favorite-sort aria-label="관심 혜택 정렬"><option value="DEADLINE"' + (favoriteSort === 'DEADLINE' ? ' selected' : '') + '>마감순</option><option value="SAVED"' + (favoriteSort === 'SAVED' ? ' selected' : '') + '>저장순</option></select></label>';
    return '<header class="my-page__content-header"><h2>저장한 관심 혜택</h2><div class="my-page__favorites-controls"><span class="my-page__status">총 ' + count + '건</span>' + sortControl + '</div></header>' + statusNote + '<div class="my-page__benefit-list">' + activeList + '</div>' + pagination + endedSection;
}

function renderFavoritesIfVisible() {
    if (currentSection === 'bookmarks') renderSection();
}

async function loadFavorites(sort = favoriteSort) {
    if (sort !== 'DEADLINE' && sort !== 'SAVED') return;
    favoriteSort = sort;
    const requestId = ++favoriteListRequestId;
    if (favoriteListController) favoriteListController.abort();
    const controller = new AbortController();
    favoriteListController = controller;
    favoriteListState.screen = 'LOADING';
    favoriteListState.errorKind = null;
    favoriteListState.errorMessage = '';
    renderFavoritesIfVisible();

    const request = (async () => {
        try {
            const auth = window.BokdeongiAuth;
            if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.refreshAuthentication !== 'function') {
                throw new FavoriteListRequestError('CONFIG', '관심 혜택 서비스를 설정하지 못했습니다.');
            }
            const authenticationState = await auth.refreshAuthentication();
            if (requestId !== favoriteListRequestId) return;
            if (authenticationState !== 'authenticated') {
                favoriteListState.screen = 'UNAUTHENTICATED';
                favoriteListState.response = null;
                renderFavoritesIfVisible();
                return;
            }

            const response = await auth.authenticatedFetch('/api/me/favorites?sort=' + encodeURIComponent(favoriteSort), { signal: controller.signal });
            if (requestId !== favoriteListRequestId) return;
            if (response.status === 401) {
                favoriteListState.screen = 'UNAUTHENTICATED';
                favoriteListState.response = null;
                renderFavoritesIfVisible();
                return;
            }
            if (!response.ok) throw new FavoriteListRequestError('API', '관심 혜택을 불러오지 못했습니다.');
            let payload;
            try {
                payload = await response.json();
            } catch {
                throw new FavoriteListRequestError('INVALID', '관심 혜택 응답 형식이 올바르지 않습니다.');
            }
            if (!isFavoriteListResponse(payload)) {
                throw new FavoriteListRequestError('INVALID', '관심 혜택 응답 형식이 올바르지 않습니다.');
            }
            if (requestId !== favoriteListRequestId) return;
            if (payload.sort !== favoriteSort) throw new FavoriteListRequestError('INVALID', '관심 혜택 정렬 결과를 확인하지 못했습니다.');
            favoriteListState.response = payload;
            favoriteListState.screen = 'RESULT';
            bookmarkPage = 1;
            renderFavoritesIfVisible();
        } catch (error) {
            if ((error && error.name === 'AbortError') || requestId !== favoriteListRequestId) return;
            const unauthenticated = error && error.type === 'unauthenticated';
            favoriteListState.screen = unauthenticated ? 'UNAUTHENTICATED' : 'ERROR';
            favoriteListState.response = unauthenticated ? null : favoriteListState.response;
            favoriteListState.errorKind = unauthenticated ? null : (error instanceof FavoriteListRequestError ? error.kind : 'NETWORK');
            favoriteListState.errorMessage = error instanceof FavoriteListRequestError ? error.message : '';
            renderFavoritesIfVisible();
            console.error('관심 혜택을 조회하지 못했습니다.', error);
        } finally {
            if (requestId === favoriteListRequestId) {
                favoriteListController = null;
                favoriteListState.requestPromise = null;
            }
        }
    })();
    favoriteListState.requestPromise = request;
    return request;
}

async function removeFavorite(benefitId) {
    if (favoriteRemovalId !== null || favoriteListState.screen !== 'RESULT') return;
    const id = favoriteBenefitId(benefitId);
    if (id === null) return;

    const requestId = ++favoriteRemovalRequestId;
    favoriteRemovalId = id;
    renderFavoritesIfVisible();
    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.getCsrfToken !== 'function' || typeof auth.refreshAuthentication !== 'function') {
            throw new FavoriteListRequestError('CONFIG', '관심 혜택 저장 서비스를 설정하지 못했습니다.');
        }
        const authenticationState = await auth.refreshAuthentication();
        if (requestId !== favoriteRemovalRequestId) return;
        if (authenticationState !== 'authenticated') {
            favoriteListState.screen = 'UNAUTHENTICATED';
            favoriteListState.response = null;
            renderFavoritesIfVisible();
            showToast('로그인이 만료되었습니다. 다시 로그인해주세요.');
            return;
        }

        const csrf = await auth.getCsrfToken();
        const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(id) + '/favorite', {
            method: 'DELETE',
            headers: { [csrf.headerName]: csrf.token }
        });
        if (requestId !== favoriteRemovalRequestId) return;
        if (response.status === 401) {
            favoriteListState.screen = 'UNAUTHENTICATED';
            favoriteListState.response = null;
            renderFavoritesIfVisible();
            showToast('로그인이 만료되었습니다. 다시 로그인해주세요.');
            return;
        }
        if (!response.ok) throw new FavoriteListRequestError(response.status === 404 ? 'NOT_FOUND' : 'API', '관심 혜택 저장을 해제하지 못했습니다.');
        let payload;
        try {
            payload = await response.json();
        } catch {
            throw new FavoriteListRequestError('INVALID', '관심 혜택 응답 형식이 올바르지 않습니다.');
        }
        if (!isFavoriteToggleResponse(payload, id) || payload.bookmarked !== false) {
            throw new FavoriteListRequestError('INVALID', '관심 혜택 저장 해제 결과를 확인하지 못했습니다.');
        }
        showToast('관심 혜택 저장을 해제했어요.');
        await loadFavorites();
    } catch (error) {
        if (requestId !== favoriteRemovalRequestId) return;
        const unauthenticated = error && error.type === 'unauthenticated';
        if (unauthenticated) {
            favoriteListState.screen = 'UNAUTHENTICATED';
            favoriteListState.response = null;
            renderFavoritesIfVisible();
        }
        const message = error instanceof FavoriteListRequestError && error.kind === 'NOT_FOUND'
            ? '존재하지 않는 혜택입니다.'
            : unauthenticated ? '로그인이 만료되었습니다. 다시 로그인해주세요.' : '관심 혜택 저장을 해제하지 못했습니다. 다시 시도해주세요.';
        showToast(message);
        console.error('관심 혜택 저장을 해제하지 못했습니다.', error);
    } finally {
        if (requestId === favoriteRemovalRequestId) {
            favoriteRemovalId = null;
            renderFavoritesIfVisible();
        }
    }
}

function renderGuide() {
    const features = [
        ['복지 혜택 찾기', '다양한 복지 혜택을 검색하고 지원 대상, 신청 기간, 필요 서류를 확인해요.', 'benefit-list.html'],
        ['맞춤 혜택 추천', '등록한 프로필을 바탕으로 해당하거나 추가 확인이 필요한 혜택을 살펴봐요.', 'benefit-recommend.html'],
        ['신청 현황', '신청을 준비 중인 혜택의 단계와 다음 할 일을 확인해요.', 'application-status.html'],
        ['쉬운 말 번역기', '어려운 안내문을 입력하고 이해하기 쉬운 말로 바꿔 확인해요.', 'easy-translator.html']
    ].map(([title, description, href], index) => '<article class="my-page__guide-item"><button class="my-page__guide-question" type="button" aria-expanded="false" aria-controls="guide-answer-' + index + '" data-guide-feature="' + index + '"><span>' + title + '</span><span aria-hidden="true">⌄</span></button><div class="my-page__guide-answer" id="guide-answer-' + index + '" hidden><p>' + description + '</p><a class="bk-btn bk-btn--secondary bk-btn--sm" href="' + href + '">' + title + '로 이동</a></div></article>').join('');
    const faqs = myPageData.faqs.map(([question, answer], index) => '<article class="my-page__faq-item"><button class="my-page__faq-question" type="button" aria-expanded="false" aria-controls="faq-answer-' + index + '" data-faq="' + index + '"><span>' + question + '</span><span aria-hidden="true">⌄</span></button><p class="my-page__faq-answer" id="faq-answer-' + index + '" hidden>' + answer + '</p></article>').join('');
    return '<header class="my-page__content-header"><div><h2>이용 안내</h2><p class="my-page__content-intro">복덩이의 주요 기능과 이용 방법을 안내해 드려요.</p></div></header><section class="my-page__section"><h3>주요 기능 이용 방법</h3><div class="my-page__guide-list">' + features + '</div></section><section class="my-page__section"><h3>자주 묻는 질문 (FAQ)</h3><div class="my-page__faq-list">' + faqs + '</div></section>';
}

function renderLegal() {
    const documents = [
        ['이용약관', '복덩이 서비스 이용에 관한 권리, 의무 및 책임사항을 안내합니다.'],
        ['개인정보 처리방침', '개인정보의 수집·이용·보관 및 처리에 관한 내용을 안내합니다.'],
        ['개인정보 제3자 제공 안내', '맞춤 혜택 제공을 위한 최소한의 정보 제공 및 위탁 사항을 안내합니다.'],
        ['저작권 및 정보 이용 안내', '서비스 내 콘텐츠의 저작권과 공공데이터 이용에 관한 사항을 안내합니다.'],
        ['면책사항', '제공되는 복지 정보는 참고용이며, 실제 신청 및 지원 여부는 해당 기관의 기준에 따릅니다.']
    ].map(([title, description]) => '<button class="my-page__legal-button" type="button" data-legal-document="' + title + '"><span><strong>' + title + '</strong><small>' + description + '</small></span><span aria-hidden="true">›</span></button>').join('');
    return '<header class="my-page__content-header"><div><h2>법적 안내사항</h2><p class="my-page__content-intro">서비스 이용과 관련된 약관 및 개인정보 처리방침을 확인할 수 있어요.</p></div></header><div class="my-page__legal-list">' + documents + '</div><aside class="my-page__notice"><div><strong>안내드립니다</strong><p>복덩이는 공공기관의 복지 정보를 기반으로 제공하는 안내 서비스로, 실제 복지 신청 및 심사는 해당 기관의 규정에 따라 진행됩니다.<br>서비스 이용 전 반드시 최신 공고와 기관의 안내를 확인해 주세요.</p></div></aside>';
}

function renderSection() {
    const target = $('#my-page-content');
    const renderer = { profile: renderProfile, bookmarks: renderBookmarks, guide: renderGuide, legal: renderLegal }[currentSection];
    document.body.dataset.section = currentSection;
    target.innerHTML = renderer();
    target.setAttribute('aria-label', document.querySelector('[data-section="' + currentSection + '"]').textContent.trim());
    document.querySelectorAll('[data-section]').forEach((button) => {
        const active = button.dataset.section === currentSection;
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-selected', String(active));
    });
}

async function loadProfile() {
    const requestId = ++profileLoadRequestId;
    const auth = window.BokdeongiAuth;
    if (!auth) {
        profileLoadState = 'error';
        if (currentSection === 'profile') renderSection();
        return;
    }

    const authStatus = await auth.refreshAuthentication();
    if (requestId !== profileLoadRequestId) return;
    if (authStatus !== 'authenticated') {
        profileLoadState = authStatus === 'unauthenticated' ? 'unauthenticated' : 'error';
        if (currentSection === 'profile') renderSection();
        return;
    }

    const consentPromise = loadConsentStatus({ render: false });
    try {
        const response = await auth.authenticatedFetch('/api/me/profile');
        if (requestId !== profileLoadRequestId) return;
        if (response.status === 401) {
            profileLoadState = 'unauthenticated';
            if (currentSection === 'profile') renderSection();
            return;
        }
        if (!response.ok) {
            profileLoadErrorStatus = response.status;
            profileLoadState = 'error';
            if (currentSection === 'profile') renderSection();
            return;
        }

        const profile = await response.json();
        if (requestId !== profileLoadRequestId) return;
        if (!profile || typeof profile !== 'object' || Array.isArray(profile)) {
            profileLoadState = 'error';
            if (currentSection === 'profile') renderSection();
            return;
        }

        applyProfileResponse(profile);
        profileLoadState = 'loaded';
        void ensureMyPageRegions();
        await consentPromise;
        if (requestId !== profileLoadRequestId) return;
        if (currentSection === 'profile') renderSection();
    } catch (error) {
        if (requestId !== profileLoadRequestId) return;
        profileLoadState = 'error';
        if (currentSection === 'profile') renderSection();
    }
}

function setPasswordChangeFieldError(form, fieldName, message) {
    const input = form.elements[fieldName];
    const messageTarget = form.querySelector('[data-password-error="' + fieldName + '"]');
    if (input) input.setAttribute('aria-invalid', String(Boolean(message)));
    if (messageTarget) messageTarget.textContent = message;
}

function setPasswordChangeStatus(form, message) {
    const status = form.querySelector('[data-password-form-status]');
    if (status) status.textContent = message;
}

function clearPasswordChangeMessages(form) {
    ['currentPassword', 'newPassword', 'newPasswordConfirm'].forEach((fieldName) => setPasswordChangeFieldError(form, fieldName, ''));
    setPasswordChangeStatus(form, '');
}

function validatePasswordChange(form) {
    clearPasswordChangeMessages(form);

    const currentPassword = form.elements.currentPassword.value;
    const newPassword = form.elements.newPassword.value;
    const newPasswordConfirm = form.elements.newPasswordConfirm.value;
    let firstInvalidInput = null;
    const invalidate = (fieldName, message) => {
        setPasswordChangeFieldError(form, fieldName, message);
        if (!firstInvalidInput) firstInvalidInput = form.elements[fieldName];
    };

    if (!currentPassword) invalidate('currentPassword', '현재 비밀번호를 입력해 주세요.');
    if (!newPassword) invalidate('newPassword', '새 비밀번호를 입력해 주세요.');
    else if (newPassword.length < 8 || newPassword.length > 64) invalidate('newPassword', '새 비밀번호는 8~64자로 입력해 주세요.');
    if (!newPasswordConfirm) invalidate('newPasswordConfirm', '새 비밀번호를 다시 입력해 주세요.');
    else if (newPassword && newPassword !== newPasswordConfirm) invalidate('newPasswordConfirm', '새 비밀번호가 일치하지 않습니다.');
    if (currentPassword && newPassword && currentPassword === newPassword) invalidate('newPassword', '현재 비밀번호와 다른 비밀번호를 입력해 주세요.');

    if (firstInvalidInput) {
        firstInvalidInput.focus();
        return null;
    }

    return { currentPassword, newPassword };
}

function setPasswordChangeSubmitting(form, submitting) {
    form.querySelectorAll('input, button').forEach((element) => { element.disabled = submitting; });
    const submit = form.querySelector('[data-password-change-submit]');
    if (submit) submit.textContent = submitting ? '변경 중…' : '비밀번호 변경';
}

async function getResponseErrorCode(response) {
    try {
        const payload = await response.clone().json();
        return payload && typeof payload.code === 'string' ? payload.code : null;
    } catch {
        return null;
    }
}

function redirectToLoginAfterPasswordChange(message) {
    try {
        sessionStorage.setItem('bokdeongi-authentication-notice', message);
    } catch {
        // 로그인 화면의 안내 메시지는 편의 기능이며, 저장소를 사용할 수 없어도 인증 상태는 제거한다.
    }
    window.location.assign('login.html');
}

function getWithdrawalDialog() {
    return document.getElementById('withdrawal-dialog');
}

function setWithdrawalStatus(message) {
    const status = document.getElementById('withdrawal-dialog-status');
    if (status) status.textContent = message;
}

function setWithdrawalSubmitting(submitting) {
    const dialog = getWithdrawalDialog();
    if (!dialog) return;
    dialog.querySelectorAll('button').forEach((button) => { button.disabled = submitting; });
    const confirm = dialog.querySelector('[data-withdrawal-confirm]');
    if (confirm) confirm.textContent = submitting ? '탈퇴 처리 중…' : '회원 탈퇴';
}

function clearUserSessionData() {
    const userSessionKeys = [
        'bokdeongi-signup-account-draft',
        'bokdeongi-signup-consents',
        'bokdeongi-signup-profile',
        'bokdeongi-signup-completion',
        'bokdeongi-authentication-notice'
    ];
    try {
        userSessionKeys.forEach((key) => sessionStorage.removeItem(key));
    } catch {
        // 저장소 정리에 실패해도 서버의 탈퇴 성공 및 인증 상태 초기화는 계속 진행한다.
    }
}

function redirectToLoginAfterWithdrawal(message) {
    try {
        sessionStorage.setItem('bokdeongi-authentication-notice', message);
    } catch {
        // 저장소를 사용할 수 없는 환경에서는 로그인 화면의 안내만 생략한다.
    }
    window.location.assign('login.html');
}

function openWithdrawalDialog(opener) {
    const dialog = getWithdrawalDialog();
    if (!dialog || withdrawalSubmitting) return;
    withdrawalOpener = opener;
    setWithdrawalStatus('');
    setWithdrawalSubmitting(false);
    dialog.showModal();
}

function closeWithdrawalDialog() {
    const dialog = getWithdrawalDialog();
    if (!dialog || withdrawalSubmitting) return;
    dialog.close();
}

async function submitWithdrawal() {
    if (withdrawalSubmitting) return;
    const auth = window.BokdeongiAuth;
    if (!auth || auth.getAuthenticationState() !== 'authenticated') {
        if (auth) auth.clearAuthentication();
        redirectToLoginAfterWithdrawal('로그인 정보가 만료되었어요. 다시 로그인해 주세요.');
        return;
    }

    withdrawalSubmitting = true;
    setWithdrawalStatus('');
    setWithdrawalSubmitting(true);
    try {
        const csrf = await auth.getCsrfToken();
        const response = await auth.authenticatedFetch('/api/auth/me', {
            method: 'DELETE',
            headers: { [csrf.headerName]: csrf.token }
        });

        if (response.status === 204) {
            clearUserSessionData();
            auth.clearAuthentication();
            redirectToLoginAfterWithdrawal('회원 탈퇴가 완료되었습니다. 다시 이용하려면 회원가입해 주세요.');
            return;
        }

        if (response.status === 401) {
            auth.clearAuthentication();
            redirectToLoginAfterWithdrawal('로그인 정보가 만료되었어요. 다시 로그인해 주세요.');
            return;
        }
        if (response.status === 403) {
            setWithdrawalStatus('보안 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        } else if (response.status >= 500) {
            setWithdrawalStatus('서버 오류로 탈퇴 처리 결과를 확인하지 못했습니다. 자동으로 다시 시도하지 않았습니다.');
        } else {
            setWithdrawalStatus('회원 탈퇴를 처리하지 못했습니다. 로그인 상태를 확인한 뒤 다시 시도해 주세요.');
        }
    } catch (error) {
        if (error && error.type === 'unauthenticated') {
            auth.clearAuthentication();
            redirectToLoginAfterWithdrawal('로그인 정보가 만료되었어요. 다시 로그인해 주세요.');
            return;
        }
        if (error && error.type === 'csrf') {
            setWithdrawalStatus('보안 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        } else {
            setWithdrawalStatus('요청 결과를 확인하지 못했습니다. 자동으로 다시 시도하지 않았습니다. 페이지를 새로고침해 로그인 상태를 확인해 주세요.');
        }
    } finally {
        withdrawalSubmitting = false;
        const dialog = getWithdrawalDialog();
        if (dialog && dialog.open) setWithdrawalSubmitting(false);
    }
}

async function submitPasswordChange(form) {
    if (passwordChangeSubmitting) return;
    const request = validatePasswordChange(form);
    if (!request) return;

    const auth = window.BokdeongiAuth;
    if (!auth || auth.getAuthenticationState() !== 'authenticated') {
        redirectToLoginAfterPasswordChange('로그인 정보가 만료되었어요. 다시 로그인해 주세요.');
        return;
    }

    passwordChangeSubmitting = true;
    setPasswordChangeSubmitting(form, true);
    try {
        const csrf = await auth.getCsrfToken();
        const response = await auth.authenticatedFetch('/api/auth/password', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
            body: JSON.stringify(request),
            shouldRefreshOnUnauthorized: async (unauthorizedResponse) => (await getResponseErrorCode(unauthorizedResponse)) !== 'AUTH_002'
        });

        if (response.status === 204) {
            form.reset();
            clearPasswordChangeMessages(form);
            auth.clearAuthentication();
            redirectToLoginAfterPasswordChange('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요.');
            return;
        }

        const errorCode = await getResponseErrorCode(response);
        if (response.status === 401 && errorCode === 'AUTH_002') {
            setPasswordChangeFieldError(form, 'currentPassword', '현재 비밀번호가 올바르지 않습니다.');
        } else if (response.status === 401) {
            auth.clearAuthentication();
            redirectToLoginAfterPasswordChange('로그인 정보가 만료되었어요. 다시 로그인해 주세요.');
            return;
        } else if (response.status === 403) {
            setPasswordChangeStatus(form, '보안 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.');
        } else if (response.status === 400 && errorCode === 'COMMON_001') {
            setPasswordChangeStatus(form, '이 계정은 비밀번호 변경을 지원하지 않거나 현재 비밀번호와 다른 비밀번호가 필요합니다.');
        } else if (response.status === 400) {
            setPasswordChangeStatus(form, '입력값을 확인한 뒤 다시 시도해 주세요.');
        } else if (response.status >= 500) {
            setPasswordChangeStatus(form, '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.');
        } else {
            setPasswordChangeStatus(form, '비밀번호를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
    } catch (error) {
        if (error && error.type === 'unauthenticated') {
            auth.clearAuthentication();
            redirectToLoginAfterPasswordChange('로그인 정보가 만료되었어요. 다시 로그인해 주세요.');
            return;
        }
        setPasswordChangeStatus(form, '네트워크 또는 보안 확인에 실패했습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요.');
    } finally {
        passwordChangeSubmitting = false;
        if (document.body.contains(form)) setPasswordChangeSubmitting(form, false);
    }
}

function currentConsentDocumentVersion(type) {
    // GET의 docVersion은 과거 동의 이력일 수 있으므로 PATCH 요청 버전으로 재사용하지 않는다.
    return window.BokdeongiConsentDocuments?.versionFor(type) || null;
}

function getSensitiveWithdrawalDialog() {
    return document.getElementById('sensitive-withdraw-dialog');
}

function getSensitiveConsentDialog() {
    return document.getElementById('sensitive-consent-dialog');
}

function openSensitiveConsentDialog(opener) {
    if (consentSubmittingType || consentLoadState !== 'loaded') return;
    const dialog = getSensitiveConsentDialog();
    if (!dialog) return;
    const documentInfo = window.BokdeongiConsentDocuments?.get('SENSITIVE');
    const content = dialog.querySelector('[data-sensitive-consent-document]');
    const confirm = dialog.querySelector('[data-sensitive-consent-confirm]');
    if (!documentInfo || !content) {
        if (confirm) confirm.disabled = true;
        showToast('민감정보 동의 문서를 불러오지 못했습니다. 동의는 진행하지 않습니다.');
        return;
    }
    dialog.querySelector('#sensitive-consent-dialog-title').textContent = documentInfo.title;
    window.BokdeongiConsentDocuments.renderInto(content, 'SENSITIVE');
    if (confirm) confirm.disabled = false;
    sensitiveConsentOpener = opener;
    dialog.showModal();
}

function closeSensitiveConsentDialog() {
    const dialog = getSensitiveConsentDialog();
    if (dialog && dialog.open) dialog.close();
}

function openSensitiveWithdrawalDialog(opener) {
    if (consentSubmittingType || !hasVerifiedSensitiveConsent()) return;
    const dialog = getSensitiveWithdrawalDialog();
    if (!dialog) return;
    sensitiveWithdrawalOpener = opener;
    dialog.showModal();
}

function closeSensitiveWithdrawalDialog() {
    const dialog = getSensitiveWithdrawalDialog();
    if (dialog && dialog.open && !consentSubmittingType) dialog.close();
}

function openDocumentConsentDialog(type) {
    if (consentSubmittingType || consentLoadState !== 'loaded' || !['DOC_PROCESS', 'DOC_RECORD'].includes(type)) return;
    const info = window.BokdeongiConsentDocuments?.get(type);
    const dialog = $('#document-consent-dialog');
    if (!info || !dialog) { showToast('동의 문서를 불러오지 못했습니다. 동의는 변경하지 않습니다.'); return; }
    dialog.dataset.consentType = type;
    $('#document-consent-dialog-title').textContent = info.title;
    window.BokdeongiConsentDocuments.renderInto(dialog.querySelector('[data-document-consent-content]'), type);
    const agree = dialog.querySelector('[data-document-consent-agree]');
    agree.hidden = consentState[type]?.agreed === true;
    dialog.showModal();
}

function requestDocumentConsentChange(type, agreed) {
    if (type === 'DOC_PROCESS' && !agreed) {
        if (!window.confirm('동의를 철회하면 이후 서류 업로드 및 자동 검토 기능을 이용할 수 없습니다. 철회할까요?')) return;
    }
    if (type === 'DOC_RECORD' && !agreed) {
        if (!window.confirm('동의를 철회하면 기존에 기억한 서류 발급일 기록이 삭제됩니다. 철회할까요?')) return;
    }
    void changeConsent(type, agreed);
}

async function changeConsent(type, agreed) {
    if (!CONSENT_TYPES.includes(type) || consentLoadState !== 'loaded' || consentSubmittingType) return;
    const docVersion = currentConsentDocumentVersion(type);
    if (!docVersion) {
        showToast('현재 동의 문서 버전을 확인하지 못했습니다. 동의는 변경하지 않습니다.');
        return;
    }
    const auth = window.BokdeongiAuth;
    if (!auth || auth.getAuthenticationState() !== 'authenticated') {
        showToast('로그인 상태를 확인한 뒤 다시 시도해 주세요.');
        return;
    }

    consentSubmittingType = type;
    if (currentSection === 'profile') renderSection();
    try {
        const response = await auth.updateConsent(type, docVersion, agreed);

        if (response.status === 204) {
            const refreshed = await loadConsentStatus({ render: false });
            if (!refreshed) {
                showToast('변경 결과를 다시 확인하지 못했어요. 동의 상태가 확인되기 전에는 저장 완료로 표시하지 않습니다.');
                return;
            }
            if (type === 'SENSITIVE' && !agreed) sensitiveEditState.codes = null;
            showToast(agreed ? '동의 상태를 서버에 저장했어요.' : '동의 철회 결과를 서버에서 다시 확인했어요.');
            return;
        }

        if (response.status === 401) {
            auth.clearAuthentication();
            profileLoadState = 'unauthenticated';
            showToast('로그인 상태가 만료되었어요. 다시 로그인해 주세요.');
        } else if (response.status === 403) {
            showToast('보안 확인에 실패했어요. 잠시 후 다시 시도해 주세요.');
        } else if (response.status === 409) {
            await loadConsentStatus({ render: false });
            showToast('동의 상태가 변경되어 최신 상태를 다시 확인했어요.');
        } else if (response.status === 400) {
            showToast('동의 문서 버전 또는 요청 내용을 확인해 주세요.');
        } else if (response.status >= 500) {
            showToast('서버 오류로 동의 상태를 변경하지 못했어요.');
        } else {
            showToast('동의 상태를 변경하지 못했어요. 다시 시도해 주세요.');
        }
    } catch (error) {
        if (error && error.type === 'unauthenticated') {
            auth.clearAuthentication();
            profileLoadState = 'unauthenticated';
            showToast('로그인 상태가 만료되었어요. 다시 로그인해 주세요.');
        } else if (error && error.type === 'csrf') {
            showToast('보안 확인에 실패했어요. 잠시 후 다시 시도해 주세요.');
        } else {
            showToast('네트워크 오류로 동의 상태를 변경하지 못했어요.');
        }
    } finally {
        consentSubmittingType = null;
        if (currentSection === 'profile') renderSection();
    }
}

async function saveSensitiveCodes(form) {
    if (sensitiveCodesSaving || !hasVerifiedSensitiveConsent()) return;
    const docVersion = currentConsentDocumentVersion('SENSITIVE');
    if (!docVersion) {
        showToast('현재 동의 문서 버전을 확인하지 못했습니다. 저장하지 않습니다.');
        return;
    }
    const sensitiveConsent = consentState.SENSITIVE;
    if (!sensitiveConsent || !sensitiveConsent.consentId) {
        showToast('서버에서 확인한 동의 정보가 없어 저장할 수 없어요. 동의 상태를 다시 확인해 주세요.');
        return;
    }

    const selectedCodes = Array.from(form.querySelectorAll('[name="sensitiveCodes"]:checked'), (input) => input.value);
    if (!selectedCodes.length) {
        showToast('민감정보 항목을 선택하거나 ‘해당 없음’을 선택해 주세요.');
        return;
    }
    const sensitiveCodes = selectedCodes.includes('NONE') ? [] : selectedCodes.filter((code) => SENSITIVE_OPTIONS.some(([allowed]) => allowed === code));
    if (sensitiveCodes.length !== (selectedCodes.includes('NONE') ? 0 : selectedCodes.length)) {
        showToast('허용되지 않은 민감정보 항목이 포함되어 저장할 수 없어요.');
        return;
    }

    const auth = window.BokdeongiAuth;
    if (!auth || auth.getAuthenticationState() !== 'authenticated') {
        showToast('로그인 상태를 확인한 뒤 다시 시도해 주세요.');
        return;
    }

    const sessionId = profileSessionId;
    sensitiveCodesSaving = true;
    const button = form.querySelector('[data-sensitive-codes-save]');
    if (button) { button.disabled = true; button.textContent = '저장 중…'; }
    try {
        const csrf = await auth.getCsrfToken();
        const response = await auth.authenticatedFetch('/api/me/profile/step4', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
            body: JSON.stringify({
                sensitiveAgreed: true,
                consentDocVersion: docVersion,
                consentId: sensitiveConsent.consentId,
                sensitiveCodes
            })
        });
        const payload = await readProfilePayload(response);
        if (sessionId !== profileSessionId) return;

        if (response.status === 200 && payload && typeof payload === 'object' && !Array.isArray(payload)) {
            applyProfileResponse(payload);
            sensitiveEditState.codes = null;
            form.querySelectorAll('[name="sensitiveCodes"]').forEach((input) => { input.checked = false; });
            showToast('민감정보를 서버에 저장했어요. 재판정 결과 반영 시점은 별도로 확인해 주세요.');
            return;
        }

        if (response.status === 409) {
            sensitiveEditState.codes = null;
            form.querySelectorAll('[name="sensitiveCodes"]').forEach((input) => { input.checked = false; });
            await loadConsentStatus({ render: false });
            showToast('동의 상태가 변경되어 저장하지 않았어요. 최신 동의 상태를 다시 확인해 주세요.');
        } else if (response.status === 401) {
            auth.clearAuthentication();
            profileLoadState = 'unauthenticated';
            showToast('로그인 상태가 만료되었어요. 다시 로그인해 주세요.');
        } else if (response.status === 403) {
            showToast('보안 확인에 실패했어요. 잠시 후 다시 시도해 주세요.');
        } else if (response.status === 400) {
            showToast('동의 정보 또는 민감정보 항목을 확인해 주세요.');
        } else if (response.status >= 500) {
            showToast('서버 오류로 민감정보 코드를 저장하지 못했어요.');
        } else {
            showToast('민감정보 코드를 저장하지 못했어요. 다시 시도해 주세요.');
        }
    } catch (error) {
        if (sessionId !== profileSessionId) return;
        if (error && error.type === 'unauthenticated') {
            auth.clearAuthentication();
            profileLoadState = 'unauthenticated';
            showToast('로그인 상태가 만료되었어요. 다시 로그인해 주세요.');
        } else if (error && error.type === 'csrf') {
            showToast('보안 확인에 실패했어요. 잠시 후 다시 시도해 주세요.');
        } else {
            showToast('네트워크 오류로 민감정보 코드를 저장하지 못했어요.');
        }
    } finally {
        sensitiveCodesSaving = false;
        if (document.body.contains(form)) {
            const saveButton = form.querySelector('[data-sensitive-codes-save]');
            if (saveButton) { saveButton.disabled = !hasVerifiedSensitiveConsent(); saveButton.textContent = '민감정보 코드 저장'; }
        }
    }
}

function uniqueProfileCodes(values) {
    return [...new Set(values)];
}

function sameProfileCodes(left, right) {
    const leftCodes = Array.isArray(left) ? uniqueProfileCodes(left) : null;
    const rightCodes = Array.isArray(right) ? uniqueProfileCodes(right) : null;
    return JSON.stringify(leftCodes) === JSON.stringify(rightCodes);
}

function selectedProfileValues(form, name) {
    return Array.from(form.querySelectorAll('[name="' + name + '"]:checked'), (input) => input.value);
}

function profileFormValue(form, name) {
    const field = form.elements[name];
    return field && typeof field.value === 'string' ? field.value.trim() : '';
}

function profileErrorMessage(payload, fallback) {
    if (payload && typeof payload.message === 'string' && payload.message.trim()) return payload.message.trim();
    if (payload && typeof payload.detail === 'string' && payload.detail.trim()) return payload.detail.trim();
    const fieldError = payload && Array.isArray(payload.fieldErrors)
        ? payload.fieldErrors.find((item) => item && typeof item.reason === 'string' && item.reason.trim())
        : null;
    return fieldError ? fieldError.reason.trim() : fallback;
}

function focusProfileError(form, section, name, message) {
    const error = form.querySelector('[data-profile-form-error]');
    if (error) {
        error.textContent = message;
        error.focus();
    }
    profileAccordion[section] = true;
    document.querySelectorAll('[data-profile-accordion-toggle]').forEach((button) => {
        const open = button.dataset.profileAccordionToggle === section || button.getAttribute('aria-expanded') === 'true';
        button.setAttribute('aria-expanded', String(open));
        const panel = $('#' + button.getAttribute('aria-controls'));
        if (panel) panel.hidden = !open;
    });
    const input = form.querySelector('[name="' + name + '"]');
    if (input) input.focus();
}

function setProfileSaving(form, saving) {
    form.querySelectorAll('input, select, button').forEach((element) => { element.disabled = saving; });
    const submit = form.querySelector('[data-profile-save]');
    if (submit) submit.textContent = saving ? '저장 중…' : '저장';
}

function buildProfileDraft(form) {
    const householdSelections = selectedProfileValues(form, 'householdTypes');
    const groupedStatusSelections = STATUS_GROUPS.map(([group]) => selectedProfileValues(form, group));
    const childrenChoice = profileFormValue(form, 'childrenCount');
    const childrenCount = childrenChoice === ''
        ? 0
        : childrenChoice === '4+'
            ? Number(profileFormValue(form, 'childrenExact'))
            : Number(childrenChoice);
    const regionCode = profileFormValue(form, 'regionCode');
    const region = getMyPageRegions()?.findByCode(regionCode);

    return {
        birthDate: profileFormValue(form, 'birthDate'),
        gender: profileFormValue(form, 'gender'),
        regionCode,
        sidoName: region?.sidoName || profileFormValue(form, 'sido'),
        sigunguName: region?.sigunguName || (form.elements.regionCode?.selectedOptions?.[0]?.textContent || '').trim(),
        householdSize: Number(profileFormValue(form, 'householdSize')),
        incomeInputType: profileFormValue(form, 'incomeInputType'),
        incomeAmountText: incomeDigits(profileFormValue(form, 'incomeAmount')),
        medianIncomePctText: profileFormValue(form, 'medianIncomePct'),
        housingType: profileFormValue(form, 'housingType'),
        hasHouse: profileFormValue(form, 'hasHouse'),
        householdTypeCodes: householdSelections.includes('NONE') ? [] : uniqueProfileCodes(householdSelections),
        maritalStatus: profileFormValue(form, 'maritalStatus') || 'ETC',
        childrenCount,
        childrenChoice,
        youngestChildBirthYear: profileFormValue(form, 'youngestYear') === '' ? null : Number(profileFormValue(form, 'youngestYear')),
        statusCodes: uniqueProfileCodes(groupedStatusSelections.flatMap((group) => group.includes('NONE') ? [] : group)),
        welfareStatus: profileFormValue(form, 'welfareStatus') || 'UNKNOWN'
    };
}

function changedProfileSteps(draft) {
    const current = profileResponse;
    if (!current) return { step1: true, step2: true, step3: true };

    const step1 = draft.birthDate !== String(current.birthDate || '')
        || draft.gender !== String(current.gender || '')
        || draft.regionCode !== String(current.regionCode || '');
    const step2 = draft.householdSize !== Number(current.householdSize)
        || draft.incomeInputType !== String(current.incomeInputType || '')
        || draft.housingType !== String(current.housingType || '')
        || (draft.hasHouse === 'true') !== current.hasHouse
        || !sameProfileCodes(draft.householdTypeCodes, current.householdTypeCodes)
        || draft.incomeAmountText !== ''
        || draft.medianIncomePctText !== String(current.medianIncomePct ?? '');
    const step3 = draft.maritalStatus !== String(current.maritalStatus || '')
        || draft.childrenCount !== Number(current.childrenCount)
        || draft.youngestChildBirthYear !== (current.youngestChildBirthYear ?? null)
        || draft.welfareStatus !== String(current.welfareStatus || '')
        || !sameProfileCodes(draft.statusCodes, current.statusCodes);
    return { step1, step2, step3 };
}

function validateProfileDraft(form, draft, changes) {
    if (changes.step1) {
        const birthDate = new Date(draft.birthDate + 'T00:00:00');
        if (!draft.birthDate || Number.isNaN(birthDate.getTime()) || birthDate > new Date()) {
            focusProfileError(form, 'basic', 'birthDate', '생년월일은 오늘 이전 날짜로 입력해 주세요.');
            return false;
        }
        if (!['M', 'F'].includes(draft.gender)) {
            focusProfileError(form, 'basic', 'gender', '성별을 선택해 주세요.');
            return false;
        }
        if (!draft.regionCode || !draft.sidoName || !draft.sigunguName) {
            focusProfileError(form, 'basic', 'regionCode', '서버에 등록된 시·군·구를 선택해 주세요.');
            return false;
        }
    }

    if (changes.step2) {
        if (!Number.isInteger(draft.householdSize) || draft.householdSize < 1) {
            focusProfileError(form, 'household', 'householdSize', '가구원 수는 1명 이상으로 선택해 주세요.');
            return false;
        }
        if (!['OWN', 'JEONSE', 'MONTHLY', 'ETC'].includes(draft.housingType) || !['true', 'false'].includes(draft.hasHouse)) {
            focusProfileError(form, 'household', 'housingType', '주거 형태와 주택 보유 여부를 확인해 주세요.');
            return false;
        }
        if (!['ANNUAL', 'MONTHLY', 'BRACKET', 'UNKNOWN'].includes(draft.incomeInputType)) {
            focusProfileError(form, 'income', 'incomeInputType', '소득 입력 방식을 선택해 주세요.');
            return false;
        }
        if (['ANNUAL', 'MONTHLY'].includes(draft.incomeInputType)) {
            const incomeAmount = Number(draft.incomeAmountText);
            if (!draft.incomeAmountText || !Number.isSafeInteger(incomeAmount) || incomeAmount <= 0) {
                focusProfileError(form, 'income', 'incomeAmount', '소득 금액은 0원보다 큰 값으로 다시 입력해 주세요.');
                return false;
            }
        }
        if (draft.incomeInputType === 'BRACKET') {
            const medianIncomePct = Number(draft.medianIncomePctText);
            if (!draft.medianIncomePctText || !Number.isFinite(medianIncomePct) || medianIncomePct < 0 || medianIncomePct > 9999.9) {
                focusProfileError(form, 'income', 'medianIncomePct', '중위소득 비율을 0~9999.9 사이로 입력해 주세요.');
                return false;
            }
        }
    }

    if (changes.step3) {
        const memberBirthYear = Number(draft.birthDate.slice(0, 4));
        if (!Number.isInteger(draft.childrenCount) || draft.childrenCount < 0) {
            focusProfileError(form, 'additional', 'childrenCount', '자녀 수를 확인해 주세요.');
            return false;
        }
        if (draft.childrenCount === 0 && draft.youngestChildBirthYear !== null) {
            focusProfileError(form, 'additional', 'youngestYear', '자녀가 없으면 막내 자녀 출생연도를 입력할 수 없어요.');
            return false;
        }
        if (draft.childrenCount > 0 && (!Number.isInteger(draft.youngestChildBirthYear)
            || draft.youngestChildBirthYear < 1900
            || draft.youngestChildBirthYear > new Date().getFullYear()
            || (Number.isInteger(memberBirthYear) && draft.youngestChildBirthYear < memberBirthYear))) {
            focusProfileError(form, 'additional', 'youngestYear', '막내 자녀 출생연도를 확인해 주세요.');
            return false;
        }
    }
    return true;
}

function profileStepRequests(draft, changes) {
    const requests = [];
    if (changes.step1) {
        requests.push({
            step: 1,
            body: {
                birthDate: draft.birthDate,
                gender: draft.gender,
                regionCode: draft.regionCode,
                sidoName: draft.sidoName,
                sigunguName: draft.sigunguName,
                name: profileResponse?.name || null
            }
        });
    }
    if (changes.step2) {
        const amountType = ['ANNUAL', 'MONTHLY'].includes(draft.incomeInputType);
        requests.push({
            step: 2,
            body: {
                householdSize: draft.householdSize,
                incomeInputType: draft.incomeInputType,
                incomeAmount: amountType ? Number(draft.incomeAmountText) : null,
                medianIncomePct: draft.incomeInputType === 'BRACKET' ? Number(draft.medianIncomePctText) : null,
                housingType: draft.housingType,
                noHouse: draft.hasHouse === 'false',
                householdTypeCodes: uniqueProfileCodes(draft.householdTypeCodes)
            }
        });
    }
    if (changes.step3) {
        requests.push({
            step: 3,
            body: {
                statusCodes: uniqueProfileCodes(draft.statusCodes),
                maritalStatus: draft.maritalStatus,
                childrenCount: draft.childrenCount,
                youngestChildBirthYear: draft.childrenCount === 0 ? null : draft.youngestChildBirthYear,
                welfareStatus: draft.welfareStatus
            }
        });
    }
    return requests;
}

async function readProfilePayload(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}

async function putProfileStep(auth, request, retriedAfterCsrfFailure = false) {
    const csrf = await auth.getCsrfToken();
    const response = await auth.authenticatedFetch('/api/me/profile/step' + request.step, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify(request.body)
    });
    const payload = await readProfilePayload(response);

    // CSRF 토큰이 회전된 경우에만 같은 요청을 한 번 더 보낸다. 403은 컨트롤러에 도달하지 않으므로
    // 저장 중복이 발생하지 않으며, 두 번째 403 이후에는 재시도하지 않는다.
    if (response.status === 403 && !retriedAfterCsrfFailure) {
        return putProfileStep(auth, request, true);
    }
    return { response, payload };
}

async function saveProfile(form) {
    if (profileSaving || !profileResponse) return;
    const sessionId = profileSessionId;
    const draft = buildProfileDraft(form);
    const changes = changedProfileSteps(draft);
    if (!changes.step1 && !changes.step2 && !changes.step3) {
        profileMode = 'view';
        renderSection();
        showToast('변경된 프로필 정보가 없어요.');
        return;
    }
    if (!validateProfileDraft(form, draft, changes)) return;

    const auth = window.BokdeongiAuth;
    if (!auth || auth.getAuthenticationState() !== 'authenticated') {
        focusProfileError(form, 'basic', 'birthDate', '로그인 상태를 확인한 뒤 다시 시도해 주세요.');
        return;
    }

    profileSaving = true;
    setProfileSaving(form, true);
    const error = form.querySelector('[data-profile-form-error]');
    if (error) error.textContent = '';
    const savedSteps = [];
    try {
        for (const request of profileStepRequests(draft, changes)) {
            const { response, payload } = await putProfileStep(auth, request);
            if (sessionId !== profileSessionId) return;
            if (!response.ok || !payload || typeof payload !== 'object' || Array.isArray(payload)) {
                const failure = new Error(profileErrorMessage(payload, '프로필 ' + request.step + '단계를 저장하지 못했어요.'));
                failure.status = response.status;
                throw failure;
            }
            // 앞 단계가 저장된 뒤 다음 단계가 실패해도, 재시도 시 이미 성공한 단계를 다시 저장하지 않는다.
            profileResponse = {
                ...payload,
                householdTypeCodes: Array.isArray(payload.householdTypeCodes) ? [...payload.householdTypeCodes] : null,
                statusCodes: Array.isArray(payload.statusCodes) ? [...payload.statusCodes] : null
            };
            savedSteps.push(request.step);
        }

        const refreshed = await auth.authenticatedFetch('/api/me/profile');
        if (sessionId !== profileSessionId) return;
        const profile = await readProfilePayload(refreshed);
        if (sessionId !== profileSessionId) return;
        if (!refreshed.ok || !profile || typeof profile !== 'object' || Array.isArray(profile)) {
            const failure = new Error(profileErrorMessage(profile, '저장 결과를 다시 불러오지 못했어요. 새로고침 후 확인해 주세요.'));
            failure.status = refreshed.status;
            throw failure;
        }

        applyProfileResponse(profile);
        profileLoadState = 'loaded';
        profileLoadErrorStatus = null;
        profileMode = 'view';
        renderSection();
        showToast('프로필을 서버에 저장했어요.');
    } catch (failure) {
        if (sessionId !== profileSessionId) return;
        const status = failure && Number.isInteger(failure.status) ? failure.status : null;
        let message = failure?.message || '네트워크 오류로 프로필을 저장하지 못했어요. 입력한 내용을 확인한 뒤 다시 시도해 주세요.';
        if (status === 401) message = '로그인 상태가 만료되었어요. 입력한 내용을 확인한 뒤 다시 로그인해 주세요.';
        else if (status === 403) message = '보안 확인에 실패했어요. 잠시 후 다시 시도해 주세요.';
        else if (status === 404) message = '프로필 정보를 찾을 수 없어요. 로그인 상태를 확인해 주세요.';
        else if (status !== null && status >= 500) message = '서버 오류로 저장 결과를 확인하지 못했어요. 입력한 내용은 유지되어 있어요.';
        if (savedSteps.length) {
            message = '프로필 ' + savedSteps.join(', ') + '단계는 저장되었지만 이후 처리에 실패했어요. ' + message;
        }
        if (error) {
            error.textContent = message;
            error.focus();
        }
    } finally {
        profileSaving = false;
        if (document.body.contains(form)) setProfileSaving(form, false);
    }
}

function setupInteractions() {
    const withdrawalDialog = getWithdrawalDialog();
    if (withdrawalDialog) {
        withdrawalDialog.addEventListener('cancel', (event) => {
            if (withdrawalSubmitting) event.preventDefault();
        });
        withdrawalDialog.addEventListener('close', () => {
            if (!withdrawalSubmitting && withdrawalOpener) withdrawalOpener.focus();
            withdrawalOpener = null;
        });
    }
    const sensitiveWithdrawalDialog = getSensitiveWithdrawalDialog();
    if (sensitiveWithdrawalDialog) {
        sensitiveWithdrawalDialog.addEventListener('cancel', (event) => {
            if (consentSubmittingType) event.preventDefault();
        });
        sensitiveWithdrawalDialog.addEventListener('close', () => {
            if (!consentSubmittingType && sensitiveWithdrawalOpener) sensitiveWithdrawalOpener.focus();
            sensitiveWithdrawalOpener = null;
        });
    }
    const sensitiveConsentDialog = getSensitiveConsentDialog();
    if (sensitiveConsentDialog) {
        sensitiveConsentDialog.addEventListener('close', () => {
            if (sensitiveConsentOpener) sensitiveConsentOpener.focus();
            sensitiveConsentOpener = null;
        });
    }
    document.addEventListener('click', (event) => {
        if (event.target.closest('[data-document-consent-close]')) { $('#document-consent-dialog').close(); return; }
        if (event.target.closest('[data-document-consent-view]')) { openDocumentConsentDialog(event.target.closest('[data-document-consent-view]').dataset.documentConsentView); return; }
        const documentConsentChange = event.target.closest('[data-document-consent-change]');
        if (documentConsentChange) {
            const [type, agreed] = documentConsentChange.dataset.documentConsentChange.split(':');
            if (agreed === 'true') openDocumentConsentDialog(type);
            else requestDocumentConsentChange(type, false);
            return;
        }
        const documentConsentAgree = event.target.closest('[data-document-consent-agree]');
        if (documentConsentAgree) {
            const dialog = $('#document-consent-dialog');
            const type = dialog.dataset.consentType;
            documentConsentAgree.disabled = true;
            void changeConsent(type, true).finally(() => { documentConsentAgree.disabled = false; if (consentState[type]?.agreed === true) dialog.close(); });
            return;
        }
        if (event.target.closest('[data-withdrawal-close]')) { closeWithdrawalDialog(); return; }
        if (event.target.closest('[data-withdrawal-confirm]')) { submitWithdrawal(); return; }
        if (event.target.closest('[data-sensitive-withdraw-close]')) { closeSensitiveWithdrawalDialog(); return; }
        if (event.target.closest('[data-sensitive-withdraw-confirm]')) {
            closeSensitiveWithdrawalDialog();
            void changeConsent('SENSITIVE', false);
            return;
        }
        if (event.target.closest('[data-sensitive-consent-close]')) { closeSensitiveConsentDialog(); return; }
        const sensitiveConsentConfirm = event.target.closest('[data-sensitive-consent-confirm]');
        if (sensitiveConsentConfirm) {
            sensitiveConsentConfirm.disabled = true;
            closeSensitiveConsentDialog();
            void changeConsent('SENSITIVE', true);
            return;
        }
        const sensitiveConsentStart = event.target.closest('[data-sensitive-consent-start]');
        if (sensitiveConsentStart) { openSensitiveConsentDialog(sensitiveConsentStart); return; }
        const sensitiveWithdrawStart = event.target.closest('[data-sensitive-withdraw-start]');
        if (sensitiveWithdrawStart) { openSensitiveWithdrawalDialog(sensitiveWithdrawStart); return; }
        const optionalConsentChange = event.target.closest('[data-optional-consent-change]');
        if (optionalConsentChange) {
            void changeConsent('OPTIONAL_INFO', optionalConsentChange.dataset.optionalConsentChange === 'true');
            return;
        }
        if (event.target.closest('[data-consent-retry]')) { void loadConsentStatus(); return; }
        if (event.target.closest('[data-consent-back]')) { profileMode = 'view'; renderSection(); return; }
        if (event.target.closest('[data-open-consent-management]')) {
            profileMode = 'consents';
            consentLoadState = 'checking';
            renderSection();
            void loadConsentStatus();
            return;
        }
        const sensitiveCodesSave = event.target.closest('[data-sensitive-codes-save]');
        if (sensitiveCodesSave) {
            const form = sensitiveCodesSave.closest('[data-profile-form]');
            if (form) void saveSensitiveCodes(form);
            return;
        }
        if (event.target.closest('[data-sensitive-consent-view]')) { openSensitiveConsentDialog(event.target.closest('[data-sensitive-consent-view]')); return; }
        if (event.target.closest('[data-profile-region-retry]')) { void ensureMyPageRegions(true); return; }
        const accordionToggle = event.target.closest('[data-profile-accordion-toggle]');
        if (accordionToggle) {
            const key = accordionToggle.dataset.profileAccordionToggle;
            const expanded = accordionToggle.getAttribute('aria-expanded') === 'true';
            profileAccordion[key] = !expanded;
            accordionToggle.setAttribute('aria-expanded', String(!expanded));
            const panel = $('#' + accordionToggle.getAttribute('aria-controls'));
            if (panel) panel.hidden = expanded;
            return;
        }
        const sectionButton = event.target.closest('.my-page__menu-button[data-section]');
        if (sectionButton) {
            currentSection = sectionButton.dataset.section;
            profileMode = 'view';
            renderSection();
            if (currentSection === 'bookmarks') void loadFavorites();
            return;
        }
        if (event.target.closest('[data-retry-favorites]')) { void loadFavorites(); return; }
        const removeBookmark = event.target.closest('[data-remove-bookmark]');
        if (removeBookmark) { void removeFavorite(removeBookmark.dataset.removeBookmark); return; }
        const faq = event.target.closest('[data-faq]');
        if (faq) { const answer = $('#' + faq.getAttribute('aria-controls')); const expanded = faq.getAttribute('aria-expanded') === 'true'; faq.setAttribute('aria-expanded', String(!expanded)); answer.hidden = expanded; return; }
        const guideFeature = event.target.closest('[data-guide-feature]');
        if (guideFeature) {
            const expanded = guideFeature.getAttribute('aria-expanded') === 'true';
            document.querySelectorAll('[data-guide-feature]').forEach((button) => {
                button.setAttribute('aria-expanded', 'false');
                const answer = $('#' + button.getAttribute('aria-controls'));
                if (answer) answer.hidden = true;
            });
            if (!expanded) {
                guideFeature.setAttribute('aria-expanded', 'true');
                const answer = $('#' + guideFeature.getAttribute('aria-controls'));
                if (answer) answer.hidden = false;
            }
            return;
        }
        if (event.target.closest('[data-profile-edit]')) { Object.assign(profileAccordion, { basic: true, household: false, income: false, additional: false, sensitive: false }); sensitiveEditState = { codes: null }; profileMode = 'edit'; renderSection(); if (regionLoadState !== 'ready') void ensureMyPageRegions(); return; }
        if (event.target.closest('[data-profile-cancel]')) { profileMode = 'view'; renderSection(); return; }
        if (event.target.closest('[data-password-change-cancel]')) { passwordChangeSubmitting = false; profileMode = 'view'; renderSection(); return; }
        const accountAction = event.target.closest('[data-account-action]');
        if (accountAction) {
            if (accountAction.dataset.accountAction === '개인정보 동의 관리') {
                profileMode = 'consents';
                consentLoadState = 'checking';
                renderSection();
                void loadConsentStatus();
                return;
            }
            if (accountAction.dataset.accountAction === '비밀번호 변경') {
                passwordChangeSubmitting = false;
                profileMode = 'password';
                renderSection();
                return;
            }
            if (accountAction.dataset.accountAction === '회원 탈퇴') {
                openWithdrawalDialog(accountAction);
                return;
            }
            showToast(accountAction.dataset.accountAction + ' 기능은 추후 계정 관리 기능과 연동될 예정입니다.');
            return;
        }
        const legalDocument = event.target.closest('[data-legal-document]');
        if (legalDocument) showToast(legalDocument.dataset.legalDocument + ' 문서는 확정본 연결 후 확인할 수 있어요.');
    });
    document.addEventListener('input', (event) => {
        const profileForm = event.target.closest('[data-profile-form]');
        if (profileForm && event.target.name === 'incomeAmount') {
            formatIncomeAmount(event.target);
        }

        const passwordForm = event.target.closest('[data-password-change-form]');
        if (passwordForm && event.target.name && ['currentPassword', 'newPassword', 'newPasswordConfirm'].includes(event.target.name)) {
            setPasswordChangeFieldError(passwordForm, event.target.name, '');
            setPasswordChangeStatus(passwordForm, '');
        }
    });
    document.addEventListener('change', (event) => {
        if (event.target.matches('[data-favorite-sort]')) {
            const nextSort = event.target.value;
            if (nextSort !== favoriteSort && ['DEADLINE', 'SAVED'].includes(nextSort)) {
                bookmarkPage = 1;
                void loadFavorites(nextSort);
            }
            return;
        }
        const form = event.target.closest('[data-profile-form]');
        if (!form) return;
        if (event.target.name === 'sido') {
            const sigungu = form.elements.regionCode;
            const options = getMyPageRegions()?.selectableFor(event.target.value) || [];
            const sejong = options.length === 1 && options[0].regionCode === '36110' && options[0].sigunguName === null;
            if (sejong) {
                sigungu.replaceChildren(new Option(options[0].sidoName, options[0].regionCode));
                sigungu.disabled = false;
                return;
            }
            sigungu.replaceChildren(new Option(options.length ? '시·군·구 선택' : '시·도를 먼저 선택해 주세요.', ''));
            options.forEach((region) => sigungu.add(new Option(region.sigunguName || region.sidoName, region.regionCode)));
            sigungu.disabled = !options.length;
        }
        if (event.target.name === 'incomeInputType') {
            const amountVisible = ['ANNUAL', 'MONTHLY'].includes(event.target.value);
            const bracketVisible = event.target.value === 'BRACKET';
            const amountFields = form.querySelector('[data-income-amount-fields]');
            const bracketFields = form.querySelector('[data-income-bracket-fields]');
            if (amountFields) {
                amountFields.hidden = !amountVisible;
                amountFields.querySelectorAll('input:not([readonly])').forEach((input) => {
                    input.disabled = !amountVisible;
                    input.required = amountVisible;
                    if (!amountVisible) input.value = '';
                });
            }
            if (bracketFields) {
                bracketFields.hidden = !bracketVisible;
                bracketFields.querySelectorAll('input').forEach((input) => {
                    input.disabled = !bracketVisible;
                    input.required = bracketVisible;
                    if (!bracketVisible) input.value = '';
                });
            }
        }
        if (event.target.name === 'childrenCount') {
            const visible = event.target.value && event.target.value !== '0';
            const field = form.querySelector('[data-youngest-field]');
            const input = field.querySelector('input');
            field.hidden = !visible; input.disabled = !visible; input.required = visible; if (!visible) input.value = '';
            const exact = form.querySelector('[data-exact-children-field]');
            const exactInput = exact.querySelector('input');
            const exactVisible = event.target.value === '4+';
            exact.hidden = !exactVisible; exactInput.disabled = !exactVisible; exactInput.required = exactVisible; if (!exactVisible) exactInput.value = '';
        }
        const group = event.target.closest('[data-exclusive-group]');
        if (group && event.target.matches('input[type="checkbox"]')) {
            const none = group.querySelector('[data-none]');
            const others = group.querySelectorAll('input:not([data-none])');
            if (event.target.dataset.none !== undefined && event.target.checked) others.forEach((input) => { input.checked = false; });
            if (event.target.dataset.none === undefined && event.target.checked) {
                if (none) none.checked = false;
                if (STATUS_GROUPS.some(([name]) => name === group.dataset.exclusiveGroup)) {
                    others.forEach((input) => { if (input !== event.target) input.checked = false; });
                }
            }
        }
    });
    document.addEventListener('submit', (event) => {
        const passwordChangeForm = event.target.closest('[data-password-change-form]');
        if (passwordChangeForm) { event.preventDefault(); submitPasswordChange(passwordChangeForm); return; }
        const profileForm = event.target.closest('[data-profile-form]');
        if (profileForm) { event.preventDefault(); saveProfile(profileForm); }
    });
    document.addEventListener('bokdeongi:pagination', (event) => {
        if (currentSection !== 'bookmarks') return;
        bookmarkPage = event.detail.page;
        renderSection();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    setupInteractions();
    renderSection();
    loadProfile();
});

if (typeof window.addEventListener === 'function') {
    window.addEventListener('bokdeongi:auth-state-change', handleMyPageAuthenticationChange);
}
