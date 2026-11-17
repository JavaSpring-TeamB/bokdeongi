const $ = (selector) => document.querySelector(selector);
const PROFILE_STORAGE_KEY = 'bokdeongi-signup-profile';
const CONSENT_STORAGE_KEY = 'bokdeongi-signup-consents';
const REGIONS = {
    '서울특별시': [{ name: '종로구', code: '11110' }, { name: '강남구', code: '11680' }, { name: '마포구', code: '11440' }],
    '부산광역시': [{ name: '중구', code: '26110' }, { name: '해운대구', code: '26350' }, { name: '사하구', code: '26380' }],
    '대구광역시': [{ name: '중구', code: '27110' }, { name: '수성구', code: '27260' }, { name: '달성군', code: '27710' }],
    '인천광역시': [{ name: '중구', code: '28110' }, { name: '남동구', code: '28200' }, { name: '서구', code: '28260' }],
    '광주광역시': [{ name: '동구', code: '29110' }, { name: '북구', code: '29170' }, { name: '광산구', code: '29200' }],
    '대전광역시': [{ name: '동구', code: '30110' }, { name: '서구', code: '30170' }, { name: '유성구', code: '30200' }],
    '울산광역시': [{ name: '중구', code: '31110' }, { name: '남구', code: '31140' }, { name: '울주군', code: '31710' }],
    '세종특별자치시': [{ name: '세종특별자치시', code: '36110' }],
    '경기도': [{ name: '수원시', code: '41110' }, { name: '성남시', code: '41130' }, { name: '고양시', code: '41280' }],
    '강원특별자치도': [{ name: '춘천시', code: '42110' }, { name: '원주시', code: '42130' }, { name: '강릉시', code: '42150' }],
    '충청북도': [{ name: '청주시', code: '43110' }, { name: '충주시', code: '43130' }, { name: '제천시', code: '43150' }],
    '충청남도': [{ name: '천안시', code: '44130' }, { name: '공주시', code: '44150' }, { name: '아산시', code: '44200' }],
    '전북특별자치도': [{ name: '전주시', code: '45110' }, { name: '군산시', code: '45130' }, { name: '익산시', code: '45140' }],
    '전라남도': [{ name: '목포시', code: '46110' }, { name: '여수시', code: '46130' }, { name: '순천시', code: '46150' }],
    '경상북도': [{ name: '포항시', code: '47110' }, { name: '경주시', code: '47130' }, { name: '구미시', code: '47190' }],
    '경상남도': [{ name: '창원시', code: '48120' }, { name: '진주시', code: '48170' }, { name: '김해시', code: '48250' }],
    '제주특별자치도': [{ name: '제주시', code: '50110' }, { name: '서귀포시', code: '50130' }]
};

function showToast(message) { const toast = $('#toast'); toast.textContent = message; toast.hidden = false; window.clearTimeout(showToast.timer); showToast.timer = window.setTimeout(() => { toast.hidden = true; }, 4200); }
function readStorage(key, fallback) { try { return JSON.parse(sessionStorage.getItem(key)) || fallback; } catch (error) { return fallback; } }
function today() { return new Date().toISOString().slice(0, 10); }
function selectedRadio(name) { return document.querySelector(`[name="${name}"]:checked`)?.value || ''; }
function selectedCodes(group) { const inputs = Array.from(document.querySelectorAll(`[data-exclusive-group="${group}"] input:checked`)); if (!inputs.length) return null; return inputs.some((input) => input.dataset.none !== undefined) ? [] : [...new Set(inputs.map((input) => input.value))]; }

