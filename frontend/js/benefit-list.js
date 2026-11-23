const FILTER_CONFIGS = {
    status: ['전체', '신청 가능', '신청 예정', '신청 마감'],
    category: ['전체', '주거', '고용·취업', '교육', '금융', '보육·가족', '건강·의료', '문화·생활'],
    target: ['전체', '청년', '대학생', '구직자', '신혼부부', '임산부', '어르신', '저소득층']
};

const PAGE_SIZE = 6;
const state = {
    query: '',
    hashtag: '',
    response: null,
    screen: 'INITIAL',
    currentPage: 1,
    errorKind: null,
    errorMessage: ''
};

const $ = (selector) => document.querySelector(selector);
let searchRequestId = 0;
let activeController = null;

class BenefitListRequestError extends Error {
    constructor(kind, message) {
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

function normalizeQuery(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function isValidSearchQuery(query) {
    return query.length === 0
        || (query.length >= 2 && /[\p{L}\p{N}]/u.test(query));
}

function parseHashtags(value) {
    if (Array.isArray(value)) {
        return value.filter(hasText).map((tag) => tag.trim());
    }
    if (!hasText(value)) return [];

    try {
        const parsed = JSON.parse(value);
        if (Array.isArray(parsed)) return parsed.filter(hasText).map((tag) => tag.trim());
    } catch {
        // API DTO가 JSON 문자열이 아닌 경우에는 쉼표 구분 문자열로 처리한다.
    }

    return value.split(',').map((tag) => tag.trim()).filter(Boolean);
}

function summaryText(item) {
    return [item.summaryWhat, item.summaryWho, item.summaryHow, item.summaryWhen]
        .filter(hasText)
        .map((value) => value.trim())
        .join(' · ');
}

function applyPeriod(item) {
    const start = hasText(item.applyStart) ? item.applyStart.trim() : '';
    const end = hasText(item.applyEnd) ? item.applyEnd.trim() : '';
    if (start && end) return start + ' ~ ' + end;
    if (start) return start + '부터';
    if (end) return end + '까지';
    return '';
}

function eligibilityLabel(value) {
    const labels = {
        ELIGIBLE: '내게 맞아요',
        CONDITIONAL: '조건 확인 필요',
        INELIGIBLE: '조건이 맞지 않아요'
    };
    return labels[value] || value;
}

function isBenefitSearchResponse(payload) {
    return payload
        && typeof payload === 'object'
        && typeof payload.keyword === 'string'
        && Array.isArray(payload.items)
        && Number.isInteger(payload.totalElements)
        && Number.isInteger(payload.totalPages)
        && Number.isInteger(payload.page)
        && Number.isInteger(payload.size)
        && typeof payload.hasNext === 'boolean'
        && Array.isArray(payload.suggestedHashtags);
}

function isBenefitSearchItem(item) {
    return item && typeof item === 'object' && (typeof item.id === 'number' || typeof item.id === 'string');
}

function toast(message) {
    const element = $('#toast');
    element.textContent = message;
    element.hidden = false;
    clearTimeout(toast.id);
    toast.id = setTimeout(() => { element.hidden = true; }, 3000);
}

function groupTitle(group) {
    return group === 'status' ? '신청 상태' : group === 'category' ? '분야' : '대상';
}

function renderFilters() {
    const groups = Object.entries(FILTER_CONFIGS).map(([group, items]) => {
        const options = items.map((item) => (
            '<label class="filter-option"><input type="checkbox" name="' + group + '" value="' + item + '" disabled><span>' + item + '</span></label>'
        )).join('');
        return '<section class="filter-group"><h3>' + groupTitle(group) + '</h3><div class="filter-options">' + options + '</div></section>';
    }).join('');

    $('#filter-groups').innerHTML = '<p class="filter-panel__notice" role="note">신청 상태·분야·대상 필터는 현재 검색 API에서 지원하지 않습니다.</p>' + groups;
}

function suggestedKeywords(hashtags = []) {
    const uniqueHashtags = [...new Set(hashtags.filter(hasText).map((tag) => tag.trim()))];
    if (!uniqueHashtags.length) return '';
    return '<div class="suggested-keywords" aria-label="추천 해시태그"><span>추천 검색어:</span>'
        + uniqueHashtags.map((tag) => '<button type="button" data-hashtag="' + escapeHtml(tag) + '">#' + escapeHtml(tag) + '</button>').join('')
        + '</div>';
}

function initial() {
    return '<section class="state-panel"><div class="state-panel__inner"><img src="img/benefit_list_logo.png" alt="혜택 검색을 안내하는 복덩이 캐릭터"><h2>아직 검색 결과가 없어요.</h2><p>검색어를 입력해 복지 혜택을 찾아보세요.</p></div></section>';
}

function empty() {
    const suggestions = state.response ? suggestedKeywords(state.response.suggestedHashtags) : '';
    return '<section class="state-panel"><div class="state-panel__inner"><img src="img/benefit_list_logo.png" alt="검색 결과 없음을 안내하는 복덩이 캐릭터"><h2>검색 결과가 나오지 않아요.</h2><p>다른 검색어로 다시 찾아보세요.</p>' + suggestions + '</div></section>';
}

function loading() {
    return '<section class="state-panel state-panel--loading" aria-label="검색 결과 불러오는 중"><div class="state-panel__inner"><span class="loading-indicator" aria-hidden="true"></span><h2>검색 결과를 불러오는 중이에요.</h2><p>입력한 검색어를 확인하고 있어요.</p></div></section>';
}

function failure() {
    const isNetworkError = state.errorKind === 'NETWORK';
    const title = isNetworkError ? '네트워크에 연결하지 못했어요.' : '검색 결과를 불러오지 못했어요.';
    const description = isNetworkError
        ? '인터넷 연결과 서버 주소를 확인한 뒤 다시 시도해주세요.'
        : (state.errorMessage || '검색어는 그대로 유지됩니다. 잠시 후 다시 시도해주세요.');
    return '<section class="state-panel state-panel--error"><div class="state-panel__inner"><h2>' + escapeHtml(title) + '</h2><p>' + escapeHtml(description) + '</p><button class="bk-btn bk-btn--secondary bk-btn--md" type="button" data-retry-search>다시 시도</button></div></section>';
}

function card(item) {
    if (!isBenefitSearchItem(item)) return '';

    const title = hasText(item.title) ? item.title.trim() : '';
    const titleMarkup = title ? '<h3>' + escapeHtml(title) + '</h3>' : '';
    const summary = summaryText(item);
    const organization = hasText(item.orgName) ? '<p class="benefit-card__organization">' + escapeHtml(item.orgName.trim()) + '</p>' : '';
    const description = summary ? '<p class="benefit-card__description">' + escapeHtml(summary) + '</p>' : '';
    const period = applyPeriod(item);
    const periodInfo = period ? '<dl class="benefit-card__info"><div><dt>신청 기간</dt><dd>' + escapeHtml(period) + '</dd></div></dl>' : '';
    const hashtags = parseHashtags(item.hashtags);
    const hashtagMarkup = hashtags.length
        ? '<div class="benefit-card__hashtags">' + hashtags.map((tag) => '<button class="hash-tag" type="button" data-hashtag="' + escapeHtml(tag) + '">#' + escapeHtml(tag) + '</button>').join('') + '</div>'
        : '';
    const eligibility = hasText(item.eligibilityResult)
        ? '<span class="status-badge status-badge--unknown">' + escapeHtml(eligibilityLabel(item.eligibilityResult.trim())) + '</span>'
        : '';

    return '<article class="benefit-card"><div class="benefit-card__top">' + eligibility + '</div><div class="benefit-card__body">' + titleMarkup + organization + description + '</div>' + periodInfo + '<div class="benefit-card__actions"><a class="bk-btn bk-btn--primary bk-btn--md benefit-card__detail" href="benefit-detail.html?id=' + encodeURIComponent(item.id) + '">상세 보기 <span aria-hidden="true">›</span></a></div>' + hashtagMarkup + '</article>';
}

function paginationMarkup(response) {
    const totalPages = response.totalPages;
    const currentPage = response.page + 1;
    if (totalPages <= 1) return '';

    const pages = totalPages <= 7
        ? Array.from({ length: totalPages }, (_, index) => index + 1)
        : [1, ...(currentPage > 4 ? ['…'] : []), ...[currentPage - 1, currentPage, currentPage + 1].filter((page) => page > 1 && page < totalPages), ...(currentPage < totalPages - 3 ? ['…'] : []), totalPages];
    const button = (page, label, disabled, current) => '<button class="pagination__button' + (current ? ' is-current' : '') + '" type="button" data-pagination-page="' + page + '"' + (current ? ' aria-current="page"' : '') + (disabled ? ' disabled' : '') + '>' + label + '</button>';
    const numbers = pages.map((page) => page === '…'
        ? '<span class="pagination__ellipsis" aria-hidden="true">…</span>'
        : button(page, page, false, page === currentPage)).join('');

    return '<nav class="pagination" aria-label="복지 혜택 검색 결과 페이지 이동">'
        + button(currentPage - 1, '이전', currentPage === 1, false)
        + '<span class="pagination__pages">' + numbers + '</span>'
        + button(currentPage + 1, '다음', !response.hasNext, false)
        + '</nav>';
}

function render() {
    const summary = $('#result-summary');
    const area = $('#result-area');
    area.setAttribute('aria-busy', String(state.screen === 'LOADING'));

    if (state.screen === 'INITIAL') {
        summary.textContent = '검색어를 입력해 혜택을 찾아보세요.';
        area.innerHTML = initial();
        return;
    }
    if (state.screen === 'LOADING') {
        summary.textContent = '검색 결과를 불러오는 중입니다.';
        area.innerHTML = loading();
        return;
    }
    if (state.screen === 'ERROR') {
        summary.textContent = state.errorKind === 'NETWORK' ? '네트워크 연결을 확인해주세요.' : '검색 결과를 불러오지 못했습니다.';
        area.innerHTML = failure();
        return;
    }

    const response = state.response;
    const activeSearch = state.hashtag ? '#' + state.hashtag : state.query ? '“' + state.query + '”' : '';
    summary.textContent = (activeSearch ? activeSearch + ' · ' : '') + '검색 결과 ' + response.totalElements + '건 · ' + response.totalPages + '페이지';
    const validItems = response.items.filter(isBenefitSearchItem);
    area.innerHTML = response.totalElements > 0 && validItems.length
        ? '<div class="benefit-list">' + validItems.map(card).join('') + '</div>' + paginationMarkup(response)
        : empty();
}

function buildRequestPath(query, hashtag, page) {
    const parameters = new URLSearchParams({
        sort: 'relevance',
        page: String(page - 1),
        size: String(PAGE_SIZE)
    });
    if (query) parameters.set('q', query);
    if (hashtag) parameters.set('hashtag', hashtag);
    return '/api/benefits?' + parameters.toString();
}

async function readErrorMessage(response) {
    try {
        const payload = await response.json();
        return hasText(payload?.message) ? payload.message.trim() : '';
    } catch {
        return '';
    }
}

async function requestBenefits(page) {
    const requestId = ++searchRequestId;
    if (activeController) activeController.abort();
    activeController = new AbortController();
    state.currentPage = page;
    state.screen = 'LOADING';
    state.errorKind = null;
    state.errorMessage = '';
    render();

    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.getApiUrl !== 'function') {
            throw new BenefitListRequestError('API', '검색 서비스를 설정하지 못했습니다.');
        }

        const response = await fetch(auth.getApiUrl(buildRequestPath(state.query, state.hashtag, page)), {
            credentials: 'include',
            signal: activeController.signal
        });
        if (!response.ok) {
            throw new BenefitListRequestError('API', await readErrorMessage(response));
        }

        const payload = await response.json();
        if (!isBenefitSearchResponse(payload)) {
            throw new BenefitListRequestError('API', '검색 결과 형식이 올바르지 않습니다.');
        }
        if (requestId !== searchRequestId) return;

        state.response = payload;
        if (!state.hashtag) state.query = payload.keyword;
        state.currentPage = payload.page + 1;
        state.screen = 'RESULT';
        render();
    } catch (error) {
        if ((error && error.name === 'AbortError') || requestId !== searchRequestId) return;
        state.screen = 'ERROR';
        state.errorKind = error instanceof BenefitListRequestError ? error.kind : 'NETWORK';
        state.errorMessage = error instanceof BenefitListRequestError ? error.message : '';
        render();
        console.error('혜택 검색 API를 호출하지 못했습니다.', error);
    } finally {
        if (requestId === searchRequestId) activeController = null;
    }
}

function runSearch() {
    const query = normalizeQuery($('#benefit-search-input').value);
    if (!query) {
        $('#search-message').textContent = '검색어를 입력해주세요.';
        return;
    }
    if (!isValidSearchQuery(query)) {
        $('#search-message').textContent = '두 글자 이상의 검색어를 입력하고, 문자 또는 숫자를 포함해주세요.';
        return;
    }

    $('#benefit-search-input').value = query;
    $('#search-message').textContent = '';
    state.query = query;
    state.hashtag = '';
    requestBenefits(1);
}

function searchByHashtag(hashtag) {
    const normalizedHashtag = normalizeQuery(hashtag).replace(/^#/, '');
    if (!normalizedHashtag) return;

    $('#benefit-search-input').value = normalizedHashtag;
    $('#search-message').textContent = '';
    state.query = '';
    state.hashtag = normalizedHashtag;
    requestBenefits(1);
}

function reset() {
    searchRequestId += 1;
    if (activeController) activeController.abort();
    activeController = null;
    state.query = '';
    state.hashtag = '';
    state.response = null;
    state.currentPage = 1;
    state.screen = 'INITIAL';
    $('#benefit-search-input').value = '';
    $('#search-message').textContent = '';
    render();
}

function applyKeywordFromUrl() {
    const parameters = new URLSearchParams(window.location.search);
    const keyword = normalizeQuery(parameters.get('keyword') ?? parameters.get('q'));
    const hashtag = normalizeQuery(parameters.get('hashtag')).replace(/^#/, '');
    if (keyword) {
        $('#benefit-search-input').value = keyword;
        runSearch();
    } else if (hashtag) {
        searchByHashtag(hashtag);
    }
}

function disableUnsupportedControls() {
    $('#filter-reset').disabled = true;
    $('#filter-reset').title = '현재 검색 API에서 필터를 지원하지 않습니다.';
    const sort = $('#sort-order');
    [...sort.options].forEach((option) => {
        option.disabled = option.value !== 'relevance';
    });
    sort.value = 'relevance';
    sort.title = '현재 검색 API는 관련도순만 지원합니다.';
}

function setup() {
    renderFilters();
    disableUnsupportedControls();
    render();

    $('#benefit-search-form').addEventListener('submit', (event) => {
        event.preventDefault();
        runSearch();
    });
    document.addEventListener('bokdeongi:pagination', (event) => {
        if (state.screen !== 'RESULT' || !state.response) return;
        const page = event.detail.page;
        if (!Number.isInteger(page) || page < 1 || page > state.response.totalPages || page === state.response.page + 1) return;
        requestBenefits(page);
    });
    document.addEventListener('click', (event) => {
        const keywordTarget = event.target.closest('[data-keyword]');
        if (keywordTarget) {
            $('#benefit-search-input').value = keywordTarget.dataset.keyword;
            runSearch();
            return;
        }
        const hashtagTarget = event.target.closest('[data-hashtag]');
        if (hashtagTarget) {
            searchByHashtag(hashtagTarget.dataset.hashtag);
            return;
        }
        if (event.target.closest('[data-retry-search]')) {
            requestBenefits(state.currentPage);
            return;
        }
        if (event.target.closest('[data-unavailable-page]')) {
            event.preventDefault();
            toast('이 페이지는 현재 준비 중입니다.');
        }
    });
    applyKeywordFromUrl();
}

document.addEventListener('DOMContentLoaded', setup);
