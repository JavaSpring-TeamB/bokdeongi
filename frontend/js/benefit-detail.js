const $ = (selector) => document.querySelector(selector);

const state = {
    benefitId: null,
    detail: null,
    screen: 'LOADING',
    errorKind: null,
    errorMessage: ''
};

const easyState = {
    benefitId: null,
    screen: 'IDLE',
    response: null,
    errorKind: null,
    errorMessage: '',
    requestPromise: null
};

const eligibilityState = {
    benefitId: null,
    screen: 'IDLE',
    response: null,
    errorKind: null,
    errorMessage: '',
    requestPromise: null
};

const favoriteState = {
    benefitId: null,
    screen: 'IDLE',
    bookmarked: null,
    errorMessage: '',
    requestPromise: null
};

const askState = {
    benefitId: null,
    screen: 'IDLE',
    response: null,
    draft: '',
    submittedQuestion: '',
    errorKind: null,
    errorMessage: '',
    requestPromise: null
};

let detailRequestId = 0;
let activeController = null;
let easyRequestId = 0;
let easyController = null;
let eligibilityRequestId = 0;
let eligibilityController = null;
let favoriteRequestId = 0;
let favoriteController = null;
let askRequestId = 0;
let askController = null;
let assistantStartPending = false;

class BenefitDetailRequestError extends Error {
    constructor(kind, message = '') {
        super(message);
        this.kind = kind;
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function hasText(value) {
    return typeof value === 'string' && value.trim().length > 0;
}

function parseBenefitId(value) {
    if (!/^[1-9]\d*$/.test(value || '')) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) ? id : null;
}

function parseHashtags(value) {
    if (Array.isArray(value)) return value.filter(hasText).map((tag) => tag.trim());
    if (!hasText(value)) return [];
    try {
        const parsed = JSON.parse(value);
        if (Array.isArray(parsed)) return parsed.filter(hasText).map((tag) => tag.trim());
    } catch {
        // DTO의 해시태그가 JSON 문자열이 아니면 쉼표 구분 문자열로 표시한다.
    }
    return value.split(',').map((tag) => tag.trim()).filter(Boolean);
}

function isSafeExternalUrl(value) {
    if (!hasText(value)) return false;
    try {
        const url = new URL(value);
        return url.protocol === 'https:' || url.protocol === 'http:';
    } catch {
        return false;
    }
}

function isBenefitDetailResponse(payload) {
    return payload
        && typeof payload === 'object'
        && (typeof payload.id === 'number' || typeof payload.id === 'string');
}

function isEasyResponse(payload) {
    const statuses = ['DONE', 'NOT_READY', 'FAILED'];
    const isOptionalText = (value) => value === null || value === undefined || typeof value === 'string';
    const summary = payload?.summary;
    const validSummary = summary === null || summary === undefined || (
        typeof summary === 'object'
        && isOptionalText(summary.who)
        && isOptionalText(summary.what)
        && isOptionalText(summary.how)
        && isOptionalText(summary.when)
    );
    return payload
        && typeof payload === 'object'
        && statuses.includes(payload.status)
        && isOptionalText(payload.text)
        && isOptionalText(payload.generatedAt)
        && isOptionalText(payload.notice)
        && validSummary;
}

function isEligibilityResponse(payload) {
    const results = ['ELIGIBLE', 'CONDITIONAL', 'INELIGIBLE'];
    const groupStatuses = ['MET', 'CHECK', 'NOT_MET'];
    const optionalText = (value) => value === null || value === undefined || typeof value === 'string';
    const validCondition = (condition) => condition
        && typeof condition === 'object'
        && groupStatuses.includes(condition.status)
        && optionalText(condition.field)
        && optionalText(condition.label)
        && optionalText(condition.expected)
        && optionalText(condition.actual)
        && optionalText(condition.sourceQuote)
        && optionalText(condition.checkType)
        && optionalText(condition.guide);
    const validGroup = (group) => group
        && typeof group === 'object'
        && Number.isInteger(group.group)
        && typeof group.alternative === 'boolean'
        && groupStatuses.includes(group.status)
        && optionalText(group.title)
        && Array.isArray(group.conditions)
        && group.conditions.every(validCondition);
    return payload
        && typeof payload === 'object'
        && (typeof payload.benefitId === 'number' || typeof payload.benefitId === 'string')
        && results.includes(payload.result)
        && optionalText(payload.title)
        && optionalText(payload.summary)
        && Array.isArray(payload.groups)
        && payload.groups.every(validGroup)
        && Array.isArray(payload.questions)
        && payload.questions.every((question) => question && typeof question === 'object' && optionalText(question.key) && optionalText(question.text) && optionalText(question.sourceQuote))
        && (payload.next === null || payload.next === undefined || typeof payload.next === 'object');
}

function isFavoriteResponse(payload, benefitId) {
    if (!payload || typeof payload !== 'object' || typeof payload.bookmarked !== 'boolean') return false;
    const responseBenefitId = typeof payload.benefitId === 'number' || typeof payload.benefitId === 'string'
        ? Number(payload.benefitId)
        : NaN;
    return Number.isSafeInteger(responseBenefitId) && responseBenefitId === benefitId;
}

function isAskResponse(payload) {
    return payload
        && typeof payload === 'object'
        && typeof payload.answer === 'string'
        && typeof payload.foundInSource === 'boolean'
        && Array.isArray(payload.sourceQuotes)
        && payload.sourceQuotes.every((quote) => typeof quote === 'string')
        && typeof payload.eligibility === 'boolean'
        && Number.isInteger(payload.remaining)
        && payload.remaining >= 0
        && typeof payload.notice === 'string';
}

function isMissing(detail, field) {
    const value = detail[field];
    const valueMissing = !hasText(value);
    const markedMissing = Array.isArray(detail.missingFields) && detail.missingFields.includes(field);
    // missingFields는 참고하되, 실제 값이 있으면 표시해 서버 값과 충돌하지 않게 한다.
    if (markedMissing && !valueMissing) return false;
    return valueMissing;
}

function amountText(detail) {
    if (!Number.isFinite(detail.maxAmount)) return '공고 확인 필요';
    const period = hasText(detail.amountPeriod) ? ' / ' + detail.amountPeriod.trim() : '';
    return detail.maxAmount.toLocaleString('ko-KR') + '원' + period;
}

function periodText(detail) {
    const start = hasText(detail.applyStart) ? detail.applyStart.trim() : '';
    const end = hasText(detail.applyEnd) ? detail.applyEnd.trim() : '';
    if (start && end) return start + ' ~ ' + end;
    if (start) return start + '부터';
    if (end) return end + '까지';
    if (hasText(detail.applyPeriodText)) return detail.applyPeriodText.trim();
    return '공고 확인 필요';
}

function syncedAtText(value) {
    return hasText(value) ? value.trim().replace('T', ' ') : '정보 제공되지 않음';
}

function summaryLines(detail) {
    return [detail.summaryWhat, detail.summaryWho, detail.summaryHow, detail.summaryWhen]
        .filter(hasText)
        .map((value) => value.trim());
}

function detailIcon(name) {
    const paths = {
        original: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"></path><path d="M14 2v6h6"></path><path d="M8 13h8"></path><path d="M8 17h6"></path>',
        target: '<circle cx="12" cy="8" r="4"></circle><path d="M4 21a8 8 0 0 1 16 0"></path>',
        amount: '<circle cx="12" cy="12" r="9"></circle><path d="M15 9.5c-.4-.8-1.3-1.3-2.5-1.3-1.5 0-2.5.8-2.5 1.9 0 3 5 1.2 5 4.1 0 1.1-1 1.9-2.5 1.9-1.2 0-2.1-.5-2.5-1.3"></path><path d="M12.5 6.5v11"></path>',
        period: '<circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path>'
    };
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + paths[name] + '</svg>';
}

function externalLink(url, label, disabledLabel) {
    if (!isSafeExternalUrl(url)) {
        return '<button class="icon-button" type="button" disabled aria-disabled="true">' + escapeHtml(disabledLabel) + '</button>';
    }
    return '<a class="icon-button" href="' + escapeHtml(url) + '" target="_blank" rel="noopener noreferrer">' + escapeHtml(label) + '</a>';
}

function panelNotice(title, message) {
    return '<section class="card source-card"><h2>' + escapeHtml(title) + '</h2><p class="small-copy">' + escapeHtml(message) + '</p></section>';
}

function bookmarkIcon() {
    return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12v17l-6-3.8-6 3.8z"></path></svg>';
}

function favoriteButtonMarkup() {
    const saved = favoriteState.screen === 'RESULT' && favoriteState.bookmarked === true;
    const loading = ['IDLE', 'LOADING', 'PENDING'].includes(favoriteState.screen);
    const retry = favoriteState.screen === 'ERROR';
    const unauthenticated = favoriteState.screen === 'UNAUTHENTICATED';
    const label = loading
        ? (favoriteState.screen === 'PENDING' ? '관심 혜택 저장 상태를 변경하는 중' : '관심 혜택 저장 상태를 확인하는 중')
        : retry
            ? '관심 혜택 저장 상태를 다시 확인'
            : unauthenticated
                ? '관심 혜택을 저장하려면 로그인 필요'
                : saved ? '관심 혜택 저장 해제' : '관심 혜택 저장';
    const title = retry
        ? '관심 상태를 확인하지 못했습니다. 다시 시도해주세요.'
        : unauthenticated ? '로그인 후 관심 혜택으로 저장할 수 있습니다.' : label;
    const disabled = loading ? ' disabled aria-disabled="true"' : '';
    const busy = loading ? ' aria-busy="true"' : '';
    const stateClass = saved ? ' is-saved' : '';
    return '<button id="save-button" class="icon-button icon-button--bookmark' + stateClass + '" type="button" aria-pressed="' + String(saved) + '" aria-label="' + label + '" title="' + title + '"' + busy + disabled + '>' + bookmarkIcon() + '</button><span id="favorite-status" class="sr-only" aria-live="polite"></span>';
}

function getPageMarkup(detail) {
    const hashtags = parseHashtags(detail.hashtags);
    const tags = hashtags.length
        ? '<div class="benefit-info-bar__tags">' + hashtags.map((tag) => '<button class="detail-tag" type="button" data-hashtag="' + escapeHtml(tag) + '">#' + escapeHtml(tag) + '</button>').join('') + '</div>'
        : '';
    const status = detail.ended ? '종료' : (hasText(detail.status) ? detail.status.trim() : '상태 정보 없음');
    const title = hasText(detail.title) ? detail.title.trim() : '혜택 상세';

    return [
        '<section class="detail-title-row">',
        '<div class="benefit-info-bar"><div class="benefit-info-bar__main"><span class="detail-status">' + escapeHtml(status) + '</span></div>' + tags + '<small>데이터 기준: ' + escapeHtml(syncedAtText(detail.syncedAt)) + '</small></div>',
        '<div class="detail-actions">' + favoriteButtonMarkup() + '<button id="share-button" class="icon-button" type="button">공유하기</button></div></section>',
        '<div class="detail-layout"><section class="detail-main">',
        '<div class="tabs" role="tablist" aria-label="혜택 상세 정보">',
        '<button class="tab-button" role="tab" id="tab-easy" aria-selected="true" aria-controls="panel-easy" data-tab="easy">쉬운 말 보기</button>',
        '<button class="tab-button" role="tab" id="tab-original" aria-selected="false" aria-controls="panel-original" data-tab="original">원문 보기</button>',
        '<button class="tab-button" role="tab" id="tab-documents" aria-selected="false" aria-controls="panel-documents" data-tab="documents">필요 서류</button>',
        '<button class="tab-button" role="tab" id="tab-apply" aria-selected="false" aria-controls="panel-apply" data-tab="apply">신청 방법</button></div>',
        easyPanel(detail), originalPanel(detail), documentsPanel(detail), applyPanel(detail),
        '</section>', sidebar(detail), '</div>'
    ].join('');
}

function easyPanel(detail) {
    return '<section class="tab-panel" id="panel-easy" role="tabpanel" aria-labelledby="tab-easy">' + easyPanelContent(detail) + eligibilityPanel() + '</section>';
}

function easySummaryCards(summary) {
    const cards = [
        ['target', '누가 받을 수 있나요', summary?.who],
        ['amount', '무엇을 지원하나요', summary?.what],
        ['original', '어떻게 신청하나요', summary?.how],
        ['period', '언제 신청하나요', summary?.when]
    ];
    return '<div class="summary-cards">' + cards.map((card) => (
        '<article class="card summary-card"><span class="summary-card__icon" aria-hidden="true">' + detailIcon(card[0]) + '</span><h3>' + card[1] + '</h3><strong>' + escapeHtml(hasText(card[2]) ? card[2].trim() : '정보 제공되지 않음') + '</strong></article>'
    )).join('') + '</div>';
}

function easyPanelContent(detail) {
    if (easyState.screen === 'IDLE' || easyState.screen === 'LOADING') {
        return panelNotice('쉬운 말 안내를 불러오는 중이에요.', '저장된 쉬운 말 결과를 확인하고 있습니다.');
    }
    if (easyState.screen === 'DONE') {
        const response = easyState.response;
        const text = hasText(response.text) ? response.text.trim() : '쉬운 말 본문 정보가 제공되지 않았습니다.';
        const generatedAt = hasText(response.generatedAt) ? '<p class="small-copy">생성 시각: ' + escapeHtml(syncedAtText(response.generatedAt)) + '</p>' : '';
        const notice = hasText(response.notice) ? '<p class="eligibility-notice">' + escapeHtml(response.notice.trim()) + '</p>' : '';
        return '<div class="summary-box"><div><h2>쉬운 말 핵심 요약</h2><p class="small-copy">AI가 공고문을 쉽게 풀어 쓴 내용입니다.</p></div></div>'
            + easySummaryCards(response.summary)
            + '<section class="source-card"><h2>쉬운 말로 풀어쓴 안내</h2><p>' + escapeHtml(text) + '</p>' + generatedAt + notice + '</section>';
    }
    if (easyState.screen === 'NOT_READY') {
        const notice = hasText(easyState.response?.notice) ? ' ' + easyState.response.notice.trim() : '';
        const loginHint = window.BokdeongiAuth?.getAuthenticationState?.() === 'unauthenticated' ? ' 로그인하면 쉬운 말로 볼 수 있어요.' : '';
        return panelNotice('쉬운 말 안내가 아직 준비되지 않았어요.', '현재는 공고 원문을 확인해주세요.' + loginHint + notice)
            + '<div class="detail-actions"><button class="icon-button" type="button" data-open-original>원문 보기</button><button class="icon-button" type="button" data-retry-easy>다시 확인</button></div>';
    }
    if (easyState.screen === 'FAILED') {
        const notice = hasText(easyState.response?.notice) ? ' ' + easyState.response.notice.trim() : '';
        return panelNotice('쉬운 말 안내를 제공하지 못했어요.', '공고 원문을 확인해주세요.' + notice)
            + '<div class="detail-actions"><button class="icon-button" type="button" data-open-original>원문 보기</button><button class="icon-button" type="button" data-retry-easy>다시 시도</button></div>';
    }
    const message = easyState.errorKind === 'NETWORK'
        ? '네트워크 연결을 확인한 뒤 다시 시도해주세요.'
        : (easyState.errorMessage || '잠시 후 다시 시도해주세요.');
    return panelNotice('쉬운 말 안내를 불러오지 못했어요.', message)
        + '<div class="detail-actions"><button class="icon-button" type="button" data-retry-easy>다시 시도</button></div>';
}

function updateEasyPanel() {
    const panel = $('#panel-easy');
    if (panel && state.detail) {
        panel.innerHTML = easyPanelContent(state.detail) + eligibilityPanel();
    }
}

function resetEasyState(benefitId) {
    easyRequestId += 1;
    if (easyController) easyController.abort();
    easyController = null;
    easyState.benefitId = benefitId;
    easyState.screen = 'IDLE';
    easyState.response = null;
    easyState.errorKind = null;
    easyState.errorMessage = '';
    easyState.requestPromise = null;
}

function eligibilityResultLabel(result) {
    const labels = {
        ELIGIBLE: '해당 가능성이 있어요',
        CONDITIONAL: '추가 확인이 필요해요',
        INELIGIBLE: '현재 조건에서는 해당하지 않아요'
    };
    return labels[result] || result;
}

function eligibilityConditionLabel(status) {
    const labels = { MET: '충족', CHECK: '확인 필요', NOT_MET: '미충족' };
    return labels[status] || status;
}

function eligibilityPanel() {
    return '<section id="eligibility-panel" class="card eligibility-card">' + eligibilityPanelContent() + '</section>';
}

function eligibilityPanelContent() {
    if (eligibilityState.screen === 'IDLE' || eligibilityState.screen === 'LOADING') {
        return '<div class="eligibility-card__heading"><h2>내 자격 확인 결과</h2></div><p class="small-copy">내 조건에 맞는지 확인하고 있어요.</p>';
    }
    if (eligibilityState.screen === 'UNAUTHENTICATED') {
        return '<div class="eligibility-card__heading"><h2>내 자격 확인 결과</h2></div><p class="small-copy">로그인하면 내 조건에 맞는 혜택인지 확인할 수 있어요.</p><a class="btn btn-secondary" href="login.html">로그인하기</a>';
    }
    if (eligibilityState.screen === 'NO_RESULT') {
        return '<div class="eligibility-card__heading"><h2>내 자격 확인 결과</h2></div><p class="small-copy">현재 이 혜택의 자격 판정 결과를 확인할 수 없습니다.</p><button class="btn btn-secondary" type="button" data-retry-eligibility>다시 시도</button>';
    }
    if (eligibilityState.screen === 'ERROR') {
        const message = eligibilityState.errorKind === 'NETWORK'
            ? '네트워크 연결을 확인한 뒤 다시 시도해주세요.'
            : (eligibilityState.errorMessage || '잠시 후 다시 시도해주세요.');
        return '<div class="eligibility-card__heading"><h2>내 자격 확인 결과</h2></div><p class="small-copy">' + escapeHtml(message) + '</p><button class="btn btn-secondary" type="button" data-retry-eligibility>다시 시도</button>';
    }

    const response = eligibilityState.response;
    const profileIncomplete = response.groups.some((group) => group.conditions.some((condition) => (
        condition.status === 'CHECK' && ['PROFILE', 'SENSITIVE'].includes(condition.checkType)
    )));
    const profileAction = profileIncomplete
        ? '<p class="eligibility-notice">프로필에 확인이 필요한 정보가 있어요. <a href="my-page.html">프로필 입력으로 이동</a></p>'
        : '';
    const groups = response.groups.length
        ? response.groups.map(eligibilityGroupMarkup).join('')
        : '<p class="small-copy">조건별 판정 근거가 제공되지 않았습니다.</p>';
    const next = hasText(response.next?.message)
        ? '<p class="eligibility-notice">' + escapeHtml(response.next.message.trim()) + '</p>'
        : '';
    return '<div class="eligibility-card__heading"><h2>내 자격 확인 결과</h2><span class="eligibility-result">' + escapeHtml(eligibilityResultLabel(response.result)) + '</span></div><p class="small-copy">' + escapeHtml(hasText(response.summary) ? response.summary.trim() : '판정 요약 정보가 제공되지 않았습니다.') + '</p>' + profileAction + groups + next;
}

function eligibilityGroupMarkup(group) {
    const title = hasText(group.title) ? group.title.trim() : '조건 묶음';
    const conditions = group.conditions.map((condition) => {
        const expected = hasText(condition.expected) ? condition.expected.trim() : '요구 조건 정보 없음';
        const sourceQuote = hasText(condition.sourceQuote) ? condition.sourceQuote.trim() : '근거 확인 필요';
        const guide = hasText(condition.guide) ? '<p class="evidence-copy">' + escapeHtml(condition.guide.trim()) + '</p>' : '';
        return '<tr class="eligibility-row"><th scope="row">' + escapeHtml(hasText(condition.label) ? condition.label.trim() : '조건') + '</th><td>' + escapeHtml(expected) + '</td><td><span class="eligibility-badge">' + escapeHtml(eligibilityConditionLabel(condition.status)) + '</span></td><td>' + escapeHtml(sourceQuote) + guide + '</td></tr>';
    }).join('');
    return '<section class="eligibility-group"><h3>' + escapeHtml(title) + ' · ' + escapeHtml(eligibilityConditionLabel(group.status)) + '</h3><div class="eligibility-table-wrap"><table class="eligibility-table"><colgroup><col class="eligibility-table__condition"><col class="eligibility-table__detail"><col class="eligibility-table__status"><col class="eligibility-table__evidence"></colgroup><thead><tr><th>조건</th><th>요구 조건</th><th>판정</th><th>공고문 근거</th></tr></thead><tbody>' + conditions + '</tbody></table></div></section>';
}

function updateEligibilityPanel() {
    const panel = $('#eligibility-panel');
    if (panel) panel.innerHTML = eligibilityPanelContent();
}

function resetEligibilityState(benefitId) {
    eligibilityRequestId += 1;
    if (eligibilityController) eligibilityController.abort();
    eligibilityController = null;
    eligibilityState.benefitId = benefitId;
    eligibilityState.screen = 'IDLE';
    eligibilityState.response = null;
    eligibilityState.errorKind = null;
    eligibilityState.errorMessage = '';
    eligibilityState.requestPromise = null;
}

function updateFavoriteButton() {
    const button = $('#save-button');
    if (!button) return;

    const saved = favoriteState.screen === 'RESULT' && favoriteState.bookmarked === true;
    const loading = ['IDLE', 'LOADING', 'PENDING'].includes(favoriteState.screen);
    const retry = favoriteState.screen === 'ERROR';
    const unauthenticated = favoriteState.screen === 'UNAUTHENTICATED';
    const label = loading
        ? (favoriteState.screen === 'PENDING' ? '관심 혜택 저장 상태를 변경하는 중' : '관심 혜택 저장 상태를 확인하는 중')
        : retry
            ? '관심 혜택 저장 상태를 다시 확인'
            : unauthenticated
                ? '관심 혜택을 저장하려면 로그인 필요'
                : saved ? '관심 혜택 저장 해제' : '관심 혜택 저장';
    button.disabled = loading;
    button.setAttribute('aria-disabled', String(loading));
    button.setAttribute('aria-busy', String(loading));
    button.setAttribute('aria-pressed', String(saved));
    button.setAttribute('aria-label', label);
    button.title = retry
        ? '관심 상태를 확인하지 못했습니다. 다시 시도해주세요.'
        : unauthenticated ? '로그인 후 관심 혜택으로 저장할 수 있습니다.' : label;
    if (button.classList) button.classList.toggle('is-saved', saved);
    button.innerHTML = bookmarkIcon();

    const status = $('#favorite-status');
    if (status) {
        status.textContent = favoriteState.screen === 'PENDING'
            ? '관심 혜택 저장 상태를 변경하는 중입니다.'
            : retry ? '관심 상태를 확인하지 못했습니다. 다시 시도해주세요.'
                : unauthenticated ? '로그인 후 관심 혜택으로 저장할 수 있습니다.'
                    : saved ? '관심 혜택으로 저장했습니다.'
                        : favoriteState.screen === 'RESULT' ? '관심 혜택으로 저장되지 않았습니다.' : '';
    }
}

function resetFavoriteState(benefitId) {
    favoriteRequestId += 1;
    if (favoriteController) favoriteController.abort();
    favoriteController = null;
    favoriteState.benefitId = benefitId;
    favoriteState.screen = 'IDLE';
    favoriteState.bookmarked = null;
    favoriteState.errorMessage = '';
    favoriteState.requestPromise = null;
}

async function loadFavorite(force = false) {
    const benefitId = state.benefitId;
    if (!benefitId || !state.detail) return;
    if (!force && favoriteState.benefitId === benefitId) {
        if (favoriteState.requestPromise) return favoriteState.requestPromise;
        if (['RESULT', 'UNAUTHENTICATED'].includes(favoriteState.screen)) return;
    }

    const requestId = ++favoriteRequestId;
    if (favoriteController) favoriteController.abort();
    const controller = new AbortController();
    favoriteController = controller;
    favoriteState.benefitId = benefitId;
    favoriteState.screen = 'LOADING';
    favoriteState.bookmarked = null;
    favoriteState.errorMessage = '';
    updateFavoriteButton();

    const request = (async () => {
        try {
            const auth = window.BokdeongiAuth;
            if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.refreshAuthentication !== 'function') {
                throw new BenefitDetailRequestError('SERVER', '로그인 상태를 확인하지 못했습니다.');
            }
            const authenticationState = await auth.refreshAuthentication();
            if (authenticationState !== 'authenticated') {
                if (requestId !== favoriteRequestId) return;
                favoriteState.screen = 'UNAUTHENTICATED';
                updateFavoriteButton();
                return;
            }

            const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/favorite', {
                signal: controller.signal
            });
            if (response.status === 401) {
                if (requestId !== favoriteRequestId) return;
                favoriteState.screen = 'UNAUTHENTICATED';
                updateFavoriteButton();
                return;
            }
            if (!response.ok) {
                throw new BenefitDetailRequestError(response.status === 404 ? 'NOT_FOUND' : 'SERVER', await readErrorMessage(response));
            }
            const payload = await response.json();
            if (!isFavoriteResponse(payload, benefitId)) {
                throw new BenefitDetailRequestError('INVALID', '관심 혜택 응답 형식이 올바르지 않습니다.');
            }
            if (requestId !== favoriteRequestId || state.benefitId !== benefitId) return;
            favoriteState.bookmarked = payload.bookmarked;
            favoriteState.screen = 'RESULT';
            updateFavoriteButton();
        } catch (error) {
            if ((error && error.name === 'AbortError') || requestId !== favoriteRequestId) return;
            favoriteState.screen = error && error.type === 'unauthenticated' ? 'UNAUTHENTICATED' : 'ERROR';
            favoriteState.errorMessage = error instanceof BenefitDetailRequestError ? error.message : '';
            updateFavoriteButton();
            console.error('관심 혜택 상태를 조회하지 못했습니다.', error);
        } finally {
            if (requestId === favoriteRequestId) {
                favoriteController = null;
                favoriteState.requestPromise = null;
            }
        }
    })();
    favoriteState.requestPromise = request;
    return request;
}

