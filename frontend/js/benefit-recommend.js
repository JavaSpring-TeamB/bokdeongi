(() => {
    const PAGE_SIZE = 6;
    const TABS = Object.freeze({
        eligible: {
            api: 'ELIGIBLE',
            label: '해당',
            empty: '현재 조건에 맞는 해당 혜택이 없어요.',
            banner: ['신청 가능성이 높은', '복지 혜택', '을 추천드려요', '입력하신 정보를 바탕으로 신청 가능성이 높은 혜택을 보여드려요.']
        },
        conditional: {
            api: 'CONDITIONAL',
            label: '조건부',
            empty: '현재 확인이 필요한 조건부 혜택이 없어요.',
            banner: ['', '조금만 더 확인하면', ' 신청할 수 있는 혜택이 있어요.', '일부 정보가 부족하거나 추가 확인이 필요한 혜택을 확인해 보세요.']
        },
        'near-miss': {
            api: 'NEAR_MISS',
            label: '아깝게 탈락',
            empty: '현재 아깝게 탈락한 혜택이 없어요.',
            banner: ['', '조건이 맞지 않아', ' 신청할 수 없어요', '시간이 지나면 조건을 충족할 수 있는 혜택을 확인해 보세요.']
        }
    });
    const TAB_ORDER = Object.keys(TABS);
    const TAB_KEY_BY_API = Object.fromEntries(Object.entries(TABS).map(([key, config]) => [config.api, key]));
    const SORTS = new Set(['RECOMMENDED', 'DEADLINE', 'AMOUNT', 'EASY']);
    const ANSWER_OPTIONS = Object.freeze([
        { value: 'Y', label: '예' },
        { value: 'N', label: '아니요' },
        { value: 'UNSURE', label: '잘 모르겠어요' }
    ]);
    const ANSWER_VALUES = new Set(ANSWER_OPTIONS.map((option) => option.value));
    const QUESTION_KEY_PATTERN = /^[a-z][a-z0-9_]{0,49}$/;
    const $ = (selector) => document.querySelector(selector);
    const queryState = {
        tab: 'ELIGIBLE',
        region: '',
        field: '',
        sort: 'RECOMMENDED',
        page: 0,
        size: PAGE_SIZE
    };
    let latestRequestId = 0;
    let loadingRequestKey = null;
    let pageInfo = { page: 0, totalPages: 0, totalElements: 0 };
    let pageRecoveryScope = null;
    const questionSelections = new Map();
    const submittingBenefitIds = new Set();

    function text(value, fallback = '') {
        return typeof value === 'string' && value.trim() ? value.trim() : fallback;
    }

    function number(value, fallback = 0) {
        return Number.isFinite(value) && value >= 0 ? value : fallback;
    }

    function isRecord(value) {
        return value !== null && typeof value === 'object' && !Array.isArray(value);
    }

    function selectedTabKey() {
        return TAB_KEY_BY_API[queryState.tab];
    }

    function requestKey(state) {
        return [state.tab, state.region, state.field, state.sort, state.page, state.size].join('|');
    }

    function setBanner(tabKey) {
        const banner = TABS[tabKey].banner;
        document.body.dataset.status = tabKey;
        $('[data-banner-prefix]').textContent = banner[0];
        $('[data-banner-emphasis]').textContent = banner[1];
        $('[data-banner-suffix]').textContent = banner[2];
        $('[data-banner-description]').textContent = banner[3];
        $('#recommend-results').setAttribute('aria-labelledby', tabKey + '-tab');
    }

    function setActiveTab(tabKey) {
        queryState.tab = TABS[tabKey].api;
        queryState.page = 0;
        pageRecoveryScope = null;
        setBanner(tabKey);
        document.querySelectorAll('[data-status-tab]').forEach((tab) => {
            const active = tab.dataset.statusTab === tabKey;
            tab.classList.toggle('is-active', active);
            tab.setAttribute('aria-selected', String(active));
        });
    }

    function setCounts(counts) {
        Object.entries(TABS).forEach(([tabKey, config]) => {
            $('[data-tab-count="' + tabKey + '"]').textContent = '(' + number(counts[config.api]) + ')';
        });
    }

    function setTotal(count) {
        $('[data-result-count]').textContent = String(number(count));
    }

    function filterSelect(name) {
        return $('[data-filter="' + name + '"]');
    }

    function setFilterControlsDisabled(disabled) {
        ['region', 'category', 'sort'].forEach((name) => {
            const select = filterSelect(name);
            select.disabled = disabled;
            select.setAttribute('aria-disabled', String(disabled));
        });
    }

    function optionValues(values) {
        if (!Array.isArray(values)) return [];
        const seen = new Set();
        return values.filter((value) => {
            if (typeof value !== 'string' || !value.trim() || seen.has(value.trim())) return false;
            seen.add(value.trim());
            return true;
        }).map((value) => value.trim());
    }

    function replaceFilterOptions(select, allLabel, values, selectedValue) {
        const fragment = document.createDocumentFragment();
        const all = document.createElement('option');
        all.value = '';
        all.textContent = allLabel;
        fragment.append(all);
        values.forEach((value) => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = value;
            fragment.append(option);
        });
        select.replaceChildren(fragment);
        select.value = values.includes(selectedValue) ? selectedValue : '';
        return select.value;
    }

    function updateFilterOptions(filters) {
        const source = isRecord(filters) ? filters : {};
        queryState.region = replaceFilterOptions(
            filterSelect('region'), '지역 전체', optionValues(source.regions), queryState.region
        );
        queryState.field = replaceFilterOptions(
            filterSelect('category'), '분야 전체', optionValues(source.fields), queryState.field
        );
        filterSelect('sort').value = queryState.sort.toLowerCase();
        setFilterControlsDisabled(false);
    }

    function resetTabFilters() {
        queryState.region = '';
        queryState.field = '';
        filterSelect('region').value = '';
        filterSelect('category').value = '';
    }

    function clearPagination() {
        $('[data-recommend-pagination]').replaceChildren();
    }

    function responsePageInfo(payload, requestedState) {
        if (!Number.isInteger(payload.page) || payload.page < 0
            || !Number.isInteger(payload.size) || payload.size < 1
            || !Number.isInteger(payload.totalPages) || payload.totalPages < 0
            || payload.size !== requestedState.size) {
            return null;
        }
        if (payload.totalPages === 0 && payload.totalElements !== 0) return null;
        return { page: payload.page, totalPages: payload.totalPages, totalElements: payload.totalElements };
    }

    function pageRecoveryKey(state) {
        return [state.tab, state.region, state.field, state.sort, state.size].join('|');
    }

    function pageNumbers(totalPages, currentPage) {
        const windowSize = 5;
        const start = Math.max(0, Math.min(currentPage - Math.floor(windowSize / 2), totalPages - windowSize));
        const end = Math.min(totalPages, start + windowSize);
        return Array.from({ length: end - start }, (_, index) => start + index);
    }

    function scrollToResults() {
        const results = $('#recommend-results');
        if (typeof results.scrollIntoView === 'function') {
            results.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    function movePage(targetPage) {
        if (!Number.isInteger(targetPage) || targetPage < 0 || targetPage >= pageInfo.totalPages
            || targetPage === queryState.page) {
            return;
        }
        queryState.page = targetPage;
        pageRecoveryScope = null;
        scrollToResults();
        loadSelectedTab();
    }

    function paginationButton(label, page, options = {}) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'pagination__button' + (options.current ? ' is-current' : '');
        button.disabled = Boolean(options.disabled);
        button.setAttribute('aria-label', label);
        if (options.current) button.setAttribute('aria-current', 'page');
        button.textContent = options.text || String(page + 1);
        if (!button.disabled && !options.current) {
            button.addEventListener('click', () => movePage(page));
        }
        return button;
    }

    function renderPagination(info) {
        const container = $('[data-recommend-pagination]');
        container.replaceChildren();
        if (info.totalPages <= 1 || info.totalElements === 0) return;

        const navigation = document.createElement('nav');
        navigation.className = 'pagination';
        navigation.setAttribute('aria-label', TABS[selectedTabKey()].label + ' 혜택 페이지 이동');
        navigation.append(paginationButton('이전 페이지', info.page - 1, {
            text: '이전',
            disabled: info.page === 0
        }));

        const pages = document.createElement('span');
        pages.className = 'pagination__pages';
        pageNumbers(info.totalPages, info.page).forEach((page) => {
            pages.append(paginationButton((page + 1) + '페이지', page, { current: page === info.page }));
        });
        navigation.append(pages);
        navigation.append(paginationButton('다음 페이지', info.page + 1, {
            text: '다음',
            disabled: info.page === info.totalPages - 1
        }));
        container.append(navigation);
    }

    function recoverInvalidPage(info, requestedState) {
        const scope = pageRecoveryKey(requestedState);
        const targetPage = info.totalPages === 0 ? 0 : info.totalPages - 1;
        if (pageRecoveryScope === scope) return false;

        pageRecoveryScope = scope;
        queryState.page = targetPage;
        window.setTimeout(() => {
            if (pageRecoveryScope === scope && queryState.page === targetPage
                && pageRecoveryKey(queryState) === scope) {
                loadSelectedTab();
            }
        }, 0);
        return true;
    }

    function createState(message, role = 'status', retry = false) {
        const state = document.createElement('div');
        state.className = 'recommend-empty-state';
        state.setAttribute('role', role);

        const title = document.createElement('strong');
        title.textContent = message;
        state.append(title);

        if (retry) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'bk-btn bk-btn--secondary bk-btn--md';
            button.textContent = '다시 시도';
            button.addEventListener('click', loadSelectedTab);
            state.append(button);
        }
        return state;
    }

    function showState(message, options = {}) {
        $('[data-recommend-list]').replaceChildren(createState(message, options.role, options.retry));
        clearPagination();
    }

    function detail(value, label) {
        if (!text(value)) return null;
        const row = document.createElement('div');
        row.className = 'recommend-card__detail';
        const labelElement = document.createElement('strong');
        labelElement.textContent = label;
        const valueElement = document.createElement('span');
        valueElement.textContent = text(value);
        row.append(labelElement, valueElement);
        return row;
    }

    function infoItem(label, value, fallback) {
        const wrapper = document.createElement('div');
        const term = document.createElement('dt');
        const description = document.createElement('dd');
        term.textContent = label;
        description.textContent = text(value, fallback);
        wrapper.append(term, description);
        return wrapper;
    }

    function httpsUrl(value) {
        try {
            const url = new URL(value);
            return url.protocol === 'https:' ? url.href : null;
        } catch {
            return null;
        }
    }

    function createAction(item) {
        const externalUrl = httpsUrl(item.sourceUrl);
        if (externalUrl) {
            const link = document.createElement('a');
            link.className = 'bk-btn bk-btn--primary bk-btn--md';
            link.href = externalUrl;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = '공고 보기';
            return link;
        }

        const benefitId = Number(item.benefitId);
        if (Number.isSafeInteger(benefitId) && benefitId > 0) {
            const link = document.createElement('a');
            link.className = 'bk-btn bk-btn--primary bk-btn--md';
            link.href = 'benefit-detail.html?id=' + encodeURIComponent(String(benefitId));
            link.textContent = '상세 보기';
            return link;
        }

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'bk-btn bk-btn--primary bk-btn--md';
        button.disabled = true;
        button.textContent = '상세 정보 준비 중';
        return button;
    }

    function createHeading(item, statusLabel) {
        const top = document.createElement('div');
        top.className = 'recommend-card__eligible-top';
        const badges = document.createElement('div');
        badges.className = 'recommend-card__badges';
        const status = document.createElement('span');
        status.className = 'recommend-status-badge';
        status.textContent = statusLabel;
        const category = document.createElement('span');
        category.className = 'recommend-category-badge';
        category.textContent = text(item.category, '분야 정보 없음');
        badges.append(status, category);
        top.append(badges);

        const content = document.createElement('div');
        content.className = 'recommend-card__eligible-content';
        const title = document.createElement('h3');
        title.textContent = text(item.title, '혜택 정보');
        const summary = document.createElement('p');
        summary.className = 'recommend-card__description';
        summary.textContent = text(item.summary, '혜택 요약 정보가 없습니다.');
        content.append(title, summary);
        return { top, content };
    }

    function createCommonDetails(item, className = 'recommend-card__eligible-reason') {
        const details = document.createElement('div');
        details.className = className;

        [
            detail(item.orgName, '담당 기관'),
            detail(item.region, '지역'),
            detail(item.apply && item.apply.method, '신청 방법'),
            detail(item.apply && item.apply.docText, '서류')
        ].filter(Boolean).forEach((row) => details.append(row));

        const tags = Array.isArray(item.hashtags)
            ? item.hashtags.filter((tag) => typeof tag === 'string' && tag.trim()).map((tag) => '#' + tag.trim())
            : [];
        if (tags.length) {
            const tagLine = document.createElement('p');
            tagLine.className = 'recommend-card__description';
            tagLine.textContent = tags.join(' ');
            details.append(tagLine);
        }
        return details;
    }

    function createEligibleCard(item) {
        const card = document.createElement('article');
        card.className = 'recommend-card recommend-card--eligible';
        const heading = createHeading(item, TABS.eligible.label);

        const info = document.createElement('dl');
        info.className = 'recommend-card__eligible-info';
        info.append(
            infoItem('지원 금액', item.amount && item.amount.text, '금액 정보 없음'),
            infoItem('신청 기간', item.period && item.period.text, '기간 정보 없음')
        );

        const details = createCommonDetails(item);
        const reasonTitle = document.createElement('strong');
        reasonTitle.textContent = '추천 이유';
        const reason = document.createElement('p');
        reason.textContent = text(item.reason, '추천 근거를 확인 중이에요.');
        details.prepend(reasonTitle, reason);

        const actions = document.createElement('div');
        actions.className = 'recommend-card__actions';
        actions.append(createAction(item));
        card.append(heading.top, heading.content, info, details, actions);
        return card;
    }

    function checkTypeLabel(type) {
        return {
            PROFILE: '프로필 정보 확인 필요',
            SENSITIVE: '민감정보 확인 필요',
            AGENCY: '담당 기관 확인 필요',
            SOURCE: '공고 원문 확인 필요'
        }[type] || '공고 조건 확인 필요';
    }

    function questionSelectionKey(item, cardIndex, check, checkIndex) {
        const benefitId = Number.isSafeInteger(Number(item.benefitId)) ? String(item.benefitId) : 'card-' + cardIndex;
        return benefitId + ':' + text(check.questionKey, 'missing-key') + ':' + checkIndex;
    }

    function validBenefitId(item) {
        const benefitId = Number(item && item.benefitId);
        return Number.isSafeInteger(benefitId) && benefitId > 0 ? benefitId : null;
    }

    function isAnswerableQuestion(check) {
        return isRecord(check)
            && check.type === 'QUESTION'
            && QUESTION_KEY_PATTERN.test(text(check.questionKey))
            && Boolean(text(check.text));
    }

    function selectedAnswersForCard(item, cardIndex) {
        const benefitId = validBenefitId(item);
        if (benefitId === null) return { error: '혜택 정보를 확인하지 못해 답변을 저장할 수 없어요.' };

        const answers = {};
        const questionKeys = new Set();
        const checks = Array.isArray(item.checks) ? item.checks : [];
        for (let checkIndex = 0; checkIndex < checks.length; checkIndex += 1) {
            const check = checks[checkIndex];
            if (!isAnswerableQuestion(check)) continue;
            const answer = questionSelections.get(questionSelectionKey(item, cardIndex, check, checkIndex));
            if (!answer) continue;
            if (!ANSWER_VALUES.has(answer)) return { error: '선택한 답변을 확인하지 못했어요. 다시 선택해 주세요.' };
            if (questionKeys.has(check.questionKey)) {
                return { error: '같은 질문 키가 중복되어 답변을 저장할 수 없어요. 목록을 새로고침해 주세요.' };
            }
            questionKeys.add(check.questionKey);
            answers[check.questionKey] = answer;
        }

        const count = Object.keys(answers).length;
        if (!count) return { error: '저장할 답변을 하나 이상 선택해 주세요.' };
        if (count > 20) return { error: '한 번에 최대 20개 질문까지 저장할 수 있어요.' };
        return { benefitId, answers };
    }

    function hasSelectedAnswer(item, cardIndex) {
        const checks = Array.isArray(item.checks) ? item.checks : [];
        return checks.some((check, checkIndex) => isAnswerableQuestion(check)
            && ANSWER_VALUES.has(questionSelections.get(questionSelectionKey(item, cardIndex, check, checkIndex))));
    }

    function clearSubmittedSelections(item, cardIndex, answers) {
        const checks = Array.isArray(item.checks) ? item.checks : [];
        checks.forEach((check, checkIndex) => {
            if (isAnswerableQuestion(check) && Object.hasOwn(answers, check.questionKey)) {
                questionSelections.delete(questionSelectionKey(item, cardIndex, check, checkIndex));
            }
        });
    }

    function answerErrorMessage(status) {
        if (status === 400) return '답변 또는 혜택 정보를 확인해 주세요.';
        if (status === 403) return '보안 검증 또는 접근 권한을 확인하지 못했어요. 새로고침 후 다시 시도해 주세요.';
        if (status >= 500) return '답변을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.';
        return '답변을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.';
    }

    function validAnswerResponse(payload, benefitId) {
        if (!isRecord(payload) || !isRecord(payload.answers)) return null;
        if (payload.benefit === null) return { answers: payload.answers, benefit: null };
        if (!isRecord(payload.benefit)
            || validBenefitId(payload.benefit) !== benefitId
            || !text(payload.benefit.status)) return null;
        return { answers: payload.answers, benefit: payload.benefit };
    }

    function appendSubmissionStatus(container, message, isError = false) {
        container.textContent = message;
        container.classList.toggle('is-error', isError);
        container.setAttribute('role', isError ? 'alert' : 'status');
    }

    function applyImmediateBenefitResult(benefit, benefitId, cardIndex, viewKey) {
        if (!benefit || viewKey !== requestKey(queryState) || selectedTabKey() !== 'conditional') return;
        const card = document.querySelector('.recommend-card[data-benefit-id="' + benefitId + '"]');
        if (!card) return;

        if (benefit.status === 'CONDITIONAL') {
            card.replaceWith(createConditionalCard(benefit, cardIndex));
            return;
        }

        const notice = document.createElement('p');
        notice.className = 'conditional-question__submission-status';
        notice.textContent = '서버 판정 결과가 갱신됐어요. 목록을 새로 불러오는 중이에요.';
        card.append(notice);
    }

    async function submitSelectedAnswers(item, cardIndex, controls) {
        const request = selectedAnswersForCard(item, cardIndex);
        if (request.error) {
            appendSubmissionStatus(controls.status, request.error, true);
            controls.update();
            return;
        }
        if (submittingBenefitIds.has(request.benefitId)) return;

        const viewKey = requestKey(queryState);
        submittingBenefitIds.add(request.benefitId);
        controls.update();
        appendSubmissionStatus(controls.status, '답변을 저장하고 서버 판정을 갱신하고 있어요.');

        try {
            const csrf = await window.BokdeongiAuth.getCsrfToken();
            const response = await window.BokdeongiAuth.authenticatedFetch('/api/me/answers', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    [csrf.headerName]: csrf.token
                },
                body: JSON.stringify({ answers: request.answers, benefitId: request.benefitId })
            });

            if (response.status === 401 || window.BokdeongiAuth.getAuthenticationState() !== 'authenticated') {
                showLoginGuidance();
                return;
            }
            if (!response.ok) {
                appendSubmissionStatus(controls.status, answerErrorMessage(response.status), true);
                return;
            }

            const payload = await response.json();
            const result = validAnswerResponse(payload, request.benefitId);
            if (!result) {
                appendSubmissionStatus(controls.status, '답변 저장 결과를 확인하지 못했어요. 목록을 새로고침해 주세요.', true);
                return;
            }

            clearSubmittedSelections(item, cardIndex, request.answers);
            if (result.benefit) {
                applyImmediateBenefitResult(result.benefit, request.benefitId, cardIndex, viewKey);
                appendSubmissionStatus(controls.status, '답변을 저장했고 서버 판정 결과를 반영하고 있어요.');
            } else {
                appendSubmissionStatus(controls.status, '답변은 저장됐지만 즉시 판정 결과를 받지 못했어요. 목록을 새로고침해 주세요.');
            }

            latestRequestId += 1;
            loadingRequestKey = null;
            loadSelectedTab({ quiet: true });
        } catch (error) {
            if (error && error.type === 'unauthenticated') {
                showLoginGuidance();
                return;
            }
            if (error && error.type === 'csrf') {
                appendSubmissionStatus(controls.status, '보안 토큰을 확인하지 못했어요. 새로고침 후 다시 시도해 주세요.', true);
                return;
            }
            appendSubmissionStatus(controls.status, '저장 여부를 확인하지 못했어요. 자동으로 다시 전송하지 않았습니다. 목록을 새로고침하거나 직접 다시 시도해 주세요.', true);
        } finally {
            submittingBenefitIds.delete(request.benefitId);
            controls.update();
        }
    }

    function updateQuestionChoiceState(choices, selectedValue, selectedMessage) {
        choices.querySelectorAll('button').forEach((button) => {
            const selected = button.dataset.answerValue === selectedValue;
            button.setAttribute('aria-pressed', String(selected));
            button.classList.toggle('is-selected', selected);
        });
        const selectedOption = ANSWER_OPTIONS.find((option) => option.value === selectedValue);
        selectedMessage.textContent = selectedOption
            ? '선택한 답변: ' + selectedOption.label + ' (아직 저장되지 않음)'
            : '답변을 선택할 수 있어요. 선택한 답변은 아직 저장되지 않아요.';
    }

    function createQuestionCheck(item, cardIndex, check, checkIndex, onSelectionChange) {
        const question = document.createElement('section');
        question.className = 'conditional-question__result conditional-question__question';
        const questionId = 'recommend-question-' + questionSelectionKey(item, cardIndex, check, checkIndex).replace(/[^a-zA-Z0-9_-]/g, '-');
        const prompt = document.createElement('p');
        prompt.id = questionId;
        prompt.className = 'conditional-question__prompt';
        prompt.textContent = text(check.text, '질문 문구를 확인하지 못했어요.');
        question.append(prompt);

        if (!text(check.questionKey) || !text(check.text)) {
            const unavailable = document.createElement('p');
            unavailable.textContent = '현재 이 질문에는 답변을 선택할 수 없어요.';
            question.append(unavailable);
            return question;
        }

        const selectionKey = questionSelectionKey(item, cardIndex, check, checkIndex);
        const choices = document.createElement('div');
        choices.className = 'conditional-question__choices';
        choices.setAttribute('role', 'group');
        choices.setAttribute('aria-labelledby', questionId);
        const selectedMessage = document.createElement('p');
        selectedMessage.className = 'conditional-question__selection';
        selectedMessage.setAttribute('aria-live', 'polite');

        ANSWER_OPTIONS.forEach((option) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'bk-btn bk-btn--outline bk-btn--sm';
            button.dataset.answerValue = option.value;
            button.textContent = option.label;
            button.addEventListener('click', () => {
                questionSelections.set(selectionKey, option.value);
                updateQuestionChoiceState(choices, option.value, selectedMessage);
                onSelectionChange();
            });
            choices.append(button);
        });
        updateQuestionChoiceState(choices, questionSelections.get(selectionKey), selectedMessage);
        question.append(choices, selectedMessage);
        return question;
    }

    function createAnswerSubmitControls(item, cardIndex) {
        const container = document.createElement('div');
        container.className = 'conditional-question__submission';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'bk-btn bk-btn--primary bk-btn--sm';
        button.textContent = '선택한 답변 저장';
        const status = document.createElement('p');
        status.className = 'conditional-question__submission-status';
        status.setAttribute('aria-live', 'polite');
        const controls = {
            status,
            update() {
                const benefitId = validBenefitId(item);
                const submitting = benefitId !== null && submittingBenefitIds.has(benefitId);
                button.disabled = !hasSelectedAnswer(item, cardIndex) || submitting;
                button.textContent = submitting ? '답변 저장 중...' : '선택한 답변 저장';
            }
        };
        button.addEventListener('click', () => submitSelectedAnswers(item, cardIndex, controls));
        container.append(button, status);
        controls.update();
        return { container, controls };
    }

    function createReadonlyCheck(check) {
        const item = document.createElement('div');
        item.className = 'conditional-question__result';
        const title = document.createElement('strong');
        title.textContent = checkTypeLabel(text(check.type));
        const message = document.createElement('p');
        message.textContent = text(check.text, '공고 조건을 확인해 주세요.');
        item.append(title, message);
        if (text(check.sourceQuote)) {
            const quote = document.createElement('p');
            quote.textContent = '근거: ' + text(check.sourceQuote);
            item.append(quote);
        }
        return item;
    }

    function createChecks(item, cardIndex) {
        const validChecks = Array.isArray(item.checks) ? item.checks.filter(isRecord) : [];
        if (!validChecks.length) return null;

        const section = document.createElement('section');
        section.className = 'conditional-question';
        const title = document.createElement('h4');
        title.textContent = '확인이 필요한 조건';
        section.append(title);

        const answerableQuestions = validChecks.filter(isAnswerableQuestion);
        const submitControls = answerableQuestions.length ? createAnswerSubmitControls(item, cardIndex) : null;

        validChecks.forEach((check, checkIndex) => {
            if (check.type === 'QUESTION') {
                section.append(createQuestionCheck(item, cardIndex, check, checkIndex, () => submitControls.controls.update()));
            } else {
                section.append(createReadonlyCheck(check));
            }
        });
        if (submitControls) section.append(submitControls.container);
        return section;
    }

    function createConditionalCard(item, cardIndex) {
        const card = document.createElement('article');
        card.className = 'recommend-card recommend-card--conditional';
        const benefitId = validBenefitId(item);
        if (benefitId !== null) card.dataset.benefitId = String(benefitId);
        const infoColumn = document.createElement('div');
        infoColumn.className = 'recommend-card__conditional-info';
        const heading = createHeading(item, TABS.conditional.label);

        const info = document.createElement('dl');
        info.className = 'recommend-card__conditional-meta';
        info.append(
            infoItem('지원 금액', item.amount && item.amount.text, '금액 정보 없음'),
            infoItem('신청 기간', item.period && item.period.text, '기간 정보 없음')
        );

        const notice = document.createElement('div');
        notice.className = 'recommend-card__conditional-notice';
        const noticeTitle = document.createElement('strong');
        noticeTitle.textContent = '추천 안내';
        const reason = document.createElement('p');
        reason.textContent = text(item.reason, '추가 확인이 필요한 조건이 있어요.');
        notice.append(noticeTitle, reason);

        const actions = document.createElement('div');
        actions.className = 'recommend-card__actions';
        actions.append(createAction(item));
        infoColumn.append(heading.top, heading.content, info, notice, actions);
        const checks = createChecks(item, cardIndex);
        card.append(infoColumn);
        if (checks) card.append(checks);
        return card;
    }

    function createNearMissCard(item) {
        const card = document.createElement('article');
        card.className = 'recommend-card recommend-card--near-miss';
        const heading = createHeading(item, TABS['near-miss'].label);

        const info = document.createElement('dl');
        info.className = 'recommend-card__near-miss-meta';
        info.append(
            infoItem('지원 금액', item.amount && item.amount.text, '금액 정보 없음'),
            infoItem('신청 기간', item.period && item.period.text, '기간 정보 없음')
        );

        const nearMiss = isRecord(item.nearMiss) ? item.nearMiss : {};
        const condition = document.createElement('div');
        condition.className = 'recommend-card__near-miss-condition';
        const conditionTitle = document.createElement('strong');
        conditionTitle.textContent = '충족하지 못한 조건';
        const label = document.createElement('p');
        label.textContent = text(nearMiss.label, '조건 확인 필요');
        const expected = document.createElement('span');
        expected.textContent = text(nearMiss.expected, text(nearMiss.sourceQuote, '공고 원문에서 기준을 확인해 주세요.'));
        condition.append(conditionTitle, label, expected);

        const reason = document.createElement('div');
        reason.className = 'recommend-card__near-miss-reason';
        const reasonTitle = document.createElement('strong');
        reasonTitle.textContent = '안내';
        const reasonText = document.createElement('p');
        reasonText.textContent = text(nearMiss.reason, text(item.reason, '공고 조건을 확인해 주세요.'));
        reason.append(reasonTitle, reasonText);

        const details = createCommonDetails(item, 'recommend-card__near-miss-reason');
        const actions = document.createElement('div');
        actions.className = 'recommend-card__actions';
        actions.append(createAction(item));
        card.append(heading.top, heading.content, info, condition, reason, details, actions);
        return card;
    }

    function createCard(item, tabKey, cardIndex) {
        if (tabKey === 'conditional') return createConditionalCard(item, cardIndex);
        if (tabKey === 'near-miss') return createNearMissCard(item);
        return createEligibleCard(item);
    }

    function validPayload(payload) {
        return isRecord(payload)
            && Array.isArray(payload.items)
            && isRecord(payload.counts)
            && Number.isInteger(payload.totalElements)
            && payload.totalElements >= 0
            && typeof payload.updating === 'boolean';
    }

    function refreshRecommendationsManually(button, message) {
        const currentKey = requestKey(queryState);
        if (loadingRequestKey === currentKey) return;

        button.disabled = true;
        button.textContent = '결과를 확인하는 중...';
        message.setAttribute('role', 'status');
        message.textContent = '최신 맞춤 혜택 결과를 확인하고 있어요.';
        loadSelectedTab({
            quiet: true,
            preserveOnError: true,
            onError(errorMessage) {
                message.setAttribute('role', 'alert');
                message.textContent = errorMessage;
            }
        }).finally(() => {
            if (!button.isConnected) return;
            button.disabled = false;
            button.textContent = '결과 새로고침';
        });
    }

    function renderRecommendations(payload, tabKey, responseInfo) {
        setCounts(payload.counts);
        setTotal(payload.totalElements);
        updateFilterOptions(payload.filters);
        pageInfo = responseInfo;
        queryState.page = responseInfo.page;
        pageRecoveryScope = null;

        const fragment = document.createDocumentFragment();
        if (payload.updating === true) {
            const updating = document.createElement('div');
            updating.className = 'recommend-update-status';
            const message = document.createElement('p');
            message.className = 'recommend-update-status__message';
            message.setAttribute('role', 'status');
            message.textContent = '맞춤 혜택을 다시 확인하고 있어요. 일부 결과는 이전 조건을 기준으로 표시될 수 있습니다.';
            const refresh = document.createElement('button');
            refresh.type = 'button';
            refresh.className = 'bk-btn bk-btn--secondary bk-btn--sm';
            refresh.textContent = '결과 새로고침';
            refresh.addEventListener('click', () => refreshRecommendationsManually(refresh, message));
            updating.append(message, refresh);
            fragment.append(updating);
        }
        if (!payload.items.length) {
            fragment.append(createState(TABS[tabKey].empty));
            $('[data-recommend-list]').replaceChildren(fragment);
            clearPagination();
            return;
        }
        payload.items.forEach((item, cardIndex) => {
            fragment.append(createCard(isRecord(item) ? item : {}, tabKey, cardIndex));
        });
        $('[data-recommend-list]').replaceChildren(fragment);
        renderPagination(responseInfo);
    }

    function showLoginGuidance() {
        setFilterControlsDisabled(true);
        setTotal(0);
        showState('맞춤 혜택을 보려면 로그인해 주세요.');
    }

    function errorMessage(status) {
        if (status === 400) return '추천 목록 요청을 확인해 주세요.';
        if (status === 403) return '추천 목록에 접근할 권한이 없어요. 다시 로그인해 주세요.';
        if (status >= 500) return '추천 서비스를 잠시 사용할 수 없어요. 잠시 후 다시 시도해 주세요.';
        return '추천 혜택을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
    }

    async function loadSelectedTab(options = {}) {
        if (!window.BokdeongiAuth || window.BokdeongiAuth.getAuthenticationState() !== 'authenticated') {
            showLoginGuidance();
            return;
        }
        const requestedState = { ...queryState };
        const requestedTab = TAB_KEY_BY_API[requestedState.tab];
        const requestedKey = requestKey(requestedState);
        if (loadingRequestKey === requestedKey) return;

        const requestId = ++latestRequestId;
        loadingRequestKey = requestedKey;
        if (!options.quiet) showState(TABS[requestedTab].label + ' 혜택을 불러오는 중이에요.');
        const showLoadError = (message) => {
            if (options.preserveOnError) {
                if (typeof options.onError === 'function') options.onError(message);
                return;
            }
            showState(message, { role: 'alert', retry: true });
        };
        try {
            const parameters = new URLSearchParams({
                tab: requestedState.tab,
                sort: requestedState.sort,
                page: String(requestedState.page),
                size: String(requestedState.size)
            });
            if (requestedState.region) parameters.set('region', requestedState.region);
            if (requestedState.field) parameters.set('field', requestedState.field);

            const response = await window.BokdeongiAuth.authenticatedFetch('/api/benefits/recommended?' + parameters);
            if (requestId !== latestRequestId || requestedKey !== requestKey(queryState)) return;
            if (response.status === 401 || window.BokdeongiAuth.getAuthenticationState() !== 'authenticated') {
                showLoginGuidance();
                return;
            }
            if (!response.ok) {
                showLoadError(errorMessage(response.status));
                return;
            }

            const payload = await response.json();
            if (requestId !== latestRequestId || requestedKey !== requestKey(queryState)) return;
            if (!validPayload(payload)) {
                showLoadError('추천 목록 응답을 확인하지 못했어요. 다시 시도해 주세요.');
                return;
            }
            const responseInfo = responsePageInfo(payload, requestedState);
            if (!responseInfo) {
                showLoadError('추천 목록 페이지 정보를 확인하지 못했어요. 다시 시도해 주세요.');
                return;
            }
            if (responseInfo.page >= responseInfo.totalPages && responseInfo.totalPages !== 0) {
                if (!recoverInvalidPage(responseInfo, requestedState)) {
                    showLoadError('추천 목록 페이지 범위를 확인하지 못했어요. 다시 시도해 주세요.');
                }
                return;
            }
            if (responseInfo.totalPages === 0 && responseInfo.page !== 0) {
                if (!recoverInvalidPage(responseInfo, requestedState)) {
                    showLoadError('추천 목록 페이지 범위를 확인하지 못했어요. 다시 시도해 주세요.');
                }
                return;
            }
            renderRecommendations(payload, requestedTab, responseInfo);
        } catch (error) {
            if (requestId !== latestRequestId || requestedKey !== requestKey(queryState)) return;
            if (error && error.type === 'unauthenticated') {
                showLoginGuidance();
                return;
            }
            showLoadError('추천 혜택을 불러오지 못했어요. 네트워크 상태를 확인하고 다시 시도해 주세요.');
        } finally {
            if (requestId === latestRequestId) loadingRequestKey = null;
        }
    }

    function activateTab(tabKey) {
        if (!Object.hasOwn(TABS, tabKey) || tabKey === selectedTabKey()) return;
        setActiveTab(tabKey);
        resetTabFilters();
        loadSelectedTab();
    }

    function setupFilters() {
        filterSelect('region').addEventListener('change', (event) => {
            queryState.region = event.target.value;
            queryState.page = 0;
            pageRecoveryScope = null;
            loadSelectedTab();
        });
        filterSelect('category').addEventListener('change', (event) => {
            queryState.field = event.target.value;
            queryState.page = 0;
            pageRecoveryScope = null;
            loadSelectedTab();
        });
        filterSelect('sort').addEventListener('change', (event) => {
            const sort = event.target.value.toUpperCase();
            if (!SORTS.has(sort)) {
                event.target.value = queryState.sort.toLowerCase();
                return;
            }
            queryState.sort = sort;
            queryState.page = 0;
            pageRecoveryScope = null;
            loadSelectedTab();
        });
    }

    function setupTabs() {
        document.querySelectorAll('[data-status-tab]').forEach((tab) => {
            tab.addEventListener('click', () => activateTab(tab.dataset.statusTab));
            tab.addEventListener('keydown', (event) => {
                const currentIndex = TAB_ORDER.indexOf(tab.dataset.statusTab);
                let nextIndex = null;
                if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % TAB_ORDER.length;
                if (event.key === 'ArrowLeft') nextIndex = (currentIndex + TAB_ORDER.length - 1) % TAB_ORDER.length;
                if (event.key === 'Home') nextIndex = 0;
                if (event.key === 'End') nextIndex = TAB_ORDER.length - 1;
                if (nextIndex === null) return;
                event.preventDefault();
                const nextTab = TAB_ORDER[nextIndex];
                document.querySelector('[data-status-tab="' + nextTab + '"]').focus();
                activateTab(nextTab);
            });
        });
    }

    function handleAuthenticationState(event) {
        const status = event.detail && event.detail.status;
        if (status === 'authenticated') {
            loadSelectedTab();
        } else if (status === 'checking') {
            showState('로그인 정보를 확인하고 있어요.');
        } else {
            latestRequestId += 1;
            loadingRequestKey = null;
            showLoginGuidance();
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        setActiveTab(selectedTabKey());
        clearPagination();
        setupFilters();
        setupTabs();
        window.addEventListener('bokdeongi:auth-state-change', handleAuthenticationState);

        if (!window.BokdeongiAuth) {
            showState('인증 정보를 준비하지 못했어요. 페이지를 새로고침해 주세요.', { role: 'alert', retry: true });
            return;
        }

        const status = window.BokdeongiAuth.getAuthenticationState();
        if (status === 'authenticated') {
            loadSelectedTab();
        } else if (status === 'checking') {
            showState('로그인 정보를 확인하고 있어요.');
        } else {
            showLoginGuidance();
        }
    });
})();
