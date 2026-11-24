(() => {
    const $ = (selector) => document.querySelector(selector);
    const assistantSteps = [
        ['자격 확인', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>'],
        ['서류 준비', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>'],
        ['서류 검토', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>'],
        ['신청 안내', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>']
    ];
    let application = null;
    let benefit = null;

    function escapeHtml(value) {
        return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
    }
    function text(value, fallback = '') { return typeof value === 'string' && value.trim() ? value.trim() : fallback; }
    function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
    function validBenefit(payload, benefitId) { return isRecord(payload) && Number(payload.id) === benefitId; }
    async function responseMessage(response) {
        try { const body = await response.json(); return text(body?.message || body?.detail); } catch { return ''; }
    }
    async function loadBenefit(benefitId) {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.refreshAuthentication !== 'function' || typeof auth.authenticatedFetch !== 'function') throw new Error('신청 서비스를 설정하지 못했습니다.');
        if (await auth.refreshAuthentication() !== 'authenticated') throw new Error('로그인 후 신청 도우미를 이용할 수 있습니다.');
        const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId));
        if (!response.ok) throw new Error((await responseMessage(response)) || '혜택 정보를 불러오지 못했습니다.');
        const payload = await response.json();
        if (!validBenefit(payload, benefitId)) throw new Error('혜택 정보 응답 형식이 올바르지 않습니다.');
        return payload;
    }
    function stepsMarkup() {
        return '<div class="assist-steps assist-steps--icon" aria-label="신청 도우미 진행 단계">' + assistantSteps.map((step, index) => '<div class="assist-step ' + (index === 0 ? 'is-complete' : index === 1 ? 'is-current' : '') + '"><span class="assist-step__icon">' + step[1] + '</span><span class="assist-step__label">' + step[0] + '</span></div>').join('') + '</div>';
    }
    function eligibilityRequiredMarkup() {
        const href = window.BokdeongiApplication.assistantUrl(1, application.id);
        return stepsMarkup() + '<section class="card empty-state"><h2>자격 확인을 먼저 완료해 주세요.</h2><p>서류 준비를 시작하려면 서버에 자격 확인 완료 상태가 저장되어 있어야 합니다.</p><a class="bk-btn bk-btn--primary bk-btn--md" href="' + href + '">자격 확인으로 이동</a></section>';
    }
    function render() {
        const title = text(benefit.title, '혜택 정보');
        const rawDocuments = text(benefit.requiredDocsText);
        const changedNotice = application.announcementChanged ? '<p class="assist-answer-summary" role="status">공고 정보가 신청을 시작한 뒤 변경되었습니다. 최신 공고 내용을 확인해 주세요.</p>' : '';
        const documents = rawDocuments
            ? '<p class="assist-answer-summary">' + escapeHtml(rawDocuments).replaceAll('\n', '<br>') + '</p>'
            : '<p class="assist-answer-summary">공고에 등록된 제출 서류 안내가 없습니다.</p>';
        $('#assist-content').innerHTML = stepsMarkup()
            + '<div class="assist-info"><strong aria-hidden="true">i</strong><span><strong>' + escapeHtml(title) + '</strong>의 공고 원문 서류 안내입니다.</span></div>'
            + '<div class="assist-documents-layout"><section class="assist-document-list"><div class="document-list-heading"><h2>공고문 제출 서류 안내</h2></div>' + documents + changedNotice + '</section>'
            + '<aside class="assist-documents-summary"><section class="assist-document-detail"><h2>서류 준비 기능 안내</h2><p class="assist-answer-summary">현재 서버는 공고 원문만 제공하고, 구조화된 필요 서류 목록·서류 ID·발급처·발급 링크를 조회하는 HTTP API는 제공하지 않습니다.</p><p class="assist-answer-summary">따라서 준비 완료 체크와 서류 검토 단계 이동은 서버 계약이 추가될 때까지 사용할 수 없습니다.</p></section><section class="progress-card"><h2>신청 진행률</h2><div class="progress-track"><span style="width:' + Math.max(0, Math.min(100, Number(application.progressPercent) || 0)) + '%"></span></div><strong>' + escapeHtml(String(application.completedItems)) + ' / ' + escapeHtml(String(application.totalItems)) + '</strong><p>서버가 계산한 전체 신청 진행률: ' + escapeHtml(String(application.progressPercent)) + '%</p></section></aside></div>'
            + '<div class="assist-actions"><a class="bk-btn bk-btn--outline bk-btn--md" href="' + window.BokdeongiApplication.assistantUrl(1, application.id) + '">이전 단계</a><button class="bk-btn bk-btn--primary bk-btn--md" type="button" disabled title="구조화된 서류 API가 필요합니다.">서류 검토로 이동</button></div>';
    }
    async function setup() {
        const container = $('#assist-content');
        application = await window.BokdeongiApplication.loadCurrentAssistantApplication(container);
        if (!application) return;
        if (application.appAnswers?.eligibilityConfirmed !== true) {
            container.innerHTML = eligibilityRequiredMarkup();
            return;
        }
        try {
            container.innerHTML = '<section class="card empty-state"><h2>서류 안내를 불러오는 중이에요.</h2><p>신청 정보와 공고문을 확인하고 있습니다.</p></section>';
            benefit = await loadBenefit(application.benefitId);
            render();
        } catch (error) {
            container.innerHTML = '<section class="card empty-state"><h2>서류 안내를 열 수 없어요.</h2><p>' + escapeHtml(error.message || '잠시 후 다시 시도해주세요.') + '</p><a class="bk-btn bk-btn--secondary bk-btn--md" href="benefit-list.html">혜택 목록으로 이동</a></section>';
        }
    }
    document.addEventListener('DOMContentLoaded', setup);
})();

document.addEventListener('DOMContentLoaded', () => {
    const $ = (selector) => document.querySelector(selector);
    let theme = localStorage.getItem('bokdeongi-theme') || 'light';
    const themeButton = $('[data-theme-toggle]');
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1);
    themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
    themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
    document.addEventListener('click', (event) => {
        const unavailable = event.target.closest('[data-unavailable-page]');
        if (unavailable) { event.preventDefault(); $('#toast').textContent = '이 페이지는 현재 준비 중입니다.'; $('#toast').hidden = false; return; }
        const fontSize = event.target.closest('[data-font-size]');
        if (fontSize) { const current = Number(localStorage.getItem('bokdeongi-font-scale') || 1); const change = fontSize.dataset.fontSize === 'increase' ? .1 : -.1; const scale = Math.min(1.2, Math.max(.9, Number((current + change).toFixed(2)))); localStorage.setItem('bokdeongi-font-scale', scale); document.documentElement.style.setProperty('--font-scale', scale); return; }
        if (event.target.closest('[data-theme-toggle]')) { theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark'; localStorage.setItem('bokdeongi-theme', theme); themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드'; themeButton.setAttribute('aria-pressed', String(theme === 'dark')); document.documentElement.dataset.theme = theme; return; }
        const menu = event.target.closest('.menu-toggle');
        if (menu) { const navigation = $('#site-navigation'); const open = navigation.classList.toggle('is-open'); menu.setAttribute('aria-expanded', String(open)); menu.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기'); }
    });
});
