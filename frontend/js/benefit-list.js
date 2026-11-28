const STATUS_FILTERS = [
    { value: 'OPEN', label: '신청 가능' },
    { value: 'UPCOMING', label: '신청 예정' },
    { value: 'CLOSED', label: '신청 마감' }
];

const PAGE_SIZE = 6;
const SORTS = new Set(['relevance', 'deadline', 'amount', 'easy']);
const state = {
    query: '',
    hashtag: '',
    sort: 'relevance',
    response: null,
    screen: 'INITIAL',
    currentPage: 1,
    errorKind: null,
    errorMessage: '',
    statuses: [],
    fields: [],
    targets: []
};

const $ = (selector) => document.querySelector(selector);
let searchRequestId = 0;
let activeController = null;
const favoriteState = {
    authState: 'UNKNOWN',
    byBenefitId: new Map()
};
let favoriteRequestId = 0;
let favoriteController = null;
const hashtagState = {
    screen: 'IDLE',
    response: null,
    errorKind: null,
    errorMessage: '',
    requestPromise: null
};
let hashtagRequestId = 0;
let hashtagController = null;
const filterGroupExpanded = { status: true, field: true, target: false };

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

function normalizeSort(value) {
    const sort = typeof value === 'string' ? value.trim().toLowerCase() : '';
    return SORTS.has(sort) ? sort : 'relevance';
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

function isHashtagResponse(payload) {
    const optionalText = (value) => value === null || value === undefined || typeof value === 'string';
    return payload
        && typeof payload === 'object'
        && Array.isArray(payload.tags)
        && payload.tags.every((tag) => tag
            && typeof tag === 'object'
            && typeof tag.name === 'string'
            && optionalText(tag.category)
            && optionalText(tag.description)
            && Number.isSafeInteger(tag.count)
            && tag.count >= 0)
        && Number.isSafeInteger(payload.untaggedCount)
        && payload.untaggedCount >= 0;
}

function displayableHashtags(tags) {
    const seen = new Set();
    return tags.filter((tag) => {
        const name = normalizeQuery(tag.name);
        if (!name || seen.has(name)) return false;
        seen.add(name);
        return true;
    }).map((tag) => ({ ...tag, name: tag.name.trim() }));
}

function renderHashtagSuggestions() {
    const container = $('#hashtag-suggestions');
    if (hashtagState.screen === 'IDLE' || hashtagState.screen === 'LOADING') {
        if (container) container.innerHTML = '<span>추천 해시태그:</span><span class="small-copy">불러오는 중…</span>';
    } else if (hashtagState.screen === 'UNAUTHENTICATED') {
        if (container) container.innerHTML = '<span>추천 해시태그:</span><span class="small-copy">로그인하면 실제 추천 해시태그를 볼 수 있어요.</span><a href="login.html">로그인하기</a>';
    } else if (hashtagState.screen === 'ERROR') {
        const message = hashtagState.errorKind === 'NETWORK'
            ? '네트워크 연결을 확인한 뒤 다시 시도해주세요.'
            : (hashtagState.errorMessage || '추천 해시태그를 불러오지 못했어요.');
        if (container) container.innerHTML = '<span>추천 해시태그:</span><span class="small-copy">' + escapeHtml(message) + '</span><button type="button" data-retry-hashtags>다시 시도</button>';
    } else {
        const tags = displayableHashtags(hashtagState.response?.tags || []);
        if (container) container.innerHTML = tags.length
            ? '<span>추천 해시태그:</span>' + tags.map((tag) => {
                const description = hasText(tag.description) ? ' title="' + escapeHtml(tag.description.trim()) + '"' : '';
                return '<button type="button" data-hashtag="' + escapeHtml(tag.name) + '"' + description + '>#' + escapeHtml(tag.name) + '</button>';
            }).join('')
            : '<span>추천 해시태그:</span><span class="small-copy">표시할 해시태그가 없어요.</span>';
    }
    renderFilters();
}

async function loadHashtags(force = false) {
    if (!force) {
        if (hashtagState.requestPromise) return hashtagState.requestPromise;
        if (['RESULT', 'UNAUTHENTICATED'].includes(hashtagState.screen)) return;
    }

    const requestId = ++hashtagRequestId;
    if (hashtagController) hashtagController.abort();
    const controller = new AbortController();
    hashtagController = controller;
    hashtagState.screen = 'LOADING';
    hashtagState.response = null;
    hashtagState.errorKind = null;
    hashtagState.errorMessage = '';
    renderHashtagSuggestions();

    const request = (async () => {
        try {
            const auth = window.BokdeongiAuth;
            if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.refreshAuthentication !== 'function') {
                throw new BenefitListRequestError('API', '로그인 상태를 확인하지 못했습니다.');
            }
            const authenticationState = await auth.refreshAuthentication();
            if (requestId !== hashtagRequestId) return;
            if (authenticationState !== 'authenticated') {
                hashtagState.screen = authenticationState === 'unauthenticated' ? 'UNAUTHENTICATED' : 'ERROR';
                hashtagState.errorKind = authenticationState === 'unauthenticated' ? null : 'API';
                hashtagState.errorMessage = authenticationState === 'unauthenticated' ? '' : '로그인 상태를 확인하지 못했습니다.';
                renderHashtagSuggestions();
                return;
            }

            const response = await auth.authenticatedFetch('/api/hashtags', { signal: controller.signal });
            if (response.status === 401) {
                if (requestId !== hashtagRequestId) return;
                hashtagState.screen = 'UNAUTHENTICATED';
                renderHashtagSuggestions();
                return;
            }
            if (!response.ok) {
                throw new BenefitListRequestError('API', await readErrorMessage(response));
            }
            const payload = await response.json();
            if (!isHashtagResponse(payload)) {
                throw new BenefitListRequestError('INVALID', '해시태그 응답 형식이 올바르지 않습니다.');
            }
            if (requestId !== hashtagRequestId) return;
            hashtagState.response = payload;
            hashtagState.screen = 'RESULT';
            renderHashtagSuggestions();
        } catch (error) {
            if ((error && error.name === 'AbortError') || requestId !== hashtagRequestId) return;
            if (error && error.type === 'unauthenticated') {
                hashtagState.screen = 'UNAUTHENTICATED';
                hashtagState.errorKind = null;
                hashtagState.errorMessage = '';
            } else {
                hashtagState.screen = 'ERROR';
                hashtagState.errorKind = error instanceof BenefitListRequestError ? error.kind : 'NETWORK';
                hashtagState.errorMessage = error instanceof BenefitListRequestError ? error.message : '';
            }
            renderHashtagSuggestions();
            console.error('추천 해시태그를 조회하지 못했습니다.', error);
        } finally {
            if (requestId === hashtagRequestId) {
                hashtagController = null;
                hashtagState.requestPromise = null;
            }
        }
    })();
    hashtagState.requestPromise = request;
    return request;
}