async function toggleFavorite() {
    const benefitId = state.benefitId;
    if (!benefitId || !state.detail || favoriteState.screen === 'PENDING') return;
    if (favoriteState.screen === 'UNAUTHENTICATED') {
        toast('로그인 후 관심 혜택으로 저장할 수 있어요.');
        return;
    }
    if (favoriteState.screen === 'ERROR') {
        loadFavorite(true);
        return;
    }
    if (favoriteState.screen !== 'RESULT') return;

    const requestId = ++favoriteRequestId;
    if (favoriteController) favoriteController.abort();
    const controller = new AbortController();
    favoriteController = controller;
    const previousBookmarked = favoriteState.bookmarked;
    favoriteState.screen = 'PENDING';
    favoriteState.errorMessage = '';
    updateFavoriteButton();

    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.getCsrfToken !== 'function') {
            throw new BenefitDetailRequestError('SERVER', '관심 혜택 저장 서비스를 설정하지 못했습니다.');
        }
        const csrf = await auth.getCsrfToken();
        const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/favorite', {
            method: previousBookmarked ? 'DELETE' : 'POST',
            headers: { [csrf.headerName]: csrf.token },
            signal: controller.signal
        });
        if (response.status === 401) {
            if (requestId !== favoriteRequestId) return;
            favoriteState.screen = 'UNAUTHENTICATED';
            favoriteState.bookmarked = null;
            updateFavoriteButton();
            toast('로그인이 만료되었습니다. 다시 로그인해주세요.');
            return;
        }
        if (!response.ok) {
            throw new BenefitDetailRequestError(response.status === 404 ? 'NOT_FOUND' : 'SERVER', await readErrorMessage(response));
        }
        const payload = await response.json();
        if (!isFavoriteResponse(payload, benefitId)) {
            throw new BenefitDetailRequestError('INVALID', '관심 혜택 응답 형식이 올바르지 않습니다.');
        }
        if (requestId !== favoriteRequestId || state.benefitId !== benefitId) return;
        favoriteState.bookmarked = payload.bookmarked;
        favoriteState.screen = 'RESULT';
        updateFavoriteButton();
        toast(payload.bookmarked ? '관심 혜택으로 저장했어요.' : '관심 혜택 저장을 해제했어요.');
    } catch (error) {
        if ((error && error.name === 'AbortError') || requestId !== favoriteRequestId) return;
        const unauthenticated = error && error.type === 'unauthenticated';
        favoriteState.bookmarked = unauthenticated ? null : previousBookmarked;
        favoriteState.screen = unauthenticated ? 'UNAUTHENTICATED' : 'RESULT';
        updateFavoriteButton();
        const message = error instanceof BenefitDetailRequestError && error.kind === 'NOT_FOUND'
            ? '존재하지 않는 혜택입니다.'
            : unauthenticated
                ? '로그인이 만료되었습니다. 다시 로그인해주세요.'
                : '관심 혜택 저장 상태를 변경하지 못했습니다. 다시 시도해주세요.';
        toast(message);
        console.error('관심 혜택 저장 상태를 변경하지 못했습니다.', error);
    } finally {
        if (requestId === favoriteRequestId) {
            favoriteController = null;
            favoriteState.requestPromise = null;
        }
    }
}

