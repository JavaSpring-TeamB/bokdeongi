const translationResult = {
    originalText: '신청인은 공고일 기준 부산광역시에 주민등록을 두고 실제 거주 중이어야 하며, 가구의 월평균 소득이 기준 중위소득 150% 이하이고 기타 세부 요건을 모두 충족하여야 한다.',
    summary: '부산에 살고 있고, 가구 소득이 기준에 맞으면 신청할 수 있어요.',
    sections: [
        {
            id: 'residence',
            category: '거주지 조건',
            original: '공고일 기준 부산광역시에 주민등록을 두고 실제 거주 중',
            simplified: '공고가 나온 날 부산에 주민등록이 되어 있고 실제로 살고 있어야 해요.',
            action: '거주지 조건을 확인해 주세요.',
            caution: '공고일을 기준으로 거주 조건을 확인해야 해요.'
        },
        {
            id: 'income',
            category: '소득 조건',
            original: '가구의 월평균 소득이 기준 중위소득 150% 이하',
            simplified: '가구의 한 달 평균 소득이 기준 중위소득의 150% 이하여야 해요.',
            action: '가구 소득 기준을 확인해 주세요.',
            caution: '소득 기준의 구체적인 계산 방법은 해당 공고를 확인해 주세요.'
        },
        {
            id: 'other',
            category: '기타 조건',
            original: '기타 세부 요건을 모두 충족',
            simplified: '그 밖에 정해진 조건도 모두 맞아야 해요.',
            action: '공고에 명시된 기타 조건도 확인해 주세요.',
            caution: '원문에 없는 신청 기한이나 지원 금액을 임의로 만들 수 없어요.'
        }
    ]
};

const $ = (selector) => document.querySelector(selector);
let selectedInputMode = 'text';
let selectedResultTab = 'simple';
let hasCurrentResult = true;

function getCopyText() {
    if (!hasCurrentResult) {
        return '';
    }

    if (selectedResultTab === 'simple') {
        return translationResult.summary + '\n\n' + translationResult.sections.map((section) => section.simplified).join('\n');
    }

    if (selectedResultTab === 'compare') {
        return translationResult.sections.map((section) => '[원문] ' + section.original + '\n[쉬운 표현] ' + section.simplified).join('\n\n');
    }

    return translationResult.sections.map((section) => '[' + section.category + ']\n원문 근거: ' + section.original + '\n쉬운 해석: ' + section.simplified).join('\n\n');
}

function simpleResultMarkup() {
    const conditions = translationResult.sections.map((section) => '<li>' + section.simplified + '</li>').join('');
    const actions = translationResult.sections.map((section) => '<li>' + section.action + '</li>').join('');
    const cautions = translationResult.sections.map((section) => '<li>' + section.caution + '</li>').join('');

    return '<section class="translation-summary">' + translationResult.summary + '</section>' +
        '<div class="translation-section-list">' +
        '<section class="translation-section"><h3>지원 내용 및 조건</h3><ul>' + conditions + '</ul></section>' +
        '<section class="translation-section"><h3>해야 할 일</h3><ul>' + actions + '</ul></section>' +
        '<section class="translation-section"><h3>주의할 점</h3><ul>' + cautions + '</ul></section>' +
        '</div>';
}

function comparisonResultMarkup() {
    const rows = translationResult.sections.map((section) => '<article class="comparison-row"><div class="comparison-text">' + section.original + '</div><span class="comparison-arrow" aria-hidden="true">→</span><div class="comparison-text comparison-text--simple">' + section.simplified + '</div></article>').join('');
    const keyPoints = translationResult.sections.map((section) => '<span>' + section.category + ' 확인</span>').join('');

    return '<p class="comparison-intro">원문과 쉬운 말을 문단별로 비교해볼 수 있어요. 핵심 내용은 쉽게 유지하고, 더 이해하기 쉬운 표현으로 바꿨어요.</p>' +
        '<div class="comparison-headings"><span>원문 (어려운 표현)</span><span></span><span>쉬운 말 (바꾼 표현)</span></div>' + rows +
        '<div class="translation-key-points"><strong>핵심 정리</strong>' + keyPoints + '</div>';
}

function evidenceResultMarkup() {
    const evidenceItems = translationResult.sections.map((section) => '<article class="evidence-item"><h3>' + section.category + '</h3><div class="evidence-item__content"><div class="evidence-item__original"><small>원문 근거</small>' + section.original + '</div><div class="evidence-item__simple"><small>쉬운 해석</small>' + section.simplified + '</div></div></article>').join('');

    return '<p class="evidence-intro">쉬운 해석은 원문 내용을 바탕으로 작성되었습니다.</p><div class="evidence-list">' + evidenceItems + '</div>';
}

