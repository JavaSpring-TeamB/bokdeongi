const recommendationData = {
    eligible: {
        banner: {
            prefix: '신청 가능성이 높은',
            emphasis: '복지 혜택',
            suffix: '을 추천드려요',
            description: '입력하신 정보를 바탕으로 신청 가능성이 높은 복지 혜택을 추천드려요.'
        },
        statusLabel: '해당',
        cards: [
            { icon: 'home', category: '주거·생활', title: '청년 월세 지원', description: '청년의 주거비 부담을 덜어주는 월세 지원 사업이에요.', primaryLabel: '지원 금액', primaryValue: '월 최대 20만원', period: '~ 2026.11.12 (D-7)', reasonLabel: '추천 이유', reason: '연령, 거주지역, 소득 조건을 충족해요.', action: '상세 보기', detailId: 1 },
            { icon: 'graduate', category: '취업·교육', title: '청년 국가기술자격 응시료 지원', description: '청년의 자격증 취득을 돕기 위해 응시료를 지원해요.', primaryLabel: '지원 금액', primaryValue: '연 최대 10만원', period: '~ 2026.12.31 (D-56)', reasonLabel: '추천 이유', reason: '청년 연령과 거주지역 조건에 해당해요.', action: '상세 보기', detailId: 2 },
            { icon: 'ticket', category: '문화·여가', title: '문화누리카드 지원', description: '문화·예술·여가 활동을 위한 이용권을 지원해요.', primaryLabel: '지원 금액', primaryValue: '연 13만원 (카드 포인트)', period: '상시 신청', reasonLabel: '추천 이유', reason: '입력하신 소득과 가구 형태가 지원 조건에 맞아요.', action: '상세 보기', detailId: 11 }
        ]
    },
    conditional: {
        banner: {
            prefix: '',
            emphasis: '조금만 더 확인하면',
            suffix: ' 신청할 수 있는 혜택이 있어요.',
            description: '일부 정보가 부족하거나 추가 확인이 필요한 혜택을 확인하고, 조건을 보완해보세요.'
        },
        statusLabel: '조건부',
        cards: [
            { icon: 'home', category: '주거·생활', title: '청년 월세 지원', description: '청년의 주거비 부담을 덜어주는 월세 지원 사업이에요.', noticeTitle: '무주택 여부 확인이 필요해요.', notice: '현재 무주택 여부가 확인되지 않아 신청 가능성을 판단할 수 없어요.', period: '~ 2026.11.12 (D-7)', action: '조건 확인하기', detailId: 1 },
            { icon: 'graduate', category: '취업·교육', title: '청년 취업 지원금', description: '청년의 구직활동과 취업 준비를 지원하는 사업이에요.', noticeTitle: '현재 취업 상태 확인이 필요해요.', notice: '재직 여부가 확인되지 않아 신청 가능 여부를 판단할 수 없어요.', period: '~ 2026.12.31 (D-56)', action: '조건 확인하기', detailId: 2 }
        ]
    },
    'near-miss': {
        banner: {
            prefix: '',
            emphasis: '조건이 맞지 않아',
            suffix: ' 신청할 수 없어요',
            description: '한 가지 조건만 충족하지 못한 혜택이에요. 어떤 조건이 필요한지 확인해보세요.'
        },
        statusLabel: '미충족',
        cards: [
            { icon: 'document', category: '문화·여가', title: '청년 문화활동 지원', description: '청년의 문화예술 활동 참여를 지원하는 사업이에요.', noticeTitle: '연령 조건이 맞지 않아요.', notice: '지원 대상은 만 19세 이상이지만 현재 만 18세예요.', period: '~ 2026.12.31 (D-56)', reasonLabel: '탈락 사유', reason: '신청일 기준 만 19세 이상이어야 신청할 수 있어요.', action: '상세 보기', detailId: 11 }
        ]
    }
};

const $ = (selector) => document.querySelector(selector);
let selectedStatus = 'eligible';
const conditionalAnswers = new Map();
const filterState = { region: 'all', category: 'all', sort: 'recommended' };
const PAGE_SIZE = 5;
const currentPages = { eligible: 1, conditional: 1, 'near-miss': 1 };

function cardIcon(name) {
    const paths = {
        home: '<path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M9 20v-6h6v6"/>',
        graduate: '<path d="m3 9 9-5 9 5-9 5-9-5Z"/><path d="M7 11.3V16c2.9 2 7.1 2 10 0v-4.7"/><path d="M21 9v6"/>',
        ticket: '<path d="M5 5h14v4a2 2 0 0 0 0 4v4H5v-4a2 2 0 0 0 0-4V5Z"/><path d="M12 7v8"/>',
        document: '<path d="M7 3.5h7l3 3v14H7z"/><path d="M14 3.5v4h3M9.5 12h5M9.5 15h5"/>'
    };

    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + paths[name] + '</svg>';
}

