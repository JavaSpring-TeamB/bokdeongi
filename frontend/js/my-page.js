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
let profileInterests = ['주거·생활', '취업·교육'];
let bookmarkPage = 1;
const BOOKMARK_PAGE_SIZE = 5;

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

function infoItems(items) {
    return items.map(([, label, value]) => '<div class="my-page__info-item"><span class="my-page__info-label">' + escapeHtml(label) + '</span><strong class="my-page__info-value">' + escapeHtml(value) + '</strong></div>').join('');
}

function renderProfileView() {
    const interests = profileInterests.map((interest) => '<span class="my-page__topic-tag">' + escapeHtml(interest) + '</span>').join('');
    return '<header class="my-page__content-header"><h2>내 정보</h2><span class="my-page__status">프로필 등록 완료</span></header>' +
        '<section class="my-page__section"><h3>기본 정보</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.basics) + '</div></section>' +
        '<section class="my-page__section"><h3>맞춤 혜택 프로필</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.settings) +
        '<div class="my-page__info-item"><span class="my-page__info-label">관심 분야</span><span class="my-page__topic-list">' + interests + '</span></div></div><div class="my-page__profile-action"><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-profile-edit>프로필 수정하기 <span aria-hidden="true">›</span></button></div></section>' +
        '<section class="my-page__section"><h3>계정 및 개인정보 관리</h3><div class="my-page__account-grid">' +
        '<button class="my-page__account-button" type="button" data-account-action="개인정보 동의 관리"><span><strong>개인정보 동의 관리</strong><small>현재 동의 내역을 확인하고 변경해요.</small></span><span aria-hidden="true">›</span></button>' +
        '<button class="my-page__account-button" type="button" data-account-action="비밀번호 변경"><span><strong>비밀번호 변경</strong><small>비밀번호를 안전하게 변경해요.</small></span><span aria-hidden="true">›</span></button>' +
        '<button class="my-page__account-button" type="button" data-account-action="회원 탈퇴"><span><strong>회원 탈퇴</strong><small>복덩이 서비스를 탈퇴할 수 있어요.</small></span><span aria-hidden="true">›</span></button></div></section>';
}

function renderProfileEditor() {
    const birthDate = profileValue('basics', '생년월일').replaceAll('.', '-');
    const field = (label, name, value, options = '') => '<label class="my-page__form-control"><span>' + label + (options.includes('required') ? ' <em aria-hidden="true">*</em>' : '') + '</span><input name="' + name + '" value="' + escapeHtml(value) + '" ' + options + '></label>';
    const checked = (interest) => profileInterests.includes(interest) ? ' checked' : '';
    return '<header class="my-page__content-header"><div><h2>프로필 수정</h2><p class="my-page__content-intro">변경 내용은 현재 화면에서만 임시로 반영되며, 새로고침하면 초기값으로 돌아갑니다.</p></div><span class="my-page__status">프로필 등록 완료</span></header>' +
        '<form class="my-page__profile-form" data-profile-form novalidate>' +
        '<section class="my-page__profile-form-section"><div class="my-page__section-heading"><h3>계정 고유 정보</h3><p>계정 정보는 이 화면에서 변경할 수 없어요.</p></div><div class="my-page__profile-form-grid">' +
        field('이름', 'name', profileValue('basics', '이름'), 'type="text" disabled') +
        field('이메일', 'email', profileValue('basics', '이메일'), 'type="email" disabled') +
        '</div></section>' +
        '<section class="my-page__profile-form-section"><h3>수정 가능한 기본 정보</h3><div class="my-page__profile-form-grid">' +
        field('생년월일', 'birthDate', birthDate, 'type="date" required max="' + new Date().toISOString().slice(0, 10) + '"') +
        field('거주지역', 'region', profileValue('basics', '거주지역'), 'type="text" required') +
        '</div></section>' +
        '<section class="my-page__profile-form-section"><h3>맞춤 혜택 프로필</h3><div class="my-page__profile-form-grid">' +
        field('가구 형태', 'household', profileValue('settings', '가구 형태'), 'type="text" required') +
        field('주거 형태', 'housing', profileValue('settings', '주거 형태'), 'type="text" required') +
        field('소득 정보', 'income', profileValue('settings', '소득 정보'), 'type="text" required') +
        '<fieldset class="my-page__interest-field"><legend>관심 분야</legend><div><label><input type="checkbox" name="interests" value="주거·생활"' + checked('주거·생활') + '> 주거·생활</label><label><input type="checkbox" name="interests" value="취업·교육"' + checked('취업·교육') + '> 취업·교육</label></div></fieldset>' +
        '</div></section><p class="my-page__form-error" data-profile-form-error role="alert" tabindex="-1"></p><div class="my-page__profile-form-actions"><button class="bk-btn bk-btn--outline bk-btn--md" type="button" data-profile-cancel>취소</button><button class="bk-btn bk-btn--primary bk-btn--md" type="submit">저장</button></div></form>';
}

function renderProfile() {
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
    const requiredFields = [['birthDate', '생년월일'], ['region', '거주지역'], ['household', '가구 형태'], ['housing', '주거 형태'], ['income', '소득 정보']];
    const missing = requiredFields.find(([name]) => !String(values[name] || '').trim());

    if (missing) {
        error.textContent = missing[1] + '을(를) 입력해 주세요.';
        error.focus();
        return;
    }

    const birthDate = new Date(values.birthDate + 'T00:00:00');
    if (Number.isNaN(birthDate.getTime()) || birthDate > new Date()) {
        error.textContent = '생년월일은 오늘 이전 날짜로 입력해 주세요.';
        error.focus();
        return;
    }

    setProfileValue('basics', '생년월일', values.birthDate.replaceAll('-', '.'));
    setProfileValue('basics', '거주지역', values.region.trim());
    setProfileValue('settings', '가구 형태', values.household.trim());
    setProfileValue('settings', '주거 형태', values.housing.trim());
    setProfileValue('settings', '소득 정보', values.income.trim());
    profileInterests = Array.from(form.querySelectorAll('input[name="interests"]:checked'), (input) => input.value);
    profileMode = 'view';
    renderSection();
    showToast('변경 내용은 현재 화면에만 임시로 반영되며, 새로고침하면 초기값으로 돌아갑니다.');
}

function setupInteractions() {
    document.addEventListener('click', (event) => {
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
        if (event.target.closest('[data-profile-edit]')) { profileMode = 'edit'; renderSection(); return; }
        if (event.target.closest('[data-profile-cancel]')) { profileMode = 'view'; renderSection(); return; }
        const accountAction = event.target.closest('[data-account-action]');
        if (accountAction) { showToast(accountAction.dataset.accountAction + ' 기능은 추후 계정 관리 기능과 연동될 예정입니다.'); return; }
        const legalDocument = event.target.closest('[data-legal-document]');
        if (legalDocument) showToast(legalDocument.dataset.legalDocument + ' 문서는 확정본 연결 후 확인할 수 있어요.');
    });
    document.addEventListener('submit', (event) => {
        const keywordForm = event.target.closest('[data-keyword-form]');
        if (keywordForm) { event.preventDefault(); addKeyword(keywordForm); return; }
        const profileForm = event.target.closest('[data-profile-form]');
        if (profileForm) { event.preventDefault(); saveProfile(profileForm); }
    });
    document.addEventListener('bokdeongi:pagination', (event) => {
        if (currentSection !== 'bookmarks') return;
        bookmarkPage = event.detail.page;
        renderSection();
    });
}

document.addEventListener('DOMContentLoaded', () => { renderSection(); setupInteractions(); });
