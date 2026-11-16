const guideSampleData = {
    benefitName: '청년 월세 지원',
    benefitDescription: '청년의 주거비 부담을 덜어주는 월세 지원 사업',
    eligibilityStatus: '신청 가능',
    applicationPeriod: '2026년 12월 31일(목) 23:59까지',
    applicationMode: 'both',
    officialApplyUrl: null,
    reviewedDocuments: [
        { name: '주민등록등본', status: '검토 통과' },
        { name: '가족관계증명서', status: '검토 통과' },
        { name: '소득금액증명', status: '검토 통과' }
    ],
    onlineProcess: [
        { label: '정부24 접속', icon: 'monitor' },
        { label: '로그인', icon: 'user' },
        { label: '신청서 작성', icon: 'document' },
        { label: '서류 첨부', icon: 'clip' },
        { label: '신청 완료', icon: 'check' }
    ]
};

const guideStorageKey = 'bokdeongi-assist-guide-snapshot';
const applicationStatusPath = 'application-status.html';
const $ = (selector) => document.querySelector(selector);

function getGuideData() {
    try {
        const savedData = sessionStorage.getItem(guideStorageKey);

        if (!savedData) {
            return guideSampleData;
        }

        return { ...guideSampleData, ...JSON.parse(savedData) };
    } catch (error) {
        return guideSampleData;
    }
}

function processIcon(name) {
    const paths = {
        monitor: '<rect x="3" y="4" width="18" height="12" rx="1"/><path d="M8 20h8M12 16v4"/>',
        user: '<circle cx="12" cy="8" r="3.25"/><path d="M5.5 20c.8-3.2 3-5 6.5-5s5.7 1.8 6.5 5"/>',
        document: '<path d="M7 3.5h7l3 3v14H7z"/><path d="M14 3.5v4h3M9.5 12h5M9.5 15h5"/>',
        clip: '<path d="m8.5 12.5 5.7-5.7a2.75 2.75 0 0 1 3.9 3.9l-7 7a4.1 4.1 0 0 1-5.8-5.8l6.6-6.6"/>',
        check: '<circle cx="12" cy="12" r="8.5"/><path d="m8.4 12 2.3 2.4 4.9-5"/>'
    };

    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + paths[name] + '</svg>';
}

function renderGuideData(data) {
    $('[data-benefit-name]').textContent = data.benefitName;
    $('[data-benefit-description]').textContent = data.benefitDescription;
    $('[data-eligibility-status]').textContent = data.eligibilityStatus;
    $('[data-application-period]').textContent = data.applicationPeriod;
    $('[data-application-method]').textContent = data.applicationMode === 'online' ? '온라인 신청' : data.applicationMode === 'visit' ? '방문 신청' : '온라인 신청 또는 방문 신청';

    const passedDocuments = data.reviewedDocuments.filter((reviewDocument) => reviewDocument.status === '검토 통과');
    const reviewStatus = $('[data-review-status]');
    reviewStatus.textContent = '검토 완료 ' + passedDocuments.length + '건 (보완 필요 없음)';
    reviewStatus.classList.add('guide-summary-list__success');
    $('[data-reviewed-document-count]').textContent = '(' + passedDocuments.length + '건)';

    const documentList = $('[data-reviewed-documents]');
    documentList.replaceChildren(...passedDocuments.map((reviewDocument) => {
        const item = document.createElement('li');
        item.textContent = reviewDocument.name;
        return item;
    }));

    $('[data-visit-documents]').textContent = passedDocuments.length > 0
        ? passedDocuments.map((reviewDocument) => reviewDocument.name).join(', ') + '을(를) 지참해 주세요.'
        : '검토 완료한 서류를 지참해 주세요.';

    const processList = $('[data-online-process]');
    processList.replaceChildren(...data.onlineProcess.map((process) => {
        const item = document.createElement('li');
        const icon = document.createElement('span');
        const label = document.createElement('span');

        icon.className = 'guide-process__icon';
        icon.innerHTML = processIcon(process.icon);
        label.textContent = process.label;
        item.append(icon, label);

        return item;
    }));

    configureApplicationMethods(data);
    configureOfficialApplyLink(data.officialApplyUrl);
}

function configureApplicationMethods(data) {
    const visitTab = $('#visit-tab');
    const onlineTab = $('#online-tab');

    if (data.applicationMode === 'online') {
        visitTab.disabled = true;
        visitTab.textContent = '방문 신청 불가';
    }

    if (data.applicationMode === 'visit') {
        onlineTab.disabled = true;
        onlineTab.textContent = '온라인 신청 불가';
        selectApplicationMethod('visit');
    }
}

function configureOfficialApplyLink(officialApplyUrl) {
    const applyButton = $('[data-official-apply]');
    const help = $('[data-official-apply-help]');

    if (!officialApplyUrl) {
        applyButton.disabled = true;
        help.textContent = '확인된 공식 온라인 접수처가 없어 공고에서 확인해 주세요.';
        return;
    }

    applyButton.disabled = false;
    applyButton.dataset.url = officialApplyUrl;
    help.textContent = '공식 신청 사이트가 새 탭에서 열립니다.';
}

function selectApplicationMethod(method) {
    const selectedTab = $('#' + method + '-tab');
    const selectedPanel = $('#' + method + '-panel');

    if (!selectedTab || selectedTab.disabled) {
        return;
    }

    document.querySelectorAll('[data-guide-tab]').forEach((tab) => {
        const isSelected = tab === selectedTab;
        tab.classList.toggle('is-active', isSelected);
        tab.setAttribute('aria-selected', String(isSelected));
    });

    document.querySelectorAll('.guide-method-panel').forEach((panel) => {
        panel.hidden = panel !== selectedPanel;
    });
}

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;

    window.setTimeout(() => {
        toast.hidden = true;
    }, 3200);
}

async function saveApplicationCompletion(button) {
    if (button.disabled) {
        return;
    }

    const saveApplicationStatus = window.bokdeongiApplicationStatusSaver;

    if (typeof saveApplicationStatus !== 'function') {
        showToast('신청 완료 저장 API를 준비 중입니다. 실제 신청 완료 여부는 공식 접수처에서 확인해 주세요.');
        return;
    }

    button.disabled = true;
    button.textContent = '저장 중...';

    try {
        await saveApplicationStatus();
        window.location.href = applicationStatusPath;
    } catch (error) {
        button.disabled = false;
        button.textContent = '신청했습니다 ✓';
        showToast('신청 완료를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
}

function setupInteractions() {
    $('[data-previous-step]').href = 'assist-review.html' + window.location.search;

    document.addEventListener('click', (event) => {
        const tab = event.target.closest('[data-guide-tab]');

        if (tab) {
            selectApplicationMethod(tab.dataset.guideTab);
            return;
        }

        const applyButton = event.target.closest('[data-official-apply]');

        if (applyButton && !applyButton.disabled && applyButton.dataset.url) {
            window.open(applyButton.dataset.url, '_blank', 'noopener,noreferrer');
            return;
        }

        const completeButton = event.target.closest('[data-application-complete]');

        if (completeButton) {
            saveApplicationCompletion(completeButton);
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    renderGuideData(getGuideData());
    setupInteractions();
});