function bookmarkIcon() {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z"/></svg>';
}

function detailRow(label, value) {
    return '<div class="recommend-card__detail"><span aria-hidden="true">✓</span><span><strong>' + label + '</strong> ' + value + '</span></div>';
}

function renderEligibleCard(card) {
    return '<article class="recommend-card recommend-card--eligible">' +
        '<div class="recommend-card__eligible-top"><div class="recommend-card__badges"><span class="recommend-status-badge">' + recommendationData.eligible.statusLabel + '</span><span class="recommend-category-badge">' + card.category + '</span></div>' +
        '<button class="recommend-card__bookmark" type="button" data-bookmark aria-label="' + card.title + ' 관심 저장"><span aria-hidden="true">' + bookmarkIcon() + '</span></button></div>' +
        '<div class="recommend-card__eligible-content"><h3>' + card.title + '</h3><p class="recommend-card__description">' + card.description + '</p></div>' +
        '<dl class="recommend-card__eligible-info"><div><dt>' + card.primaryLabel + '</dt><dd>' + card.primaryValue + '</dd></div><div><dt>신청 기간</dt><dd>' + card.period + '</dd></div></dl>' +
        '<div class="recommend-card__eligible-reason"><strong>' + card.reasonLabel + '</strong><p>' + card.reason + '</p></div>' +
        '<div class="recommend-card__actions"><a class="bk-btn bk-btn--primary bk-btn--md" href="benefit-detail.html?id=' + card.detailId + '">상세 보기 <span aria-hidden="true">›</span></a></div>' +
        '</article>';
}

function conditionalQuestionId(card) {
    return 'benefit-' + card.detailId + '-condition';
}

function renderConditionalQuestion(card) {
    const questionId = conditionalQuestionId(card);
    const answer = conditionalAnswers.get(questionId);
    const prompt = card.noticeTitle || card.notice;

    if (answer) {
        const answerLabel = { YES: '예', NO: '아니오', UNSURE: '아직 몰라요' }[answer];
        const tone = answer === 'YES' ? 'yes' : answer === 'NO' ? 'no' : 'unsure';
        const guidance = answer === 'UNSURE'
            ? '<div class="conditional-question__result conditional-question__result--' + tone + '" role="status"><strong>기관에 확인해 보세요</strong><p><span>답변: ' + answerLabel + '</span> 공고문 또는 담당 기관에서 확인해 주세요.</p></div>'
            : '<div class="conditional-question__result conditional-question__result--' + tone + '" role="status"><strong>답변: ' + answerLabel + '</strong><p>실제 자격 판정은 서버 연동 후 반영됩니다.</p></div>';
        return '<section class="conditional-question" aria-labelledby="question-' + questionId + '"><h4 id="question-' + questionId + '">추가 확인이 필요한 항목</h4><p class="conditional-question__prompt">' + prompt + '</p>' + guidance + '</section>';
    }

    return '<section class="conditional-question" aria-labelledby="question-' + questionId + '"><h4 id="question-' + questionId + '">추가 확인이 필요한 항목</h4><p class="conditional-question__prompt">' + prompt + '</p><div class="conditional-question__choices" role="group" aria-label="' + prompt + '"><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-conditional-answer="' + questionId + '" data-answer-value="YES">예</button><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-conditional-answer="' + questionId + '" data-answer-value="NO">아니오</button><button class="bk-btn bk-btn--outline bk-btn--sm" type="button" data-conditional-answer="' + questionId + '" data-answer-value="UNSURE">아직 몰라요</button></div></section>';
}

function renderConditionalCard(card) {
    const primaryInfo = card.primaryLabel
        ? '<div><dt>' + card.primaryLabel + '</dt><dd>' + card.primaryValue + '</dd></div>'
        : '';
    const notice = card.noticeTitle
        ? '<div class="recommend-card__conditional-notice"><strong>' + card.noticeTitle + '</strong><p>' + card.notice + '</p></div>'
        : '';

    return '<article class="recommend-card recommend-card--conditional"><div class="recommend-card__conditional-info"><div class="recommend-card__eligible-top"><div class="recommend-card__badges"><span class="recommend-status-badge">' + recommendationData.conditional.statusLabel + '</span><span class="recommend-category-badge">' + card.category + '</span></div><button class="recommend-card__bookmark" type="button" data-bookmark aria-label="' + card.title + ' 관심 저장"><span aria-hidden="true">' + bookmarkIcon() + '</span></button></div><div class="recommend-card__eligible-content"><h3>' + card.title + '</h3><p class="recommend-card__description">' + card.description + '</p></div><dl class="recommend-card__conditional-meta">' + primaryInfo + '<div><dt>신청 기간</dt><dd>' + card.period + '</dd></div></dl>' + notice + '<div class="recommend-card__actions"><a class="bk-btn bk-btn--secondary bk-btn--md" href="benefit-detail.html?id=' + card.detailId + '">상세 보기 <span aria-hidden="true">›</span></a></div></div>' + renderConditionalQuestion(card) + '</article>';
}

