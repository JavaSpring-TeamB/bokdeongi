const reviewDocuments = [
    {
        id: 'resident',
        name: '주민등록등본',
        fileName: '주민등록등본.pdf',
        required: true,
        status: '검토 통과',
        result: '발급일 확인 완료',
        reason: '제출된 서류에서 필요한 거주지 및 세대 정보를 확인했어요.',
        action: '추가 조치가 필요하지 않아요.',
        alternative: '해당 없음'
    },
    {
        id: 'income',
        name: '소득금액증명',
        fileName: '소득금액증명.jpg',
        required: true,
        status: '보완 필요',
        result: '일부 정보 인식 실패',
        reason: '문서 내 숫자 영역이 흐려 OCR 인식이 어려워요.',
        action: '원본 PDF 또는 선명한 이미지로 다시 업로드해 주세요.',
        alternative: '불가'
    },
    {
        id: 'family',
        name: '가족관계증명서',
        fileName: '가족관계증명서.pdf',
        required: true,
        status: '보완 필요',
        result: '발급일 기준 초과',
        reason: '확인된 발급일이 공고에서 요구하는 기준을 초과했어요.',
        action: '최근에 발급한 가족관계증명서를 다시 업로드해 주세요.',
        alternative: '불가'
    }
];

let selectedDocumentId = 'income';
let isReviewing = false;

function isDevelopmentTestMode() {
    const localPreview = window.location.protocol === 'file:' || ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname);
    return localPreview && new URLSearchParams(window.location.search).get('devTest') === '1';
}

