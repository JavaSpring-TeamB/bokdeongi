const benefits = [
    ['청년 월세 지원', '복지 지원 기관', '주거', '청년,대학생', '신청 가능', '월 최대 20만원', '2026-12-31', '주거비 부담을 줄이기 위한 월세 지원 사업', '청년,월세,주거지원', 20, 2],
    ['국민취업지원제도', '고용복지플러스센터', '고용·취업', '청년,구직자', '신청 가능', '월 최대 50만원', '2026-11-30', '취업을 준비하는 구직자의 생활과 취업 활동을 지원합니다.', '취업지원,구직자', 50, 3],
    ['국가장학금', '한국장학재단', '교육', '청년,대학생', '신청 예정', '학기당 최대 350만원', '2027-01-15', '대학생의 등록금 부담을 낮추기 위한 장학 지원입니다.', '장학금,대학생', 350, 4],
    ['청년도약계좌', '서민금융진흥원', '금융', '청년', '신청 가능', '월 최대 70만원 납입', '상시', '청년의 중장기 자산 형성을 돕는 금융 지원입니다.', '청년,금융', 70, 2],
    ['국민내일배움카드', '고용노동부', '고용·취업', '청년,구직자', '신청 가능', '최대 500만원', '상시', '직업훈련 비용을 지원해 새로운 역량 개발을 돕습니다.', '취업지원,교육', 500, 1],
    ['첫만남이용권', '보건복지부', '보육·가족', '임산부,신혼부부', '신청 가능', '첫째 200만원', '상시', '출생아의 초기 양육 부담을 덜어주는 이용권입니다.', '출산,보육', 200, 1],
    ['부모급여 지원', '보건복지부', '보육·가족', '임산부,신혼부부', '신청 가능', '월 최대 100만원', '상시', '영아기 가정의 돌봄과 양육을 지원합니다.', '출산,보육', 100, 1],
    ['주거급여', '주거복지센터', '주거', '저소득층', '신청 가능', '가구별 차등 지원', '상시', '소득과 주거 형태에 따라 임차료 또는 수선비를 지원합니다.', '주거지원,저소득층', 45, 2],
    ['에너지바우처', '한국에너지공단', '문화·생활', '저소득층,어르신', '신청 예정', '가구별 차등 지원', '2026-12-20', '에너지 취약 가구의 냉난방 비용 부담을 줄입니다.', '에너지,저소득층', 30, 2],
    ['청년 자격증 응시료 지원', '청년지원센터', '고용·취업', '청년,구직자', '신청 마감', '연 최대 10만원', '2026-11-18', '취업 준비에 필요한 자격증 시험 응시료를 지원합니다.', '청년,취업지원', 10, 4],
    ['문화누리카드', '문화체육관광부', '문화·생활', '저소득층', '신청 가능', '연 14만원', '2026-12-31', '문화예술과 여행, 체육 활동을 위한 이용권입니다.', '문화,저소득층', 14, 1],
    ['노인일자리 지원사업', '노인인력개발원', '고용·취업', '어르신', '신청 예정', '월 최대 76만원', '2027-01-10', '어르신의 사회 참여와 소득 활동을 지원합니다.', '어르신,일자리', 76, 3]
].map(([title, organization, category, target, status, amount, deadline, description, hashtags, amountValue, ease], id) => ({
    id: id + 1,
    title,
    organization,
    category,
    target: target.split(','),
    status,
    amount,
    deadline,
    description,
    hashtags: hashtags.split(','),
    amountValue,
    ease,
    bookmarked: false
}));

const configs = {
    status: ['전체', '신청 가능', '신청 예정', '신청 마감'],
    category: ['전체', '주거', '고용·취업', '교육', '금융', '보육·가족', '건강·의료', '문화·생활'],
    target: ['전체', '청년', '대학생', '구직자', '신혼부부', '임산부', '어르신', '저소득층']
};

const state = {
    query: '',
    filters: {
        status: ['전체'],
        category: ['전체'],
        target: ['전체']
    },
    screen: 'INITIAL'
};

const $ = (selector) => document.querySelector(selector);

function toast(message) {
    const element = $('#toast');

    element.textContent = message;
    element.hidden = false;
    clearTimeout(toast.id);
    toast.id = setTimeout(() => {
        element.hidden = true;
    }, 3000);
}

function isAllSelected(group) {
    return state.filters[group].includes('전체');
}

function hasActiveFilters() {
    return Object.keys(configs).some((group) => !isAllSelected(group));
}

function updateFilter(group, value, checked) {
    const selections = state.filters[group];

    if (value === '전체') {
        state.filters[group] = checked ? ['전체'] : selections.filter((item) => item !== '전체');
    } else {
        const nextSelections = selections.filter((item) => item !== '전체');

        if (checked) {
            nextSelections.push(value);
        } else {
            state.filters[group] = nextSelections.filter((item) => item !== value);
        }

        if (checked) {
            state.filters[group] = [...new Set(nextSelections)];
        }
    }

    if (state.filters[group].length === 0) {
        state.filters[group] = ['전체'];
    }
}