function renderNearMissCard(card) {
    const primaryInfo = card.primaryLabel
        ? '<div><dt>' + card.primaryLabel + '</dt><dd>' + card.primaryValue + '</dd></div>'
        : '';
    const unmetCondition = card.noticeTitle
        ? '<div class="recommend-card__near-miss-condition"><strong>충족하지 못한 조건</strong><p>' + card.noticeTitle + '</p><span>' + card.notice + '</span></div>'
        : '';
    const reason = card.reasonLabel
        ? '<div class="recommend-card__near-miss-reason"><strong>' + card.reasonLabel + '</strong><p>' + card.reason + '</p></div>'
        : '';

    return '<article class="recommend-card recommend-card--near-miss"><div class="recommend-card__eligible-top"><div class="recommend-card__badges"><span class="recommend-status-badge">' + recommendationData['near-miss'].statusLabel + '</span><span class="recommend-category-badge">' + card.category + '</span></div><button class="recommend-card__bookmark" type="button" data-bookmark aria-label="' + card.title + ' 관심 저장"><span aria-hidden="true">' + bookmarkIcon() + '</span></button></div><div class="recommend-card__eligible-content"><h3>' + card.title + '</h3><p class="recommend-card__description">' + card.description + '</p></div><dl class="recommend-card__near-miss-meta">' + primaryInfo + '<div><dt>신청 기간</dt><dd>' + card.period + '</dd></div></dl>' + unmetCondition + reason + '<div class="recommend-card__actions"><a class="bk-btn bk-btn--outline bk-btn--md" href="benefit-detail.html?id=' + card.detailId + '">상세 보기 <span aria-hidden="true">›</span></a></div></article>';
}

function renderCard(card, status) {
    const data = recommendationData[status];
    const primaryRow = card.primaryLabel ? detailRow(card.primaryLabel, card.primaryValue) : '';
    const notice = card.noticeTitle
        ? '<div class="recommend-card__notice"><strong>' + card.noticeTitle + '</strong><span>' + card.notice + '</span></div>'
        : '';
    const reasonRow = card.reasonLabel ? detailRow(card.reasonLabel, card.reason) : '';
    const action = status === 'conditional'
        ? '<button class="btn" type="button" data-condition-check>조건 확인하기 <span aria-hidden="true">›</span></button>'
        : '<a class="btn" href="benefit-detail.html?id=' + card.detailId + '">상세 보기 <span aria-hidden="true">›</span></a>';

    return '<article class="recommend-card">' +
        '<div class="recommend-card__icon">' + cardIcon(card.icon) + '</div>' +
        '<div><div class="recommend-card__badges"><span class="recommend-status-badge">' + data.statusLabel + '</span><span class="recommend-category-badge">' + card.category + '</span></div>' +
        '<h3>' + card.title + '</h3><p class="recommend-card__description">' + card.description + '</p></div>' +
        '<div class="recommend-card__details">' + notice + primaryRow + detailRow('신청 기간', card.period) + reasonRow + '</div>' +
        '<div class="recommend-card__actions"><button class="recommend-card__bookmark" type="button" data-bookmark aria-label="' + card.title + ' 관심 저장"><span aria-hidden="true">' + bookmarkIcon() + '</span></button>' + action + '</div>' +
        '</article>';
}

function parsePeriodEnd(period) {
    const match = String(period || '').match(/(20\d{2})[.\-/](\d{1,2})[.\-/](\d{1,2})/);
    if (!match) return null;

    const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    return date.getFullYear() === Number(match[1]) && date.getMonth() === Number(match[2]) - 1 && date.getDate() === Number(match[3]) ? date : null;
}

function isClosed(card) {
    const deadline = parsePeriodEnd(card.period);
    if (!deadline) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return deadline < today;
}

function parseAmount(value) {
    const amount = String(value || '');
    const tenThousandMatch = amount.match(/(\d+(?:\.\d+)?)\s*만원/);
    if (tenThousandMatch) return Number(tenThousandMatch[1]) * 10000;
    const wonMatch = amount.match(/(\d+(?:\.\d+)?)\s*원/);
    return wonMatch ? Number(wonMatch[1]) : null;
}

function availableCards(status) {
    return recommendationData[status].cards.filter((card) => !isClosed(card));
}