function isFavoriteResponse(payload, benefitId) {
    if (!payload || typeof payload !== 'object' || typeof payload.bookmarked !== 'boolean') return false;
    const responseBenefitId = typeof payload.benefitId === 'number' || typeof payload.benefitId === 'string'
        ? Number(payload.benefitId)
        : NaN;
    return Number.isSafeInteger(responseBenefitId) && responseBenefitId === benefitId;
}

function bookmarkIcon() {
    return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h12v17l-6-3.8-6 3.8z"></path></svg>';
}

function favoriteRecord(benefitId) {
    return favoriteState.byBenefitId.get(String(benefitId)) || { screen: 'LOADING', bookmarked: null };
}

function favoriteButtonMarkup(benefitId) {
    const record = favoriteRecord(benefitId);
    const saved = record.screen === 'RESULT' && record.bookmarked === true;
    const pending = record.screen === 'PENDING';
    const unauthenticated = favoriteState.authState === 'UNAUTHENTICATED';
    const authError = favoriteState.authState === 'ERROR';
    const loading = !unauthenticated && !authError && (favoriteState.authState === 'UNKNOWN' || favoriteState.authState === 'CHECKING' || record.screen === 'LOADING');
    const retry = authError || record.screen === 'ERROR';
    const label = loading
        ? '관심 혜택 저장 상태를 확인하는 중'
        : pending ? '관심 혜택 저장 상태를 변경하는 중'
            : retry ? '관심 혜택 저장 상태를 다시 확인'
                : unauthenticated ? '관심 혜택을 저장하려면 로그인 필요'
                    : saved ? '관심 혜택 저장 해제' : '관심 혜택 저장';
    const title = retry
        ? '관심 상태를 확인하지 못했습니다. 다시 시도해주세요.'
        : unauthenticated ? '로그인 후 관심 혜택으로 저장할 수 있습니다.' : label;
    const disabled = (loading || pending) ? ' disabled aria-disabled="true"' : '';
    const busy = (loading || pending) ? ' aria-busy="true"' : '';
    return '<button class="bookmark-button' + (saved ? ' is-saved' : '') + '" type="button" data-favorite-benefit-id="' + escapeHtml(benefitId) + '" aria-pressed="' + String(saved) + '" aria-label="' + label + '" title="' + title + '"' + disabled + busy + '>' + bookmarkIcon() + '</button>';
}

