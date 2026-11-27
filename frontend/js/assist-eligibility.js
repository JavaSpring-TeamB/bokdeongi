function formatEligibilityAmount(value) {
    const numericAmount = typeof value === 'number'
        ? value
        : typeof value === 'string' && value.trim() !== '' ? Number(value) : NaN;
    return Number.isFinite(numericAmount) && numericAmount >= 0
        ? numericAmount.toLocaleString('ko-KR') + '원'
        : '지원 금액 정보 없음';
}

(() => {
    const $ = (selector) => document.querySelector(selector);
    const ANSWERS = Object.freeze([['Y', '예'], ['N', '아니오'], ['UNSURE', '아직 몰라요']]);
    const ANSWER_VALUES = new Set(ANSWERS.map(([value]) => value));
    const QUESTION_KEY = /^[a-z][a-z0-9_]{0,49}$/;
    const assistantSteps = [
        ['자격 확인', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>'],
        ['서류 준비', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>'],
        ['서류 검토', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>'],
        ['신청 안내', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>']
    ];
    const state = { application: null, benefit: null, eligibility: null, savedAnswers: new Map(), selections: new Map(), manualConfirmed: false, answerSaving: false, completing: false, answerMessage: '', answerError: false };

    function escapeHtml(value) {
        return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
    }
    function text(value, fallback = '') { return typeof value === 'string' && value.trim() ? value.trim() : fallback; }
    function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
    function validBenefit(payload, benefitId) { return isRecord(payload) && Number(payload.id) === benefitId; }
    function validEligibility(payload, benefitId) {
        const results = ['ELIGIBLE', 'CONDITIONAL', 'INELIGIBLE'];
        const statuses = ['MET', 'CHECK', 'NOT_MET'];
        const optionalText = (value) => value == null || typeof value === 'string';
        const validCondition = (condition) => isRecord(condition) && statuses.includes(condition.status)
            && ['field', 'label', 'expected', 'actual', 'sourceQuote', 'checkType', 'guide'].every((key) => optionalText(condition[key]));
        return isRecord(payload) && Number(payload.benefitId) === benefitId && results.includes(payload.result)
            && optionalText(payload.title) && optionalText(payload.summary) && Array.isArray(payload.groups)
            && payload.groups.every((group) => isRecord(group) && Number.isInteger(group.group) && typeof group.alternative === 'boolean'
                && statuses.includes(group.status) && optionalText(group.title) && Array.isArray(group.conditions) && group.conditions.every(validCondition))
            && Array.isArray(payload.questions) && payload.questions.every((question) => isRecord(question)
                && QUESTION_KEY.test(text(question.key)) && Boolean(text(question.text)) && optionalText(question.sourceQuote))
            && (payload.next == null || isRecord(payload.next));
    }
    async function errorMessage(response) {
        try { const payload = await response.json(); return text(payload?.message || payload?.detail); } catch { return ''; }
    }
    async function authenticatedFetch(path, options) {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.refreshAuthentication !== 'function' || typeof auth.authenticatedFetch !== 'function') throw new Error('신청 서비스를 설정하지 못했습니다.');
        if (await auth.refreshAuthentication() !== 'authenticated') throw new Error('로그인 후 신청 도우미를 이용할 수 있습니다.');
        return auth.authenticatedFetch(path, options);
    }
    async function loadProfileAnswers() {
        const response = await authenticatedFetch('/api/me/profile/answers');
        if (!response.ok) throw new Error((await errorMessage(response)) || '저장된 추가 답변을 불러오지 못했습니다.');
        const entries = await response.json();
        if (!Array.isArray(entries)) throw new Error('저장된 추가 답변 응답 형식이 올바르지 않습니다.');
        state.savedAnswers.clear();
        entries.forEach((entry) => { if (isRecord(entry) && QUESTION_KEY.test(text(entry.key)) && ANSWER_VALUES.has(entry.value)) state.savedAnswers.set(entry.key, entry.value); });
    }
    async function loadBenefitAndEligibility() {
        const benefitId = state.application.benefitId;
        const [benefitResponse, eligibilityResponse] = await Promise.all([
            authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId)),
            authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/eligibility')
        ]);
        if (!benefitResponse.ok) throw new Error((await errorMessage(benefitResponse)) || '혜택 정보를 불러오지 못했습니다.');
        if (!eligibilityResponse.ok) throw new Error((await errorMessage(eligibilityResponse)) || '자격 판정 결과를 확인할 수 없습니다.');
        const [benefit, eligibility] = await Promise.all([benefitResponse.json(), eligibilityResponse.json()]);
        if (!validBenefit(benefit, benefitId)) throw new Error('혜택 정보 응답 형식이 올바르지 않습니다.');
        if (!validEligibility(eligibility, benefitId)) throw new Error('자격 판정 응답 형식이 올바르지 않습니다.');
        state.benefit = benefit;
        state.eligibility = eligibility;
    }
    function currentAnswer(key) { return state.selections.get(key) || state.savedAnswers.get(key) || ''; }
    function questionList() { return state.eligibility.questions.filter((question) => QUESTION_KEY.test(text(question.key)) && text(question.text)); }
    function conditionStatusLabel(status) { return ({ MET: '충족', CHECK: '확인 필요', NOT_MET: '미충족' })[status] || '확인 필요'; }
    function resultLabel(result) { return ({ ELIGIBLE: '해당 가능성이 있어요', CONDITIONAL: '추가 확인이 필요해요', INELIGIBLE: '현재 조건에서는 해당하지 않아요' })[result] || '판정 정보 확인 필요'; }
    function statusClass(status) { return status === 'MET' || status === 'ELIGIBLE' ? 'success' : 'warning'; }
    function rows() {
        const conditions = state.eligibility.groups.flatMap((group) => group.conditions.map((condition) => ({ group, condition })));
        if (!conditions.length) return '<tr><td colspan="4">조건별 판정 근거가 제공되지 않았습니다.</td></tr>';
        return conditions.map(({ group, condition }, index) => {
            const evidenceId = 'evidence-' + index;
            const evidence = [text(condition.sourceQuote), text(condition.guide)].filter(Boolean).join('\n');
            const groupTitle = text(group.title);
            return '<tr class="assist-table__data-row"><th scope="row">' + escapeHtml(text(condition.label, '조건')) + (groupTitle ? '<br><small>' + escapeHtml(groupTitle) + '</small>' : '') + '</th><td>' + escapeHtml(text(condition.actual) || text(condition.expected, '정보 제공되지 않음')) + '</td><td class="assist-table__status"><span class="status-badge ' + statusClass(condition.status) + '">' + escapeHtml(conditionStatusLabel(condition.status)) + '</span></td><td class="assist-table__evidence"><button class="bk-btn bk-btn--tertiary bk-btn--sm text-button" type="button" aria-expanded="false" aria-controls="' + evidenceId + '" data-evidence="' + index + '">근거 보기 <span aria-hidden="true">›</span></button></td></tr><tr class="assist-evidence-row" id="' + evidenceId + '" hidden><td colspan="4"><p class="evidence">' + escapeHtml(evidence || '공고문 근거가 제공되지 않았습니다.').replaceAll('\n', '<br>') + '</p></td></tr>';
        }).join('');
    }
    function questionsMarkup() {
        const questions = questionList();
        if (!questions.length) return '<p class="assist-answer-summary">추가로 답할 질문이 없습니다.</p>';
        return questions.map((question, index) => {
            const selected = currentAnswer(question.key);
            const radios = ANSWERS.map(([value, label]) => '<label><input type="radio" name="question-' + escapeHtml(question.key) + '" value="' + value + '" data-question-key="' + escapeHtml(question.key) + '"' + (selected === value ? ' checked' : '') + (state.answerSaving || state.completing ? ' disabled' : '') + '> ' + label + '</label>').join('');
            return '<section class="assist-question__item"><strong>' + escapeHtml(question.text) + '</strong>' + (text(question.sourceQuote) ? '<p class="assist-answer-summary">공고문 근거: ' + escapeHtml(question.sourceQuote) + '</p>' : '') + '<fieldset><legend class="sr-only">추가 질문 ' + (index + 1) + '</legend>' + radios + '</fieldset></section>';
        }).join('') + '<button class="bk-btn bk-btn--secondary bk-btn--sm" type="button" data-save-answers' + (state.answerSaving || state.completing ? ' disabled' : '') + '>' + (state.answerSaving ? '답변 저장 중...' : '선택한 답변 저장') + '</button><p class="assist-answer-summary" role="' + (state.answerError ? 'alert' : 'status') + '">' + escapeHtml(state.answerMessage || '선택한 답변은 저장 버튼을 눌러 서버에 반영해 주세요.') + '</p>';
    }
    function eligibleToProceed() {
        const next = state.eligibility?.next;
        return Boolean(next?.canProceed === true && (!next.manualConfirmRequired || state.manualConfirmed))
            && state.selections.size === 0
            && questionList().every((question) => ANSWER_VALUES.has(currentAnswer(question.key)));
    }
    function canComplete() {
        return eligibleToProceed() && !state.answerSaving && !state.completing;
    }
    function progressInfo() {
        const next = state.eligibility.next;
        const manual = next?.manualConfirmRequired === true ? '<label><input type="checkbox" data-manual-confirm' + (state.manualConfirmed ? ' checked' : '') + (state.answerSaving || state.completing ? ' disabled' : '') + '> 공고문 원문을 확인했고 자격 조건을 직접 확인했어요.</label>' + (text(next.sourceText) ? '<p class="assist-answer-summary">' + escapeHtml(next.sourceText) + '</p>' : '') : '';
        return '<section class="assist-status"><h2>현재 판정: <span class="status-badge ' + statusClass(state.eligibility.result) + '">' + escapeHtml(resultLabel(state.eligibility.result)) + '</span></h2><p>' + escapeHtml(text(next?.message, '자격 판정 결과를 확인해 주세요.')) + '</p>' + (next?.agencyCheckRecommended ? '<p>기관에 한 번 더 확인하면 좋아요.</p>' : '') + manual + '</section>';
    }
    function render() {
        const benefitTitle = text(state.benefit.title, text(state.eligibility.title, '혜택 정보'));
        const amount = formatEligibilityAmount(state.benefit.maxAmount);
        const period = text(state.benefit.applyPeriodText, '신청 기간 정보 없음');
        $('#assist-content').innerHTML = '<div class="assist-steps assist-steps--icon" aria-label="신청 도우미 진행 단계">' + assistantSteps.map((step, index) => '<div class="assist-step ' + (index === 0 ? 'is-current' : '') + '"><span class="assist-step__icon">' + step[1] + '</span><span class="assist-step__label">' + step[0] + '</span></div>').join('') + '</div><div class="assist-info"><strong aria-hidden="true">i</strong><span><strong>' + escapeHtml(benefitTitle) + '</strong> · ' + escapeHtml(amount) + ' · ' + escapeHtml(period) + '</span></div><section class="card assist-card"><h2>자격 확인 결과</h2><p class="assist-answer-summary">' + escapeHtml(text(state.eligibility.summary, '자격 판정 요약 정보가 제공되지 않았습니다.')) + '</p><div class="assist-table-wrap"><table class="assist-table"><thead><tr><th>확인 항목</th><th>내 정보 및 판정</th><th>판정 상태</th><th>근거</th></tr></thead><tbody>' + rows() + '</tbody></table></div><section class="assist-question"><h2>추가 확인이 필요한 항목</h2>' + questionsMarkup() + '</section>' + progressInfo() + '</section><div class="assist-actions"><a class="bk-btn bk-btn--outline bk-btn--md" href="benefit-detail.html?id=' + encodeURIComponent(state.application.benefitId) + '">이전으로</a><button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-complete-eligibility' + (canComplete() ? '' : ' disabled') + '>' + (state.completing ? '저장 중...' : '서류 준비로 이동') + '</button></div>';
    }
    function setAnswerMessage(message, error = false) { state.answerMessage = message; state.answerError = error; render(); }
    async function saveAnswers() {
        const answers = {};
        questionList().forEach((question) => { const selected = state.selections.get(question.key); if (ANSWER_VALUES.has(selected) && selected !== state.savedAnswers.get(question.key)) answers[question.key] = selected; });
        if (!Object.keys(answers).length) return setAnswerMessage('저장할 변경 답변이 없습니다.');
        state.answerSaving = true;
        setAnswerMessage('답변을 저장하고 자격 판정을 갱신하고 있어요.');
        try {
            const csrf = await window.BokdeongiAuth.getCsrfToken();
            const response = await authenticatedFetch('/api/me/answers', { method: 'PUT', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: JSON.stringify({ answers, benefitId: state.application.benefitId }) });
            if (!response.ok) throw new Error((await errorMessage(response)) || '답변을 저장하지 못했습니다.');
            const payload = await response.json();
            if (!isRecord(payload) || !isRecord(payload.answers)) throw new Error('답변 저장 결과 형식이 올바르지 않습니다.');
            Object.entries(payload.answers).forEach(([key, value]) => { if (QUESTION_KEY.test(key) && ANSWER_VALUES.has(value)) state.savedAnswers.set(key, value); });
            state.selections.clear();
            await loadBenefitAndEligibility();
            state.answerMessage = '답변을 저장했고 서버 자격 판정을 갱신했습니다.';
            state.answerError = false;
        } catch (error) {
            state.answerMessage = error.message || '답변 저장 여부를 확인하지 못했습니다. 입력한 답변은 유지됩니다.';
            state.answerError = true;
        } finally { state.answerSaving = false; render(); }
    }
    async function completeEligibility() {
        if (!canComplete()) return;
        state.completing = true;
        render();
        try {
            await loadBenefitAndEligibility();
            if (!eligibleToProceed()) { state.answerMessage = '최신 자격 판정 결과에서 다음 단계로 이동할 수 없습니다. 내용을 다시 확인해 주세요.'; state.answerError = true; return; }
            const updated = await window.BokdeongiApplication.updateApplication(state.application.id, { step: 2, appAnswers: { eligibilityConfirmed: true } });
            state.application = updated;
            window.location.assign(window.BokdeongiApplication.assistantUrl(2, updated.id));
        } catch (error) { state.answerMessage = window.BokdeongiApplication.messageFor(error); state.answerError = true; }
        finally { state.completing = false; render(); }
    }
    function bindEvents() {
        document.addEventListener('click', (event) => {
            const evidence = event.target.closest('[data-evidence]');
            if (evidence) { const row = document.getElementById(evidence.getAttribute('aria-controls')); if (!row) return; const open = row.hidden; row.hidden = !open; evidence.setAttribute('aria-expanded', String(open)); evidence.innerHTML = open ? '근거 닫기' : '근거 보기 <span aria-hidden="true">›</span>'; return; }
            if (event.target.closest('[data-save-answers]')) void saveAnswers();
            if (event.target.closest('[data-complete-eligibility]')) void completeEligibility();
        });
        document.addEventListener('change', (event) => {
            const input = event.target;
            if (input.matches('[data-question-key]') && ANSWER_VALUES.has(input.value)) {
                if (state.savedAnswers.get(input.dataset.questionKey) === input.value) state.selections.delete(input.dataset.questionKey);
                else state.selections.set(input.dataset.questionKey, input.value);
                state.answerMessage = '';
                state.answerError = false;
                render();
            }
            if (input.matches('[data-manual-confirm]')) { state.manualConfirmed = input.checked; render(); }
        });
    }
    async function setup() {
        const container = $('#assist-content');
        state.application = await window.BokdeongiApplication.loadCurrentAssistantApplication(container);
        if (!state.application) return;
        try {
            container.innerHTML = '<section class="card empty-state"><h2>자격 정보를 불러오는 중이에요.</h2><p>신청 정보와 자격 판정 결과를 확인하고 있습니다.</p></section>';
            await Promise.all([loadBenefitAndEligibility(), loadProfileAnswers()]);
            render();
            bindEvents();
        } catch (error) {
            container.innerHTML = '<section class="card empty-state"><h2>자격 정보를 열 수 없어요.</h2><p>' + escapeHtml(error.message || '잠시 후 다시 시도해주세요.') + '</p><a class="bk-btn bk-btn--secondary bk-btn--md" href="benefit-list.html">혜택 목록으로 이동</a></section>';
        }
    }
    document.addEventListener('DOMContentLoaded', setup);
})();