function getVisibleCards(status) {
    const cards = availableCards(status).filter((card) => {
        if (filterState.category !== 'all' && card.category !== filterState.category) return false;
        if (filterState.region !== 'all' && card.region !== filterState.region) return false;
        return true;
    });

    return cards.sort((first, second) => {
        if (filterState.sort === 'deadline') {
            const firstDate = parsePeriodEnd(first.period);
            const secondDate = parsePeriodEnd(second.period);
            if (!firstDate) return secondDate ? 1 : 0;
            if (!secondDate) return -1;
            return firstDate - secondDate;
        }
        if (filterState.sort === 'amount') {
            const firstAmount = parseAmount(first.primaryValue);
            const secondAmount = parseAmount(second.primaryValue);
            if (firstAmount === null) return secondAmount === null ? 0 : 1;
            if (secondAmount === null) return -1;
            return secondAmount - firstAmount;
        }
        return 0;
    });
}

function emptyStateMarkup() {
    return '<div class="recommend-empty-state" role="status"><strong>조건에 맞는 혜택이 없어요.</strong><p>분야 또는 정렬 기준을 조정해 보세요.</p></div>';
}

function renderStatus(status) {
    const data = recommendationData[status];
    const banner = data.banner;
    const cards = getVisibleCards(status);
    currentPages[status] = window.BokdeongiPagination.clampPage(cards.length, currentPages[status], PAGE_SIZE);
    const start = (currentPages[status] - 1) * PAGE_SIZE;
    const visibleCards = cards.slice(start, start + PAGE_SIZE);

    document.body.dataset.status = status;
    $('[data-banner-prefix]').textContent = banner.prefix;
    $('[data-banner-emphasis]').textContent = banner.emphasis;
    $('[data-banner-suffix]').textContent = banner.suffix;
    $('[data-banner-description]').textContent = banner.description;
    $('[data-result-count]').textContent = String(cards.length);
    const render = status === 'eligible'
        ? renderEligibleCard
        : status === 'conditional'
            ? renderConditionalCard
            : renderNearMissCard;
    $('[data-recommend-list]').innerHTML = cards.length ? visibleCards.map(render).join('') : emptyStateMarkup();
    $('[data-recommend-pagination]').innerHTML = cards.length
        ? window.BokdeongiPagination.markup({ totalCount: cards.length, currentPage: currentPages[status], pageSize: PAGE_SIZE, label: data.statusLabel + ' 혜택' })
        : '';
    $('#recommend-results').setAttribute('aria-labelledby', status + '-tab');

    document.querySelectorAll('[data-status-tab]').forEach((tab) => {
        const isSelected = tab.dataset.statusTab === status;
        tab.classList.toggle('is-active', isSelected);
        tab.setAttribute('aria-selected', String(isSelected));
    });
}

function renderTabCounts() {
    document.querySelectorAll('[data-tab-count]').forEach((element) => {
        const count = availableCards(element.dataset.tabCount).length;
        element.textContent = '(' + count + ')';
    });
}

function setupFilters() {
    const categorySelect = $('[data-filter="category"]');
    const categories = [...new Set(Object.values(recommendationData).flatMap((data) => data.cards.map((card) => card.category)).filter(Boolean))];
    categorySelect.insertAdjacentHTML('beforeend', categories.map((category) => '<option value="' + category + '">' + category + '</option>').join(''));

    document.addEventListener('change', (event) => {
        const filter = event.target.closest('[data-filter]');
        if (!filter) return;
        filterState[filter.dataset.filter] = filter.value;
        currentPages[selectedStatus] = 1;
        renderStatus(selectedStatus);
    });
}

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;

    window.setTimeout(() => {
        toast.hidden = true;
    }, 2800);
}

function setupInteractions() {
    document.addEventListener('click', (event) => {
        const tab = event.target.closest('[data-status-tab]');
        if (tab) {
            selectedStatus = tab.dataset.statusTab;
            currentPages[selectedStatus] = 1;
            renderStatus(selectedStatus);
            return;
        }

        if (event.target.closest('[data-bookmark]')) {
            showToast('관심 저장 기능은 API 연동 후 사용할 수 있어요.');
            return;
        }

        const answerButton = event.target.closest('[data-conditional-answer]');
        if (answerButton) {
            conditionalAnswers.set(answerButton.dataset.conditionalAnswer, answerButton.dataset.answerValue);
            renderStatus(selectedStatus);
            return;
        }

        if (event.target.closest('[data-condition-check]')) {
            showToast('조건 확인과 재판정 기능은 추후 연동 예정입니다.');
        }
    });
    document.addEventListener('bokdeongi:pagination', (event) => {
        currentPages[selectedStatus] = event.detail.page;
        renderStatus(selectedStatus);
    });
}

document.addEventListener('DOMContentLoaded', () => {
    renderTabCounts();
    setupFilters();
    renderStatus(selectedStatus);
    setupInteractions();
});