function resetFavoriteStates() {
    favoriteRequestId += 1;
    if (favoriteController) favoriteController.abort();
    favoriteController = null;
    favoriteState.authState = 'UNKNOWN';
    favoriteState.byBenefitId.clear();
}

function toast(message) {
    const element = $('#toast');
    element.textContent = message;
    element.hidden = false;
    clearTimeout(toast.id);
    toast.id = setTimeout(() => { element.hidden = true; }, 3000);
}

function filterOption(group, value, label, count, checked) {
    const countMarkup = Number.isSafeInteger(count) ? ' <small>(' + count + ')</small>' : '';
    return '<label class="filter-option"><input type="checkbox" data-filter-group="' + group + '" value="' + escapeHtml(value) + '"' + (checked ? ' checked' : '') + '><span>' + escapeHtml(label) + countMarkup + '</span></label>';
}

function filterGroupMarkup(group, title, content, selectedCount) {
    const expanded = filterGroupExpanded[group];
    const selected = selectedCount ? '<span class="filter-group__selected">선택 ' + selectedCount + '</span>' : '';
    return '<section class="filter-group"><h3><button class="filter-group__toggle" type="button" data-filter-toggle="' + group
        + '" aria-expanded="' + expanded + '" aria-controls="filter-group-content-' + group + '"><span>' + title + '</span>' + selected
        + '</button></h3><div id="filter-group-content-' + group + '" class="filter-group__content"' + (expanded ? '' : ' hidden') + '>' + content + '</div></section>';
}

function tagFilterGroup(group, title, category, selected) {
    if (hashtagState.screen === 'LOADING' || hashtagState.screen === 'IDLE') {
        return filterGroupMarkup(group, title, '<p class="filter-panel__notice">필터를 불러오는 중이에요.</p>', selected.length);
    }
    if (hashtagState.screen === 'UNAUTHENTICATED') {
        return filterGroupMarkup(group, title, '<p class="filter-panel__notice">로그인하면 실제 ' + title + ' 필터를 사용할 수 있어요. <a href="login.html">로그인하기</a></p>', selected.length);
    }
    if (hashtagState.screen === 'ERROR') {
        return filterGroupMarkup(group, title, '<p class="filter-panel__notice">필터를 불러오지 못했어요. <button type="button" data-retry-filter-tags>다시 시도</button></p>', selected.length);
    }
    const tags = displayableHashtags((hashtagState.response?.tags || []).filter((tag) => tag.category === category));
    if (!tags.length) return filterGroupMarkup(group, title, '<p class="filter-panel__notice">표시할 ' + title + ' 필터가 없어요.</p>', selected.length);
    return filterGroupMarkup(group, title, '<div class="filter-options">'
        + tags.map((tag) => filterOption(group, tag.name, tag.name, tag.count, selected.includes(tag.name))).join('') + '</div>', selected.length);
}