function matchesGroup(benefit, group) {
    const selections = state.filters[group];

    if (selections.includes('전체')) {
        return true;
    }

    if (group === 'status') {
        return selections.includes(benefit.status);
    }

    if (group === 'category') {
        return selections.includes(benefit.category);
    }

    return selections.some((target) => benefit.target.includes(target));
}

function filtered() {
    const query = state.query.toLocaleLowerCase('ko-KR');

    return benefits.filter((benefit) => {
        const searchableText = [
            benefit.title,
            benefit.organization,
            benefit.description,
            ...benefit.hashtags
        ].join(' ').toLocaleLowerCase('ko-KR');

        return (
            (!query || searchableText.includes(query)) &&
            matchesGroup(benefit, 'status') &&
            matchesGroup(benefit, 'category') &&
            matchesGroup(benefit, 'target')
        );
    });
}

function activeFilters() {
    const selected = Object.values(state.filters).flat().filter((value) => value !== '전체');

    return [state.query && '“' + state.query + '”', ...selected].filter(Boolean).join(' · ');
}

function groupTitle(group) {
    return group === 'status' ? '신청 상태' : group === 'category' ? '분야' : '대상';
}

function renderFilters() {
    const groups = Object.entries(configs).map(([group, items]) => {
        const options = items.map((item) => {
            const checked = state.filters[group].includes(item) ? ' checked' : '';

            return [
                '<label class="filter-option">',
                '<input type="checkbox" name="' + group + '" value="' + item + '"' + checked + '>',
                '<span>' + item + '</span>',
                '</label>'
            ].join('');
        }).join('');

        return '<section class="filter-group"><h3>' + groupTitle(group) + '</h3><div class="filter-options">' + options + '</div></section>';
    }).join('');

    $('#filter-groups').innerHTML = groups;
}

function empty(initial) {
    const title = initial ? '아직 검색 결과가 없어요.' : '검색 결과가 나오지 않아요.';
    const description = initial
        ? '검색어를 입력하거나, 원하는 조건을 선택해 복지 혜택을 찾아보세요.'
        : '다른 검색어나 조건으로 다시 찾아보세요.';
    const actions = initial
        ? ''
        : [
            '<button id="empty-reset" class="btn btn-secondary" type="button">필터 초기화</button>',
            '<div class="suggested-keywords">',
            '<button type="button" data-keyword="청년">#청년</button>',
            '<button type="button" data-keyword="주거지원">#주거지원</button>',
            '<button type="button" data-keyword="취업지원">#취업지원</button>',
            '</div>'
        ].join('');
    const imageAlt = initial ? '혜택 검색을 안내하는' : '검색 결과 없음을 안내하는';

    return [
        '<section class="state-panel"><div class="state-panel__inner">',
        '<img src="img/benefit_list_logo.png" alt="' + imageAlt + ' 복덩이 캐릭터">',
        '<h2>' + title + '</h2>',
        '<p>' + description + '</p>',
        actions,
        '</div></section>'
    ].join('');
}

function documentIcon() {
    return [
        '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">',
        '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z"></path>',
        '<path d="M14 3v6h6"></path>',
        '<path d="M8 13h8"></path>',
        '<path d="M8 17h6"></path>',
        '</svg>'
    ].join('');
}

function card(benefit) {
    const tone = benefit.status === '신청 예정'
        ? ' status-badge--upcoming'
        : benefit.status === '신청 마감'
            ? ' status-badge--closed'
            : '';
    const targets = benefit.target.map((target) => '<span class="benefit-tag">' + target + '</span>').join('');
    const hashtags = benefit.hashtags.map((hashtag) => '<button class="hash-tag" type="button" data-hashtag="' + hashtag + '">#' + hashtag + '</button>').join('');
    const savedClass = benefit.bookmarked ? ' is-saved' : '';
    const savedLabel = benefit.bookmarked ? '해제' : '저장';
    const deadline = benefit.deadline === '상시' ? '상시 신청' : '마감 ' + benefit.deadline;

    return [
        '<article class="benefit-card card">',
        '<span class="benefit-card__icon" aria-hidden="true">' + documentIcon() + '</span>',
        '<div>',
        '<div class="benefit-card__badges"><span class="status-badge' + tone + '">' + benefit.status + '</span></div>',
        '<h3>' + benefit.title + '</h3>',
        '<p class="benefit-card__description">' + benefit.organization + ' · ' + benefit.description + '</p>',
        '<div class="benefit-card__meta"><strong>' + benefit.amount + '</strong><span aria-hidden="true">|</span><span>' + deadline + '</span></div>',
        '<div class="benefit-card__tags"><span class="benefit-tag">' + benefit.category + '</span>' + targets + hashtags + '</div>',
        '</div>',
        '<div class="benefit-card__actions">',
        '<button class="bookmark-button' + savedClass + '" type="button" data-bookmark="' + benefit.id + '" aria-label="' + benefit.title + ' 관심 ' + savedLabel + '" aria-pressed="' + benefit.bookmarked + '">🔖</button>',
        '<button class="btn" type="button" data-detail="' + benefit.id + '">상세 보기</button>',
        '</div>',
        '</article>'
    ].join('');
}

