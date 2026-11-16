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

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;
    window.setTimeout(() => { toast.hidden = true; }, 2800);
}

function infoItems(items) {
    return items.map(([icon, label, value]) => '<div class="my-page__info-item"><span class="my-page__info-icon" aria-hidden="true">' + icon + '</span><span class="my-page__info-label">' + label + '</span><strong class="my-page__info-value">' + value + '</strong></div>').join('');
}

function renderProfile() {
    return '<header class="my-page__content-header"><h2>내 정보</h2><span class="my-page__status">프로필 등록 완료</span></header>' +
        '<section class="my-page__section"><h3>기본 정보</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.basics) + '</div></section>' +
        '<section class="my-page__section"><h3>맞춤 혜택 프로필</h3><div class="my-page__info-grid">' + infoItems(myPageData.profile.settings) +
        '<div class="my-page__info-item"><span class="my-page__info-icon" aria-hidden="true">◇</span><span class="my-page__info-label">관심 분야</span><span class="my-page__topic-list"><span class="my-page__topic-tag">주거·생활</span><span class="my-page__topic-tag">취업·교육</span></span></div></div><div class="my-page__profile-action"><button class="btn" type="button" data-profile-edit>프로필 수정하기 <span aria-hidden="true">›</span></button></div></section>' +
        '<section class="my-page__section"><h3>계정 및 개인정보 관리</h3><div class="my-page__account-grid">' +
        '<button class="my-page__account-button" type="button" data-account-action="개인정보 동의 관리"><span aria-hidden="true">♢</span><span><strong>개인정보 동의 관리</strong><small>현재 동의 내역을 확인하고 변경해요.</small></span><span aria-hidden="true">›</span></button>' +
        '<button class="my-page__account-button" type="button" data-account-action="비밀번호 변경"><span aria-hidden="true">▣</span><span><strong>비밀번호 변경</strong><small>비밀번호를 안전하게 변경해요.</small></span><span aria-hidden="true">›</span></button>' +
        '<button class="my-page__account-button" type="button" data-account-action="회원 탈퇴"><span aria-hidden="true">□</span><span><strong>회원 탈퇴</strong><small>복덩이 서비스를 탈퇴할 수 있어요.</small></span><span aria-hidden="true">›</span></button></div></section>';
}

function renderBookmarks() {
    const cards = myPageData.bookmarks.map((benefit) => {
        const statusClass = benefit.status === '조건부' ? ' my-page__benefit-status--conditional' : '';
        return '<article class="my-page__benefit-card"><span class="my-page__benefit-icon" aria-hidden="true">' + benefit.icon + '</span><div><div class="my-page__benefit-meta"><span class="my-page__benefit-category">' + benefit.category + '</span><span class="my-page__benefit-status' + statusClass + '">' + benefit.status + '</span><span class="my-page__benefit-deadline">▣ ' + benefit.deadline + '</span></div><h3>' + benefit.title + '</h3><p>' + benefit.description + '</p></div><div class="my-page__benefit-actions"><a class="btn btn-secondary" href="benefit-detail.html">상세 보기</a><button class="btn btn-secondary" type="button" data-remove-bookmark="' + benefit.id + '">관심 해제</button></div></article>';
    }).join('');
    const list = cards || '<div class="my-page__empty">저장한 관심 혜택이 없어요. 복지 혜택 찾기에서 관심 있는 혜택을 저장해 보세요.</div>';
    return '<header class="my-page__content-header"><h2>저장한 관심 혜택</h2></header><div class="my-page__benefit-list">' + list + '</div>' +
        '<section class="my-page__section"><h3>관심 키워드</h3><p class="my-page__keyword-description">관심 있는 키워드를 등록하고 관리할 수 있어요.</p><div class="my-page__keyword-list" data-keyword-list></div><form class="my-page__keyword-form" data-keyword-form><label class="sr-only" for="keyword-input">관심 키워드</label><input id="keyword-input" name="keyword" type="text" maxlength="20" placeholder="관심 키워드를 입력해 주세요. (예: 청년, 지원금, 주거지원)"><button class="btn" type="submit">추가</button></form></section>';
}