function renderFilters() {
    const allSelected = STATUS_FILTERS.every((filter) => state.statuses.includes(filter.value));
    const statusOptions = filterOption('status', 'ALL', '전체 (마감 포함)', null, allSelected)
        + STATUS_FILTERS.map((filter) => filterOption('status', filter.value, filter.label, null, state.statuses.includes(filter.value))).join('');
    const container = $('#filter-groups');
    if (!container) return;
    container.innerHTML = filterGroupMarkup('status', '신청 상태', '<div class="filter-options">' + statusOptions + '</div>', state.statuses.length)
        + tagFilterGroup('field', '분야', '분야', state.fields)
        + tagFilterGroup('target', '대상', '대상', state.targets);
}

function toggleFilterGroup(button) {
    const group = button.dataset.filterToggle;
    const content = $('#filter-group-content-' + group);
    if (!content || !Object.hasOwn(filterGroupExpanded, group)) return;
    filterGroupExpanded[group] = !filterGroupExpanded[group];
    content.hidden = !filterGroupExpanded[group];
    button.setAttribute('aria-expanded', String(filterGroupExpanded[group]));
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
    const organization = hasText(item.orgName) ? '<p class="benefit-card__organization">' + escapeHtml(item.orgName.trim()) + '</p>' : '';
    const period = applyPeriod(item);
    const periodInfo = period ? '<dl class="benefit-card__info"><div><dt>신청 기간</dt><dd>' + escapeHtml(period) + '</dd></div></dl>' : '';
    const hashtags = parseHashtags(item.hashtags);
    const hashtagMarkup = hashtags.length
        ? '<div class="benefit-card__hashtags">' + hashtags.map((tag) => '<button class="hash-tag" type="button" data-hashtag="' + escapeHtml(tag) + '">#' + escapeHtml(tag) + '</button>').join('') + '</div>'
        : '';
    const eligibility = hasText(item.eligibilityResult)
        ? '<span class="status-badge status-badge--unknown">' + escapeHtml(eligibilityLabel(item.eligibilityResult.trim())) + '</span>'
        : '';

    return '<article class="benefit-card"><div class="benefit-card__top">' + eligibility + favoriteButtonMarkup(item.id) + '</div><div class="benefit-card__body">' + titleMarkup + organization + '</div>' + periodInfo + hashtagMarkup + '<div class="benefit-card__actions"><a class="bk-btn bk-btn--primary bk-btn--md benefit-card__detail" href="benefit-detail.html?id=' + encodeURIComponent(item.id) + '">상세 보기 <span aria-hidden="true">›</span></a></div></article>';
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

function buildRequestPath(query, hashtag, page, filters = state) {
    const parameters = new URLSearchParams({
        sort: normalizeSort(filters.sort),
        page: String(page - 1),
        size: String(PAGE_SIZE)
    });
    if (query) parameters.set('q', query);
    if (hashtag) parameters.set('hashtag', hashtag);
    (filters.statuses || []).forEach((status) => parameters.append('status', status));
    (filters.fields || []).forEach((field) => parameters.append('field', field));
    (filters.targets || []).forEach((target) => parameters.append('target', target));
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

async function loadFavoriteStates(items) {
    const benefitIds = [...new Set(items
        .filter(isBenefitSearchItem)
        .map((item) => Number(item.id))
        .filter(Number.isSafeInteger))];
    if (!benefitIds.length) return;

    const requestId = ++favoriteRequestId;
    if (favoriteController) favoriteController.abort();
    const controller = new AbortController();
    favoriteController = controller;
    favoriteState.authState = 'CHECKING';
    render();

    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.refreshAuthentication !== 'function') {
            throw new BenefitListRequestError('API', '로그인 상태를 확인하지 못했습니다.');
        }
        const authenticationState = await auth.refreshAuthentication();
        if (requestId !== favoriteRequestId) return;
        if (authenticationState !== 'authenticated') {
            favoriteState.authState = 'UNAUTHENTICATED';
            render();
            return;
        }

        favoriteState.authState = 'AUTHENTICATED';
        benefitIds.forEach((benefitId) => favoriteState.byBenefitId.set(String(benefitId), { screen: 'LOADING', bookmarked: null }));
        render();
        await Promise.all(benefitIds.map((benefitId) => loadFavoriteState(benefitId, requestId, controller.signal)));
    } catch (error) {
        if ((error && error.name === 'AbortError') || requestId !== favoriteRequestId) return;
        favoriteState.authState = error && error.type === 'unauthenticated' ? 'UNAUTHENTICATED' : 'ERROR';
        render();
        console.error('관심 혜택 상태를 조회하지 못했습니다.', error);
    } finally {
        if (requestId === favoriteRequestId) favoriteController = null;
    }
}

