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

const stepNames = ['자격 확인', '서류 준비', '서류 검토', '신청 안내'];
const stepIcons = [
    '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>',
    '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>',
    '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>',
    '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>'
];
const $ = (selector) => document.querySelector(selector);
let selectedStatus = 'all';
let currentPage = 1;
const PAGE_SIZE = 4;

function getApplications(status) {
    return status === 'all'
        ? mockApplications
        : mockApplications.filter((application) => application.status === status);
}

function renderStepper(application) {
    return '<div class="assist-steps assist-steps--icon application-card-stepper" aria-label="' + application.title + ' 신청 진행 단계">' +
        stepNames.map((name, index) => {
            let className = '';

            if (application.status === 'completed' || index < application.currentStep) {
                className = 'is-complete';
            } else if (index === application.currentStep) {
                className = 'is-current';
            }

            return '<div class="assist-step ' + className + '"><span class="assist-step__icon">' + stepIcons[index] + '</span><span class="assist-step__label">' + name + '</span></div>';
        }).join('') + '</div>';
}

function renderDeadline(application) {
    const dDay = application.dDay ? '<span class="application-card__d-day">' + application.dDay + '</span>' : '';
    return '<p class="application-card__deadline"><span>' + application.deadlineLabel + ' ' + application.deadline + '</span>' + dDay + '</p>';
}

function renderCard(application) {
    const resumeLink = application.resumePage
        ? '<a class="bk-btn bk-btn--primary bk-btn--md" href="' + application.resumePage + '?applicationId=' + encodeURIComponent(application.id) + '">이어서 작성하기</a>'
        : '';

    return '<article class="application-card">' +
        '<div class="application-card__benefit">' +
        '<span class="application-state-badge application-state-badge--' + application.status + '">' + application.statusLabel + '</span>' +
        '<h3>' + application.title + '</h3><p class="application-card__description">' + application.description + '</p>' + renderDeadline(application) +
        '</div>' +
        '<div class="application-card__progress">' + renderStepper(application) +
        '<div class="application-progress-row"><span>진행률</span><div class="application-progress-track" style="--progress:' + application.progress + '%"><span></span></div><strong>' + application.progress + '%</strong></div>' +
        '</div>' +
        '<div class="application-card__action"><section class="application-card__task"><h4>' + application.taskTitle + '</h4><p>' + application.task + '</p></section>' +
        '<div class="application-card__buttons">' + resumeLink + '<a class="bk-btn bk-btn--outline bk-btn--md" href="benefit-detail.html?id=' + application.detailId + '">상세 보기</a></div></div>' +
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
    currentPage = window.BokdeongiPagination.clampPage(applications.length, currentPage, PAGE_SIZE);
    const start = (currentPage - 1) * PAGE_SIZE;
    $('[data-application-list]').innerHTML = applications.slice(start, start + PAGE_SIZE).map(renderCard).join('');
    $('[data-application-pagination]').innerHTML = window.BokdeongiPagination.markup({
        totalCount: applications.length,
        currentPage,
        pageSize: PAGE_SIZE,
        label: '신청 현황'
    });
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

function setupInteractions() {
    document.addEventListener('click', (event) => {
        const tab = event.target.closest('[data-status-tab]');
        if (tab) {
            selectedStatus = tab.dataset.statusTab;
            currentPage = 1;
            renderApplications();
            return;
        }

        if (event.target.closest('[data-calendar-save]')) {
            showToast('일정 저장 기능은 캘린더 연동 또는 .ics 기능 준비 후 사용할 수 있어요.');
        }
    });
    document.addEventListener('bokdeongi:pagination', (event) => {
        currentPage = event.detail.page;
        renderApplications();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    renderSummary();
    renderTabCounts();
    renderApplications();
    setupInteractions();
});
