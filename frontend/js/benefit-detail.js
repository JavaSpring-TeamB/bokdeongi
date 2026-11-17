const mockBenefits = {
    1: {
        id: 1,
        title: '청년 월세 지원',
        organization: '복지 지원 기관',
        category: '주거',
        region: '전국',
        status: '접수 중',
        amount: '월 최대 20만원',
        period: '2026. 01. 01. ~ 2026. 12. 31.',
        applicationMethod: '온라인 신청',
        applicationUrl: null,
        phone: '1600-0000',
        tags: ['청년', '월세', '주거지원'],
        summary: [
            '청년의 월세 부담을 덜어주는 주거비 지원 사업이에요.',
            '거주지·나이·소득 등 공고 조건을 충족해야 해요.',
            '지원 금액과 기간은 해당 공고를 기준으로 확인할 수 있어요.'
        ],
        eligibility: {
            status: '해당',
            conditions: [
                { name: '나이', detail: '사용자 정보와 공고 기준을 확인할 수 있어요.', evidence: '만 19세 이상 34세 이하 청년' },
                { name: '거주 지역', detail: '사용자 거주 지역과 공고 기준을 확인할 수 있어요.', evidence: '대한민국에 거주하는 무주택 청년' },
                { name: '소득 기준', detail: '사용자 소득과 공고 기준을 확인할 수 있어요.', evidence: '소득 기준은 공식 공고문을 확인해주세요.' }
            ]
        }
    }
};

const mockNotice = {
    title: '부산광역시 청년 월세 지원사업 공고',
    organization: '부산광역시',
    noticeDate: '2026년 11월 30일',
    introduction: '부산광역시에서는 청년의 주거비 부담을 완화하고 안정적인 주거생활을 지원하기 위해 「청년 월세 지원사업」을 다음과 같이 공고합니다.',
    sections: [
        ['사업 개요', ['사업명: 청년 월세 지원사업', '지원내용: 월 최대 20만원의 월세 지원', '지원기간: 최대 12개월', '신청기간: 2026년 11월 30일 ~ 2026년 12월 31일']],
        ['지원 대상', ['부산광역시에 거주하는 청년', '만 19세 이상 34세 이하', '무주택자', '소득 기준을 충족하는 자', '세부 지원 조건은 해당 공고의 기준에 따릅니다.']],
        ['지원 내용', ['지원 금액: 월 최대 20만원', '지원 기간: 최대 12개월', '지원 방식: 신청 및 심사 후 지원']],
        ['신청 방법', ['신청 자격 확인', '필요 서류 준비', '온라인 신청서 작성', '관련 서류 제출', '신청 접수 및 심사']],
        ['제출 서류', ['신청서', '주민등록등본', '임대차계약서', '월세 납부 증빙서류', '기타 필요 서류']],
        ['유의사항', ['제출 서류가 누락되면 보완 요청이 발생할 수 있습니다.', '신청 정보가 사실과 다르면 지원이 제한될 수 있습니다.', '신청 기간 및 세부 조건은 실제 공식 공고를 확인해야 합니다.']],
        ['문의처', ['담당 기관: 부산광역시 청년정책 담당 부서', '문의 전화: 더미 공고문 데이터이므로 미제공']]
    ],
    officialUrl: null,
    pdfUrl: null,
    isMock: true
};

const mockDocuments = [
    { id: 'resident', name: '주민등록등본', required: true, purpose: '거주지 및 세대 구성 확인', issuer: '정부24·주민센터', validity: '공고 기준 확인', online: '온라인 발급 가능', steps: ['정부24 공식 홈페이지 접속', '주민등록등본 발급 서비스 검색', '본인 인증 후 발급 신청'] },
    { id: 'family', name: '가족관계증명서', required: true, purpose: '가족관계 확인', issuer: '전자가족관계등록시스템', validity: '공고 기준 확인', online: '온라인 발급 가능', steps: ['공식 발급 사이트 접속', '본인 인증 후 증명서 발급', '출력 또는 파일 저장'] },
    { id: 'income', name: '소득금액증명', required: true, purpose: '소득 요건 확인', issuer: '홈택스·세무서', validity: '공고 기준 확인', online: '온라인 발급 가능', steps: ['홈택스 공식 홈페이지 접속', '소득금액증명 메뉴 선택', '본인 인증 후 발급 신청'] }
];

