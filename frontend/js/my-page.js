const myPageData = {
    profile: {
        basics: [['♙', '이름', '김복덩'], ['▣', '생년월일', '2004.11.04'], ['⌖', '거주지역', '부산광역시'], ['✉', '이메일', 'bokdeongi@email.com']],
        settings: [['♧', '가구 형태', '1인 가구'], ['⌂', '주거 형태', '월세'], ['▤', '소득 정보', '중위소득 150% 이하']]
    },
    bookmarks: [
        { id: 'monthly-rent', icon: '⌂', title: '청년 월세 지원', category: '주거·생활', status: '해당', deadline: 'D-12', description: '청년의 주거비 부담을 낮추기 위해 월세를 지원하는 제도예요.' },
        { id: 'exam-fee', icon: '▱', title: '국가기술자격 응시료 지원', category: '취업·교육', status: '조건부', deadline: 'D-30', description: '청년의 국가기술자격 시험 응시료 일부를 지원해요.' },
        { id: 'culture-card', icon: '▣', title: '문화누리카드 지원', category: '문화·여가', status: '해당', deadline: '상시', description: '문화·예술·여행·체육 활동을 위한 문화누리카드를 지원해요.' }
    ],
    keywords: ['청년', '지원금', '주거지원', '복지'],
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
let profileInterests = ['주거·생활', '취업·교육'];
let bookmarkPage = 1;
const BOOKMARK_PAGE_SIZE = 4;
const PROFILE_REGIONS = {
    '서울특별시': [['종로구', '11110'], ['강남구', '11680'], ['마포구', '11440']], '부산광역시': [['중구', '26110'], ['해운대구', '26350'], ['사하구', '26380']],
    '대구광역시': [['중구', '27110'], ['수성구', '27260'], ['달성군', '27710']], '인천광역시': [['중구', '28110'], ['남동구', '28200'], ['서구', '28260']],
    '광주광역시': [['동구', '29110'], ['북구', '29170'], ['광산구', '29200']], '대전광역시': [['동구', '30110'], ['서구', '30170'], ['유성구', '30200']],
    '울산광역시': [['중구', '31110'], ['남구', '31140'], ['울주군', '31710']], '세종특별자치시': [['세종특별자치시', '36110']],
    '경기도': [['수원시', '41110'], ['성남시', '41130'], ['고양시', '41280']], '강원특별자치도': [['춘천시', '42110'], ['원주시', '42130'], ['강릉시', '42150']],
    '충청북도': [['청주시', '43110'], ['충주시', '43130'], ['제천시', '43150']], '충청남도': [['천안시', '44130'], ['공주시', '44150'], ['아산시', '44200']],
    '전북특별자치도': [['전주시', '45110'], ['군산시', '45130'], ['익산시', '45140']], '전라남도': [['목포시', '46110'], ['여수시', '46130'], ['순천시', '46150']],
    '경상북도': [['포항시', '47110'], ['경주시', '47130'], ['구미시', '47190']], '경상남도': [['창원시', '48120'], ['진주시', '48170'], ['김해시', '48250']],
    '제주특별자치도': [['제주시', '50110'], ['서귀포시', '50130']]
};
const profileAccordion = { basic: true, household: false, income: false, additional: false, interests: false, sensitive: false };
let profileEditorState = {
    gender: '', sido: '부산광역시', sigunguName: '', regionCode: '', householdSize: '1', housingType: 'MONTHLY', hasHouse: '', incomeInputType: '', incomeAmount: '', incomeBaseYear: String(new Date().getFullYear()),
    maritalStatus: '', childrenCount: '', childrenExact: '', youngestYear: '', householdTypes: null, statusCodes: null, welfareStatus: ''
};
const SENSITIVE_CONSENT_STORAGE_KEY = 'bokdeongi-signup-consents';
const SENSITIVE_OPTIONS = [
    ['JA0301', '예비부모·난임', '임신·출산 관련 지원 안내에 활용합니다.'], ['JA0302', '임산부', '임신 중 받을 수 있는 지원 안내에 활용합니다.'],
    ['JA0303', '출산·입양', '출산·입양 관련 지원 안내에 활용합니다.'], ['JA0328', '장애 관련', '장애 관련 지원 안내에 활용합니다.'],
    ['JA0329', '보훈 관련', '보훈 관련 지원 안내에 활용합니다.'], ['JA0330', '질병 관련', '질병 관련 지원 안내에 활용합니다.'],
    ['JA0401', '다문화 가구', '다문화 가구 지원 안내에 활용합니다.'], ['JA0402', '북한이탈주민', '북한이탈주민 지원 안내에 활용합니다.']
];
function initialSensitiveConsent() { try { return (JSON.parse(sessionStorage.getItem(SENSITIVE_CONSENT_STORAGE_KEY)) || []).some((consent) => consent.consent_type === 'SENSITIVE' && consent.agreed === true); } catch (error) { return false; } }
let sensitiveState = { consent: initialSensitiveConsent(), codes: null };
let sensitiveEditState = { consent: sensitiveState.consent, codes: sensitiveState.codes };

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
    const item = myPageData.profile[group].find(([, itemLabel]) => itemLabel === label);
    return item ? item[2] : '';
}