const reviewStorageKey = 'bokdeongi-review-file-meta';
const $ = (selector) => document.querySelector(selector);
const assistantSteps = [
    ['자격 확인', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>'],
    ['서류 준비', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>'],
    ['서류 검토', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>'],
    ['신청 안내', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>']
];

function getSelectedDocument() {
    return reviewDocuments.find((document) => document.id === selectedDocumentId);
}

function getStatusClass(status) {
    if (status === '검토 통과') {
        return 'success';
    }

    if (status === '보완 필요') {
        return 'danger';
    }

    return 'pending';
}

function renderSteps() {
    return '<div class="assist-steps assist-steps--icon" aria-label="신청 도우미 진행 단계">' + assistantSteps.map((step, index) => '<div class="assist-step ' + (index < 2 ? 'is-complete' : index === 2 ? 'is-current' : '') + '"><span class="assist-step__icon">' + step[1] + '</span><span class="assist-step__label">' + step[0] + '</span></div>').join('') + '</div>';
}

function getSummary() {
    const requiredDocuments = reviewDocuments.filter((document) => document.required);
    const passed = requiredDocuments.filter((document) => document.status === '검토 통과').length;
    const needsFix = requiredDocuments.filter((document) => document.status === '보완 필요').length;
    const reviewing = requiredDocuments.filter((document) => document.status === '검토 중').length;

    return { requiredDocuments, passed, needsFix, reviewing, canProceed: passed === requiredDocuments.length };
}

function getSummaryMessage(summary) {
    if (summary.reviewing > 0) {
        return {
            className: 'is-pending',
            text: '서류를 검토하고 있습니다. 검토 완료 후 결과를 확인해 주세요.'
        };
    }

    if (summary.canProceed) {
        return {
            className: '',
            text: '필수 서류 검토가 완료되었습니다. 다음 단계로 이동할 수 있어요.'
        };
    }

    return {
        className: 'is-warning',
        text: '보완이 필요한 서류가 있습니다. 해당 서류를 다시 제출해 주세요.'
    };
}

function renderDocumentItem(document) {
    const selectedClass = document.id === selectedDocumentId ? 'is-selected' : '';
    const action = document.status === '보완 필요' ? '다시 업로드' : document.status === '검토 통과' ? '결과 보기' : '결과 보기';

    return '<div class="review-document ' + selectedClass + '">' +
        '<button class="review-document__select" type="button" data-document-id="' + document.id + '">' +
        '<span><h3>' + document.name + '</h3><p>' + document.fileName + ' · ' + document.result + '</p></span>' +
        '</button>' +
        '<button class="review-document__status" type="button" data-document-id="' + document.id + '"><span class="review-document__result"><span class="status-badge ' + getStatusClass(document.status) + '">' + document.status + '</span></span></button>' +
        '<button class="bk-btn bk-btn--secondary bk-btn--sm review-document__action" type="button" data-row-action="' + document.id + '">' + action + '</button>' +
        '</div>';
}

function render() {
    const selected = getSelectedDocument();
    const summary = getSummary();
    const summaryMessage = getSummaryMessage(summary);
    const developmentTestMode = isDevelopmentTestMode();
    const actionLabel = summary.canProceed || developmentTestMode ? '신청 안내로 이동' : '보완 후 다시 검토';
    const queryString = window.location.search;

    $('#assist-content').innerHTML = renderSteps() +
        '<div class="assist-info"><strong aria-hidden="true">i</strong><span>업로드한 서류를 분석해 검토 결과를 안내해 드려요. 문제가 있는 서류는 보완 후 다시 제출할 수 있어요.</span></div>' +
        '<div class="review-layout">' +
        '<section class="review-card" aria-labelledby="submitted-documents-title">' +
        '<div class="review-heading"><h2 id="submitted-documents-title">제출 서류 목록</h2>' +
        '<div class="review-counts"><span class="review-counts__pass">통과 ' + summary.passed + '건</span><span class="review-counts__fix">보완 필요 ' + summary.needsFix + '건</span></div></div>' +
        reviewDocuments.map(renderDocumentItem).join('') +
        '</section>' +
        '<aside class="review-summary-card">' +
        '<section class="review-detail-section" aria-labelledby="review-detail-title">' +
        '<h2 id="review-detail-title" class="review-detail-title">선택한 서류 상세</h2>' +
        '<p class="review-detail__file">' + selected.fileName + '</p>' +
        '<dl class="review-detail-list">' +
        '<div><dt>현재 상태</dt><dd><span class="status-badge ' + getStatusClass(selected.status) + '">' + selected.status + '</span></dd></div>' +
        '<div><dt>검토 결과</dt><dd>' + selected.result + '</dd></div>' +
        '<div><dt>세부 사유</dt><dd>' + selected.reason + '</dd></div>' +
        '<div><dt>권장 조치</dt><dd>' + selected.action + '</dd></div>' +
        '<div><dt>대체 가능</dt><dd>' + selected.alternative + '</dd></div>' +
        '</dl>' +
        '<div class="review-upload-actions">' +
        '<button class="bk-btn bk-btn--secondary bk-btn--md" type="button" data-upload ' + (isReviewing ? 'disabled' : '') + '>' + (isReviewing ? '검토 요청 중...' : '다시 업로드') + '</button>' +
        '<a class="bk-btn bk-btn--secondary bk-btn--md" href="assist-documents.html?id=' + encodeURIComponent(selected.id) + '">발급 안내</a>' +
        '</div>' +
        '</section>' +
        '<section class="review-summary-section" aria-labelledby="review-summary-title">' +
        '<h2 id="review-summary-title" class="review-detail-title">검토 결과 요약</h2>' +
        '<dl class="review-summary">' +
        '<div><dt>검토 통과</dt><dd>' + summary.passed + '건</dd></div>' +
        '<div><dt>보완 필요</dt><dd>' + summary.needsFix + '건</dd></div>' +
        '<div><dt>다음 단계 이동 가능</dt><dd>' + (summary.canProceed ? '가능' : '보완 후 가능') + '</dd></div>' +
        '</dl>' +
        '<p class="review-summary-message ' + summaryMessage.className + '">' + summaryMessage.text + '</p>' +
        '<p class="review-api-note">검토 결과는 예시 데이터이며, 실제 OCR 검토 API 연동 후 결과로 교체됩니다.</p>' +
        '</section>' +
        '</aside></div>' +
        '<p class="review-live-region" aria-live="polite"></p>' +
        '<div class="assist-actions">' +
        '<a class="bk-btn bk-btn--outline bk-btn--md" href="assist-documents.html' + queryString + '">이전 단계</a>' +
        '<button class="bk-btn bk-btn--primary bk-btn--md" type="button" data-next ' + (summary.canProceed || developmentTestMode ? '' : 'aria-disabled="true"') + '>' + actionLabel + '</button>' +
        '</div>';
}

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;

    window.setTimeout(() => {
        toast.hidden = true;
    }, 3200);
}

function saveFileMeta(document, file) {
    const existing = JSON.parse(sessionStorage.getItem(reviewStorageKey) || '{}');

    existing[document.id] = {
        name: file.name,
        size: file.size,
        lastModified: file.lastModified
    };

    sessionStorage.setItem(reviewStorageKey, JSON.stringify(existing));
}

function validateFile(file) {
    const allowedTypes = ['application/pdf', 'image/jpeg', 'image/png'];
    const allowedExtension = /\.(pdf|jpe?g|png)$/i.test(file.name);

    if (!allowedTypes.includes(file.type) && !allowedExtension) {
        return 'PDF, JPG, JPEG, PNG 파일만 업로드할 수 있어요.';
    }

    if (file.size > 10 * 1024 * 1024) {
        return '파일 크기는 10MB 이하로 업로드해 주세요.';
    }

    return '';
}

function handleFileSelection(file) {
    const validationMessage = validateFile(file);

    if (validationMessage) {
        showToast(validationMessage);
        return;
    }

    const document = getSelectedDocument();
    saveFileMeta(document, file);
    document.fileName = file.name;
    document.status = '검토 중';
    document.result = '검토 요청 대기';
    document.reason = '선택한 파일은 브라우저에 원본을 저장하지 않고, 실제 OCR 검토 API 연동을 기다리고 있어요.';
    document.action = '검토 결과가 준비되면 다시 확인해 주세요.';
    isReviewing = true;
    render();
    showToast('파일을 선택했습니다. 실제 검토 API 연동이 필요합니다.');

    window.setTimeout(() => {
        isReviewing = false;
        render();
    }, 900);
}

function handleNextStep() {
    const summary = getSummary();

    if (!summary.canProceed) {
        if (isDevelopmentTestMode()) {
            showToast('개발 테스트 모드로 신청 안내 화면을 표시합니다. 실제 OCR 검토 결과는 저장하지 않습니다.');
            window.location.href = 'assist-guide.html' + window.location.search;
            return;
        }
        const firstNeedsFix = reviewDocuments.find((document) => document.status === '보완 필요');

        if (firstNeedsFix) {
            selectedDocumentId = firstNeedsFix.id;
            render();
            showToast('보완이 필요한 서류를 선택했습니다. 파일을 다시 업로드해 주세요.');
            return;
        }

        showToast('서류 검토가 완료될 때까지 다음 단계로 이동할 수 없어요.');
        return;
    }

    window.location.href = 'assist-guide.html' + window.location.search;
}

function setupInteractions() {
    document.addEventListener('click', (event) => {
        const documentButton = event.target.closest('[data-document-id]');

        if (documentButton) {
            selectedDocumentId = documentButton.dataset.documentId;
            render();
            return;
        }

        const rowAction = event.target.closest('[data-row-action]');

        if (rowAction) {
            selectedDocumentId = rowAction.dataset.rowAction;

            if (getSelectedDocument().status === '보완 필요') {
                $('#review-file-input').click();
            } else {
                render();
            }

            return;
        }

        if (event.target.closest('[data-upload]')) {
            $('#review-file-input').click();
            return;
        }

        if (event.target.closest('[data-next]')) {
            handleNextStep();
        }
    });

    $('#review-file-input').addEventListener('change', (event) => {
        const file = event.target.files[0];

        if (file) {
            handleFileSelection(file);
        }

        event.target.value = '';
    });
}

document.addEventListener('DOMContentLoaded', () => {
    render();
    setupInteractions();
});