const mockApplicationSteps = [
    { order: 1, title: '신청 사이트 접속', description: '공식 접수처로 이동', icon: 'apply' },
    { order: 2, title: '로그인', description: '본인 인증 진행', icon: 'target' },
    { order: 3, title: '신청서 작성', description: '신청 정보 입력', icon: 'original' },
    { order: 4, title: '서류 제출', description: '필요 서류 등록', icon: 'documents' },
    { order: 5, title: '신청 완료', description: '접수 여부 확인', icon: 'apply' }
];

const $ = (selector) => document.querySelector(selector);

function iconBookmark() {
    return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z"></path></svg>';
}

function detailIcon(name) {
    const paths = {
        easy: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"></path>',
        original: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"></path><path d="M14 2v6h6"></path><path d="M8 13h8"></path><path d="M8 17h6"></path>',
        documents: '<path d="M9 12h6"></path><path d="M9 16h6"></path><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"></path><path d="M14 2v6h6"></path>',
        apply: '<path d="m9 11 3 3L22 4"></path><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>',
        target: '<circle cx="12" cy="8" r="4"></circle><path d="M4 21a8 8 0 0 1 16 0"></path>',
        amount: '<circle cx="12" cy="12" r="9"></circle><path d="M15 9.5c-.4-.8-1.3-1.3-2.5-1.3-1.5 0-2.5.8-2.5 1.9 0 3 5 1.2 5 4.1 0 1.1-1 1.9-2.5 1.9-1.2 0-2.1-.5-2.5-1.3"></path><path d="M12.5 6.5v11"></path>',
        period: '<circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path>',
        home: '<path d="m3 11 9-8 9 8"></path><path d="M5 10v10h14V10"></path><path d="M9 20v-6h6v6"></path>'
    };
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + paths[name] + '</svg>';
}

function toast(message) {
    const element = $('#toast');
    element.textContent = message;
    element.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => {
        element.hidden = true;
    }, 3000);
}

function getBenefit() {
    const id = new URLSearchParams(window.location.search).get('id') || '1';
    return mockBenefits[id];
}

function getPageMarkup(benefit) {
    if (!benefit) {
        return '<section class="card empty-state"><h1>혜택 정보를 찾을 수 없어요.</h1><p>목록에서 다시 혜택을 선택해주세요.</p><a class="btn btn-secondary" href="benefit-list.html">목록으로 돌아가기</a></section>';
    }

    return [
        '<section class="detail-title-row">',
        '<div class="benefit-info-bar"><div class="benefit-info-bar__main"><span class="detail-status">' + benefit.status + '</span><span>' + benefit.organization + '</span><span>지원 분야: ' + benefit.category + '</span><span>지역: ' + benefit.region + '</span></div>',
        '<div class="benefit-info-bar__tags">' + benefit.tags.map((tag) => '<button class="detail-tag" type="button" data-hashtag="' + tag + '">#' + tag + '</button>').join('') + '</div><small>데이터 기준: 2026. 11. 13.</small></div>',
        '<div class="detail-actions"><button id="save-button" class="icon-button icon-button--bookmark" type="button" aria-pressed="false" aria-label="관심 저장" title="관심 저장">' + iconBookmark() + '</button><button id="share-button" class="icon-button" type="button">공유하기</button></div></section>',
        '<div class="detail-layout"><section class="detail-main">',
        '<div class="tabs" role="tablist" aria-label="혜택 상세 정보">',
        '<button class="tab-button" role="tab" id="tab-easy" aria-selected="true" aria-controls="panel-easy" data-tab="easy">쉬운 말 보기</button>',
        '<button class="tab-button" role="tab" id="tab-original" aria-selected="false" aria-controls="panel-original" data-tab="original">원문 보기</button>',
        '<button class="tab-button" role="tab" id="tab-documents" aria-selected="false" aria-controls="panel-documents" data-tab="documents">필요 서류</button>',
        '<button class="tab-button" role="tab" id="tab-apply" aria-selected="false" aria-controls="panel-apply" data-tab="apply">신청 방법</button></div>',
        easyPanel(benefit), originalPanel(benefit), documentsPanel(), applyPanel(benefit),
        '</section>', sidebar(benefit), '</div>'
    ].join('');
}