function populateRegions() {
    const sido = $('#profile-sido');
    Object.keys(REGIONS).forEach((name) => { const option = new Option(name, name); sido.add(option); });
    sido.addEventListener('change', () => { populateSigungu(); saveProfile(); });
}
function populateSigungu(selectedCode = '') {
    const sigungu = $('#profile-sigungu'); const options = REGIONS[$('#profile-sido').value] || [];
    sigungu.replaceChildren(new Option(options.length ? '시·군·구 선택' : '시·도를 먼저 선택해 주세요.', ''));
    options.forEach(({ name, code }) => sigungu.add(new Option(name, code)));
    sigungu.disabled = !options.length;
    sigungu.value = selectedCode;
}
function setupExclusiveGroups() {
    document.querySelectorAll('[data-exclusive-group]').forEach((group) => group.addEventListener('change', (event) => {
        const input = event.target; if (!input.matches('input[type="checkbox"]')) return;
        const none = group.querySelector('[data-none]'); const others = Array.from(group.querySelectorAll('input:not([data-none])'));
        if (input.dataset.none !== undefined && input.checked) others.forEach((item) => { item.checked = false; });
        if (input.dataset.none === undefined && input.checked) none.checked = false;
        saveProfile();
    }));
}
function setupChildrenFields() {
    const choice = $('#profile-children-count'); const exact = $('#exact-children-field'); const youngest = $('#youngest-child-field');
    youngest.max = String(new Date().getFullYear());
    const update = () => { const hasChildren = choice.value && choice.value !== '0'; exact.hidden = choice.value !== '4+'; youngest.hidden = !hasChildren; if (!hasChildren) { $('#profile-youngest-year').value = ''; } if (choice.value !== '4+') $('#profile-children-exact').value = ''; saveProfile(); };
    choice.addEventListener('change', update); return update;
}
function setupIncomeFields() {
    const container = $('#income-amount-fields'); const amount = $('#profile-income-amount'); const year = $('#profile-income-year');
    year.value = String(new Date().getFullYear()); year.max = String(new Date().getFullYear());
    const update = () => { const type = selectedRadio('income_input_type'); container.hidden = !['ANNUAL', 'MONTHLY'].includes(type); amount.required = !container.hidden; year.required = !container.hidden; if (container.hidden) { amount.value = ''; } saveProfile(); };
    document.querySelectorAll('[name="income_input_type"]').forEach((input) => input.addEventListener('change', update)); return update;
}
function sensitiveConsentGiven() { return readStorage(CONSENT_STORAGE_KEY, []).some((consent) => consent.consent_type === 'SENSITIVE' && consent.agreed === true); }
function selectedSensitiveCodes() { return sensitiveConsentGiven() ? selectedCodes('sensitive') : null; }
function setupSensitiveSection() {
    const allowed = sensitiveConsentGiven(); const section = $('#sensitive-section');
    section.classList.toggle('is-disabled', !allowed); section.setAttribute('aria-disabled', String(!allowed));
    section.querySelectorAll('input').forEach((input) => { input.disabled = !allowed; if (!allowed) input.checked = false; });
    $('#sensitive-notice').hidden = allowed;
}

