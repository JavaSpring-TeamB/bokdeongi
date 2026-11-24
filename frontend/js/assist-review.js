(() => {
    const $ = (selector) => document.querySelector(selector);
    const assistantSteps = [
        ['자격 확인', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>'],
        ['서류 준비', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>'],
        ['서류 검토', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>'],
        ['신청 안내', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>']
    ];
    const state = { application: null, benefit: null, documents: null, missing: null, entries: [], selectedKey: null, uploadingKey: null, transientResults: new Map(), error: '', moving: false };

    function escapeHtml(value) {
        return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
    }
    function text(value, fallback = '') { return typeof value === 'string' && value.trim() ? value.trim() : fallback; }
    function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
    function safeUrl(value) {
        try { const url = new URL(value); return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : null; } catch { return null; }
    }
    async function responseMessage(response) {
        try { const body = await response.json(); return text(body?.message || body?.detail); } catch { return ''; }
    }
    async function loadBenefit(benefitId) {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.refreshAuthentication !== 'function' || typeof auth.authenticatedFetch !== 'function') throw new Error('신청 서비스를 설정하지 못했습니다.');
        if (await auth.refreshAuthentication() !== 'authenticated') throw new Error('로그인 후 신청 도우미를 이용할 수 있습니다.');
        const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId));
        if (!response.ok) throw new Error((await responseMessage(response)) || '혜택 정보를 불러오지 못했습니다.');
        const benefit = await response.json();
        if (!isRecord(benefit) || Number(benefit.id) !== benefitId) throw new Error('혜택 정보 응답 형식이 올바르지 않습니다.');
        return benefit;
    }
    function stepsMarkup() {
        return '<div class="assist-steps assist-steps--icon" aria-label="신청 도우미 진행 단계">' + assistantSteps.map((step, index) => '<div class="assist-step ' + (index < 2 ? 'is-complete' : index === 2 ? 'is-current' : '') + '"><span class="assist-step__icon">' + step[1] + '</span><span class="assist-step__label">' + step[0] + '</span></div>').join('') + '</div>';
    }
    function entriesFrom(documents) {
        const entries = [];
        for (const group of documents.groups || []) {
            for (const item of group?.items || []) {
                const id = window.BokdeongiApplication.parseApplicationId(item?.documentTypeId);
                if (id === null || !Object.prototype.hasOwnProperty.call(state.documents.states, String(id))) continue;
                entries.push({ key: String(id), documentTypeId: id, kind: 'document', group, item, name: text(item.name, '제출 서류') });
            }
        }
        (documents.forms || []).forEach((name, index) => entries.push({ key: 'form:' + index, kind: 'form', name: text(name, '신청 양식') }));
        (documents.unknown || []).forEach((name, index) => entries.push({ key: 'unknown:' + index, kind: 'unknown', name: text(name, '확인이 필요한 서류') }));
        return entries.filter((entry) => Object.prototype.hasOwnProperty.call(state.documents.states, entry.key));
    }
    function documentState(entry) { return state.documents.states[entry.key] || { prepared: false, reviewed: false, result: null, reason: null }; }
    function transientResult(entry) { return state.transientResults.get(entry.key) || null; }
    function statusFor(entry) {
        if (state.uploadingKey === entry.key) return ['검토 요청 중', 'pending'];
        const transient = transientResult(entry);
        if (transient?.result === 'RETRY') return ['다시 업로드 필요', 'danger'];
        const current = documentState(entry);
        if (current.result === 'READY' || current.reviewed) return ['검토 통과', 'success'];
        if (current.result === 'MISSING' || current.result === 'INVALID') return ['보완 필요', 'danger'];
        if (current.prepared && entry.item?.ocrSupported) return ['검토 필요', 'pending'];
        if (current.prepared) return ['준비 완료', 'success'];
        return ['준비 필요', 'pending'];
    }
    function isPrepared(entry) { return documentState(entry).prepared === true; }
    function allPrepared() { return state.entries.length > 0 && state.entries.every(isPrepared); }
    function selectedEntry() { return state.entries.find((entry) => entry.key === state.selectedKey) || state.entries[0] || null; }
    function errorMessage(error) {
        const message = window.BokdeongiApplication.messageFor(error);
        if (error?.code === 'CONSENT_REQUIRED') return message + ' 동의 등록 기능은 현재 준비 중입니다.';
        if (error?.code === 'UNREADABLE') return message + ' 파일을 더 선명하게 촬영하거나 다른 파일로 다시 시도해 주세요.';
        if (error?.code === 'AI_UNAVAILABLE') return message + ' 신청과 기존 검토 상태는 유지됩니다.';
        return message;
    }
    function reviewDescription(entry) {
        const transient = transientResult(entry);
        const current = documentState(entry);
        const result = transient || current;
        if (result?.result === 'RETRY') return { result: '재업로드 필요', reason: text(result.reason, '파일을 다시 확인해 주세요.'), action: '새 파일을 선택해 다시 검토해 주세요.' };
        if (result?.result === 'MISSING') return { result: '필수 항목 보완', reason: text(result.reason, '필수 항목을 확인하지 못했습니다.'), action: '부족한 항목이 보이도록 다시 발급하거나 촬영해 주세요.' };
        if (result?.result === 'INVALID') return { result: '발급일 확인 필요', reason: text(result.reason, '발급일 조건을 충족하지 못했습니다.'), action: '서버 안내에 따라 최근 서류로 다시 검토해 주세요.' };
        if (result?.result === 'READY' || current.reviewed) return { result: '검토 완료', reason: text(result.reason, '서버에서 검토를 완료했습니다.'), action: '추가 조치가 필요하지 않습니다.' };
        if (!entry.item?.ocrSupported) return { result: current.prepared ? '준비 완료' : '준비 필요', reason: '이 서류는 사진 OCR 검토 대상이 아닙니다.', action: '서류 준비 단계에서 준비 상태를 확인해 주세요.' };
        return { result: current.prepared ? '검토 필요' : '준비 필요', reason: current.prepared ? '파일을 올려 OCR 검토를 요청할 수 있습니다.' : '서류 준비를 먼저 완료해 주세요.', action: current.prepared ? 'JPG, PNG 또는 PDF 파일을 선택해 검토해 주세요.' : '서류 준비 화면으로 돌아가 준비 완료를 표시해 주세요.' };
    }
    function documentItemMarkup(entry) {
        const [status, statusClass] = statusFor(entry);
        const selected = entry.key === state.selectedKey ? ' is-selected' : '';
        const source = entry.kind === 'document' ? text(entry.group?.place, text(entry.item?.issuer, '발급처 확인 필요')) : entry.kind === 'form' ? '신청 양식' : '공고문 확인 필요';
        const action = entry.kind === 'document' && entry.item?.ocrSupported && isPrepared(entry) ? '검토하기' : '상세 보기';
        return '<div class="review-document' + selected + '"><button class="review-document__select" type="button" data-document-key="' + escapeHtml(entry.key) + '"><span><h3>' + escapeHtml(entry.name) + '</h3><p>' + escapeHtml(source) + '</p></span></button><span class="review-document__status"><span class="status-badge ' + statusClass + '">' + status + '</span></span><button class="bk-btn bk-btn--secondary bk-btn--sm review-document__action" type="button" data-row-action="' + escapeHtml(entry.key) + '"' + (state.uploadingKey ? ' disabled' : '') + '>' + action + '</button></div>';
    }
    function detailMarkup(entry) {
        if (!entry) return '<section class="review-detail-section"><h2 class="review-detail-title">선택한 서류 상세</h2><p>서버에서 검토할 서류를 찾지 못했습니다.</p></section>';
        const current = documentState(entry);
        const result = transientResult(entry) || current;
        const description = reviewDescription(entry);
        const alternatives = entry.kind === 'document' && Array.isArray(entry.item?.orDocs) && entry.item.orDocs.length
            ? '<div><dt>대체 가능 서류</dt><dd>' + escapeHtml(entry.item.orDocs.map((item) => text(item?.name)).filter(Boolean).join(', ')) + '</dd></div>' : '';
        const issueDate = result?.issueDate ? '<div><dt>확인된 발급일</dt><dd>' + escapeHtml(result.issueDate) + '</dd></div>' : '';
        const recognizedType = result?.recognizedType ? '<div><dt>인식된 서류</dt><dd>' + escapeHtml(result.recognizedType) + '</dd></div>' : '';
        const found = result?.foundFields?.length ? '<div><dt>확인 항목</dt><dd>' + escapeHtml(result.foundFields.join(', ')) + '</dd></div>' : '';
        const fields = result?.missingFields?.length ? '<div><dt>부족 항목</dt><dd>' + escapeHtml(result.missingFields.join(', ')) + '</dd></div>' : '';
        const external = entry.kind === 'document' ? safeUrl(entry.group?.url) : null;
        const uploadable = entry.kind === 'document' && entry.item?.ocrSupported && isPrepared(entry);
        return '<section class="review-detail-section" aria-labelledby="review-detail-title"><h2 id="review-detail-title" class="review-detail-title">선택한 서류 상세</h2><p class="review-detail__file">' + escapeHtml(entry.name) + '</p><dl class="review-detail-list"><div><dt>현재 상태</dt><dd>' + escapeHtml(statusFor(entry)[0]) + '</dd></div><div><dt>검토 결과</dt><dd>' + escapeHtml(description.result) + '</dd></div><div><dt>세부 사유</dt><dd>' + escapeHtml(description.reason) + '</dd></div><div><dt>권장 조치</dt><dd>' + escapeHtml(description.action) + '</dd></div>' + recognizedType + issueDate + found + fields + alternatives + '</dl><div class="review-upload-actions">' + (uploadable ? '<button class="bk-btn bk-btn--secondary bk-btn--md" type="button" data-upload' + (state.uploadingKey ? ' disabled' : '') + '>' + (state.uploadingKey === entry.key ? '검토 요청 중...' : '파일 선택 후 검토') + '</button>' : '') + (external ? '<a class="bk-btn bk-btn--outline bk-btn--md" href="' + escapeHtml(external) + '" target="_blank" rel="noopener noreferrer">발급처 열기</a>' : '') + '<a class="bk-btn bk-btn--secondary bk-btn--md" href="' + window.BokdeongiApplication.assistantUrl(2, state.application.id) + '">서류 준비로 이동</a></div></section>';
    }
    function missingMarkup() {
        if (state.missing.allReady) return '<section class="review-summary-section"><h2 class="review-detail-title">미충족 서류 안내</h2><p class="review-summary-message">서버 기준으로 보완할 서류가 없습니다.</p></section>';
        const groups = state.missing.groups || [];
        const groupMarkup = groups.map((group) => '<div class="review-summary"><div><dt>' + escapeHtml(text(group?.issuer, '발급처')) + '</dt><dd>' + (group?.online ? '온라인 발급 가능' : escapeHtml(text(group?.visitTip, '방문 발급 확인'))) + '</dd></div>' + (group?.documents || []).map((document) => '<div><dt>' + escapeHtml(text(document?.name, '서류')) + '</dt><dd>' + escapeHtml(text(document?.reason, '보완이 필요합니다.')) + (text(document?.howTo) ? '<br>' + escapeHtml(text(document.howTo)) : '') + (text(document?.alternatives) ? '<br>대체 안내: ' + escapeHtml(text(document.alternatives)) : '') + (document?.orDocs?.length ? '<br>대체 서류: ' + escapeHtml(document.orDocs.filter((name) => typeof name === 'string').join(', ')) : '') + '</dd></div>').join('') + (safeUrl(group?.issueUrl) ? '<p><a href="' + escapeHtml(safeUrl(group.issueUrl)) + '" target="_blank" rel="noopener noreferrer">발급처 열기</a></p>' : '') + '</div>').join('');
        const forms = (state.missing.forms || []).length ? '<p>신청 기관 서식: ' + escapeHtml(state.missing.forms.join(', ')) + '</p>' : '';
        const unknown = (state.missing.unknown || []).length ? '<p>공고문 확인 필요: ' + escapeHtml(state.missing.unknown.join(', ')) + '</p>' : '';
        return '<section class="review-summary-section"><h2 class="review-detail-title">미충족 서류 안내</h2>' + (groupMarkup || '<p>서버에서 발급처별 보완 안내를 찾지 못했습니다.</p>') + forms + unknown + '</section>';
    }
    function summaryMarkup() {
        const checked = state.entries.filter((entry) => documentState(entry).reviewed).length;
        const failed = state.entries.filter((entry) => ['MISSING', 'INVALID'].includes(documentState(entry).result)).length;
        return '<section class="review-summary-section"><h2 class="review-detail-title">검토 결과 요약</h2><dl class="review-summary"><div><dt>검토 완료</dt><dd>' + checked + '건</dd></div><div><dt>보완 필요</dt><dd>' + failed + '건</dd></div><div><dt>다음 단계 이동</dt><dd>' + (state.missing.allReady ? '가능' : '보완 후 가능') + '</dd></div></dl><p class="review-summary-message' + (state.missing.allReady ? '' : ' is-warning') + '">' + (state.missing.allReady ? '서버 기준으로 필요한 서류가 모두 준비되었습니다.' : '서버가 안내한 미충족 서류를 보완해 주세요.') + '</p></section>';
    }
    function render() {
        const selected = selectedEntry();
        if (selected) state.selectedKey = selected.key;
        const canMove = state.missing.allReady && !state.uploadingKey && !state.moving;
        const error = state.error ? '<p class="review-summary-message is-warning" role="alert">' + escapeHtml(state.error) + '</p>' : '';
        $('#assist-content').innerHTML = stepsMarkup() + '<div class="assist-info"><strong aria-hidden="true">i</strong><span><strong>' + escapeHtml(text(state.benefit.title, '혜택 정보')) + '</strong>의 실제 서류 검토 상태입니다. OCR 결과는 서버가 판단합니다.</span></div><div class="review-layout"><section class="review-card" aria-labelledby="submitted-documents-title"><div class="review-heading"><h2 id="submitted-documents-title">제출 서류 목록</h2><div class="review-counts"><span class="review-counts__pass">검토 완료 ' + state.entries.filter((entry) => documentState(entry).reviewed).length + '건</span><span class="review-counts__fix">미충족 ' + (state.missing.groups || []).reduce((count, group) => count + (group.documents || []).length, 0) + '건</span></div></div>' + (state.entries.length ? state.entries.map(documentItemMarkup).join('') : '<p>서버에서 검토할 서류가 없습니다.</p>') + '</section><aside class="review-summary-card">' + detailMarkup(selected) + summaryMarkup() + missingMarkup() + error + '</aside></div><p class="review-live-region" aria-live="polite">' + (state.uploadingKey ? '서류 검토 요청을 처리하고 있습니다.' : '') + '</p><div class="assist-actions"><a class="bk-btn bk-btn--outline bk-btn--md" data-previous href="' + window.BokdeongiApplication.assistantUrl(2, state.application.id) + '"' + (state.uploadingKey || state.moving ? ' aria-disabled="true" tabindex="-1"' : '') + '>이전 단계</a><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-next' + (canMove ? '' : ' disabled') + '>신청 안내로 이동</button></div>';
    }
    function bindInteractions() {
        document.addEventListener('click', (event) => {
            const select = event.target.closest('[data-document-key]');
            if (select) { state.selectedKey = select.dataset.documentKey; state.error = ''; render(); return; }
            const rowAction = event.target.closest('[data-row-action]');
            if (rowAction && !state.uploadingKey) { state.selectedKey = rowAction.dataset.rowAction; const entry = selectedEntry(); if (entry?.item?.ocrSupported && isPrepared(entry)) $('#review-file-input').click(); else render(); return; }
            if (event.target.closest('[data-upload]') && !state.uploadingKey) { $('#review-file-input').click(); return; }
            if (event.target.closest('[data-previous]') && (state.uploadingKey || state.moving)) { event.preventDefault(); return; }
            if (event.target.closest('[data-next]')) moveNext();
        });
        $('#review-file-input').addEventListener('change', (event) => {
            const file = event.target.files?.[0];
            event.target.value = '';
            if (file) upload(file);
        });
    }
    async function refreshServerState() {
        const [application, documents, missing] = await Promise.all([
            window.BokdeongiApplication.getApplication(state.application.id),
            window.BokdeongiApplication.getDocuments(state.application.id),
            window.BokdeongiApplication.getMissingDocuments(state.application.id)
        ]);
        state.application = application;
        state.documents = documents;
        state.missing = missing;
        state.entries = entriesFrom(documents.documents);
        if (!state.entries.some((entry) => entry.key === state.selectedKey)) state.selectedKey = state.entries[0]?.key || null;
    }
    async function upload(file) {
        const entry = selectedEntry();
        if (!entry?.item?.ocrSupported || !isPrepared(entry) || state.uploadingKey) return;
        state.error = '';
        state.uploadingKey = entry.key;
        render();
        try {
            const result = await window.BokdeongiApplication.reviewDocument(state.application.id, entry.documentTypeId, file);
            if (result.result === 'RETRY') {
                state.transientResults.set(entry.key, result);
            } else {
                state.transientResults.delete(entry.key);
                await refreshServerState();
            }
        } catch (error) {
            state.error = errorMessage(error);
        } finally {
            state.uploadingKey = null;
            render();
        }
    }
    async function moveNext() {
        if (state.uploadingKey || state.moving) return;
        state.moving = true;
        state.error = '';
        render();
        try {
            state.missing = await window.BokdeongiApplication.getMissingDocuments(state.application.id);
            if (!state.missing.allReady) {
                state.error = '서버에서 보완이 필요한 서류가 확인되었습니다. 안내를 확인해 주세요.';
                return;
            }
            state.application = await window.BokdeongiApplication.updateApplication(state.application.id, { step: 4 });
            window.location.assign(window.BokdeongiApplication.assistantUrl(4, state.application.id));
        } catch (error) {
            state.error = errorMessage(error);
        } finally {
            state.moving = false;
            render();
        }
    }
    function renderGate(title, description, href, label) {
        $('#assist-content').innerHTML = stepsMarkup() + '<section class="card empty-state"><h2>' + escapeHtml(title) + '</h2><p>' + escapeHtml(description) + '</p><a class="bk-btn bk-btn--primary bk-btn--md" href="' + href + '">' + escapeHtml(label) + '</a></section>';
    }
    async function load() {
        try {
            $('#assist-content').innerHTML = '<section class="card empty-state"><h2>서류 검토 정보를 불러오는 중이에요.</h2><p>신청 정보와 서류 상태를 확인하고 있습니다.</p></section>';
            const [benefit, documents, missing] = await Promise.all([
                loadBenefit(state.application.benefitId),
                window.BokdeongiApplication.getDocuments(state.application.id),
                window.BokdeongiApplication.getMissingDocuments(state.application.id)
            ]);
            state.benefit = benefit;
            state.documents = documents;
            state.missing = missing;
            state.entries = entriesFrom(documents.documents);
            state.selectedKey = state.entries[0]?.key || null;
            if (state.entries.length > 0 && !allPrepared()) {
                renderGate('서류 준비를 먼저 완료해 주세요.', '검토를 시작하려면 모든 필요한 서류가 준비 완료 상태여야 합니다.', window.BokdeongiApplication.assistantUrl(2, state.application.id), '서류 준비로 이동');
                return;
            }
            render();
        } catch (error) {
            $('#assist-content').innerHTML = '<section class="card empty-state"><h2>서류 검토 정보를 열 수 없어요.</h2><p>' + escapeHtml(errorMessage(error)) + '</p><button class="bk-btn bk-btn--secondary bk-btn--md" type="button" data-retry-load>다시 시도</button></section>';
            document.querySelector('[data-retry-load]')?.addEventListener('click', load);
        }
    }
    document.addEventListener('DOMContentLoaded', async () => {
        state.application = await window.BokdeongiApplication.loadCurrentAssistantApplication($('#assist-content'));
        if (!state.application) return;
        if (state.application.appAnswers?.eligibilityConfirmed !== true) {
            renderGate('자격 확인을 먼저 완료해 주세요.', '서류 검토를 시작하려면 서버에 자격 확인 완료 상태가 저장되어 있어야 합니다.', window.BokdeongiApplication.assistantUrl(1, state.application.id), '자격 확인으로 이동');
            return;
        }
        bindInteractions();
        load();
    });
})();
