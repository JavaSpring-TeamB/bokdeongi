const mockApplications = [
    {
        id: 'monthly-rent',
        status: 'in-progress',
        statusLabel: '진행 중',
        title: '청년 월세 지원',
        description: '청년층의 주거비 부담을 덜어주는 월세 지원 사업이에요.',
        deadlineLabel: '신청 마감',
        deadline: '2026년 11월 21일',
        dDay: 'D-3',
        progress: 60,
        currentStep: 2,
        taskTitle: '다음 할 일',
        task: '통장 사본 업로드 필요',
        resumePage: 'assist-review.html',
        detailId: 1
    },
    {
        id: 'housing-cost',
        status: 'in-progress',
        statusLabel: '진행 중',
        title: '청년 주거비 지원',
        description: '청년의 안정적인 주거를 위해 주거비를 지원하는 사업이에요.',
        deadlineLabel: '신청 마감',
        deadline: '2026년 11월 28일',
        dDay: 'D-10',
        progress: 35,
        currentStep: 1,
        taskTitle: '다음 할 일',
        task: '가족관계증명서 제출',
        resumePage: 'assist-documents.html',
        detailId: 2
    },
    {
        id: 'living-support',
        status: 'preparing',
        statusLabel: '준비 중',
        title: '청년 생활 지원',
        description: '청년의 생활 안정을 위해 기본 생활비를 지원하는 사업이에요.',
        deadlineLabel: '신청 일정',
        deadline: '상시',
        dDay: '',
        progress: 20,
        currentStep: 0,
        taskTitle: '다음 할 일',
        task: '추가 조건 확인 필요',
        resumePage: 'assist-eligibility.html',
        detailId: 8
    },
    {
        id: 'culture-support',
        status: 'completed',
        statusLabel: '완료',
        title: '청년 문화생활 지원',
        description: '청년의 문화·여가 활동을 지원하는 사업이에요.',
        deadlineLabel: '신청 완료일',
        deadline: '2026년 11월 10일',
        dDay: '',
        progress: 100,
        currentStep: 4,
        taskTitle: '후속 조치',
        task: '추가 서류 없음',
        resumePage: '',
        detailId: 11
    }
];

const stepNames = ['자격 확인', '서류 준비', '서류 검토', '신청 완료'];
const $ = (selector) => document.querySelector(selector);
let selectedStatus = 'all';

function getApplications(status) {
    return status === 'all'
        ? mockApplications
        : mockApplications.filter((application) => application.status === status);
}

function renderStepper(application) {
    return '<div class="assist-steps application-card-stepper" aria-label="' + application.title + ' 신청 진행 단계">' +
        stepNames.map((name, index) => {
            let className = '';
            let label = String(index + 1);

            if (application.status === 'completed' || index < application.currentStep) {
                className = 'is-complete';
                label = '✓';
            } else if (index === application.currentStep) {
                className = 'is-current';
            }

            return '<div class="assist-step ' + className + '" data-step="' + label + '">' + name + '</div>';
        }).join('') + '</div>';
}

function renderDeadline(application) {
    const dDay = application.dDay ? '<span class="application-card__d-day">' + application.dDay + '</span>' : '';
    return '<p class="application-card__deadline"><span>' + application.deadlineLabel + ' ' + application.deadline + '</span>' + dDay + '</p>';
}

function renderCard(application) {
    const resumeLink = application.resumePage
        ? '<a class="btn" href="' + application.resumePage + '?applicationId=' + encodeURIComponent(application.id) + '">이어서 작성하기</a>'
        : '';

    return '<article class="application-card">' +
        '<div class="application-card__benefit">' +
        '<span class="application-state-badge application-state-badge--' + application.status + '">' + application.statusLabel + '</span>' +
        '<h3>' + application.title + '</h3><p class="application-card__description">' + application.description + '</p>' + renderDeadline(application) +
        '</div>' +
        '<div class="application-card__progress">' + renderStepper(application) +
        '<div class="application-progress-row"><span>진행률 <span aria-hidden="true">i</span></span><div class="application-progress-track" style="--progress:' + application.progress + '%"><span></span></div><strong>' + application.progress + '%</strong></div>' +
        '</div>' +
        '<div class="application-card__action"><section class="application-card__task"><h4>' + application.taskTitle + '</h4><p>' + application.task + '</p></section>' +
        '<div class="application-card__buttons">' + resumeLink + '<a class="btn btn-secondary" href="benefit-detail.html?id=' + application.detailId + '">상세 보기</a></div></div>' +
        '</article>';
}

function renderSummary() {
    const statuses = ['in-progress', 'preparing', 'completed'];

    statuses.forEach((status) => {
        const count = getApplications(status).length;
        $('[data-summary-count="' + status + '"]').textContent = count + '건';
    });
}

function renderTabCounts() {
    document.querySelectorAll('[data-tab-count]').forEach((element) => {
        element.textContent = '(' + getApplications(element.dataset.tabCount).length + ')';
    });
}

function renderApplications() {
    const applications = getApplications(selectedStatus);
    $('[data-application-list]').innerHTML = applications.map(renderCard).join('');
    $('#application-list').setAttribute('aria-labelledby', selectedStatus + '-tab');

    document.querySelectorAll('[data-status-tab]').forEach((tab) => {
        const isSelected = tab.dataset.statusTab === selectedStatus;
        tab.classList.toggle('is-active', isSelected);
        tab.setAttribute('aria-selected', String(isSelected));
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
            renderApplications();
            return;
        }

        if (event.target.closest('[data-calendar-save]')) {
            showToast('일정 저장 기능은 캘린더 연동 또는 .ics 기능 준비 후 사용할 수 있어요.');
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    renderSummary();
    renderTabCounts();
    renderApplications();
    setupHeaderInteractions();
    setupInteractions();
});