function multilineMarkup(value) {
    return escapeHtml(value).replaceAll('\n', '<br>');
}

function askPanel() {
    return '<section id="ask-panel" class="card">' + askPanelContent() + '</section>';
}

function askPanelContent() {
    const loadingAuthentication = askState.screen === 'IDLE' || askState.screen === 'AUTHENTICATING';
    const unauthenticated = askState.screen === 'UNAUTHENTICATED';
    const pending = askState.screen === 'PENDING';
    const limited = askState.screen === 'LIMITED' || (askState.screen === 'RESULT' && askState.response?.remaining === 0);
    const disabled = loadingAuthentication || unauthenticated || pending || limited;
    const prompt = loadingAuthentication
        ? '<p class="small-copy">로그인 상태를 확인하고 있어요.</p>'
        : unauthenticated
            ? '<p class="small-copy">로그인하면 공고문 내용을 질문할 수 있어요.</p><a class="btn btn-secondary" href="login.html">로그인하기</a>'
            : limited
                ? '<p class="small-copy">오늘 사용할 수 있는 질문 횟수를 모두 사용했어요. 내일 다시 물어봐 주세요.</p>'
                : '<p class="small-copy">공고 원문에 있는 내용만 답변해 드려요. 개인정보는 입력하지 마세요.</p>';
    const question = hasText(askState.submittedQuestion)
        ? '<div class="ask-conversation"><p class="ask-question"><strong>질문</strong> ' + multilineMarkup(askState.submittedQuestion) + '</p>' + askAnswerMarkup() + '</div>'
        : '';
    const error = askState.errorMessage
        ? '<p class="ask-message" role="alert">' + escapeHtml(askState.errorMessage) + '</p>'
            + (askState.screen === 'ERROR' && hasText(askState.submittedQuestion)
                ? '<button class="btn btn-secondary" type="button" data-retry-ask>같은 질문 다시 전송</button><p class="small-copy">네트워크 오류 뒤에는 서버에서 이미 처리됐을 수 있어요.</p>'
                : '')
        : '';
    const remaining = askState.response && Number.isInteger(askState.response.remaining)
        ? '<p class="small-copy">오늘 남은 질문: ' + askState.response.remaining + '회</p>'
        : '';
    const form = '<form id="question-form" class="question-form"><label class="sr-only" for="question-input">공고문 질문</label><textarea id="question-input" maxlength="200" placeholder="예: 어떤 서류가 필요한가요?"' + (disabled ? ' disabled' : '') + '>' + escapeHtml(askState.draft) + '</textarea><button class="btn primary-button" type="submit"' + (disabled ? ' disabled aria-disabled="true"' : '') + '>' + (pending ? '답변 생성 중…' : '질문 전송') + '</button></form>';
    return '<h2>복덩이에게 물어보세요</h2>' + prompt + remaining + question + error + form;
}