async function loadFavoriteState(benefitId, batchRequestId, signal) {
    try {
        const response = await window.BokdeongiAuth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/favorite', { signal });
        if (batchRequestId !== favoriteRequestId) return;
        if (response.status === 401) {
            favoriteState.authState = 'UNAUTHENTICATED';
            favoriteState.byBenefitId.clear();
            render();
            return;
        }
        if (!response.ok) {
            favoriteState.byBenefitId.set(String(benefitId), { screen: 'ERROR', bookmarked: null });
            render();
            return;
        }
        const payload = await response.json();
        if (!isFavoriteResponse(payload, benefitId)) {
            favoriteState.byBenefitId.set(String(benefitId), { screen: 'ERROR', bookmarked: null });
            render();
            return;
        }
        favoriteState.byBenefitId.set(String(benefitId), { screen: 'RESULT', bookmarked: payload.bookmarked });
        render();
    } catch (error) {
        if ((error && error.name === 'AbortError') || batchRequestId !== favoriteRequestId) return;
        if (error && error.type === 'unauthenticated') {
            favoriteState.authState = 'UNAUTHENTICATED';
            favoriteState.byBenefitId.clear();
        } else {
            favoriteState.byBenefitId.set(String(benefitId), { screen: 'ERROR', bookmarked: null });
        }
        render();
    }
}