function setProfileValue(group, label, value) {
    const item = myPageData.profile[group].find(([, itemLabel]) => itemLabel === label);
    if (item) item[2] = value;
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
    if (profile.incomeInputType === 'ANNUAL') return '연소득 입력됨';
    if (profile.incomeInputType === 'MONTHLY') return '월소득 입력됨';
    return '미입력';
}

function applyProfileResponse(profile) {
    setProfileValue('basics', '이름', displayValue(profile.name));
    setProfileValue('basics', '생년월일', formatBirthDate(profile.birthDate));
    setProfileValue('basics', '거주지역', formatRegion(profile));
    setProfileValue('basics', '이메일', displayValue(profile.email));

    setProfileValue('settings', '가구 형태', profile.householdSize === null || profile.householdSize === undefined ? '미입력' : profile.householdSize + '인 가구');
    setProfileValue('settings', '주거 형태', { OWN: '자가', JEONSE: '전세', MONTHLY: '월세', ETC: '기타' }[profile.housingType] || '미입력');
    setProfileValue('settings', '소득 정보', formatIncome(profile));

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
        maritalStatus: displayValue(profile.maritalStatus, ''),
        childrenCount: profile.childrenCount === null || profile.childrenCount === undefined ? '' : profile.childrenCount >= 4 ? '4+' : String(profile.childrenCount),
        childrenExact: profile.childrenCount !== null && profile.childrenCount !== undefined && profile.childrenCount >= 4 ? String(profile.childrenCount) : '',
        youngestYear: profile.youngestChildBirthYear === null || profile.youngestChildBirthYear === undefined ? '' : String(profile.youngestChildBirthYear),
        householdTypes: Array.isArray(profile.householdTypeCodes) ? [...profile.householdTypeCodes] : null,
        statusCodes: Array.isArray(profile.statusCodes) ? [...profile.statusCodes] : null,
        welfareStatus: displayValue(profile.welfareStatus, '')
    };
    sensitiveState = { consent: false, codes: null };
    profileCompleted = profile.profileCompleted === true;
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
    const interests = profileInterests.map((interest) => '<span class="my-page__topic-tag">' + escapeHtml(interest) + '</span>').join('');
    const profileStatus = profileCompleted ? '프로필 등록 완료' : '프로필 입력 중';
    return '<header class="my-page__content-header"><h2>내 정보</h2><span class="my-page__status">' + profileStatus + '</span></header>' +
        '<section class="my-page__section"><h3>기본 정보</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.basics) + '</div></section>' +
        '<section class="my-page__section"><h3>맞춤 혜택 프로필</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.settings) +
        '<div class="my-page__info-item"><span class="my-page__info-label">관심 분야</span><span class="my-page__topic-list">' + interests + '</span></div></div><div class="my-page__profile-action"><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-profile-edit>프로필 수정하기 <span aria-hidden="true">›</span></button></div></section>' +
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