function askAnswerMarkup() {
    if (askState.screen === 'PENDING') {
        return '<p class="ask-message" aria-live="polite">공고 원문을 확인하며 답변을 만들고 있어요.</p>';
    }
    if (askState.screen !== 'RESULT' || !askState.response) return '';

    const response = askState.response;
    const quotes = response.sourceQuotes.length
        ? '<section class="ask-evidence"><h3>공고 원문 근거</h3><ul>' + response.sourceQuotes.map((quote) => '<li>' + multilineMarkup(quote) + '</li>').join('') + '</ul></section>'
        : '<p class="small-copy">공고 원문에서 확인된 근거 문장이 제공되지 않았어요.</p>';
    const eligibility = response.eligibility
        ? '<p class="eligibility-notice">자격 여부는 AI 답변이 아닌 내 자격 확인 결과를 기준으로 확인해주세요. <button class="text-button" type="button" data-open-eligibility>내 자격 확인 보기</button></p>'
        : '';
    const notice = hasText(response.notice) ? '<p class="small-copy">' + multilineMarkup(response.notice) + '</p>' : '';
    return '<section class="answer"><h3>복덩이 답변</h3><p>' + multilineMarkup(response.answer) + '</p>' + quotes + eligibility + notice + '</section>';
}

function updateAskPanel() {
    const panel = $('#ask-panel');
    if (panel) panel.innerHTML = askPanelContent();
}