function sortBenefits(list) {
    const sort = $('#sort-order').value;

    return list.sort((first, second) => {
        if (sort === 'amount') {
            return second.amountValue - first.amountValue;
        }

        if (sort === 'easy') {
            return first.ease - second.ease;
        }

        if (sort === 'deadline') {
            if (first.deadline === '상시') {
                return 1;
            }

            if (second.deadline === '상시') {
                return -1;
            }

            return first.deadline.localeCompare(second.deadline);
        }

        return first.id - second.id;
    });
}

function render() {
    const summary = $('#result-summary');
    const area = $('#result-area');

    if (state.screen === 'INITIAL') {
        summary.textContent = '검색어 또는 조건을 선택해 혜택을 찾아보세요.';
        area.innerHTML = empty(true);
        return;
    }

    const list = sortBenefits(filtered());
    const active = activeFilters();

    summary.textContent = (active ? active + ' · ' : '') + '검색 결과 ' + list.length + '건';
    area.innerHTML = list.length ? '<div class="benefit-list">' + list.map(card).join('') + '</div>' : empty(false);
}

function runSearch(forceResult = false) {
    const query = $('#benefit-search-input').value.trim();
    const invalid = query.length === 1 || /^[^\p{L}\p{N}]+$/u.test(query);

    if (!query && !hasActiveFilters() && !forceResult) {
        $('#search-message').textContent = '검색어를 입력하거나 원하는 조건을 선택해주세요.';
        return;
    }

    if (query && invalid) {
        $('#search-message').textContent = '두 글자 이상의 검색어를 입력해주세요.';
        return;
    }

    $('#search-message').textContent = '';
    state.query = query;
    state.screen = 'RESULT';
    render();
}

function reset() {
    state.filters = {
        status: ['전체'],
        category: ['전체'],
        target: ['전체']
    };
    state.query = '';
    state.screen = 'INITIAL';
    $('#benefit-search-input').value = '';
    $('#search-message').textContent = '';
    renderFilters();
    render();
}

function setup() {
    let theme = localStorage.getItem('bokdeongi-theme') || 'light';
    const themeButton = $('[data-theme-toggle]');

    document.documentElement.dataset.theme = theme;
    document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1);
    themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
    themeButton.setAttribute('aria-pressed', theme === 'dark');
    renderFilters();
    render();

    $('#benefit-search-form').addEventListener('submit', (event) => {
        event.preventDefault();
        runSearch();
    });

    document.addEventListener('change', (event) => {
        if (event.target.matches('#filter-groups input')) {
            updateFilter(event.target.name, event.target.value, event.target.checked);
            renderFilters();
            runSearch(true);
        }

        if (event.target.id === 'sort-order' && state.screen !== 'INITIAL') {
            render();
        }
    });

    document.addEventListener('click', (event) => {
        let target = event.target.closest('[data-keyword]');

        if (target) {
            $('#benefit-search-input').value = target.dataset.keyword;
            runSearch();
            return;
        }

        if (event.target.closest('#filter-reset, #empty-reset')) {
            reset();
            return;
        }

        target = event.target.closest('[data-hashtag]');
        if (target) {
            $('#benefit-search-input').value = target.dataset.hashtag;
            runSearch();
            return;
        }

        target = event.target.closest('[data-bookmark]');
        if (target) {
            const benefit = benefits.find((item) => item.id === Number(target.dataset.bookmark));

            benefit.bookmarked = !benefit.bookmarked;
            toast(benefit.bookmarked ? '관심 혜택은 로그인 후 저장할 수 있어요.' : '관심 저장을 해제했어요.');
            render();
            return;
        }

        if (event.target.closest('[data-detail]')) {
            toast('상세 페이지는 현재 준비 중입니다.');
            return;
        }

        if (event.target.closest('[data-unavailable-page]')) {
            event.preventDefault();
            toast('이 페이지는 현재 준비 중입니다.');
            return;
        }

        target = event.target.closest('[data-font-size]');
        if (target) {
            const currentScale = Number(localStorage.getItem('bokdeongi-font-scale') || 1);
            const adjustment = target.dataset.fontSize === 'increase' ? 0.1 : -0.1;
            const nextScale = Math.min(1.2, Math.max(0.9, Number((currentScale + adjustment).toFixed(2))));

            localStorage.setItem('bokdeongi-font-scale', nextScale);
            document.documentElement.style.setProperty('--font-scale', nextScale);
            return;
        }

        if (event.target.closest('[data-theme-toggle]')) {
            theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = theme;
            localStorage.setItem('bokdeongi-theme', theme);
            themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
            themeButton.setAttribute('aria-pressed', theme === 'dark');
            return;
        }

        target = event.target.closest('.menu-toggle');
        if (target) {
            const navigation = $('#site-navigation');
            const open = !navigation.classList.contains('is-open');

            navigation.classList.toggle('is-open', open);
            target.setAttribute('aria-expanded', open);
            target.setAttribute('aria-label', open ? '메뉴 닫기' : '메뉴 열기');
        }
    });
}

document.addEventListener('DOMContentLoaded', setup);