function getChildrenCount() { const choice = $('#profile-children-count').value; return choice === '4+' ? $('#profile-children-exact').value : choice; }
function buildProfilePayload() {
    const incomeType = selectedRadio('income_input_type');
    return {
        birth_date: $('#profile-birth-date').value || null, gender: selectedRadio('gender') || null, region_code: $('#profile-sigungu').value || null,
        household_size: $('#profile-household-size').value || null, housing_type: selectedRadio('housing_type') || null, has_house: selectedRadio('has_house') || null,
        income_input_type: incomeType || null, income_amount: ['ANNUAL', 'MONTHLY'].includes(incomeType) ? $('#profile-income-amount').value || null : null,
        income_base_year: ['ANNUAL', 'MONTHLY'].includes(incomeType) ? $('#profile-income-year').value || null : null, median_income_pct: null,
        marital_status: selectedRadio('marital_status') || null, children_count: getChildrenCount() || null, youngest_child_birth_year: $('#profile-youngest-year').value || null,
        household_type_codes: selectedCodes('household'), status_codes: selectedCodes('status'), welfare_status: selectedRadio('welfare_status') || null,
        // 서버 저장 시 동의된 민감 코드만 암호화해 sensitive_enc에 매핑한다. null=미입력, []=명시적 해당 없음.
        sensitive_codes: selectedSensitiveCodes(), profile_step: 3
    };
}
function saveProfile() { const { sensitive_codes: sensitiveCodes, ...profile } = buildProfilePayload(); try { sessionStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile)); } catch (error) { /* 현재 세션에서만 보관한다. */ } }
function restoreProfile(updateChildren, updateIncome) {
    const profile = readStorage(PROFILE_STORAGE_KEY, null); if (!profile) return;
    $('#profile-birth-date').value = profile.birth_date || ''; $('#profile-sido').value = Object.keys(REGIONS).find((name) => REGIONS[name].some((region) => region.code === profile.region_code)) || ''; populateSigungu(profile.region_code || '');
    ['gender', 'housing_type', 'has_house', 'income_input_type', 'marital_status', 'welfare_status'].forEach((name) => { const input = document.querySelector(`[name="${name}"][value="${profile[name]}"]`); if (input) input.checked = true; });
    $('#profile-household-size').value = profile.household_size || ''; const children = String(profile.children_count || ''); $('#profile-children-count').value = Number(children) >= 4 ? '4+' : children; $('#profile-children-exact').value = Number(children) >= 4 ? children : ''; $('#profile-youngest-year').value = profile.youngest_child_birth_year || ''; $('#profile-income-amount').value = profile.income_amount || ''; $('#profile-income-year').value = profile.income_base_year || String(new Date().getFullYear());
    [['household', profile.household_type_codes], ['status', profile.status_codes]].forEach(([group, codes]) => { if (codes === null) return; const selector = `[data-exclusive-group="${group}"]`; if (Array.isArray(codes) && !codes.length) { document.querySelector(`${selector} [data-none]`).checked = true; return; } document.querySelectorAll(`${selector} input`).forEach((input) => { input.checked = Array.isArray(codes) && codes.includes(input.value); }); });
    updateChildren(); updateIncome(); updateAge();
}
function updateAge() { const value = $('#profile-birth-date').value; const target = $('#profile-age'); if (!value) { target.textContent = '입력 후 표시'; return; } const birth = new Date(`${value}T00:00:00`); const now = new Date(); let age = now.getFullYear() - birth.getFullYear(); if (now < new Date(now.getFullYear(), birth.getMonth(), birth.getDate())) age -= 1; target.textContent = age >= 0 ? `만 ${age}세` : '날짜를 확인해 주세요.'; }
function setInvalid(selector, invalid) { document.querySelectorAll(selector).forEach((input) => input.setAttribute('aria-invalid', String(invalid))); }
function focusFirstInvalidProfileField() { window.requestAnimationFrame(() => { const input = document.querySelector('#signup-profile-form :invalid, #signup-profile-form [aria-invalid="true"]'); if (!input) return; input.focus({ preventScroll: true }); input.scrollIntoView({ behavior: 'smooth', block: 'center' }); }); }
function validateProfile() {
    const errors = []; const birth = $('#profile-birth-date');
    if (!birth.value) errors.push('생년월일을 입력해 주세요.'); else if (birth.value > today()) errors.push('생년월일은 오늘 이전 날짜여야 합니다.');
    if (!selectedRadio('gender')) errors.push('성별을 선택해 주세요.'); if (!$('#profile-sigungu').value) errors.push('거주 지역의 시·도와 시·군·구를 선택해 주세요.'); if (!$('#profile-household-size').value) errors.push('가구원 수를 선택해 주세요.'); if (!selectedRadio('housing_type')) errors.push('주거 형태를 선택해 주세요.'); if (!selectedRadio('has_house')) errors.push('주택 보유 여부를 선택해 주세요.');
    const incomeType = selectedRadio('income_input_type'); if (!incomeType) errors.push('소득 입력 방식을 선택해 주세요.'); if (['ANNUAL', 'MONTHLY'].includes(incomeType)) { if ($('#profile-income-amount').value === '' || Number($('#profile-income-amount').value) < 0) errors.push('0원 이상의 소득 금액을 입력해 주세요.'); if (!$('#profile-income-year').value || Number($('#profile-income-year').value) > new Date().getFullYear()) errors.push('올바른 소득 기준 연도를 입력해 주세요.'); }
    if (!selectedRadio('marital_status')) errors.push('혼인 여부를 선택해 주세요.'); const childChoice = $('#profile-children-count').value; const youngestYear = Number($('#profile-youngest-year').value); if (!childChoice) errors.push('자녀 수를 선택해 주세요.'); if (childChoice === '4+' && Number($('#profile-children-exact').value) < 4) errors.push('정확한 자녀 수를 4명 이상으로 입력해 주세요.'); if (childChoice && childChoice !== '0' && (!Number.isInteger(youngestYear) || youngestYear < 1900 || youngestYear > new Date().getFullYear())) errors.push('올바른 막내 자녀 출생연도를 입력해 주세요.');
    if (selectedCodes('household') === null) errors.push('가구 유형 또는 해당 없음을 선택해 주세요.'); if (selectedCodes('status') === null) errors.push('현재 활동 상태 또는 해당 없음을 선택해 주세요.'); if (!selectedRadio('welfare_status')) errors.push('복지 수급 상태를 선택해 주세요.');
    const error = $('#profile-form-error'); error.textContent = errors[0] || ''; if (errors.length) { error.focus(); focusFirstInvalidProfileField(); showToast(`입력 항목 ${errors.length}개를 확인해 주세요.`); return false; } return true;
}
function setupHeaderInteractions() {
    let theme = localStorage.getItem('bokdeongi-theme') || 'light'; const themeButton = $('[data-theme-toggle]'); document.documentElement.dataset.theme = theme; document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1); themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드'; themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
    document.addEventListener('click', (event) => { const unavailable = event.target.closest('[data-unavailable-page]'); if (unavailable) { event.preventDefault(); showToast('이 페이지는 현재 준비 중입니다.'); return; } const fontSize = event.target.closest('[data-font-size]'); if (fontSize) { const current = Number(localStorage.getItem('bokdeongi-font-scale') || 1); const change = fontSize.dataset.fontSize === 'increase' ? .1 : -.1; const scale = Math.min(1.2, Math.max(.9, Number((current + change).toFixed(2)))); localStorage.setItem('bokdeongi-font-scale', scale); document.documentElement.style.setProperty('--font-scale', scale); return; } if (event.target.closest('[data-theme-toggle]')) { theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; document.documentElement.dataset.theme = theme; localStorage.setItem('bokdeongi-theme', theme); themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드'; themeButton.setAttribute('aria-pressed', String(theme === 'dark')); return; } const menu = event.target.closest('.menu-toggle'); if (menu) { const navigation = $('#site-navigation'); const isOpen = navigation.classList.toggle('is-open'); menu.setAttribute('aria-expanded', String(isOpen)); menu.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기'); } });
}
document.addEventListener('DOMContentLoaded', () => { setupHeaderInteractions(); populateRegions(); setupExclusiveGroups(); const updateChildren = setupChildrenFields(); const updateIncome = setupIncomeFields(); setupSensitiveSection(); restoreProfile(updateChildren, updateIncome); $('#profile-birth-date').max = today(); $('#profile-birth-date').addEventListener('input', updateAge); $('#signup-profile-form').addEventListener('input', saveProfile); $('#signup-profile-form').addEventListener('change', saveProfile); $('#signup-profile-form').addEventListener('submit', (event) => { event.preventDefault(); if (!validateProfile()) return; saveProfile(); showToast('프로필 입력을 이 브라우저 세션에 임시 보관했습니다. 가입 저장 API와 완료 화면 연결 후 완료 단계로 이동합니다.'); }); });
