(() => {
    const $ = (selector) => document.querySelector(selector);
    const assistantSteps = [
        ['자격 확인', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>'],
        ['서류 준비', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>'],
        ['서류 검토', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>'],
        ['신청 안내', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>']
    ];
    const state = { application: null, benefit: null, documents: null, entries: [], selectedKey: null, records: [], recordsStatus: 'checking', recordsError: '', recordConsentStatus: 'checking', recordRequest: null, recordRefreshRequested: false, recordSavingKey: null, recordFormError: '', recordNotice: '', recordIssueDates: new Map(), localPrepared: new Map(), pending: new Map(), failed: new Map(), moving: false, version: 0 };

    function escapeHtml(value) {
        return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
    }
    function text(value, fallback = '') { return typeof value === 'string' && value.trim() ? value.trim() : fallback; }
    function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
    function validBenefit(payload, benefitId) { return isRecord(payload) && Number(payload.id) === benefitId; }
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
        const payload = await response.json();
        if (!validBenefit(payload, benefitId)) throw new Error('혜택 정보 응답 형식이 올바르지 않습니다.');
        return payload;
    }
    function stepsMarkup() {
        return '<div class="assist-steps assist-steps--icon" aria-label="신청 도우미 진행 단계">' + assistantSteps.map((step, index) => '<div class="assist-step ' + (index === 0 ? 'is-complete' : index === 1 ? 'is-current' : '') + '"><span class="assist-step__icon">' + step[1] + '</span><span class="assist-step__label">' + step[0] + '</span></div>').join('') + '</div>';
    }
    function eligibilityRequiredMarkup() {
        const href = window.BokdeongiApplication.assistantUrl(1, state.application.id);
        return stepsMarkup() + '<section class="card empty-state"><h2>자격 확인을 먼저 완료해 주세요.</h2><p>서류 준비를 시작하려면 서버에 자격 확인 완료 상태가 저장되어 있어야 합니다.</p><a class="bk-btn bk-btn--primary bk-btn--md" href="' + href + '">자격 확인으로 이동</a></section>';
    }
    function entriesFrom(documents) {
        const entries = [];
        const groups = Array.isArray(documents?.groups) ? documents.groups : [];
        groups.forEach((group) => {
            const items = Array.isArray(group?.items) ? group.items : [];
            items.forEach((item) => {
                const id = Number(item?.documentTypeId);
                if (!Number.isSafeInteger(id) || id < 1) return;
                entries.push({ key: String(id), kind: 'document', group, item, name: text(item.name, '제출 서류') });
            });
        });
        (Array.isArray(documents?.forms) ? documents.forms : []).forEach((name, index) => entries.push({ key: 'form:' + index, kind: 'form', name: text(name, '신청 양식') }));
        (Array.isArray(documents?.unknown) ? documents.unknown : []).forEach((name, index) => entries.push({ key: 'unknown:' + index, kind: 'unknown', name: text(name, '확인이 필요한 서류') }));
        return entries.filter((entry) => Object.prototype.hasOwnProperty.call(state.documents.states, entry.key));
    }
    function displayState(key) {
        const server = state.documents?.states?.[key] || { prepared: false, reviewed: false };
        return { ...server, prepared: state.localPrepared.has(key) ? state.localPrepared.get(key) : server.prepared };
    }
    function isPending(key) { return (state.pending.get(key) || 0) > 0; }
    function selectedEntry() { return state.entries.find((entry) => entry.key === state.selectedKey) || state.entries[0] || null; }
    function recordDate(value) { return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : '확인할 수 없음'; }
    function recordValidity(record) {
        if (record.validityDays === null || record.valid === null) return '유효기간 기준 없음';
        return record.valid ? '유효' : '만료';
    }
    function documentRecordsMarkup() {
        if (state.recordsStatus === 'checking') return '<section class="remembered-records" aria-live="polite"><h3>기억한 서류 기록</h3><p>동의 상태를 확인하고 있습니다.</p></section>';
        if (state.recordsStatus === 'consent-required') return '<section class="remembered-records"><h3>기억한 서류 기록</h3><p>서류 유효기간 기억에 동의하면 저장된 발급일과 유효기간을 확인할 수 있어요.</p><a class="bk-btn bk-btn--outline bk-btn--sm" href="./my-page.html">마이페이지 동의 관리로 이동</a></section>';
        if (state.recordsStatus === 'error') return '<section class="remembered-records"><h3>기억한 서류 기록</h3><p role="alert">' + escapeHtml(state.recordsError || '서류 기록을 불러오지 못했습니다.') + '</p><button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-record-retry>다시 불러오기</button></section>';
        if (!state.records.length) return '<section class="remembered-records"><h3>기억한 서류 기록</h3><p>저장된 서류 기록이 없습니다.</p></section>';
        return '<section class="remembered-records"><h3>기억한 서류 기록</h3><ul>' + state.records.map((record) => {
            const matched = state.entries.some((entry) => entry.kind === 'document' && Number(entry.item.documentTypeId) === Number(record.documentTypeId));
            const validity = record.validityDays === null ? '기준 없음' : '발급 후 ' + record.validityDays + '일';
            return '<li><strong>' + escapeHtml(record.documentName) + '</strong>' + (matched ? '<span class="remembered-records__match">현재 신청 서류</span>' : '')
                + '<dl><div><dt>발급일</dt><dd>' + escapeHtml(recordDate(record.issueDate)) + '</dd></div>'
                + '<div><dt>유효기간</dt><dd>' + escapeHtml(validity) + '</dd></div>'
                + '<div><dt>만료일</dt><dd>' + escapeHtml(record.expiresAt ? recordDate(record.expiresAt) : '없음') + '</dd></div>'
                + '<div><dt>상태</dt><dd>' + escapeHtml(recordValidity(record)) + '</dd></div></dl></li>';
        }).join('') + '</ul></section>';
    }
    function recordRegistrationMarkup(entry) {
        if (entry.kind !== 'document' || state.recordConsentStatus !== 'agreed') return '';
        const id = Number(entry.item.documentTypeId);
        const key = String(id);
        const saving = state.recordSavingKey === key;
        const disabled = saving || state.recordsStatus === 'checking';
        const value = state.recordIssueDates.get(key) || '';
        const error = state.recordFormError ? '<p class="remembered-records__error" role="alert">' + escapeHtml(state.recordFormError) + '</p>' : '';
        const notice = state.recordNotice ? '<p class="remembered-records__notice" role="status">' + escapeHtml(state.recordNotice) + '</p>' : '';
        return '<section class="record-registration"><h3>발급일 기억하기</h3><label for="record-issue-date">서류 발급일</label><input id="record-issue-date" type="date" data-record-issue-date="' + key + '" max="' + window.BokdeongiApplication.currentSeoulDate() + '" value="' + escapeHtml(value) + '"' + (disabled ? ' disabled' : '') + '><p class="record-registration__help">저장된 기록은 신청 준비 완료나 OCR 검토 상태를 바꾸지 않습니다.</p>' + error + notice + '<button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-record-submit="' + key + '"' + (disabled ? ' disabled' : '') + '>' + (saving ? '저장 중...' : '발급일 등록') + '</button></section>';
    }
    function allPrepared() { return state.documents !== null && state.entries.every((entry) => displayState(entry.key).prepared); }
    function canMoveNext() { return allPrepared() && state.pending.size === 0 && state.failed.size === 0 && !state.moving; }
    function statusLabel(entry) {
        const documentState = displayState(entry.key);
        if (isPending(entry.key)) return ['저장 중', 'alternative'];
        if (state.failed.has(entry.key)) return ['저장 실패', 'needed'];
        return documentState.prepared ? ['준비 완료', 'ready'] : ['준비 필요', 'needed'];
    }
    function optionMarkup(entry) {
        const [label, className] = statusLabel(entry);
        const selected = entry.key === state.selectedKey;
        const support = entry.kind === 'document' ? (entry.item?.ocrSupported ? 'AI 서류 검토 지원' : '직접 준비·확인') : '별도 확인 필요';
        return '<button type="button" class="assist-document-option ' + (selected ? 'is-selected' : '') + '" data-document-key="' + escapeHtml(entry.key) + '" aria-pressed="' + String(selected) + '"><span><h3>' + escapeHtml(entry.name) + '</h3><small class="document-ocr-label">' + support + '</small></span><span class="doc-state ' + className + '">' + label + '</span></button>';
    }
    function listMarkup() {
        let previousGroup = null;
        let previousKind = null;
        return state.entries.map((entry) => {
            let heading = '';
            if (entry.kind === 'document' && entry.group !== previousGroup) {
                heading = '<div class="agency-documents"><div>' + escapeHtml(text(entry.group?.place, '발급처 확인 필요')) + '</div></div>';
                previousGroup = entry.group;
                previousKind = entry.kind;
            } else if (entry.kind !== previousKind) {
                heading = '<div class="agency-documents"><div>' + (entry.kind === 'form' ? '신청 기관 서식' : '공고문 확인 서류') + '</div></div>';
                previousKind = entry.kind;
            }
            return heading + optionMarkup(entry);
        }).join('');
    }
    function detailMarkup(entry) {
        if (!entry) return '<section class="assist-document-detail"><h2>서류 상세</h2><p class="assist-answer-summary">서버에서 준비할 서류를 찾지 못했습니다.</p></section>';
        const documentState = displayState(entry.key);
        const pending = isPending(entry.key);
        const failed = state.failed.get(entry.key);
        const groupUrl = entry.kind === 'document' ? safeUrl(entry.group?.url) : null;
        const alternatives = entry.kind === 'document' && Array.isArray(entry.item?.orDocs) && entry.item.orDocs.length
            ? '<div><dt>대체 가능 서류</dt><dd>' + escapeHtml(entry.item.orDocs.map((item) => text(item?.name, '')).filter(Boolean).join(', ')) + '</dd></div>' : '';
        const sourceName = entry.kind === 'document' && text(entry.item?.sourceName) && text(entry.item?.sourceName) !== entry.name
            ? '<div><dt>공고문 표기</dt><dd>' + escapeHtml(text(entry.item.sourceName)) + '</dd></div>' : '';
        const rows = entry.kind === 'document'
            ? '<div><dt>발급처</dt><dd>' + escapeHtml(text(entry.group?.place, text(entry.item?.issuer, '확인 필요'))) + '</dd></div>'
                + '<div><dt>발급 방법</dt><dd>' + escapeHtml(text(entry.item?.issueMethod, entry.item?.onlineAvailable ? '온라인 발급 가능' : '발급처 확인 필요')) + '</dd></div>'
                + sourceName + (entry.item?.validityDays ? '<div><dt>유효 기간</dt><dd>발급 후 ' + escapeHtml(String(entry.item.validityDays)) + '일 이내</dd></div>' : '') + alternatives
            : '<div><dt>분류</dt><dd>' + (entry.kind === 'form' ? '신청 양식' : '공고문 원문 확인') + '</dd></div>';
        const reviewNote = entry.kind === 'document'
            ? entry.item?.ocrSupported ? 'AI 서류 검토 지원: 준비 완료 후 3단계에서 PDF 또는 이미지 파일을 첨부해 검토받을 수 있습니다.'
                : 'AI 검토 미지원: 직접 서류를 준비하고 확인해 주세요. 준비 완료 체크는 파일 제출이나 OCR 검토 통과가 아닙니다.'
            : '별도 확인 필요: 이 서류는 AI 파일 검토 대상이 아닙니다. 공고문이나 담당 기관에서 제출 방법을 확인해 주세요.';
        const checked = documentState.prepared ? ' checked' : '';
        const disabled = pending || state.moving ? ' disabled' : '';
        const external = groupUrl ? '<a class="bk-btn bk-btn--outline bk-btn--md" href="' + escapeHtml(groupUrl) + '" target="_blank" rel="noopener noreferrer">발급처 열기</a>' : '';
        const actions = '<div class="document-detail-actions"><label class="bk-btn bk-btn--outline bk-btn--md document-prepared-control"><input type="checkbox" data-document-prepared="' + escapeHtml(entry.key) + '"' + checked + disabled + '> 준비 완료</label>' + external + '</div>';
        const error = failed ? '<p class="assist-answer-summary" role="alert">' + escapeHtml(failed.message) + '</p><button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-retry-document="' + escapeHtml(entry.key) + '">저장 다시 시도</button>' : '';
        return '<section class="assist-document-detail"><h2>서류 상세</h2><h3>' + escapeHtml(entry.name) + '</h3><dl class="selected-info">' + rows + '</dl><p class="document-review-note">' + escapeHtml(reviewNote) + '</p>' + actions + recordRegistrationMarkup(entry) + error + '</section>';
    }
    function progressMarkup() {
        const percent = Math.max(0, Math.min(100, Number(state.application.progressPercent) || 0));
        return '<section class="progress-card"><h2>신청 진행률</h2><div class="progress-track"><span style="width:' + percent + '%"></span></div><strong>' + escapeHtml(String(state.application.completedItems)) + ' / ' + escapeHtml(String(state.application.totalItems)) + '</strong><p>서버가 계산한 전체 신청 진행률: ' + escapeHtml(String(state.application.progressPercent)) + '%</p></section>';
    }
    function contactsMarkup() {
        const contacts = Array.isArray(state.documents?.documents?.contacts) ? state.documents.documents.contacts : [];
        if (!contacts.length) return '';
        return '<section class="progress-card"><h2>문의처</h2>' + contacts.map((contact) => '<p>' + escapeHtml(text(contact?.name, '문의처')) + (text(contact?.phone) ? ' · ' + escapeHtml(text(contact.phone)) : '') + '</p>').join('') + '</section>';
    }
    function moveHint() {
        if (state.entries.length === 0) return '서버에서 서류 준비 완료 조건을 확인할 수 없습니다.';
        if (state.pending.size) return '서류 준비 상태를 저장하고 있습니다.';
        if (state.failed.size) return '저장에 실패한 서류를 다시 저장해 주세요.';
        return allPrepared() ? '모든 서류가 준비되었습니다.' : '모든 서류를 준비 완료로 표시하면 다음 단계로 이동할 수 있습니다.';
    }
    function render() {
        const listScrollTop = $('[data-document-option-list]')?.scrollTop || 0;
        const entry = selectedEntry();
        if (entry) state.selectedKey = entry.key;
        const changedNotice = state.application.announcementChanged ? '<p class="assist-answer-summary" role="status">공고 정보가 신청을 시작한 뒤 변경되었습니다. 최신 공고 내용을 확인해 주세요.</p>' : '';
        $('#assist-content').innerHTML = stepsMarkup()
            + '<div class="assist-info"><strong aria-hidden="true">i</strong><span><strong>' + escapeHtml(text(state.benefit.title, '혜택 정보')) + '</strong>의 제출 서류와 준비 상태입니다.</span></div>'
            + '<div class="assist-documents-layout"><section class="assist-document-list"><div class="document-list-heading"><h2>제출 서류</h2><div class="document-counts"><span>' + state.entries.filter((item) => displayState(item.key).prepared).length + ' / ' + state.entries.length + ' 준비</span></div></div>' + changedNotice + (state.entries.length ? '<div class="document-option-list" data-document-option-list role="region" aria-label="제출 서류 목록" tabindex="0">' + listMarkup() + '</div>' : '<p class="assist-answer-summary">준비할 서류가 없습니다. 다음 단계로 이동할 수 있어요.</p>') + documentRecordsMarkup() + '</section><aside class="assist-documents-summary">' + detailMarkup(entry) + progressMarkup() + contactsMarkup() + '</aside></div>'
            + '<p class="assist-answer-summary" role="status">' + moveHint() + '</p>' + (state.failed.get('_next') ? '<p class="assist-answer-summary" role="alert">' + escapeHtml(state.failed.get('_next').message) + '</p>' : '') + '<div class="assist-actions"><a class="bk-btn bk-btn--outline bk-btn--md"' + (state.pending.size || state.moving ? ' aria-disabled="true" tabindex="-1"' : '') + ' href="' + window.BokdeongiApplication.assistantUrl(1, state.application.id) + '">이전 단계</a><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-next-step' + (canMoveNext() ? '' : ' disabled') + '>서류 검토로 이동</button></div>';
        const optionList = $('[data-document-option-list]');
        if (optionList) optionList.scrollTop = listScrollTop;
        bindEvents();
    }
    function bindEvents() {
        document.querySelectorAll('[data-document-key]').forEach((button) => button.addEventListener('click', () => { state.selectedKey = button.dataset.documentKey; state.recordFormError = ''; state.recordNotice = ''; render(); }));
        document.querySelector('[data-document-prepared]')?.addEventListener('change', (event) => savePrepared(event.target.dataset.documentPrepared, event.target.checked));
        document.querySelector('[data-retry-document]')?.addEventListener('click', (event) => savePrepared(event.currentTarget.dataset.retryDocument, state.localPrepared.get(event.currentTarget.dataset.retryDocument)));
        document.querySelector('[data-next-step]')?.addEventListener('click', moveNext);
        document.querySelector('[data-record-retry]')?.addEventListener('click', loadDocumentRecords);
        document.querySelector('[data-record-issue-date]')?.addEventListener('input', (event) => {
            state.recordIssueDates.set(event.currentTarget.dataset.recordIssueDate, event.currentTarget.value);
        });
        document.querySelector('[data-record-submit]')?.addEventListener('click', (event) => {
            void saveDocumentRecord(event.currentTarget.dataset.recordSubmit);
        });
    }
    async function loadDocumentRecords({ force = false } = {}) {
        if (state.recordRequest) {
            if (!force) return state.recordRequest;
            await state.recordRequest;
        }
        if (state.recordRequest) return state.recordRequest;
        const request = (async () => {
            state.records = [];
            state.recordsError = '';
            state.recordsStatus = 'checking';
            state.recordConsentStatus = 'checking';
            render();
            try {
                const auth = window.BokdeongiAuth;
                const version = window.BokdeongiConsentDocuments?.versionFor('DOC_RECORD');
                if (!auth || typeof auth.getConsentStatus !== 'function' || !version) throw new Error('동의 상태 확인 기능을 사용할 수 없습니다.');
                const consentResponse = await auth.getConsentStatus();
                if (!consentResponse.ok) throw new Error('동의 상태를 확인하지 못했습니다.');
                const consentPayload = await consentResponse.json();
                const consent = Array.isArray(consentPayload?.consents)
                    ? consentPayload.consents.find((item) => item?.consentType === 'DOC_RECORD') : null;
                if (consent?.agreed !== true || consent.docVersion !== version) {
                    state.recordsStatus = 'consent-required';
                    state.recordConsentStatus = 'required';
                    return;
                }
                state.recordConsentStatus = 'agreed';
                const payload = await window.BokdeongiApplication.getDocumentRecords();
                state.records = payload.records;
                state.recordsStatus = 'ready';
            } catch (error) {
                state.records = [];
                if (error?.code === 'CONSENT_REQUIRED') {
                    state.recordsStatus = 'consent-required';
                    state.recordConsentStatus = 'required';
                } else {
                    state.recordsStatus = 'error';
                    if (state.recordConsentStatus !== 'agreed') state.recordConsentStatus = 'error';
                    state.recordsError = error?.message || window.BokdeongiApplication.messageFor(error);
                }
            } finally {
                state.recordRequest = null;
                render();
                if (state.recordRefreshRequested && document.visibilityState === 'visible') {
                    state.recordRefreshRequested = false;
                    void loadDocumentRecords();
                }
            }
        })();
        state.recordRequest = request;
        return request;
    }
    async function saveDocumentRecord(documentTypeId) {
        const key = String(documentTypeId || '');
        const entry = state.entries.find((item) => item.kind === 'document' && String(item.item.documentTypeId) === key);
        if (!entry || state.recordConsentStatus !== 'agreed' || state.recordSavingKey !== null) return;
        const issueDate = state.recordIssueDates.get(key) || '';
        if (!window.BokdeongiApplication.isValidDocumentIssueDate(issueDate)) {
            state.recordFormError = issueDate ? '발급일은 올바른 날짜이며 오늘 또는 이전 날짜여야 합니다.' : '서류 발급일을 입력해 주세요.';
            state.recordNotice = '';
            render();
            return;
        }
        state.recordSavingKey = key;
        state.recordFormError = '';
        state.recordNotice = '';
        render();
        try {
            await window.BokdeongiApplication.registerDocumentRecord(Number(key), issueDate);
            state.recordIssueDates.delete(key);
            await loadDocumentRecords({ force: true });
            if (state.recordConsentStatus === 'required') {
                state.recordNotice = '';
            } else if (state.recordsStatus === 'ready') {
                state.recordNotice = '발급일을 저장하고 최신 기록을 불러왔어요.';
            } else {
                state.recordNotice = '발급일은 저장했지만 최신 기록을 불러오지 못했습니다. 기록 영역에서 다시 시도해 주세요.';
            }
        } catch (error) {
            if (error?.code === 'CONSENT_REQUIRED') {
                state.records = [];
                state.recordsStatus = 'consent-required';
                state.recordConsentStatus = 'required';
                state.recordFormError = '';
                state.recordNotice = '';
            } else {
                state.recordFormError = window.BokdeongiApplication.messageFor(error);
            }
        } finally {
            state.recordSavingKey = null;
            render();
        }
    }
    async function refreshApplication(version) {
        try {
            const refreshed = await window.BokdeongiApplication.getApplication(state.application.id);
            if (version === state.version) state.application = refreshed;
        } catch { /* 문서 저장은 이미 성공했으므로 다음 화면 갱신 때 다시 조회한다. */ }
    }
    async function savePrepared(key, prepared) {
        if (!state.entries.some((entry) => entry.key === key) || typeof prepared !== 'boolean') return;
        state.version += 1;
        const version = state.version;
        state.localPrepared.set(key, prepared);
        state.failed.delete(key);
        state.pending.set(key, (state.pending.get(key) || 0) + 1);
        render();
        try {
            state.documents = await window.BokdeongiApplication.updateDocumentPrepared(state.application.id, key, prepared);
            if (state.localPrepared.get(key) === prepared) state.localPrepared.delete(key);
            await refreshApplication(version);
        } catch (error) {
            state.failed.set(key, { prepared, message: window.BokdeongiApplication.messageFor(error) });
        } finally {
            const remaining = (state.pending.get(key) || 1) - 1;
            if (remaining > 0) state.pending.set(key, remaining); else state.pending.delete(key);
            render();
        }
    }
    async function moveNext() {
        if (!canMoveNext()) return;
        state.moving = true;
        render();
        try {
            state.application = await window.BokdeongiApplication.updateApplication(state.application.id, { step: 3 });
            window.location.assign(window.BokdeongiApplication.assistantUrl(3, state.application.id));
        } catch (error) {
            state.moving = false;
            state.failed.set('_next', { message: window.BokdeongiApplication.messageFor(error) });
            render();
        }
    }
    function showLoadError(error) {
        $('#assist-content').innerHTML = '<section class="card empty-state"><h2>서류 안내를 열 수 없어요.</h2><p>' + escapeHtml(window.BokdeongiApplication.messageFor(error)) + '</p><button class="bk-btn bk-btn--secondary bk-btn--md" type="button" data-retry-load>다시 시도</button></section>';
        document.querySelector('[data-retry-load]')?.addEventListener('click', loadDocuments);
    }
    async function loadDocuments() {
        try {
            $('#assist-content').innerHTML = '<section class="card empty-state"><h2>서류 안내를 불러오는 중이에요.</h2><p>신청 정보와 제출 서류를 확인하고 있습니다.</p></section>';
            const [benefit, documents] = await Promise.all([loadBenefit(state.application.benefitId), window.BokdeongiApplication.getDocuments(state.application.id)]);
            state.benefit = benefit;
            state.documents = documents;
            state.entries = entriesFrom(documents.documents);
            state.selectedKey = state.entries[0]?.key || null;
            render();
            void loadDocumentRecords();
        } catch (error) { showLoadError(error); }
    }
    async function setup() {
        const container = $('#assist-content');
        state.application = await window.BokdeongiApplication.loadCurrentAssistantApplication(container);
        if (!state.application) return;
        if (state.application.appAnswers?.eligibilityConfirmed !== true) { container.innerHTML = eligibilityRequiredMarkup(); return; }
        loadDocuments();
    }
    document.addEventListener('DOMContentLoaded', setup);
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden' && state.application) {
            state.records = [];
            state.recordsStatus = 'checking';
        } else if (document.visibilityState === 'visible' && state.application) {
            if (state.recordRequest) state.recordRefreshRequested = true;
            else { state.recordRefreshRequested = false; void loadDocumentRecords(); }
        }
    });
})();

document.addEventListener('DOMContentLoaded', () => {
    document.addEventListener('click', (event) => {
        const unavailable = event.target.closest('[data-unavailable-page]');
        if (unavailable) {
            event.preventDefault();
            const toast = document.querySelector('#toast');
            toast.textContent = '이 페이지는 현재 준비 중입니다.';
            toast.hidden = false;
        }
    });
});