function resetAskState(benefitId) {
    askRequestId += 1;
    if (askController) askController.abort();
    askController = null;
    askState.benefitId = benefitId;
    askState.screen = 'IDLE';
    askState.response = null;
    askState.draft = '';
    askState.submittedQuestion = '';
    askState.errorKind = null;
    askState.errorMessage = '';
    askState.requestPromise = null;
}

async function loadAskAuthentication() {
    const benefitId = state.benefitId;
    if (!benefitId || !state.detail) return;
    const requestId = ++askRequestId;
    askState.benefitId = benefitId;
    askState.screen = 'AUTHENTICATING';
    askState.errorKind = null;
    askState.errorMessage = '';
    updateAskPanel();

    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.refreshAuthentication !== 'function') {
            throw new BenefitDetailRequestError('SERVER', '로그인 상태를 확인하지 못했습니다.');
        }
        const authenticationState = await auth.refreshAuthentication();
        if (requestId !== askRequestId || state.benefitId !== benefitId) return;
        askState.screen = authenticationState === 'authenticated' ? 'READY' : 'UNAUTHENTICATED';
        updateAskPanel();
    } catch (error) {
        if (requestId !== askRequestId || (error && error.name === 'AbortError')) return;
        askState.screen = 'ERROR';
        askState.errorKind = error && error.type === 'network' ? 'NETWORK' : 'SERVER';
        askState.errorMessage = '로그인 상태를 확인하지 못했습니다. 다시 시도해주세요.';
        updateAskPanel();
    }
}

function normalizeQuestion(value) {
    return typeof value === 'string' ? value.trim() : '';
}