function easyPanel(benefit) {
    const cards = [
        ['target', '지원 대상', '독립 거주 청년', '공고 기준의 자격 조건을 확인하세요.'],
        ['amount', '지원 내용', benefit.amount, '지원 금액은 공고 기준으로 달라질 수 있어요.'],
        ['period', '신청 기간', benefit.period, '마감일 전에 공식 사이트에서 신청하세요.']
    ];
    const rows = benefit.eligibility.conditions.map((condition) => '<tr class="eligibility-row"><th scope="row"><span class="condition-check">✓</span>' + condition.name + '</th><td>' + condition.detail + '</td><td><span class="eligibility-badge">' + benefit.eligibility.status + '</span></td><td><button class="text-button" type="button" aria-expanded="false" data-evidence="' + condition.evidence + '">근거 보기</button></td></tr><tr class="evidence-row" hidden><td colspan="4"><p class="evidence-copy"></p></td></tr>').join('');
    return '<section class="tab-panel" id="panel-easy" role="tabpanel" aria-labelledby="tab-easy"><div class="summary-box"><div><h2>3줄 핵심 요약</h2><ul>' + benefit.summary.map((summary) => '<li><span aria-hidden="true">✓</span>' + summary + '</li>').join('') + '</ul></div></div><div class="summary-cards">' + cards.map((card) => '<article class="card summary-card"><span class="summary-card__icon" aria-hidden="true">' + detailIcon(card[0]) + '</span><h3>' + card[1] + '</h3><strong>' + card[2] + '</strong><p>' + card[3] + '</p></article>').join('') + '</div><section class="card eligibility-card"><div class="eligibility-card__heading"><h2>내 자격 확인 결과</h2><span class="eligibility-result"><span aria-hidden="true">✓</span> ' + benefit.eligibility.status + '</span></div><div class="eligibility-table-wrap"><table class="eligibility-table"><colgroup><col class="eligibility-table__condition"><col class="eligibility-table__detail"><col class="eligibility-table__status"><col class="eligibility-table__evidence"></colgroup><thead><tr><th>조건</th><th>사용자 정보 및 기준</th><th>판정</th><th>근거</th></tr></thead><tbody>' + rows + '</tbody></table></div><p class="eligibility-notice">입력하신 정보를 바탕으로 신청 가능성을 안내해 드려요. 최종 지원 여부는 담당 기관의 심사를 통해 결정됩니다.</p></section></section>';
}