async function toggleFavorite(benefitId) {
    const record = favoriteRecord(benefitId);
    if (favoriteState.authState === 'UNAUTHENTICATED') {
        toast('로그인 후 관심 혜택으로 저장할 수 있어요.');
        return;
    }
    if (favoriteState.authState === 'ERROR') {
        const currentItems = state.response?.items || [];
        resetFavoriteStates();
        loadFavoriteStates(currentItems);
        return;
    }
    if (favoriteState.authState !== 'AUTHENTICATED' || record.screen === 'LOADING' || record.screen === 'PENDING') return;
    if (record.screen === 'ERROR') {
        const currentItems = state.response?.items || [];
        resetFavoriteStates();
        loadFavoriteStates(currentItems);
        return;
    }
    if (record.screen !== 'RESULT') return;

    const requestId = favoriteRequestId;
    const previousBookmarked = record.bookmarked;
    favoriteState.byBenefitId.set(String(benefitId), { screen: 'PENDING', bookmarked: previousBookmarked });
    render();
    try {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.authenticatedFetch !== 'function' || typeof auth.getCsrfToken !== 'function') {
            throw new BenefitListRequestError('API', '관심 혜택 저장 서비스를 설정하지 못했습니다.');
        }
        const csrf = await auth.getCsrfToken();
        const response = await auth.authenticatedFetch('/api/benefits/' + encodeURIComponent(benefitId) + '/favorite', {
            method: previousBookmarked ? 'DELETE' : 'POST',
            headers: { [csrf.headerName]: csrf.token }
        });
        if (requestId !== favoriteRequestId) return;
        if (response.status === 401) {
            favoriteState.authState = 'UNAUTHENTICATED';
            favoriteState.byBenefitId.clear();
            render();
            toast('로그인이 만료되었습니다. 다시 로그인해주세요.');
            return;
        }
        if (!response.ok) {
            throw new BenefitListRequestError(response.status === 404 ? 'NOT_FOUND' : 'API', await readErrorMessage(response));
        }
        const payload = await response.json();
        if (!isFavoriteResponse(payload, benefitId)) {
            throw new BenefitListRequestError('INVALID', '관심 혜택 응답 형식이 올바르지 않습니다.');
        }
        if (requestId !== favoriteRequestId) return;
        favoriteState.byBenefitId.set(String(benefitId), { screen: 'RESULT', bookmarked: payload.bookmarked });
        render();
        toast(payload.bookmarked ? '관심 혜택으로 저장했어요.' : '관심 혜택 저장을 해제했어요.');
    } catch (error) {
        if (requestId !== favoriteRequestId) return;
        const unauthenticated = error && error.type === 'unauthenticated';
        if (unauthenticated) {
            favoriteState.authState = 'UNAUTHENTICATED';
            favoriteState.byBenefitId.clear();
        } else {
            favoriteState.byBenefitId.set(String(benefitId), { screen: 'RESULT', bookmarked: previousBookmarked });
        }
        render();
        toast(error instanceof BenefitListRequestError && error.kind === 'NOT_FOUND'
            ? '존재하지 않는 혜택입니다.'
            : unauthenticated
                ? '로그인이 만료되었습니다. 다시 로그인해주세요.'
                : '관심 혜택 저장 상태를 변경하지 못했습니다. 다시 시도해주세요.');
        console.error('관심 혜택 저장 상태를 변경하지 못했습니다.', error);
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
    resetFavoriteStates();
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
        loadFavoriteStates(payload.items);
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

function syncSearchUrl({ push = false } = {}) {
    if (!window.history || typeof window.history.replaceState !== 'function') return;
    const parameters = new URLSearchParams();
    parameters.set('sort', normalizeSort(state.sort));
    if (state.query) parameters.set('q', state.query);
    if (state.hashtag) parameters.set('hashtag', state.hashtag);
    state.statuses.forEach((status) => parameters.append('status', status));
    state.fields.forEach((field) => parameters.append('field', field));
    state.targets.forEach((target) => parameters.append('target', target));
    const path = hasText(window.location.pathname) ? window.location.pathname : 'benefit-list.html';
    const query = parameters.toString();
    const updateHistory = push && typeof window.history.pushState === 'function'
        ? window.history.pushState
        : window.history.replaceState;
    updateHistory.call(window.history, null, '', path + (query ? '?' + query : ''));
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
    syncSearchUrl({ push: true });
    requestBenefits(1);
}

function searchByHashtag(hashtag) {
    const normalizedHashtag = normalizeQuery(hashtag).replace(/^#/, '');
    if (!normalizedHashtag) return;

    $('#benefit-search-input').value = normalizedHashtag;
    $('#search-message').textContent = '';
    state.query = '';
    state.hashtag = normalizedHashtag;
    syncSearchUrl({ push: true });
    requestBenefits(1);
}

function reset() {
    searchRequestId += 1;
    if (activeController) activeController.abort();
    activeController = null;
    resetFavoriteStates();
    state.query = '';
    state.hashtag = '';
    state.sort = 'relevance';
    state.statuses = [];
    state.fields = [];
    state.targets = [];
    state.response = null;
    state.currentPage = 1;
    state.screen = 'INITIAL';
    $('#benefit-search-input').value = '';
    $('#search-message').textContent = '';
    syncSortControl();
    render();
}

function updateFilterSelection(group, value, checked) {
    if (group === 'status') {
        if (value === 'ALL') state.statuses = checked ? STATUS_FILTERS.map((filter) => filter.value) : [];
        else {
            state.statuses = STATUS_FILTERS.filter((filter) => {
                const current = filter.value === value ? checked : state.statuses.includes(filter.value);
                return current;
            }).map((filter) => filter.value);
        }
        return;
    }
    const key = group === 'field' ? 'fields' : 'targets';
    const selected = new Set(state[key]);
    if (checked) selected.add(value); else selected.delete(value);
    state[key] = [...selected];
}

function applyKeywordFromUrl(request = true) {
    const parameters = new URLSearchParams(window.location.search);
    const keyword = normalizeQuery(parameters.get('keyword') ?? parameters.get('q'));
    const hashtag = normalizeQuery(parameters.get('hashtag')).replace(/^#/, '');
    state.query = keyword;
    state.hashtag = keyword ? '' : hashtag;
    state.sort = normalizeSort(parameters.get('sort'));
    state.statuses = [...new Set(parameters.getAll('status').map((value) => value.trim().toUpperCase()).filter((value) => STATUS_FILTERS.some((filter) => filter.value === value)))];
    state.fields = [...new Set(parameters.getAll('field').map(normalizeQuery).filter(Boolean))].slice(0, 20);
    state.targets = [...new Set(parameters.getAll('target').map(normalizeQuery).filter(Boolean))].slice(0, 20);
    if (state.statuses.length) filterGroupExpanded.status = true;
    if (state.fields.length) filterGroupExpanded.field = true;
    if (state.targets.length) filterGroupExpanded.target = true;
    $('#benefit-search-input').value = keyword || hashtag;
    syncSortControl();
    renderFilters();
    if (request && (keyword || hashtag || state.statuses.length || state.fields.length || state.targets.length)) requestBenefits(1);
}

function syncSortControl() {
    const sort = $('#sort-order');
    if (sort) sort.value = normalizeSort(state.sort);
}

function changeSort(value) {
    const nextSort = normalizeSort(value);
    if (nextSort === state.sort) {
        syncSortControl();
        return;
    }
    state.sort = nextSort;
    syncSortControl();
    syncSearchUrl({ push: true });
    requestBenefits(1);
}

function setup() {
    renderFilters();
    syncSortControl();
    render();
    loadHashtags();

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
        const filterToggle = event.target.closest('[data-filter-toggle]');
        if (filterToggle) {
            toggleFilterGroup(filterToggle);
            return;
        }
        if (event.target.closest('#filter-reset')) {
            state.statuses = [];
            state.fields = [];
            state.targets = [];
            syncSearchUrl({ push: true });
            renderFilters();
            requestBenefits(1);
            return;
        }
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
        if (event.target.closest('[data-retry-hashtags]')) {
            loadHashtags(true);
            return;
        }
        if (event.target.closest('[data-retry-filter-tags]')) {
            loadHashtags(true);
            return;
        }
        const favoriteButton = event.target.closest('[data-favorite-benefit-id]');
        if (favoriteButton) {
            const benefitId = Number(favoriteButton.dataset.favoriteBenefitId);
            if (Number.isSafeInteger(benefitId) && benefitId > 0) toggleFavorite(benefitId);
            return;
        }
        if (event.target.closest('[data-unavailable-page]')) {
            event.preventDefault();
            toast('이 페이지는 현재 준비 중입니다.');
        }
    });
    document.addEventListener('change', (event) => {
        if (event.target.matches('#sort-order')) {
            changeSort(event.target.value);
            return;
        }
        const input = event.target.closest('[data-filter-group]');
        if (!input) return;
        updateFilterSelection(input.dataset.filterGroup, input.value, input.checked);
        syncSearchUrl({ push: true });
        renderFilters();
        requestBenefits(1);
    });
    window.addEventListener?.('popstate', () => applyKeywordFromUrl(true));
    applyKeywordFromUrl();
}

document.addEventListener('DOMContentLoaded', setup);