function renderResult() {
    const result = $('#translation-result');
    const copyButton = $('[data-copy-result]');

    if (!hasCurrentResult) {
        result.innerHTML = '<div class="translator-empty-state">입력한 원문에 대한 결과가 없어요.<br>예시 원문을 그대로 입력한 뒤 다시 시도하거나, AI 번역 기능 연동 후 이용해 주세요.</div>';
        copyButton.disabled = true;
    } else {
        const markup = selectedResultTab === 'simple'
            ? simpleResultMarkup()
            : selectedResultTab === 'compare'
                ? comparisonResultMarkup()
                : evidenceResultMarkup();

        result.innerHTML = markup;
        copyButton.disabled = false;
    }

    result.setAttribute('aria-labelledby', selectedResultTab + '-tab');
    document.querySelectorAll('[data-result-tab]').forEach((tab) => {
        const isSelected = tab.dataset.resultTab === selectedResultTab;
        tab.classList.toggle('is-active', isSelected);
        tab.setAttribute('aria-selected', String(isSelected));
        tab.disabled = !hasCurrentResult;
    });
}

function updateInputPanels() {
    document.querySelectorAll('.translator-input-panel').forEach((panel) => {
        panel.hidden = panel.id !== selectedInputMode + '-input-panel';
    });
}

function selectInputMode(mode) {
    selectedInputMode = mode;
    document.querySelectorAll('[data-input-mode]').forEach((tab) => {
        const isSelected = tab.dataset.inputMode === mode;
        tab.classList.toggle('is-active', isSelected);
        tab.setAttribute('aria-selected', String(isSelected));
    });

    updateInputPanels();

    if (mode !== 'text') {
        hasCurrentResult = false;
        renderResult();
    }

    updateSubmitState();
}

function updateSubmitState() {
    const button = $('[data-translate]');
    const sourceText = $('#source-text').value.trim();
    button.disabled = selectedInputMode === 'text' && sourceText.length === 0;
}

function updateSourceTextState() {
    const sourceText = $('#source-text').value;
    $('[data-character-count]').textContent = String(sourceText.length);

    if (sourceText !== translationResult.originalText) {
        hasCurrentResult = false;
        renderResult();
    }

    updateSubmitState();
}

function updateSelectedFileName(input) {
    const target = $('[data-file-name="' + input.id.replace('-input', '') + '"]');
    if (target) target.textContent = input.files.length ? input.files[0].name : '선택된 파일 없음';
}

function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.hidden = false;

    window.setTimeout(() => {
        toast.hidden = true;
    }, 2800);
}

async function copyResult() {
    const text = getCopyText();

    if (!text) {
        showToast('복사할 결과가 없어요.');
        return;
    }

    try {
        await navigator.clipboard.writeText(text);
        showToast('현재 결과를 복사했어요.');
    } catch (error) {
        showToast('결과 복사에 실패했어요. 직접 선택해 복사해 주세요.');
    }
}

function translateText() {
    if (selectedInputMode !== 'text') {
        showToast('이미지와 PDF 분석 기능은 추후 연동 예정입니다.');
        return;
    }

    const sourceText = $('#source-text').value.trim();
    if (!sourceText) {
        showToast('원문을 입력해 주세요.');
        return;
    }

    if (sourceText !== translationResult.originalText) {
        hasCurrentResult = false;
        renderResult();
        showToast('입력한 원문은 AI 번역 기능 연동 후 분석할 수 있어요.');
        return;
    }

    hasCurrentResult = true;
    renderResult();
    showToast('예시 원문에 대한 쉬운 말 결과를 표시했어요.');
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
    $('#source-text').addEventListener('input', updateSourceTextState);
    document.querySelectorAll('.translator-file-input').forEach((input) => {
        input.addEventListener('change', () => updateSelectedFileName(input));
    });

    document.addEventListener('click', (event) => {
        const inputTab = event.target.closest('[data-input-mode]');
        if (inputTab) {
            selectInputMode(inputTab.dataset.inputMode);
            return;
        }

        const resultTab = event.target.closest('[data-result-tab]');
        if (resultTab && hasCurrentResult) {
            selectedResultTab = resultTab.dataset.resultTab;
            renderResult();
            return;
        }

        if (event.target.closest('[data-translate]')) {
            translateText();
            return;
        }

        if (event.target.closest('[data-copy-result]')) {
            copyResult();
        }
    });
}

document.addEventListener('DOMContentLoaded', () => {
    $('#source-text').value = translationResult.originalText;
    updateInputPanels();
    updateSourceTextState();
    renderResult();
    setupHeaderInteractions();
    setupInteractions();
});