function originalPanel(benefit) {
    const sections = mockNotice.sections.map((section, index) => '<section class="notice-document__section"><h3>' + (index + 1) + '. ' + section[0] + '</h3><ul>' + section[1].map((item) => '<li>' + item + '</li>').join('') + '</ul></section>').join('');
    return '<section class="tab-panel" id="panel-original" role="tabpanel" aria-labelledby="tab-original" hidden><div class="notice-intro"><span class="notice-intro__icon" aria-hidden="true">i</span><p>해당 사업의 공식 공고문 원문을 확인할 수 있어요. 공고문 내용은 기관의 발표 기준을 따르며, 최신 내용은 아래 원문 링크에서 확인해주세요.</p></div><div class="notice-heading"><h2>공고문 원문</h2><div class="notice-heading__actions"><span>공식 공고 자료</span><div class="detail-actions"><button class="icon-button" type="button" data-official>공고문 바로가기</button><button class="icon-button" type="button" data-pdf>PDF 다운로드</button></div></div></div><div class="notice-viewer"><article class="notice-document"><header><p>' + mockNotice.organization + '</p><h3>' + mockNotice.title + '</h3><p>' + mockNotice.introduction + '</p><p class="notice-document__date">' + mockNotice.noticeDate + '<br>' + mockNotice.organization + '시장</p></header>' + sections + '<p class="notice-document__footnote">※ 본 문서는 화면 구현을 위한 예시 공고문이며 실제 공공기관의 공식 공고가 아닙니다.</p></article></div></section>';
}

function documentsPanel() {
    const required = mockDocuments.filter((document) => document.required).length;
    const cards = mockDocuments.map((document) => '<article class="document-item" data-document="' + document.id + '"><div class="document-item__basic"><span class="document-item__icon" aria-hidden="true">' + detailIcon('original') + '</span><div><h3>' + document.name + ' <span class="requirement-badge' + (document.required ? '' : ' optional') + '">' + (document.required ? '필수' : '선택') + '</span></h3><p>' + document.purpose + '</p></div></div><div class="document-item__issue"><p><strong>발급처</strong><span>' + document.issuer + '</span></p><p><strong>유효기간</strong><span>' + document.validity + '</span></p></div><button class="icon-button document-toggle" type="button" aria-expanded="false" data-document-guide="' + document.id + '">발급 방법 보기</button><div class="document-guide" hidden><h4>' + document.name + ' 발급 방법</h4><p>발급처: ' + document.issuer + '<br>' + document.online + '</p><ol>' + document.steps.map((step) => '<li>' + step + '</li>').join('') + '</ol><p>유효기간 및 주의사항은 실제 공고를 확인해 주세요.</p></div></article>').join('');
    return '<section class="tab-panel" id="panel-documents" role="tabpanel" aria-labelledby="tab-documents" hidden><div class="notice-intro"><span class="notice-intro__icon" aria-hidden="true">i</span><p>신청할 때 아래 서류를 준비해야 해요. 서류마다 필요한 이유와 발급 방법을 확인하고 미리 준비해보세요.</p></div><div class="document-heading"><h2>신청에 필요한 서류</h2><div class="document-summary"><span>전체 ' + mockDocuments.length + '건</span><span>필수 ' + required + '건</span><span>선택 ' + (mockDocuments.length - required) + '건</span></div></div><div class="document-list">' + cards + '</div><div class="documents-note"><h3>알아두세요!</h3><ul><li>모든 서류는 신청일 기준 유효기간을 확인해 주세요.</li><li>상황에 따라 추가 서류가 요구될 수 있어요. 기관이 직접 확인하는 서류는 제출이 생략될 수 있으므로 실제 공고를 확인해 주세요.</li></ul></div></section>';
}

