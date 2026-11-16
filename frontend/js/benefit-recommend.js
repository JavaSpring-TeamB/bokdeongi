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

function renderStatus(status) {
    const data = recommendationData[status];
    const banner = data.banner;

    document.body.dataset.status = status;
    $('[data-banner-prefix]').textContent = banner.prefix;
    $('[data-banner-emphasis]').textContent = banner.emphasis;
    $('[data-banner-suffix]').textContent = banner.suffix;
    $('[data-banner-description]').textContent = banner.description;
    $('[data-result-count]').textContent = String(data.cards.length);
    $('[data-recommend-list]').innerHTML = data.cards.map((card) => renderCard(card, status)).join('');
    $('#recommend-results').setAttribute('aria-labelledby', status + '-tab');

    document.querySelectorAll('[data-status-tab]').forEach((tab) => {
        const isSelected = tab.dataset.statusTab === status;
        tab.classList.toggle('is-active', isSelected);
        tab.setAttribute('aria-selected', String(isSelected));
    });
}

function renderTabCounts() {
    document.querySelectorAll('[data-tab-count]').forEach((element) => {
        const count = recommendationData[element.dataset.tabCount].cards.length;
        element.textContent = '(' + count + ')';
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

function setupHeaderInteractions() {
    let theme = localStorage.getItem('bokdeongi-theme') || 'light';
    const themeButton = $('[data-theme-toggle]');

    document.documentElement.dataset.theme = theme;
    document.documentElement.style.setProperty('--font-scale', localStorage.getItem('bokdeongi-font-scale') || 1);
    themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
    themeButton.setAttribute('aria-pressed', String(theme === 'dark'));

    document.addEventListener('click', (event) => {
        const unavailable = event.target.closest('[data-unavailable-page]');
        if (unavailable) {
            event.preventDefault();
            showToast('이 페이지는 현재 준비 중입니다.');
            return;
        }

        const fontSize = event.target.closest('[data-font-size]');
        if (fontSize) {
            const current = Number(localStorage.getItem('bokdeongi-font-scale') || 1);
            const change = fontSize.dataset.fontSize === 'increase' ? .1 : -.1;
            const scale = Math.min(1.2, Math.max(.9, Number((current + change).toFixed(2))));
            localStorage.setItem('bokdeongi-font-scale', scale);
            document.documentElement.style.setProperty('--font-scale', scale);
            return;
        }

        if (event.target.closest('[data-theme-toggle]')) {
            theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = theme;
            localStorage.setItem('bokdeongi-theme', theme);
            themeButton.textContent = theme === 'dark' ? '라이트 모드' : '다크 모드';
            themeButton.setAttribute('aria-pressed', String(theme === 'dark'));
            return;
        }

        const menu = event.target.closest('.menu-toggle');
        if (menu) {
            const navigation = $('#site-navigation');
            const isOpen = navigation.classList.toggle('is-open');
            menu.setAttribute('aria-expanded', String(isOpen));
            menu.setAttribute('aria-label', isOpen ? '메뉴 닫기' : '메뉴 열기');
        }
    });
}

function setupInteractions() {
    document.addEventListener('click', (event) => {
        const tab = event.target.closest('[data-status-tab]');
        if (tab) {
            selectedStatus = tab.dataset.statusTab;
            renderStatus(selectedStatus);
            return;
        }

        if (event.target.closest('[data-bookmark]')) {
            showToast('관심 저장 기능은 API 연동 후 사용할 수 있어요.');
            return;
        }

        if (event.target.closest('[data-condition-check]')) {
            showToast('조건 확인과 재판정 기능은 추후 연동 예정입니다.');
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    renderTabCounts();
    renderStatus(selectedStatus);
    setupHeaderInteractions();
    setupInteractions();
});
