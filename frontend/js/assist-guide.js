(() => {
    const $ = (selector) => document.querySelector(selector);
    const state = { application: null, benefit: null, guide: null, missing: null, completing: false };
    const applicationStatusPath = 'application-status.html';

    function text(value, fallback = '') { return typeof value === 'string' && value.trim() ? value.trim() : fallback; }
    function safeUrl(value) {
        try { const url = new URL(value); return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null; } catch { return null; }
    }
    function setText(selector, value) { const element = $(selector); if (element) element.textContent = value; }
    function showToast(message) { const toast = $('#toast'); toast.textContent = message; toast.hidden = false; window.setTimeout(() => { toast.hidden = true; }, 3200); }
    function errorMessage(error) { return window.BokdeongiApplication.messageFor(error); }
    async function loadBenefit(benefitId) {
        const auth = window.BokdeongiAuth;
        if (!auth || await auth.refreshAuthentication() !== 'authenticated') throw new Error('로그인 후 신청 안내를 이용할 수 있습니다.');
        const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId));
        if (!response.ok) throw new Error('혜택 정보를 불러오지 못했습니다.');
        const benefit = await response.json();
        if (!benefit || Number(benefit.id) !== benefitId) throw new Error('혜택 정보 응답 형식이 올바르지 않습니다.');
        return benefit;
    }
    function mode() {
        return ({ ONLINE: 'online', VISIT: 'visit', ONLINE_OR_VISIT: 'both' })[state.guide.method] || null;
    }
    function selectMethod(method) {
        const selected = $('#' + method + '-tab');
        const panel = $('#' + method + '-panel');
        if (!selected || !panel) return;
        document.querySelectorAll('[data-guide-tab]').forEach((tab) => { const active = tab === selected; tab.classList.toggle('is-active', active); tab.setAttribute('aria-selected', String(active)); });
        document.querySelectorAll('.guide-method-panel').forEach((item) => { item.hidden = item !== panel; });
    }
    function configureMethods() {
        const current = mode();
        const tabs = $('.guide-method-tabs'); const online = $('#online-panel'); const visit = $('#visit-panel'); const unavailable = $('[data-application-mode-unavailable]');
        tabs.hidden = current !== 'both'; online.hidden = current !== 'online' && current !== 'both'; visit.hidden = current !== 'visit'; unavailable.hidden = current !== null;
        setText('[data-application-method]', current === 'online' ? '온라인 신청' : current === 'visit' ? '방문 신청' : current === 'both' ? '온라인 또는 방문 신청' : '신청 방식 확인 필요');
        setText('.guide-method-card__description', current === 'both' ? '원하는 방법을 선택해 신청할 수 있어요.' : current === 'online' ? '온라인으로 신청할 수 있어요.' : current === 'visit' ? '방문 접수 안내를 확인해 주세요.' : '공식 공고에서 신청 방법을 확인해 주세요.');
        if (current === 'both') selectMethod('online');
    }
    function renderSteps() {
        const list = $('[data-online-process]');
        list.replaceChildren(...state.guide.steps.sort((a, b) => a.order - b.order).map((step) => { const item = document.createElement('li'); item.textContent = step.text; return item; }));
    }
    function renderNotes() {
        const list = $('.guide-notice ul');
        const notes = state.guide.notes.filter((note) => text(note));
        list.replaceChildren(...notes.map((note) => { const item = document.createElement('li'); item.textContent = note; return item; }));
        $('.guide-notice').hidden = notes.length === 0;
    }
    function renderLinks() {
        const applyUrl = safeUrl(state.guide.applyUrl); const detailUrl = safeUrl(state.guide.detailUrl);
        const button = $('[data-official-apply]'); const help = $('[data-official-apply-help]'); const detail = $('[data-detail-link]');
        button.disabled = !applyUrl; button.dataset.url = applyUrl || '';
        button.firstChild.textContent = applyUrl ? (text(state.guide.siteName, '공식 사이트') + '에서 신청하기 ') : '공식 신청 링크 없음 ';
        help.textContent = applyUrl ? '공식 신청 사이트가 새 탭에서 열립니다. 외부 사이트 방문만으로 신청 완료가 저장되지는 않습니다.' : '확인된 온라인 접수처가 없습니다. 공고 상세 또는 방문 안내를 확인해 주세요.';
        detail.hidden = !detailUrl; detail.href = detailUrl || '#';
    }
    function contactText(contacts) { return (contacts || []).map((contact) => [text(contact?.name), text(contact?.phone)].filter(Boolean).join(' · ')).filter(Boolean).join(', ') || '확인 가능한 문의처가 없습니다.'; }
    function renderVisit() {
        const visit = state.guide.visit;
        setText('[data-visit-places]', visit?.places?.filter((place) => text(place)).join(', ') || '확인 가능한 접수처가 없습니다.');
        setText('[data-visit-method]', text(visit?.applyMethodText, '공고에서 신청 방법을 확인해 주세요.'));
        setText('[data-visit-contacts]', contactText(visit?.contacts?.length ? visit.contacts : state.guide.contacts));
        setText('[data-visit-documents]', visit?.items?.filter((item) => text(item)).join(', ') || '서버에서 방문 준비물을 제공하지 않았습니다.');
        $('.guide-visit-help').textContent = visit?.placeFromOrg ? '접수처는 소관기관 정보로 표시되었습니다. 정확한 접수처는 문의처에 확인해 주세요.' : '';
    }
    function renderCopyCards() {
        const section = $('[data-copy-cards]'); const list = $('[data-copy-card-list]');
        const cards = state.guide.copyCards.filter((card) => text(card.value));
        section.hidden = cards.length === 0; list.replaceChildren();
        cards.forEach((card) => { const row = document.createElement('div'); const label = document.createElement('strong'); const value = document.createElement('span'); const button = document.createElement('button'); label.textContent = card.label; value.textContent = card.value; button.type = 'button'; button.className = 'bk-btn bk-btn--secondary bk-btn--sm'; button.dataset.copyValue = card.value; button.textContent = '복사'; row.append(label, value, button); list.append(row); });
    }
    function render() {
        setText('.assist-breadcrumb', '홈 › AI 신청 도우미 › ' + text(state.benefit.title, '신청 안내'));
        setText('[data-benefit-name]', text(state.benefit.title, '혜택 정보'));
        setText('[data-benefit-description]', text(state.benefit.summary, '서버에서 지원 내용을 제공하지 않았습니다.'));
        setText('[data-eligibility-status]', state.application.appAnswers.eligibilityConfirmed ? '신청 준비 완료' : '자격 확인 필요');
        setText('[data-application-period]', text(state.benefit.applyPeriodText, '신청 기간 확인 필요'));
        setText('[data-review-status]', state.missing.allReady ? '서버 기준 보완 필요 서류 없음' : '보완 필요 서류가 있습니다.');
        $('[data-review-status]').classList.toggle('guide-summary-list__success', state.missing.allReady);
        setText('[data-reviewed-document-count]', ''); $('[data-reviewed-documents]').replaceChildren();
        $('.guide-data-note').textContent = '서류 준비 및 검토 상태는 서버의 부족 서류 안내 결과를 기준으로 표시합니다.';
        configureMethods(); renderSteps(); renderNotes(); renderLinks(); renderVisit(); renderCopyCards();
        $('[data-previous-step]').href = window.BokdeongiApplication.assistantUrl(3, state.application.id);
    }
    async function copyValue(value) {
        try { if (!navigator.clipboard?.writeText) throw new Error(); await navigator.clipboard.writeText(value); showToast('복사했어요.'); } catch { showToast('복사하지 못했어요. 직접 선택해 복사해 주세요.'); }
    }
    async function complete() {
        if (state.completing || !window.confirm('공식 신청 절차를 실제로 완료했나요? 확인하면 신청 완료 상태를 저장합니다.')) return;
        state.completing = true; const button = $('[data-application-complete]'); button.disabled = true; button.textContent = '확인 중...';
        try {
            const [application, missing] = await Promise.all([window.BokdeongiApplication.getApplication(state.application.id), window.BokdeongiApplication.getMissingDocuments(state.application.id)]);
            if (application.appStatus !== 'IN_PROGRESS' || application.appAnswers?.eligibilityConfirmed !== true || !missing.allReady) throw new window.BokdeongiApplication.ApplicationApiError('CONFLICT', '현재 신청 상태에서는 완료 처리할 수 없습니다.');
            const updated = await window.BokdeongiApplication.updateApplicationStatus(application.id, 'SUBMITTED');
            if (updated.appStatus !== 'SUBMITTED') throw new window.BokdeongiApplication.ApplicationApiError('INVALID', '신청 완료 상태를 확인하지 못했습니다.');
            window.location.assign(applicationStatusPath);
        } catch (error) {
            if (error?.kind === 'CONFLICT') { try { const current = await window.BokdeongiApplication.getApplication(state.application.id); if (current.appStatus === 'SUBMITTED') { window.location.assign(applicationStatusPath); return; } } catch {} }
            showToast(errorMessage(error));
        } finally { state.completing = false; button.disabled = false; button.innerHTML = '신청했습니다 <span aria-hidden="true">✓</span>'; }
    }
    function gate(title, description, step, label) { $('#main-content').innerHTML = '<section class="card empty-state"><h1>' + title + '</h1><p>' + description + '</p><a class="bk-btn bk-btn--primary bk-btn--md" href="' + window.BokdeongiApplication.assistantUrl(step, state.application.id) + '">' + label + '</a></section>'; }
    async function load() {
        try {
            const [benefit, missing] = await Promise.all([loadBenefit(state.application.benefitId), window.BokdeongiApplication.getMissingDocuments(state.application.id)]);
            state.benefit = benefit; state.missing = missing;
            if (!state.application.appAnswers?.eligibilityConfirmed) return gate('자격 확인을 먼저 완료해 주세요.', '신청 안내를 시작하려면 자격 확인이 필요합니다.', 1, '자격 확인으로 이동');
            if (!missing.allReady) return gate('서류 검토를 먼저 완료해 주세요.', '보완이 필요한 서류가 있습니다.', 3, '서류 검토로 이동');
            state.guide = await window.BokdeongiApplication.getApplicationGuide(state.application.id); render();
        } catch (error) { $('#main-content').innerHTML = '<section class="card empty-state"><h1>신청 안내를 열 수 없어요.</h1><p>' + errorMessage(error) + '</p><button type="button" class="bk-btn bk-btn--secondary bk-btn--md" data-retry>다시 시도</button></section>'; $('[data-retry]')?.addEventListener('click', load); }
    }
    document.addEventListener('DOMContentLoaded', async () => {
        state.application = await window.BokdeongiApplication.loadCurrentAssistantApplication($('#main-content')); if (!state.application) return;
        document.addEventListener('click', (event) => { const tab = event.target.closest('[data-guide-tab]'); if (tab) return selectMethod(tab.dataset.guideTab); const apply = event.target.closest('[data-official-apply]'); if (apply?.dataset.url) return window.open(apply.dataset.url, '_blank', 'noopener,noreferrer'); const copy = event.target.closest('[data-copy-value]'); if (copy) return copyValue(copy.dataset.copyValue); if (event.target.closest('[data-application-complete]')) complete(); });
        load();
    });
})();