function applyPanel(benefit) {
    const steps = mockApplicationSteps.map((step, index) => '<div class="application-process__item"><article class="application-step"><span class="application-step__icon">' + detailIcon(step.icon) + '</span><span class="application-step__number">' + step.order + '</span><h3>' + step.title + '</h3><p>' + step.description + '</p></article>' + (index < mockApplicationSteps.length - 1 ? '<span class="application-process__arrow" aria-hidden="true">→</span>' : '') + '</div>').join('');
    const disabled = benefit.applicationUrl ? '' : ' disabled aria-disabled="true"';
    return '<section class="tab-panel" id="panel-apply" role="tabpanel" aria-labelledby="tab-apply" hidden><div class="notice-intro"><span class="notice-intro__icon" aria-hidden="true">i</span><p>이 사업은 ' + benefit.applicationMethod + '할 수 있어요. 아래 절차를 따라 신청을 진행해보세요.</p></div><h2 class="apply-title">신청 절차</h2><div class="application-process">' + steps + '</div><section class="application-cta"><div><h3>공식 사이트에서 신청하기</h3><p>신청서를 작성하고 필요한 서류를 제출할 수 있어요.</p></div><button class="btn primary-button" type="button" data-application-url' + disabled + '>공식 신청 사이트 바로가기 ↗</button></section><section class="apply-notice"><h3>신청 시 유의사항</h3><ul><li>신청 전 지원 대상과 제출 서류를 확인해 주세요.</li><li>신청 기간이 지나면 접수가 제한될 수 있어요.</li><li>신청 완료 후 접수 여부를 꼭 확인해 주세요.</li><li>실제 신청 절차와 제출 방법은 공식 공고를 우선 확인해 주세요.</li></ul></section></section>';
}

function sidebar(benefit) {
    return '<aside class="detail-sidebar"><section class="card"><h2>신청 정보</h2><dl class="application-info"><div><dt>신청 상태</dt><dd>' + benefit.status + '</dd></div><div><dt>신청 기간</dt><dd>' + benefit.period + '</dd></div><div><dt>신청 방법</dt><dd>온라인 신청</dd></div><div><dt>담당 기관</dt><dd>' + benefit.organization + '</dd></div><div><dt>문의처</dt><dd>' + benefit.phone + '</dd></div></dl><button id="assistant-button" class="btn primary-button" type="button">AI 신청 도우미 시작하기</button></section><section class="card"><h2>복덩이에게 물어보세요</h2><p class="small-copy">공고문에 나온 내용을 바탕으로 답변해 드립니다.</p><div class="question-suggestions"><button type="button" data-question="휴학생도 신청할 수 있나요?">휴학생도 신청할 수 있나요?</button><button type="button" data-question="소득 조건이 어떻게 되나요?">소득 조건</button><button type="button" data-question="어떤 서류가 필요한가요?">필요 서류</button></div><form id="question-form" class="question-form"><label class="sr-only" for="question-input">공고문 질문</label><textarea id="question-input" placeholder="궁금한 내용을 입력하세요."></textarea><button class="btn primary-button" type="submit">질문 전송</button></form><div id="answer" class="answer" aria-live="polite" hidden></div></section></aside>';
}

function selectTab(target) {
    document.querySelectorAll('.tab-button').forEach((button) => {
        const selected = button === target;
        button.setAttribute('aria-selected', selected);
        $('#' + button.getAttribute('aria-controls')).hidden = !selected;
    });
}

async function copyUrl() {
    try {
        await navigator.clipboard.writeText(window.location.href);
        toast('현재 페이지 주소를 복사했어요.');
    } catch {
        toast('주소 복사에 실패했어요. 주소창에서 다시 복사해주세요.');
    }
}