async function submitAsk(question) {
    const benefitId = state.benefitId;
    if (!benefitId || !state.detail || askState.screen === 'PENDING') return;
    const normalizedQuestion = normalizeQuestion(question);
    if (!normalizedQuestion) {
        askState.errorKind = 'INPUT';
        askState.errorMessage = '질문을 입력해주세요.';
        askState.screen = 'READY';
        updateAskPanel();
        return;
    }
    if (normalizedQuestion.length > 200) {
        askState.errorKind = 'INPUT';
        askState.errorMessage = '질문은 200자 이내로 입력해주세요.';
        askState.draft = normalizedQuestion;
        askState.screen = 'READY';
        updateAskPanel();
        return;
    }
    if (askState.screen === 'UNAUTHENTICATED') {
        updateAskPanel();
        return;
    }
    if (askState.screen !== 'READY' && askState.screen !== 'RESULT' && askState.screen !== 'ERROR') return;

    const requestId = ++askRequestId;
    if (askController) askController.abort();
    const controller = new AbortController();
    askController = controller;
    askState.draft = normalizedQuestion;
    askState.submittedQuestion = normalizedQuestion;
    askState.response = null;
    askState.screen = 'PENDING';
    askState.errorKind = null;
    askState.errorMessage = '';
    updateAskPanel();

    const request = (async () => {
        try {
            const auth = window.BokdeongiAuth;
            if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.getCsrfToken !== 'function') {
                throw new BenefitDetailRequestError('SERVER', '공고문 질문 서비스를 설정하지 못했습니다.');
            }
            const csrf = await auth.getCsrfToken();
            const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/ask', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
                body: JSON.stringify({ question: normalizedQuestion }),
                signal: controller.signal
            });
            if (requestId !== askRequestId || state.benefitId !== benefitId) return;
            if (response.status === 401) {
                askState.screen = 'UNAUTHENTICATED';
                updateAskPanel();
                return;
            }
            if (response.status === 429) {
                askState.screen = 'LIMITED';
                askState.errorKind = 'LIMIT';
                askState.errorMessage = await readErrorMessage(response) || '오늘 사용할 수 있는 질문 횟수를 모두 사용했어요.';
                updateAskPanel();
                return;
            }
            if (!response.ok) {
                throw new BenefitDetailRequestError(response.status === 400 ? 'INPUT' : response.status === 404 ? 'NOT_FOUND' : response.status === 502 ? 'AI_UNAVAILABLE' : 'SERVER', await readErrorMessage(response));
            }
            const payload = await response.json();
            if (!isAskResponse(payload)) {
                throw new BenefitDetailRequestError('INVALID', '공고문 질문 응답 형식이 올바르지 않습니다.');
            }
            if (requestId !== askRequestId || state.benefitId !== benefitId) return;
            askState.response = payload;
            askState.draft = '';
            askState.screen = 'RESULT';
            updateAskPanel();
        } catch (error) {
            if ((error && error.name === 'AbortError') || requestId !== askRequestId) return;
            if (error && error.type === 'unauthenticated') {
                askState.screen = 'UNAUTHENTICATED';
                askState.errorMessage = '';
            } else {
                askState.screen = 'ERROR';
                askState.errorKind = error instanceof BenefitDetailRequestError ? error.kind : 'NETWORK';
                const fallback = askState.errorKind === 'AI_UNAVAILABLE'
                    ? '지금은 질문에 답할 수 없어요. 공고 원문을 확인하거나 잠시 후 다시 물어봐 주세요.'
                    : askState.errorKind === 'NOT_FOUND'
                        ? '혜택을 찾을 수 없어요.'
                        : askState.errorKind === 'INPUT'
                            ? '질문 형식을 확인해주세요.'
                            : askState.errorKind === 'NETWORK'
                                ? '네트워크 연결을 확인한 뒤 다시 시도해주세요.'
                                : '질문 요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
                askState.errorMessage = error instanceof BenefitDetailRequestError && error.message ? error.message : fallback;
            }
            updateAskPanel();
        } finally {
            if (requestId === askRequestId) {
                askController = null;
                askState.requestPromise = null;
            }
        }
    })();
    askState.requestPromise = request;
    return request;
}

async function loadEligibility(force = false) {
    const benefitId = state.benefitId;
    if (!benefitId || !state.detail) return;
    if (!force && eligibilityState.benefitId === benefitId) {
        if (eligibilityState.requestPromise) return eligibilityState.requestPromise;
        if (['RESULT', 'UNAUTHENTICATED', 'NO_RESULT'].includes(eligibilityState.screen)) return;
    }

    const requestId = ++eligibilityRequestId;
    if (eligibilityController) eligibilityController.abort();
    const controller = new AbortController();
    eligibilityController = controller;
    eligibilityState.benefitId = benefitId;
    eligibilityState.screen = 'LOADING';
    eligibilityState.response = null;
    eligibilityState.errorKind = null;
    eligibilityState.errorMessage = '';
    updateEligibilityPanel();

    const request = (async () => {
        try {
            const auth = window.BokdeongiAuth;
            if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.refreshAuthentication !== 'function') {
                throw new BenefitDetailRequestError('SERVER', '로그인 상태를 확인하지 못했습니다.');
            }
            const authenticationState = await auth.refreshAuthentication();
            if (authenticationState !== 'authenticated') {
                if (requestId !== eligibilityRequestId) return;
                eligibilityState.screen = 'UNAUTHENTICATED';
                updateEligibilityPanel();
                return;
            }

            const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/eligibility', {
                signal: controller.signal
            });
            if (response.status === 401 || response.status === 403) {
                if (requestId !== eligibilityRequestId) return;
                eligibilityState.screen = 'UNAUTHENTICATED';
                updateEligibilityPanel();
                return;
            }
            if (response.status === 404) {
                if (requestId !== eligibilityRequestId) return;
                eligibilityState.screen = 'NO_RESULT';
                updateEligibilityPanel();
                return;
            }
            if (!response.ok) {
                throw new BenefitDetailRequestError('SERVER', await readErrorMessage(response));
            }
            const payload = await response.json();
            if (!isEligibilityResponse(payload)) {
                throw new BenefitDetailRequestError('INVALID', '자격 판정 결과 형식이 올바르지 않습니다.');
            }
            if (requestId !== eligibilityRequestId || state.benefitId !== benefitId) return;

            eligibilityState.response = payload;
            eligibilityState.screen = 'RESULT';
            updateEligibilityPanel();
        } catch (error) {
            if ((error && error.name === 'AbortError') || requestId !== eligibilityRequestId) return;
            if (error && error.type === 'unauthenticated') {
                eligibilityState.screen = 'UNAUTHENTICATED';
            } else {
                eligibilityState.screen = 'ERROR';
                eligibilityState.errorKind = error instanceof BenefitDetailRequestError ? error.kind : 'NETWORK';
                eligibilityState.errorMessage = error instanceof BenefitDetailRequestError ? error.message : '';
            }
            updateEligibilityPanel();
            console.error('혜택 자격 판정 API를 호출하지 못했습니다.', error);
        } finally {
            if (requestId === eligibilityRequestId) {
                eligibilityController = null;
                eligibilityState.requestPromise = null;
            }
        }
    })();
    eligibilityState.requestPromise = request;
    return request;
}

