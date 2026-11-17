const eligibilityItems = [
    { name: '연령', detail: '만 24세 (요건: 만 19~34세)', status: '충족', evidence: '공고문 연령 요건은 실제 API 연동 후 표시됩니다.' },
    { name: '거주지역', detail: '부산광역시 부산진구 (요건: 부산 거주)', status: '충족', evidence: '공고문 거주지역 요건은 실제 API 연동 후 표시됩니다.' },
    { name: '무주택 여부', detail: '프로필 정보 없음', status: '확인 필요', evidence: '' },
    { name: '소득 기준', detail: '중위소득 기준 충족', status: '충족', evidence: '공고문 소득 요건은 실제 API 연동 후 표시됩니다.' }
];
const $ = (selector) => document.querySelector(selector);
const benefitId = new URLSearchParams(window.location.search).get('id') || '1';
const housingQuestionId = 'housing-status';
const housingAnswerKey = 'bokdeongi:eligibility:' + benefitId + ':' + housingQuestionId;
const assistantSteps = [
    ['자격 확인', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="4" width="12" height="16" rx="2"></rect><path d="M9 4.5h6v3H9zM9 13l2 2 4-4"></path></svg>'],
    ['서류 준비', '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="11" height="11" rx="2"></rect><path d="M9 7V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2"></path></svg>'],
    ['서류 검토', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4M3 15v4a2 2 0 0 0 2 2h4M21 15v4a2 2 0 0 1-2 2h-4"></path><path d="m8 12 2.5 2.5L16 9"></path></svg>'],
    ['신청 안내', '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 4 16 7-7 3-3 6z"></path><path d="m13 14 3-3"></path></svg>']
];

function getSavedHousingAnswer() {
    const value = localStorage.getItem(housingAnswerKey);
    return ['YES', 'NO', 'UNSURE'].includes(value) ? value : null;
}

function rows() { return eligibilityItems.map((item, index) => '<tr class="assist-table__data-row"><th scope="row">' + item.name + '</th><td>' + item.detail + '</td><td class="assist-table__status"><span class="status-badge ' + (item.status === '충족' ? 'success' : 'warning') + '">' + item.status + '</span></td><td class="assist-table__evidence"><button class="bk-btn bk-btn--tertiary bk-btn--sm text-button" type="button" aria-expanded="false" aria-controls="evidence-' + index + '" data-evidence="' + index + '">근거 보기 <span aria-hidden="true">›</span></button></td></tr><tr class="assist-evidence-row" id="evidence-' + index + '" hidden><td colspan="4"><p class="evidence"></p></td></tr>').join(''); }

function questionContent(answer) {
    if (!answer) return '<strong>현재 무주택자이신가요?</strong><fieldset><legend class="sr-only">무주택 여부</legend><label><input type="radio" name="housing" value="YES"> 예</label><label><input type="radio" name="housing" value="NO"> 아니오</label><label><input type="radio" name="housing" value="UNSURE"> 아직 몰라요</label></fieldset><p class="assist-answer-summary">답변은 이 혜택의 자격 확인에만 임시 저장됩니다.</p>';
    if (answer === 'UNSURE') return '<div class="assist-agency-guidance"><strong>기관에 확인해 보세요</strong><p>담당 기관명과 문의처 데이터가 아직 연동되지 않았어요. 최신 공고에서 담당 기관과 문의처를 확인해 주세요.</p></div>';
    return '<p class="assist-answer-summary">답변이 이 브라우저에 임시 저장되었습니다. 규칙 엔진 재판정은 서버 연동 후 반영됩니다.</p>';
}

function render() {
    const answer = getSavedHousingAnswer();
    $('#assist-content').innerHTML = '<div class="assist-steps assist-steps--icon" aria-label="신청 도우미 진행 단계">' + assistantSteps.map((step, index) => '<div class="assist-step ' + (index === 0 ? 'is-current' : '') + '"><span class="assist-step__icon">' + step[1] + '</span><span class="assist-step__label">' + step[0] + '</span></div>').join('') + '</div><div class="assist-info"><strong aria-hidden="true">i</strong><span>프로필에 입력한 정보는 자동으로 반영되며, 부족한 정보만 추가로 확인합니다.</span></div><section class="card assist-card"><h2>자격 확인 결과</h2><div class="assist-table-wrap"><table class="assist-table"><thead><tr><th>확인 항목</th><th>내 정보 및 판정</th><th>판정 상태</th><th>근거</th></tr></thead><tbody>' + rows() + '</tbody></table></div><section class="assist-question"><h2>추가 확인이 필요한 항목</h2>' + questionContent(answer) + '</section><section class="assist-status"><h2>현재 판정: <span class="status-badge warning">추가 확인 필요</span></h2><p>' + (answer === 'UNSURE' ? '기관 확인이 필요한 답변으로, 서버 규칙 엔진의 조건부 판정 연동이 필요합니다.' : '추가 질문에 답하면 신청 가능 여부를 더 정확히 확인할 수 있어요.') + '</p></section></section><div class="assist-actions"><a class="bk-btn bk-btn--outline bk-btn--md" href="benefit-detail.html?id=' + benefitId + '">이전으로</a><a class="bk-btn bk-btn--primary bk-btn--md" href="assist-documents.html?id=' + benefitId + '">서류 준비로 이동</a></div>';
}

function setup() {
    render();
    document.addEventListener('click', (event) => {
        const button = event.target.closest('[data-evidence]');
        if (!button) return;
        const item = eligibilityItems[Number(button.dataset.evidence)];
        const evidenceRow = document.getElementById(button.getAttribute('aria-controls'));
        const copy = evidenceRow.querySelector('.evidence');
        const open = evidenceRow.hidden;
        copy.textContent = open ? (item.evidence || '공고에서 확인할 수 있는 근거가 없습니다.') : '';
        evidenceRow.hidden = !open;
        button.closest('.assist-table__data-row').classList.toggle('is-expanded', open);
        button.setAttribute('aria-expanded', String(open));
        button.innerHTML = open ? '근거 닫기' : '근거 보기 <span aria-hidden="true">›</span>';
    });
    document.addEventListener('change', (event) => {
        if (event.target.name !== 'housing') return;
        localStorage.setItem(housingAnswerKey, event.target.value);
        render();
    });
}
document.addEventListener('DOMContentLoaded', setup);