function setup() {
    const benefit = getBenefit();
    let theme = localStorage.getItem('bokdeongi-theme') || 'light';
    const themeButton = $('[data-theme-toggle]');

    document.documentElement.dataset.theme = theme;
    document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1);
    themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
    themeButton.setAttribute('aria-pressed', theme === 'dark');
    $('#detail-page').innerHTML = getPageMarkup(benefit);
    if (!benefit) return;
    $('#detail-banner-copy').innerHTML = '<h1>' + benefit.title + '</h1><p>' + benefit.summary[0] + '</p>';

    document.addEventListener('click', (event) => {
        const tab = event.target.closest('.tab-button');
        if (tab) selectTab(tab);
        const evidence = event.target.closest('[data-evidence]');
        if (evidence) {
            const row = evidence.closest('.eligibility-row');
            const evidenceRow = row.nextElementSibling;
            const copy = evidenceRow.querySelector('.evidence-copy');
            const expanded = evidenceRow.hidden;
            copy.textContent = expanded ? evidence.dataset.evidence : '';
            evidenceRow.hidden = !expanded;
            row.classList.toggle('is-evidence-open', expanded);
            evidence.setAttribute('aria-expanded', String(expanded));
            evidence.textContent = expanded ? '근거 닫기' : '근거 보기';
        }
        const guide = event.target.closest('[data-document-guide]');
        if (guide) {
            document.querySelectorAll('.document-toggle').forEach((button) => {
                const panel = button.nextElementSibling;
                if (button !== guide) {
                    button.setAttribute('aria-expanded', 'false');
                    panel.hidden = true;
                }
            });
            const panel = guide.nextElementSibling;
            const expanded = guide.getAttribute('aria-expanded') === 'true';
            guide.setAttribute('aria-expanded', String(!expanded));
            guide.textContent = expanded ? '발급 방법 보기' : '발급 방법 닫기';
            panel.hidden = expanded;
        }
        const hashtag = event.target.closest('[data-hashtag]');
        if (hashtag) window.location.href = 'benefit-list.html?keyword=' + encodeURIComponent(hashtag.dataset.hashtag);
        const question = event.target.closest('[data-question]');
        if (question) $('#question-input').value = question.dataset.question;
        if (event.target.closest('#save-button')) {
            const button = $('#save-button');
            const saved = button.classList.toggle('is-saved');
            const label = saved ? '관심 저장됨' : '관심 저장';
            button.setAttribute('aria-pressed', saved);
            button.setAttribute('aria-label', label);
            button.setAttribute('title', label);
            button.innerHTML = iconBookmark();
            toast(saved ? '로그인 후 관심 혜택으로 저장할 수 있어요.' : '관심 저장을 해제했어요.');
        }
        if (event.target.closest('#share-button')) copyUrl();
        if (event.target.closest('#assistant-button')) window.location.href = 'assist-eligibility.html';
        if (event.target.closest('[data-official], [data-pdf]')) toast('현재 더미 공고문에서는 공식 공고 링크가 제공되지 않습니다.');
        if (event.target.closest('[data-application-url]')) {
            if (benefit.applicationUrl) window.open(benefit.applicationUrl, '_blank', 'noopener');
            else toast('현재 공식 신청 사이트 링크가 제공되지 않습니다.');
        }
        if (event.target.closest('[data-unavailable-page]')) {
            event.preventDefault();
            toast('이 페이지는 현재 준비 중입니다.');
        }
        const fontSize = event.target.closest('[data-font-size]');
        if (fontSize) {
            const current = Number(localStorage.getItem('bokdeongi-font-scale') || 1);
            const change = fontSize.dataset.fontSize === 'increase' ? 0.1 : -0.1;
            const scale = Math.min(1.2, Math.max(0.9, Number((current + change).toFixed(2))));
            localStorage.setItem('bokdeongi-font-scale', scale);
            document.documentElement.style.setProperty('--font-scale', scale);
        }
        if (event.target.closest('[data-theme-toggle]')) {
            theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = theme;
            localStorage.setItem('bokdeongi-theme', theme);
            themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
            themeButton.setAttribute('aria-pressed', theme === 'dark');
        }
        const menu = event.target.closest('.menu-toggle');
        if (menu) {
            const navigation = $('#site-navigation');
            const open = !navigation.classList.contains('is-open');
            navigation.classList.toggle('is-open', open);
            menu.setAttribute('aria-expanded', open);
            menu.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
        }
    });

    $('#question-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const question = $('#question-input').value.trim();
        if (!question) {
            toast('질문을 입력해주세요.');
            return;
        }
        const answer = $('#answer');
        answer.hidden = false;
        answer.textContent = '공고문 기준으로 확인이 필요한 내용입니다. 정확한 조건은 공식 공고와 담당 기관(' + benefit.phone + ')에 문의해주세요. 근거: 공고에 명시 없음';
    });
}

document.addEventListener('DOMContentLoaded', setup);