async function loadEasy(force = false) {
    const benefitId = state.benefitId;
    if (!benefitId || !state.detail) return;
    if (!force && easyState.benefitId === benefitId) {
        if (easyState.requestPromise) return easyState.requestPromise;
        if (['DONE', 'NOT_READY', 'FAILED'].includes(easyState.screen)) return;
    }

    const requestId = ++easyRequestId;
    if (easyController) easyController.abort();
    const controller = new AbortController();
    easyController = controller;
    easyState.benefitId = benefitId;
    easyState.screen = 'LOADING';
    easyState.response = null;
    easyState.errorKind = null;
    easyState.errorMessage = '';
    updateEasyPanel();

    const request = (async () => {
        try {
            const auth = window.BokdeongiAuth;
            if (!auth || typeof auth.getApiUrl !== 'function') {
                throw new BenefitDetailRequestError('SERVER', '쉬운 말 조회 서비스를 설정하지 못했습니다.');
            }
            const response = await fetch(auth.getApiUrl('/api/benefits/' + encodeURIComponent(benefitId) + '/easy'), {
                credentials: 'include',
                signal: controller.signal
            });
            if (!response.ok) {
                throw new BenefitDetailRequestError('SERVER', await readErrorMessage(response));
            }
            const payload = await response.json();
            if (!isEasyResponse(payload)) {
                throw new BenefitDetailRequestError('INVALID', '쉬운 말 결과 형식이 올바르지 않습니다.');
            }
            if (requestId !== easyRequestId || state.benefitId !== benefitId) return;

            easyState.response = payload;
            easyState.screen = payload.status;
            updateEasyPanel();
        } catch (error) {
            if ((error && error.name === 'AbortError') || requestId !== easyRequestId) return;
            easyState.screen = 'ERROR';
            easyState.errorKind = error instanceof BenefitDetailRequestError ? error.kind : 'NETWORK';
            easyState.errorMessage = error instanceof BenefitDetailRequestError ? error.message : '';
            updateEasyPanel();
            console.error('혜택 쉬운 말 API를 호출하지 못했습니다.', error);
        } finally {
            if (requestId === easyRequestId) {
                easyController = null;
                easyState.requestPromise = null;
            }
        }
    })();
    easyState.requestPromise = request;
    return request;
}

function originalPanel(detail) {
    const sections = [
        ['지원 내용', 'contentText', detail.contentText],
        ['선정 기준', 'criteriaText', detail.criteriaText],
        ['신청 기간 안내', 'applyPeriodText', detail.applyPeriodText]
    ].filter(([, field, value]) => !isMissing(detail, field) && hasText(value))
        .map(([heading, , value]) => '<section class="notice-document__section"><h3>' + escapeHtml(heading) + '</h3><p>' + escapeHtml(value.trim()) + '</p></section>').join('');
    const content = sections || '<p class="small-copy">표시할 공고 원문 정보가 제공되지 않았습니다.</p>';
    return '<section class="tab-panel" id="panel-original" role="tabpanel" aria-labelledby="tab-original" hidden><div class="notice-intro"><span class="notice-intro__icon" aria-hidden="true">i</span><p>아래 내용은 공고에서 제공된 원문 정보입니다. 최신 내용은 공식 공고문을 확인해주세요.</p></div><div class="notice-heading"><h2>공고문 원문</h2><div class="notice-heading__actions"><span>공식 공고 자료</span><div class="detail-actions">' + externalLink(detail.detailUrl, '공고문 바로가기', '공식 공고문 없음') + '<button class="icon-button" type="button" disabled aria-disabled="true">PDF 자료 없음</button></div></div></div><div class="notice-viewer"><article class="notice-document">' + content + '</article></div></section>';
}

function documentsPanel(detail) {
    if (isMissing(detail, 'requiredDocsText')) {
        return '<section class="tab-panel" id="panel-documents" role="tabpanel" aria-labelledby="tab-documents" hidden>' + panelNotice('신청에 필요한 서류', '필요 서류 정보가 제공되지 않았습니다. 공식 공고문에서 확인해 주세요.') + '</section>';
    }
    return '<section class="tab-panel" id="panel-documents" role="tabpanel" aria-labelledby="tab-documents" hidden><div class="notice-intro"><span class="notice-intro__icon" aria-hidden="true">i</span><p>아래는 공고에 제공된 필요 서류 원문입니다.</p></div><section class="source-card"><h2>신청에 필요한 서류</h2><p>' + escapeHtml(detail.requiredDocsText.trim()) + '</p></section></section>';
}

function applyPanel(detail) {
    const hasApplyLink = isSafeExternalUrl(detail.onlineApplyUrl) && !detail.ended;
    const method = isMissing(detail, 'applyMethodText') ? '신청 방법 정보가 제공되지 않았습니다. 공식 공고문에서 확인해 주세요.' : detail.applyMethodText.trim();
    const applicationButton = hasApplyLink
        ? '<a class="btn primary-button" href="' + escapeHtml(detail.onlineApplyUrl) + '" target="_blank" rel="noopener noreferrer">공식 신청 사이트 바로가기 ↗</a>'
        : '<button class="btn primary-button" type="button" disabled aria-disabled="true">' + (detail.ended ? '종료된 혜택입니다' : '공식 신청 링크 없음') + '</button>';
    return '<section class="tab-panel" id="panel-apply" role="tabpanel" aria-labelledby="tab-apply" hidden><div class="notice-intro"><span class="notice-intro__icon" aria-hidden="true">i</span><p>아래는 공고에 제공된 신청 방법 원문입니다.</p></div><section class="source-card"><h2>신청 방법</h2><p>' + escapeHtml(method) + '</p></section><section class="application-cta"><div><h3>공식 사이트에서 신청하기</h3><p>' + (detail.ended ? '이 혜택은 종료되어 신청할 수 없습니다.' : '신청 가능 여부와 제출 방법은 공식 공고를 확인해주세요.') + '</p></div>' + applicationButton + '</section></section>';
}

function sidebar(detail) {
    const target = isMissing(detail, 'targetText') ? '공고 확인 필요' : detail.targetText.trim();
    const method = isMissing(detail, 'applyMethodText') ? '공고 확인 필요' : detail.applyMethodText.trim();
    const assistantButton = detail.ended
        ? '<button id="assistant-button" class="btn primary-button" type="button" disabled aria-disabled="true">종료된 혜택입니다</button>'
        : '<button id="assistant-button" class="btn primary-button" type="button">AI 신청 도우미로 시작하기</button>';
    return '<aside class="detail-sidebar"><section class="card"><h2>신청 정보</h2><dl class="application-info"><div><dt>공고 상태</dt><dd>' + escapeHtml(detail.ended ? '종료' : (detail.status || '정보 제공되지 않음')) + '</dd></div><div><dt>지원 대상</dt><dd>' + escapeHtml(target) + '</dd></div><div><dt>지원 금액</dt><dd>' + escapeHtml(amountText(detail)) + '</dd></div><div><dt>신청 기간</dt><dd>' + escapeHtml(periodText(detail)) + '</dd></div><div><dt>신청 방법</dt><dd>' + escapeHtml(method) + '</dd></div></dl>' + assistantButton + '</section>' + askPanel() + '</aside>';
}

function loadingMarkup() {
    return '<section class="card empty-state" aria-live="polite"><h1>혜택 정보를 불러오는 중이에요.</h1><p>잠시만 기다려주세요.</p></section>';
}

function errorMarkup() {
    const messages = {
        ID_MISSING: ['혜택 ID가 없어요.', '목록에서 혜택을 다시 선택해주세요.'],
        ID_INVALID: ['잘못된 혜택 ID예요.', '목록에서 혜택을 다시 선택해주세요.'],
        NOT_FOUND: ['혜택 정보를 찾을 수 없어요.', '삭제되었거나 존재하지 않는 혜택입니다.'],
        NETWORK: ['네트워크에 연결하지 못했어요.', '인터넷 연결과 서버 주소를 확인한 뒤 다시 시도해주세요.'],
        SERVER: ['혜택 정보를 불러오지 못했어요.', state.errorMessage || '잠시 후 다시 시도해주세요.']
    };
    const [title, description] = messages[state.errorKind] || messages.SERVER;
    const retry = state.benefitId ? '<button class="btn btn-secondary" type="button" data-retry-detail>다시 시도</button>' : '';
    return '<section class="card empty-state"><h1>' + escapeHtml(title) + '</h1><p>' + escapeHtml(description) + '</p>' + retry + '<a class="btn btn-secondary" href="benefit-list.html">목록으로 돌아가기</a></section>';
}