function renderProfileEditor() {
    const birthDate = profileValue('basics', '생년월일').replaceAll('.', '-');
    const field = (label, name, value, options = '') => '<label class="my-page__form-control"><span>' + label + (options.includes('required') ? ' <em aria-hidden="true">*</em>' : '') + '</span><input name="' + name + '" value="' + escapeHtml(value) + '" ' + options + '></label>';
    const checked = (interest) => profileInterests.includes(interest) ? ' checked' : '';
    const selected = (value, expected) => value === expected ? ' selected' : '';
    const radio = (name, value, label, state) => '<label><input type="radio" name="' + name + '" value="' + value + '"' + (state === value ? ' checked' : '') + '> ' + label + '</label>';
    const checks = (group, items, values) => items.map(([value, label, none]) => '<label><input type="checkbox" value="' + value + '"' + (none ? ' data-none' : '') + (values && (none ? values.length === 0 : values.includes(value)) ? ' checked' : '') + '> ' + label + '</label>').join('');
    const accordion = (key, title, content) => '<section class="my-page__profile-accordion" data-profile-accordion="' + key + '"><button class="my-page__profile-accordion-button" type="button" aria-expanded="' + profileAccordion[key] + '" aria-controls="profile-panel-' + key + '" data-profile-accordion-toggle="' + key + '"><span>' + title + '</span><span aria-hidden="true">⌄</span></button><div class="my-page__profile-accordion-panel" id="profile-panel-' + key + '"' + (profileAccordion[key] ? '' : ' hidden') + '>' + content + '</div></section>';
    const regionNames = Object.keys(PROFILE_REGIONS);
    if (profileEditorState.sido && !regionNames.includes(profileEditorState.sido)) regionNames.push(profileEditorState.sido);
    const regionOptions = regionNames.map((name) => '<option value="' + escapeHtml(name) + '"' + selected(profileEditorState.sido, name) + '>' + escapeHtml(name) + '</option>').join('');
    const sigunguRegions = [...(PROFILE_REGIONS[profileEditorState.sido] || [])];
    if (profileEditorState.regionCode && !sigunguRegions.some(([, code]) => code === profileEditorState.regionCode)) {
        sigunguRegions.push([profileEditorState.sigunguName || profileEditorState.regionCode, profileEditorState.regionCode]);
    }
    const sigunguOptions = sigunguRegions.map(([name, code]) => '<option value="' + escapeHtml(code) + '"' + selected(profileEditorState.regionCode, code) + '>' + escapeHtml(name) + '</option>').join('');
    const currentYear = new Date().getFullYear();
    const incomeVisible = ['ANNUAL', 'MONTHLY'].includes(profileEditorState.incomeInputType);
    const childVisible = profileEditorState.childrenCount && profileEditorState.childrenCount !== '0';
    const exactChildrenVisible = profileEditorState.childrenCount === '4+';
    const sensitiveCodes = sensitiveEditState.codes;
    const sensitiveOptions = SENSITIVE_OPTIONS.map(([code, label, description]) => '<label><input type="checkbox" name="sensitiveCodes" value="' + code + '"' + (sensitiveCodes && sensitiveCodes.includes(code) ? ' checked' : '') + (sensitiveEditState.consent ? '' : ' disabled') + '><span><strong>' + label + '</strong><small>' + description + '</small></span></label>').join('') + '<label class="my-page__sensitive-none"><input type="checkbox" name="sensitiveCodes" value="NONE" data-none' + (Array.isArray(sensitiveCodes) && sensitiveCodes.length === 0 ? ' checked' : '') + (sensitiveEditState.consent ? '' : ' disabled') + '><span><strong>해당 없음</strong><small>관련 항목이 없음을 명시합니다.</small></span></label>';
    const sensitiveContent = '<p class="my-page__sensitive-intro">민감정보는 관련 복지 혜택을 더 정확하게 안내하기 위해 사용됩니다. 입력은 선택 사항이며, 동의 여부와 입력 내용은 변경할 수 있습니다.</p><div class="my-page__sensitive-consent"><label><input type="checkbox" data-sensitive-consent' + (sensitiveEditState.consent ? ' checked' : '') + '> 민감정보 수집·이용 동의 <small>(선택)</small></label><button class="my-page__sensitive-view" type="button" data-sensitive-consent-view>내용 보기</button></div><p class="my-page__sensitive-notice" data-sensitive-reason' + (sensitiveEditState.consent ? ' hidden' : '') + '>동의하지 않으면 민감정보를 입력할 수 없어요. 동의 후에도 입력하지 않고 건너뛸 수 있습니다.</p><div class="my-page__sensitive-options" data-exclusive-group="sensitiveCodes"' + (sensitiveEditState.consent ? '' : ' hidden') + '>' + sensitiveOptions + '</div><div class="my-page__sensitive-withdraw" data-sensitive-withdraw hidden><p>동의를 철회하면 민감정보 선택값이 이 화면에서 해제되며, 추천 혜택이 달라질 수 있어요. 실제 삭제·철회는 저장 API 연동 후 처리됩니다.</p><div><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-sensitive-withdraw-cancel>취소</button><button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-sensitive-withdraw-confirm>동의 철회 확인</button></div></div>';
    return '<header class="my-page__content-header"><div><h2>프로필 수정</h2><p class="my-page__content-intro">변경 내용은 현재 화면에서만 임시로 반영되며, 새로고침하면 초기값으로 돌아갑니다.</p></div><span class="my-page__status">' + (profileCompleted ? '프로필 등록 완료' : '프로필 입력 중') + '</span></header>' +
        '<form class="my-page__profile-form" data-profile-form novalidate>' +
        '<section class="my-page__profile-form-section"><div class="my-page__section-heading"><h3>계정 고유 정보</h3><p>계정 정보는 이 화면에서 변경할 수 없어요.</p></div><div class="my-page__profile-form-grid">' +
        field('이름', 'name', profileValue('basics', '이름'), 'type="text" disabled') +
        field('이메일', 'email', profileValue('basics', '이메일'), 'type="email" disabled') +
        '</div></section>' +
        accordion('basic', '기본 정보', '<div class="my-page__profile-form-grid">' + field('생년월일', 'birthDate', birthDate, 'type="date" required max="' + new Date().toISOString().slice(0, 10) + '"') + '<fieldset class="my-page__choice-field"><legend>성별 <em aria-hidden="true">*</em></legend><div>' + radio('gender', 'M', '남성', profileEditorState.gender) + radio('gender', 'F', '여성', profileEditorState.gender) + '</div></fieldset><label class="my-page__form-control"><span>시·도 <em aria-hidden="true">*</em></span><select name="sido" required><option value="">시·도 선택</option>' + regionOptions + '</select></label><label class="my-page__form-control"><span>시·군·구 <em aria-hidden="true">*</em></span><select name="regionCode" required' + (profileEditorState.sido ? '' : ' disabled') + '><option value="">' + (profileEditorState.sido ? '시·군·구 선택' : '시·도를 먼저 선택해 주세요.') + '</option>' + sigunguOptions + '</select></label></div><p class="my-page__form-help">상세 주소와 우편번호는 수집하지 않아요.</p>') +
        accordion('household', '가구 및 주거 정보', '<div class="my-page__profile-form-grid"><label class="my-page__form-control"><span>가구원 수 <em aria-hidden="true">*</em></span><select name="householdSize" required><option value="">선택해 주세요.</option>' + ['1', '2', '3', '4', '5'].map((value) => '<option value="' + value + '"' + selected(profileEditorState.householdSize, value) + '>' + (value === '5' ? '5명 이상' : value + '명') + '</option>').join('') + '</select></label><fieldset class="my-page__choice-field"><legend>주거 형태 <em aria-hidden="true">*</em></legend><div>' + radio('housingType', 'OWN', '자가', profileEditorState.housingType) + radio('housingType', 'JEONSE', '전세', profileEditorState.housingType) + radio('housingType', 'MONTHLY', '월세', profileEditorState.housingType) + radio('housingType', 'ETC', '기타', profileEditorState.housingType) + '</div></fieldset><fieldset class="my-page__choice-field"><legend>주택 보유 여부 <em aria-hidden="true">*</em></legend><div>' + radio('hasHouse', 'true', '보유', profileEditorState.hasHouse) + radio('hasHouse', 'false', '미보유', profileEditorState.hasHouse) + '</div></fieldset></div>') +
        accordion('income', '소득 정보', '<p class="my-page__form-help">입력한 금액은 현재 화면에서만 임시로 보관됩니다.</p><fieldset class="my-page__choice-field"><legend>입력 방식 <em aria-hidden="true">*</em></legend><div>' + radio('incomeInputType', 'ANNUAL', '연소득', profileEditorState.incomeInputType) + radio('incomeInputType', 'MONTHLY', '월소득', profileEditorState.incomeInputType) + radio('incomeInputType', 'UNKNOWN', '잘 모르겠어요', profileEditorState.incomeInputType) + '</div></fieldset><div class="my-page__profile-form-grid" data-income-fields' + (incomeVisible ? '' : ' hidden') + '>' + field('소득 금액', 'incomeAmount', profileEditorState.incomeAmount, 'type="number" min="0" inputmode="numeric"' + (incomeVisible ? ' required' : ' disabled')) + field('소득 기준 연도', 'incomeBaseYear', profileEditorState.incomeBaseYear, 'type="number" min="2000" max="' + currentYear + '" inputmode="numeric"' + (incomeVisible ? ' required' : ' disabled')) + '</div>') +
        accordion('additional', '추가 정보', '<div class="my-page__profile-form-grid"><fieldset class="my-page__choice-field"><legend>혼인 여부 <em aria-hidden="true">*</em></legend><div>' + radio('maritalStatus', 'SINGLE', '미혼', profileEditorState.maritalStatus) + radio('maritalStatus', 'MARRIED', '기혼', profileEditorState.maritalStatus) + radio('maritalStatus', 'ETC', '기타', profileEditorState.maritalStatus) + '</div></fieldset><label class="my-page__form-control"><span>자녀 수 <em aria-hidden="true">*</em></span><select name="childrenCount" required><option value="">선택해 주세요.</option><option value="0"' + selected(profileEditorState.childrenCount, '0') + '>없음</option><option value="1"' + selected(profileEditorState.childrenCount, '1') + '>1명</option><option value="2"' + selected(profileEditorState.childrenCount, '2') + '>2명</option><option value="3"' + selected(profileEditorState.childrenCount, '3') + '>3명</option><option value="4+"' + selected(profileEditorState.childrenCount, '4+') + '>4명 이상</option></select></label><label class="my-page__form-control" data-exact-children-field' + (exactChildrenVisible ? '' : ' hidden') + '><span>정확한 자녀 수 <em aria-hidden="true">*</em></span><input name="childrenExact" type="number" min="4" inputmode="numeric" value="' + escapeHtml(profileEditorState.childrenExact) + '"' + (exactChildrenVisible ? ' required' : ' disabled') + '></label><label class="my-page__form-control" data-youngest-field' + (childVisible ? '' : ' hidden') + '><span>막내 자녀 출생연도 <em aria-hidden="true">*</em></span><input name="youngestYear" type="number" min="1900" max="' + currentYear + '" inputmode="numeric" value="' + escapeHtml(profileEditorState.youngestYear) + '"' + (childVisible ? ' required' : ' disabled') + '></label></div><fieldset class="my-page__multi-choice" data-exclusive-group="householdTypes"><legend>가구 유형 <em aria-hidden="true">*</em></legend><div>' + checks('householdTypes', [['JA0404', '1인 가구'], ['JA0403', '한부모 가구'], ['JA0403', '조손 가구'], ['JA0411', '다자녀 가구'], ['JA0413', '신규 전입 가구'], ['JA0414', '확대가족'], ['NONE', '해당 없음', true]], profileEditorState.householdTypes) + '</div></fieldset><fieldset class="my-page__multi-choice" data-exclusive-group="statusCodes"><legend>현재 활동 상태 <em aria-hidden="true">*</em></legend><div>' + checks('statusCodes', [['JA0317', '초등학생'], ['JA0318', '중학생'], ['JA0319', '고등학생'], ['JA0320', '대학생'], ['JA0326', '재직자'], ['JA0327', '구직자'], ['SELF_EMPLOYED', '자영업자'], ['JA0313', '농업 종사자'], ['JA0314', '어업 종사자'], ['JA0315', '축산업 종사자'], ['JA0316', '임업 종사자'], ['NONE', '해당 없음', true]], profileEditorState.statusCodes) + '</div></fieldset><fieldset class="my-page__choice-field"><legend>복지 수급 상태 <em aria-hidden="true">*</em></legend><div>' + radio('welfareStatus', 'BASIC', '기초생활수급자', profileEditorState.welfareStatus) + radio('welfareStatus', 'NEAR_POOR', '차상위계층', profileEditorState.welfareStatus) + radio('welfareStatus', 'NONE', '해당 없음', profileEditorState.welfareStatus) + radio('welfareStatus', 'UNKNOWN', '잘 모르겠어요', profileEditorState.welfareStatus) + '</div></fieldset>') +
        accordion('interests', '관심 분야', '<fieldset class="my-page__interest-field"><legend>관심 분야</legend><div><label><input type="checkbox" name="interests" value="주거·생활"' + checked('주거·생활') + '> 주거·생활</label><label><input type="checkbox" name="interests" value="취업·교육"' + checked('취업·교육') + '> 취업·교육</label></div></fieldset>') +
        accordion('sensitive', '민감정보 관리 (선택)', sensitiveContent) +
        '<p class="my-page__form-error" data-profile-form-error role="alert" tabindex="-1"></p><div class="my-page__profile-form-actions"><button class="bk-btn bk-btn--outline bk-btn--md" type="button" data-profile-cancel>취소</button><button class="bk-btn bk-btn--primary bk-btn--md" type="submit">저장</button></div></form>';
}