function renderGuide() {
    const features = [
        ['⌕', '복지 혜택 찾기', '다양한 복지 혜택을 검색하고 확인해요.', 'benefit-list.html'],
        ['✦', '맞춤 혜택 추천', '내 정보에 맞는 혜택을 추천받아요.', 'benefit-recommend.html'],
        ['▤', '신청 현황', '신청한 혜택의 진행 상태를 확인해요.', 'application-status.html'],
        ['◌', '쉬운 말 번역기', '어려운 안내문을 쉬운 말로 바꿔요.', 'easy-translator.html']
    ].map(([icon, title, description, href]) => '<a class="my-page__feature-link" href="' + href + '"><span class="my-page__feature-icon" aria-hidden="true">' + icon + '</span><h3>' + title + '</h3><p>' + description + '</p></a>').join('');
    const faqs = myPageData.faqs.map(([question, answer], index) => '<article class="my-page__faq-item"><button class="my-page__faq-question" type="button" aria-expanded="false" aria-controls="faq-answer-' + index + '" data-faq="' + index + '"><span>' + question + '</span><span aria-hidden="true">⌄</span></button><p class="my-page__faq-answer" id="faq-answer-' + index + '" hidden>' + answer + '</p></article>').join('');
    return '<header class="my-page__content-header"><div><h2>이용 안내</h2><p class="my-page__content-intro">복덩이의 주요 기능과 이용 방법을 안내해 드려요.</p></div></header><section class="my-page__section"><h3>주요 기능 이용 방법</h3><div class="my-page__feature-grid">' + features + '</div></section><section class="my-page__section"><h3>자주 묻는 질문 (FAQ)</h3><div class="my-page__faq-list">' + faqs + '</div></section>';
}

function renderLegal() {
    const documents = [
        ['▧', '이용약관', '복덩이 서비스 이용에 관한 권리, 의무 및 책임사항을 안내합니다.'],
        ['♢', '개인정보 처리방침', '개인정보의 수집·이용·보관 및 처리에 관한 내용을 안내합니다.'],
        ['▤', '개인정보 제3자 제공 안내', '맞춤 혜택 제공을 위한 최소한의 정보 제공 및 위탁 사항을 안내합니다.'],
        ['ⓘ', '저작권 및 정보 이용 안내', '서비스 내 콘텐츠의 저작권과 공공데이터 이용에 관한 사항을 안내합니다.'],
        ['!', '면책사항', '제공되는 복지 정보는 참고용이며, 실제 신청 및 지원 여부는 해당 기관의 기준에 따릅니다.']
    ].map(([icon, title, description]) => '<button class="my-page__legal-button" type="button" data-legal-document="' + title + '"><span class="my-page__legal-icon" aria-hidden="true">' + icon + '</span><span><strong>' + title + '</strong><small>' + description + '</small></span><span aria-hidden="true">›</span></button>').join('');
    return '<header class="my-page__content-header"><div><h2>법적 안내사항</h2><p class="my-page__content-intro">서비스 이용과 관련된 약관 및 개인정보 처리방침을 확인할 수 있어요.</p></div></header><div class="my-page__legal-list">' + documents + '</div><aside class="my-page__notice"><span aria-hidden="true">ⓘ</span><div><strong>안내드립니다</strong><p>복덩이는 공공기관의 복지 정보를 기반으로 제공하는 안내 서비스로, 실제 복지 신청 및 심사는 해당 기관의 규정에 따라 진행됩니다.<br>서비스 이용 전 반드시 최신 공고와 기관의 안내를 확인해 주세요.</p></div></aside>';
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

function setupInteractions() {
    document.addEventListener('click', (event) => {
        const sectionButton = event.target.closest('[data-section]');
        if (sectionButton) { currentSection = sectionButton.dataset.section; renderSection(); return; }
        const removeBookmark = event.target.closest('[data-remove-bookmark]');
        if (removeBookmark) { myPageData.bookmarks = myPageData.bookmarks.filter((benefit) => benefit.id !== removeBookmark.dataset.removeBookmark); renderSection(); showToast('관심 혜택 목록에서 제거했어요. 이 변경은 현재 브라우저에서만 유지됩니다.'); return; }
        const removeKeyword = event.target.closest('[data-remove-keyword]');
        if (removeKeyword) { myPageData.keywords = myPageData.keywords.filter((keyword) => keyword !== removeKeyword.dataset.removeKeyword); renderKeywords(); showToast('관심 키워드를 삭제했어요.'); return; }
        const faq = event.target.closest('[data-faq]');
        if (faq) { const answer = $('#' + faq.getAttribute('aria-controls')); const expanded = faq.getAttribute('aria-expanded') === 'true'; faq.setAttribute('aria-expanded', String(!expanded)); answer.hidden = expanded; return; }
        if (event.target.closest('[data-profile-edit]')) { showToast('프로필 수정 기능은 실제 계정 정보 연동 후 이용할 수 있어요.'); return; }
        const accountAction = event.target.closest('[data-account-action]');
        if (accountAction) { showToast(accountAction.dataset.accountAction + ' 기능은 추후 계정 관리 기능과 연동될 예정입니다.'); return; }
        const legalDocument = event.target.closest('[data-legal-document]');
        if (legalDocument) showToast(legalDocument.dataset.legalDocument + ' 문서는 확정본 연결 후 확인할 수 있어요.');
    });
    document.addEventListener('submit', (event) => { const form = event.target.closest('[data-keyword-form]'); if (form) { event.preventDefault(); addKeyword(form); } });
}

document.addEventListener('DOMContentLoaded', () => { renderSection(); setupHeaderInteractions(); setupInteractions(); });