function render() {
    const page = $('#detail-page');
    const banner = $('#detail-banner-copy');
    if (state.screen === 'LOADING') {
        banner.replaceChildren();
        page.innerHTML = loadingMarkup();
        return;
    }
    if (state.screen === 'ERROR') {
        banner.replaceChildren();
        page.innerHTML = errorMarkup();
        return;
    }

    const detail = state.detail;
    const title = hasText(detail.title) ? detail.title.trim() : '혜택 상세';
    const summary = summaryLines(detail)[0] || '공고 상세 정보를 확인하세요.';
    banner.innerHTML = '<h1>' + escapeHtml(title) + '</h1><p>' + escapeHtml(summary) + '</p>';
    page.innerHTML = getPageMarkup(detail);
}

async function readErrorMessage(response) {
    try {
        const payload = await response.json();
        const message = payload?.message || payload?.detail;
        return hasText(message) ? message.trim() : '';
    } catch {
        return '';
    }
}

async function loadDetail(id) {
    const requestId = ++detailRequestId;
    if (activeController) activeController.abort();
    resetEasyState(null);
    resetEligibilityState(null);
    resetFavoriteState(null);
    resetAskState(null);
    activeController = new AbortController();
    state.benefitId = id;
    state.detail = null;
    state.screen = 'LOADING';
    state.errorKind = null;
    state.errorMessage = '';
    render();

    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.getApiUrl !== 'function') {
            throw new BenefitDetailRequestError('SERVER', '상세 조회 서비스를 설정하지 못했습니다.');
        }
        const response = await fetch(auth.getApiUrl('/api/benefits/' + encodeURIComponent(id)), {
            credentials: 'include',
            signal: activeController.signal
        });
        if (response.status === 404) {
            throw new BenefitDetailRequestError('NOT_FOUND', await readErrorMessage(response));
        }
        if (!response.ok) {
            throw new BenefitDetailRequestError('SERVER', await readErrorMessage(response));
        }

        const payload = await response.json();
        if (!isBenefitDetailResponse(payload)) {
            throw new BenefitDetailRequestError('SERVER', '혜택 상세 결과 형식이 올바르지 않습니다.');
        }
        if (requestId !== detailRequestId) return;

        state.detail = payload;
        resetEasyState(id);
        resetEligibilityState(id);
        resetFavoriteState(id);
        resetAskState(id);
        state.screen = 'RESULT';
        render();
        loadEasy();
        loadEligibility();
        loadFavorite();
        loadAskAuthentication();
    } catch (error) {
        if ((error && error.name === 'AbortError') || requestId !== detailRequestId) return;
        state.screen = 'ERROR';
        state.errorKind = error instanceof BenefitDetailRequestError ? error.kind : 'NETWORK';
        state.errorMessage = error instanceof BenefitDetailRequestError ? error.message : '';
        render();
        console.error('혜택 상세 API를 호출하지 못했습니다.', error);
    } finally {
        if (requestId === detailRequestId) activeController = null;
    }
}

function selectTab(target) {
    document.querySelectorAll('.tab-button').forEach((button) => {
        const selected = button === target;
        button.setAttribute('aria-selected', String(selected));
        const panel = $('#' + button.getAttribute('aria-controls'));
        if (panel) panel.hidden = !selected;
    });
    if (target.dataset.tab === 'easy') loadEasy();
}

async function copyUrl() {
    try {
        await navigator.clipboard.writeText(window.location.href);
        toast('현재 페이지 주소를 복사했어요.');
    } catch {
        toast('주소 복사에 실패했어요. 주소창에서 다시 복사해주세요.');
    }
}

function toast(message) {
    const element = $('#toast');
    element.textContent = message;
    element.hidden = false;
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => { element.hidden = true; }, 3000);
}

async function startAssistant() {
    if (assistantStartPending || !state.detail || !state.benefitId || state.detail.ended) return;
    const api = window.BokdeongiApplication;
    if (!api) {
        toast('신청 도우미를 준비하지 못했습니다. 잠시 후 다시 시도해주세요.');
        return;
    }
    const button = $('#assistant-button');
    assistantStartPending = true;
    if (button) {
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        button.textContent = '신청 정보를 준비하는 중…';
    }
    try {
        const application = await api.startApplication(state.benefitId);
        if (!api.canEdit(application)) {
            toast('현재 상태의 신청은 신청 도우미에서 수정할 수 없습니다.');
            return;
        }
        const target = api.assistantUrl(application.step, application.id);
        if (!target) throw new api.ApplicationApiError('INVALID', '신청 단계 정보를 확인하지 못했습니다.');
        window.location.assign(target);
    } catch (error) {
        toast(api.messageFor(error));
    } finally {
        assistantStartPending = false;
        if (button && state.screen === 'RESULT' && state.detail && !state.detail.ended) {
            button.disabled = false;
            button.removeAttribute('aria-busy');
            button.textContent = 'AI 신청 도우미로 시작하기';
        }
    }
}

function setup() {
    const id = parseBenefitId(new URLSearchParams(window.location.search).get('id'));
    document.addEventListener('click', (event) => {
        const tab = event.target.closest('.tab-button');
        if (tab) selectTab(tab);

        if (event.target.closest('[data-open-original]')) {
            const originalTab = $('#tab-original');
            if (originalTab) selectTab(originalTab);
        }
        if (event.target.closest('[data-retry-easy]')) loadEasy(true);
        if (event.target.closest('[data-retry-eligibility]')) loadEligibility(true);
        if (event.target.closest('[data-retry-ask]') && hasText(askState.submittedQuestion)) submitAsk(askState.submittedQuestion);

        if (event.target.closest('[data-open-eligibility]')) {
            const easyTab = $('#tab-easy');
            if (easyTab) selectTab(easyTab);
        }

        const hashtag = event.target.closest('[data-hashtag]');
        if (hashtag) window.location.href = 'benefit-list.html?hashtag=' + encodeURIComponent(hashtag.dataset.hashtag);

        if (event.target.closest('#share-button')) copyUrl();
        if (event.target.closest('#save-button')) toggleFavorite();
        if (event.target.closest('#assistant-button')) void startAssistant();
        if (event.target.closest('[data-retry-detail]') && state.benefitId) loadDetail(state.benefitId);
    });
    document.addEventListener('submit', (event) => {
        if (!event.target.closest('#question-form')) return;
        event.preventDefault();
        const input = $('#question-input');
        submitAsk(input ? input.value : '');
    });
    document.addEventListener('keydown', (event) => {
        if (event.target?.id !== 'question-input' || event.key !== 'Enter' || event.shiftKey) return;
        event.preventDefault();
        submitAsk(event.target.value);
    });

    if (id === null) {
        state.screen = 'ERROR';
        state.errorKind = new URLSearchParams(window.location.search).has('id') ? 'ID_INVALID' : 'ID_MISSING';
        render();
        return;
    }
    loadDetail(id);
}

document.addEventListener('DOMContentLoaded', setup);