function renderProfile() {
    if (profileLoadState !== 'loaded') return renderProfileLoadState();
    if (profileMode === 'password') return renderPasswordChange();
    return profileMode === 'edit' ? renderProfileEditor() : renderProfileView();
}

function renderBookmarks() {
    bookmarkPage = window.BokdeongiPagination.clampPage(myPageData.bookmarks.length, bookmarkPage, BOOKMARK_PAGE_SIZE);
    const start = (bookmarkPage - 1) * BOOKMARK_PAGE_SIZE;
    const cards = myPageData.bookmarks.slice(start, start + BOOKMARK_PAGE_SIZE).map((benefit) => {
        const statusClass = benefit.status === '조건부' ? ' my-page__benefit-status--conditional' : '';
        return '<article class="my-page__benefit-card"><div class="my-page__benefit-content"><div class="my-page__benefit-meta"><span class="my-page__benefit-status' + statusClass + '">' + escapeHtml(benefit.status) + '</span><span class="my-page__benefit-category">' + escapeHtml(benefit.category) + '</span><span class="my-page__benefit-deadline">마감 ' + escapeHtml(benefit.deadline) + '</span></div><h3>' + escapeHtml(benefit.title) + '</h3><p>' + escapeHtml(benefit.description) + '</p></div><div class="my-page__benefit-actions"><a class="bk-btn bk-btn--outline bk-btn--sm" href="benefit-detail.html">상세 보기</a><button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-remove-bookmark="' + escapeHtml(benefit.id) + '">관심 해제</button></div></article>';
    }).join('');
    const list = cards || '<div class="my-page__empty">저장한 관심 혜택이 없어요. 복지 혜택 찾기에서 관심 있는 혜택을 저장해 보세요.</div>';
    const pagination = window.BokdeongiPagination.markup({ totalCount: myPageData.bookmarks.length, currentPage: bookmarkPage, pageSize: BOOKMARK_PAGE_SIZE, label: '저장한 관심 혜택' });
    return '<header class="my-page__content-header"><h2>저장한 관심 혜택</h2></header><div class="my-page__benefit-list">' + list + '</div>' + pagination +
        '<section class="my-page__section my-page__keyword-section"><h3>관심 키워드</h3><p class="my-page__keyword-description">관심 있는 키워드를 등록하고 관리할 수 있어요.</p><div class="my-page__keyword-list" data-keyword-list></div><form class="my-page__keyword-form" data-keyword-form><label class="sr-only" for="keyword-input">관심 키워드</label><input id="keyword-input" name="keyword" type="text" maxlength="20" placeholder="관심 키워드를 입력해 주세요. (예: 청년, 지원금, 주거지원)"><button class="bk-btn bk-btn--primary bk-btn--md" type="submit">추가</button></form></section>';
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

function renderKeywords() {
    const target = $('[data-keyword-list]');
    if (!target) return;
    target.replaceChildren();
    myPageData.keywords.forEach((keyword) => {
        const tag = document.createElement('span');
        tag.className = 'my-page__keyword';
        tag.append('#' + keyword);
        const removeButton = document.createElement('button');
        removeButton.className = 'my-page__keyword-remove';
        removeButton.type = 'button';
        removeButton.dataset.removeKeyword = keyword;
        removeButton.setAttribute('aria-label', keyword + ' 키워드 삭제');
        removeButton.textContent = '×';
        tag.append(removeButton);
        target.append(tag);
    });
}

function renderSection() {
    const target = $('#my-page-content');
    const renderer = { profile: renderProfile, bookmarks: renderBookmarks, guide: renderGuide, legal: renderLegal }[currentSection];
    target.innerHTML = renderer();
    target.setAttribute('aria-label', document.querySelector('[data-section="' + currentSection + '"]').textContent.trim());
    if (currentSection === 'bookmarks') renderKeywords();
    document.querySelectorAll('[data-section]').forEach((button) => {
        const active = button.dataset.section === currentSection;
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-selected', String(active));
    });
}

async function loadProfile() {
    const auth = window.BokdeongiAuth;
    if (!auth) {
        profileLoadState = 'error';
        if (currentSection === 'profile') renderSection();
        return;
    }

    const authStatus = await auth.refreshAuthentication();
    if (authStatus !== 'authenticated') {
        profileLoadState = authStatus === 'unauthenticated' ? 'unauthenticated' : 'error';
        if (currentSection === 'profile') renderSection();
        return;
    }

    try {
        const response = await auth.authenticatedFetch('/api/me/profile');
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
        if (!profile || typeof profile !== 'object' || Array.isArray(profile)) {
            profileLoadState = 'error';
            if (currentSection === 'profile') renderSection();
            return;
        }

        applyProfileResponse(profile);
        profileLoadState = 'loaded';
        if (currentSection === 'profile') renderSection();
    } catch (error) {
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

function addKeyword(form) {
    const input = form.elements.keyword;
    const keyword = input.value.trim().replace(/^#/, '');
    if (!keyword) { showToast('관심 키워드를 입력해 주세요.'); return; }
    if (myPageData.keywords.some((item) => item.toLocaleLowerCase('ko-KR') === keyword.toLocaleLowerCase('ko-KR'))) { showToast('이미 등록한 관심 키워드예요.'); return; }
    myPageData.keywords.push(keyword);
    input.value = '';
    renderKeywords();
    showToast('관심 키워드를 추가했어요. 이 변경은 현재 브라우저에서만 유지됩니다.');
}

function saveProfile(form) {
    const values = Object.fromEntries(new FormData(form));
    const error = form.querySelector('[data-profile-form-error]');
    const valuesOf = (name) => Array.from(form.querySelectorAll('[name="' + name + '"]:checked'), (input) => input.value);
    const exclusiveValues = (group) => { const selected = valuesOf(group); return selected.includes('NONE') ? [] : selected; };
    const requiredFields = [['birthDate', '생년월일', 'basic'], ['gender', '성별', 'basic'], ['sido', '시·도', 'basic'], ['regionCode', '시·군·구', 'basic'], ['householdSize', '가구원 수', 'household'], ['housingType', '주거 형태', 'household'], ['hasHouse', '주택 보유 여부', 'household'], ['incomeInputType', '소득 입력 방식', 'income'], ['maritalStatus', '혼인 여부', 'additional'], ['childrenCount', '자녀 수', 'additional'], ['welfareStatus', '복지 수급 상태', 'additional']];
    const missing = requiredFields.find(([name]) => !String(values[name] || '').trim()) ||
        (exclusiveValues('householdTypes').length === 0 && !valuesOf('householdTypes').includes('NONE') ? ['householdTypes', '가구 유형 또는 해당 없음', 'additional'] : null) ||
        (exclusiveValues('statusCodes').length === 0 && !valuesOf('statusCodes').includes('NONE') ? ['statusCodes', '현재 활동 상태 또는 해당 없음', 'additional'] : null) ||
        (['ANNUAL', 'MONTHLY'].includes(values.incomeInputType) && (!String(values.incomeAmount || '').trim() || !String(values.incomeBaseYear || '').trim()) ? ['incomeAmount', '소득 금액과 기준 연도', 'income'] : null) ||
        (values.childrenCount === '4+' && Number(values.childrenExact) < 4 ? ['childrenExact', '정확한 자녀 수', 'additional'] : null) ||
        (values.childrenCount && values.childrenCount !== '0' && !String(values.youngestYear || '').trim() ? ['youngestYear', '막내 자녀 출생연도', 'additional'] : null);
    const openAndFocus = (section, name) => { profileAccordion[section] = true; document.querySelectorAll('[data-profile-accordion-toggle]').forEach((button) => { const open = button.dataset.profileAccordionToggle === section || button.getAttribute('aria-expanded') === 'true'; button.setAttribute('aria-expanded', String(open)); const panel = $('#' + button.getAttribute('aria-controls')); if (panel) panel.hidden = !open; }); const input = form.querySelector('[name="' + name + '"]'); if (input) input.focus(); };
    if (missing) { error.textContent = missing[1] + '을(를) 확인해 주세요.'; openAndFocus(missing[2], missing[0]); return; }
    const birthDate = new Date(values.birthDate + 'T00:00:00');
    if (Number.isNaN(birthDate.getTime()) || birthDate > new Date()) { error.textContent = '생년월일은 오늘 이전 날짜로 입력해 주세요.'; openAndFocus('basic', 'birthDate'); return; }
    if (['ANNUAL', 'MONTHLY'].includes(values.incomeInputType) && (Number(values.incomeAmount) < 0 || Number(values.incomeBaseYear) > new Date().getFullYear())) { error.textContent = '소득 금액과 기준 연도를 확인해 주세요.'; openAndFocus('income', 'incomeAmount'); return; }
    if (values.childrenCount !== '0' && (!Number.isInteger(Number(values.youngestYear)) || Number(values.youngestYear) < 1900 || Number(values.youngestYear) > new Date().getFullYear())) { error.textContent = '막내 자녀 출생연도를 확인해 주세요.'; openAndFocus('additional', 'youngestYear'); return; }

    profileEditorState = { gender: values.gender, sido: values.sido, regionCode: values.regionCode, householdSize: values.householdSize, housingType: values.housingType, hasHouse: values.hasHouse, incomeInputType: values.incomeInputType, incomeAmount: values.incomeAmount || '', incomeBaseYear: values.incomeBaseYear || String(new Date().getFullYear()), maritalStatus: values.maritalStatus, childrenCount: values.childrenCount, childrenExact: values.childrenExact || '', youngestYear: values.youngestYear || '', householdTypes: valuesOf('householdTypes').includes('NONE') ? [] : exclusiveValues('householdTypes'), statusCodes: valuesOf('statusCodes').includes('NONE') ? [] : exclusiveValues('statusCodes'), welfareStatus: values.welfareStatus };
    const selectedSensitiveCodes = valuesOf('sensitiveCodes');
    sensitiveEditState.codes = sensitiveEditState.consent ? (selectedSensitiveCodes.includes('NONE') ? [] : selectedSensitiveCodes.length ? selectedSensitiveCodes : null) : null;
    sensitiveState = { consent: sensitiveEditState.consent, codes: sensitiveEditState.codes === null ? null : [...sensitiveEditState.codes] };
    setProfileValue('basics', '생년월일', values.birthDate.replaceAll('-', '.'));
    setProfileValue('basics', '거주지역', values.sido);
    setProfileValue('settings', '가구 형태', values.householdSize + '인 가구');
    setProfileValue('settings', '주거 형태', { OWN: '자가', JEONSE: '전세', MONTHLY: '월세', ETC: '기타' }[values.housingType]);
    setProfileValue('settings', '소득 정보', values.incomeInputType === 'UNKNOWN' ? '잘 모르겠어요' : values.incomeInputType === 'ANNUAL' ? '연소득 입력' : '월소득 입력');
    profileInterests = Array.from(form.querySelectorAll('input[name="interests"]:checked'), (input) => input.value);
    profileMode = 'view';
    renderSection();
    showToast('변경 내용은 현재 화면에만 임시로 반영되며, 새로고침하면 초기값으로 돌아갑니다.');
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
    document.addEventListener('click', (event) => {
        if (event.target.closest('[data-withdrawal-close]')) { closeWithdrawalDialog(); return; }
        if (event.target.closest('[data-withdrawal-confirm]')) { submitWithdrawal(); return; }
        if (event.target.closest('[data-sensitive-consent-view]')) { showToast('정식 민감정보 동의 문서가 아직 연결되지 않았습니다. 가입 단계의 동의 문서와 연결 후 내용을 확인할 수 있어요.'); return; }
        const withdrawConfirm = event.target.closest('[data-sensitive-withdraw-confirm]');
        if (withdrawConfirm) {
            const form = withdrawConfirm.closest('[data-profile-form]');
            sensitiveEditState = { consent: false, codes: null };
            form.querySelector('[data-sensitive-consent]').checked = false;
            form.querySelector('[data-exclusive-group="sensitiveCodes"]').hidden = true;
            form.querySelector('[data-exclusive-group="sensitiveCodes"]').querySelectorAll('input').forEach((input) => { input.checked = false; input.disabled = true; });
            form.querySelector('[data-sensitive-reason]').hidden = false;
            form.querySelector('[data-sensitive-withdraw]').hidden = true;
            return;
        }
        const withdrawCancel = event.target.closest('[data-sensitive-withdraw-cancel]');
        if (withdrawCancel) { withdrawCancel.closest('[data-profile-form]').querySelector('[data-sensitive-withdraw]').hidden = true; return; }
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
        const sectionButton = event.target.closest('[data-section]');
        if (sectionButton) { currentSection = sectionButton.dataset.section; profileMode = 'view'; renderSection(); return; }
        const removeBookmark = event.target.closest('[data-remove-bookmark]');
        if (removeBookmark) { myPageData.bookmarks = myPageData.bookmarks.filter((benefit) => benefit.id !== removeBookmark.dataset.removeBookmark); renderSection(); showToast('관심 혜택 목록에서 제거했어요. 이 변경은 현재 브라우저에서만 유지됩니다.'); return; }
        const removeKeyword = event.target.closest('[data-remove-keyword]');
        if (removeKeyword) { myPageData.keywords = myPageData.keywords.filter((keyword) => keyword !== removeKeyword.dataset.removeKeyword); renderKeywords(); showToast('관심 키워드를 삭제했어요.'); return; }
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
        if (event.target.closest('[data-profile-edit]')) { Object.assign(profileAccordion, { basic: true, household: false, income: false, additional: false, interests: false, sensitive: false }); sensitiveEditState = { consent: sensitiveState.consent, codes: sensitiveState.codes === null ? null : [...sensitiveState.codes] }; profileMode = 'edit'; renderSection(); return; }
        if (event.target.closest('[data-profile-cancel]')) { profileMode = 'view'; renderSection(); return; }
        if (event.target.closest('[data-password-change-cancel]')) { passwordChangeSubmitting = false; profileMode = 'view'; renderSection(); return; }
        const accountAction = event.target.closest('[data-account-action]');
        if (accountAction) {
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
        const form = event.target.closest('[data-password-change-form]');
        if (!form || !event.target.name) return;
        if (['currentPassword', 'newPassword', 'newPasswordConfirm'].includes(event.target.name)) {
            setPasswordChangeFieldError(form, event.target.name, '');
            setPasswordChangeStatus(form, '');
        }
    });
    document.addEventListener('change', (event) => {
        const form = event.target.closest('[data-profile-form]');
        if (!form) return;
        if (event.target.name === 'sido') {
            const sigungu = form.elements.regionCode;
            const options = PROFILE_REGIONS[event.target.value] || [];
            sigungu.replaceChildren(new Option(options.length ? '시·군·구 선택' : '시·도를 먼저 선택해 주세요.', ''));
            options.forEach(([name, code]) => sigungu.add(new Option(name, code)));
            sigungu.disabled = !options.length;
        }
        if (event.target.name === 'incomeInputType') {
            const visible = ['ANNUAL', 'MONTHLY'].includes(event.target.value);
            const fields = form.querySelector('[data-income-fields]');
            fields.hidden = !visible;
            fields.querySelectorAll('input').forEach((input) => { input.disabled = !visible; input.required = visible; if (!visible) input.value = ''; });
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
        if (event.target.matches('[data-sensitive-consent]')) {
            const sensitiveOptions = form.querySelector('[data-exclusive-group="sensitiveCodes"]');
            if (event.target.checked) {
                sensitiveEditState.consent = true;
                sensitiveOptions.hidden = false;
                sensitiveOptions.querySelectorAll('input').forEach((input) => { input.disabled = false; });
                form.querySelector('[data-sensitive-reason]').hidden = true;
            } else {
                event.target.checked = true;
                form.querySelector('[data-sensitive-withdraw]').hidden = false;
            }
        }
        const group = event.target.closest('[data-exclusive-group]');
        if (group && event.target.matches('input[type="checkbox"]')) {
            const none = group.querySelector('[data-none]');
            const others = group.querySelectorAll('input:not([data-none])');
            if (event.target.dataset.none !== undefined && event.target.checked) others.forEach((input) => { input.checked = false; });
            if (event.target.dataset.none === undefined && event.target.checked) none.checked = false;
        }
    });
    document.addEventListener('submit', (event) => {
        const keywordForm = event.target.closest('[data-keyword-form]');
        if (keywordForm) { event.preventDefault(); addKeyword(keywordForm); return; }
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
