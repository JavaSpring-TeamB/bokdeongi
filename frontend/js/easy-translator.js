const $ = (selector) => document.querySelector(selector);
const MAX_TEXT_LENGTH = 5000;
const translatorState = { screen: 'IDLE', response: null, errorKind: null, errorMessage: '', lastSubmittedText: '' };
let selectedInputMode = 'text';
let selectedResultTab = 'simple';
let translateRequestId = 0;
let translateController = null;

class EasyTextRequestError extends Error {
    constructor(kind, message) { super(message); this.kind = kind; }
}

function optionalText(value) { return typeof value === 'string' && value.trim() ? value.trim() : ''; }

function validSection(section) {
    return section && typeof section === 'object'
        && typeof section.category === 'string' && typeof section.original === 'string' && typeof section.simplified === 'string'
        && (section.action === null || section.action === undefined || typeof section.action === 'string')
        && (section.caution === null || section.caution === undefined || typeof section.caution === 'string');
}

function isEasyTextResponse(payload) {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return false;
    if (!['DONE', 'FAILED'].includes(payload.status) || !optionalText(payload.summary)
        || !Array.isArray(payload.sections) || !payload.sections.every(validSection)
        || typeof payload.masked !== 'boolean' || !Number.isSafeInteger(payload.remaining) || payload.remaining < 0
        || !optionalText(payload.notice)) return false;
    return payload.status !== 'FAILED' || payload.sections.length === 0;
}

function resultIsReady() { return translatorState.screen === 'RESULT' && translatorState.response; }
function detailTabsAvailable() { return resultIsReady() && translatorState.response.status === 'DONE' && translatorState.response.sections.length > 0; }

function element(tagName, className, text) {
    const node = document.createElement(tagName);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function resultMessage(className, message, role) {
    const node = element('div', className, message);
    if (role) node.setAttribute('role', role);
    return node;
}

function appendSummary(result, response) {
    if (response.masked) result.append(element('p', 'translator-masked-notice', '개인정보를 가리고 보냈어요'));
    result.append(element('section', 'translation-summary', response.summary));
}

function appendSimpleResult(result, sections) {
    if (!sections.length) return;
    const list = element('div', 'translation-section-list');
    const groups = [
        ['지원 내용 및 조건', sections.map((section) => section.simplified).filter(optionalText)],
        ['해야 할 일', sections.map((section) => optionalText(section.action)).filter(Boolean)],
        ['주의할 점', sections.map((section) => optionalText(section.caution)).filter(Boolean)]
    ];
    groups.forEach(([heading, values]) => {
        if (!values.length) return;
        const group = element('section', 'translation-section');
        const items = element('ul');
        values.forEach((value) => items.append(element('li', '', value)));
        group.append(element('h3', '', heading), items);
        list.append(group);
    });
    result.append(list);
}

function appendComparisonResult(result, sections) {
    result.append(element('p', 'comparison-intro', '원문과 쉬운 말을 문단별로 비교해볼 수 있어요.'));
    const headings = element('div', 'comparison-headings');
    headings.append(element('span', '', '원문 (어려운 표현)'), element('span'), element('span', '', '쉬운 말 (바꾼 표현)'));
    result.append(headings);
    sections.forEach((section) => {
        const row = element('article', 'comparison-row');
        const arrow = element('span', 'comparison-arrow', '→');
        arrow.setAttribute('aria-hidden', 'true');
        row.append(element('div', 'comparison-text', section.original), arrow, element('div', 'comparison-text comparison-text--simple', section.simplified));
        result.append(row);
    });
}

function appendEvidenceResult(result, sections) {
    result.append(element('p', 'evidence-intro', '쉬운 해석은 원문 내용을 바탕으로 작성되었습니다.'));
    const list = element('div', 'evidence-list');
    sections.forEach((section) => {
        const item = element('article', 'evidence-item');
        const content = element('div', 'evidence-item__content');
        const original = element('div', 'evidence-item__original');
        original.append(element('small', '', '원문 근거'), document.createTextNode(section.original));
        const simplified = element('div', 'evidence-item__simple');
        simplified.append(element('small', '', '쉬운 해석'), document.createTextNode(section.simplified));
        content.append(original, simplified);
        item.append(element('h3', '', section.category), content);
        list.append(item);
    });
    result.append(list);
}

function appendResponseMeta(result, response) {
    result.append(
        element('p', 'translator-result-meta', '오늘 남은 횟수: ' + response.remaining + '회'),
        element('p', 'translator-result-notice', response.notice)
    );
}

function renderResult() {
    const result = $('#translation-result');
    const copyButton = $('[data-copy-result]');
    if (!result || !copyButton) return;
    result.replaceChildren();
    result.removeAttribute('aria-busy');
    copyButton.disabled = !resultIsReady();

    if (translatorState.screen === 'LOADING') {
        result.setAttribute('aria-busy', 'true');
        result.append(resultMessage('translator-empty-state', '쉬운 말로 바꾸고 있어요.', 'status'));
    } else if (translatorState.screen === 'ERROR') {
        result.append(resultMessage('translator-empty-state', translatorState.errorMessage || '쉬운 말로 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.', 'alert'));
    } else if (!resultIsReady()) {
        result.append(resultMessage('translator-empty-state', '원문을 붙여 넣고 쉬운 말로 바꾸기를 눌러 주세요.'));
    } else {
        const response = translatorState.response;
        if (!detailTabsAvailable()) selectedResultTab = 'simple';
        appendSummary(result, response);
        if (response.status === 'DONE') {
            if (selectedResultTab === 'compare') appendComparisonResult(result, response.sections);
            else if (selectedResultTab === 'evidence') appendEvidenceResult(result, response.sections);
            else appendSimpleResult(result, response.sections);
            appendResponseMeta(result, response);
        }
    }

    result.setAttribute('aria-labelledby', selectedResultTab + '-tab');
    document.querySelectorAll('[data-result-tab]').forEach((tab) => {
        const selected = tab.dataset.resultTab === selectedResultTab;
        const unavailable = !resultIsReady() || (tab.dataset.resultTab !== 'simple' && !detailTabsAvailable());
        tab.classList.toggle('is-active', selected);
        tab.setAttribute('aria-selected', String(selected));
        tab.disabled = unavailable;
    });
}

function updateInputPanels() {
    document.querySelectorAll('.translator-input-panel').forEach((panel) => { panel.hidden = panel.id !== selectedInputMode + '-input-panel'; });
}

function updateSubmitState() {
    const button = $('[data-translate]');
    const sourceText = $('#source-text')?.value.trim() || '';
    if (button) button.disabled = translatorState.screen === 'LOADING' || selectedInputMode !== 'text' || sourceText.length === 0;
}

function resetTranslationResult() {
    translateRequestId += 1;
    if (translateController) translateController.abort();
    translateController = null;
    translatorState.screen = 'IDLE';
    translatorState.response = null;
    translatorState.errorKind = null;
    translatorState.errorMessage = '';
    translatorState.lastSubmittedText = '';
    selectedResultTab = 'simple';
}

function selectInputMode(mode) {
    selectedInputMode = mode;
    document.querySelectorAll('[data-input-mode]').forEach((tab) => {
        const selected = tab.dataset.inputMode === mode;
        tab.classList.toggle('is-active', selected);
        tab.setAttribute('aria-selected', String(selected));
    });
    updateInputPanels();
    if (mode !== 'text') { resetTranslationResult(); renderResult(); }
    updateSubmitState();
}

function updateSourceTextState() {
    const source = $('#source-text');
    if (!source) return;
    $('[data-character-count]').textContent = String(source.value.length);
    if ((translatorState.screen === 'RESULT' || translatorState.screen === 'LOADING') && source.value.trim() !== translatorState.lastSubmittedText) {
        resetTranslationResult();
        renderResult();
    }
    updateSubmitState();
}

function showToast(message) {
    const toast = $('#toast');
    if (!toast) return;
    toast.textContent = message;
    toast.hidden = false;
    window.setTimeout(() => { toast.hidden = true; }, 2800);
}

function getCopyText() {
    if (!resultIsReady()) return '';
    const response = translatorState.response;
    if (response.status === 'FAILED') return response.summary;
    if (selectedResultTab === 'compare') return response.sections.map((section) => '[원문] ' + section.original + '\n[쉬운 표현] ' + section.simplified).join('\n\n');
    if (selectedResultTab === 'evidence') return response.sections.map((section) => '[' + section.category + ']\n원문 근거: ' + section.original + '\n쉬운 해석: ' + section.simplified).join('\n\n');
    return [response.summary, ...response.sections.map((section) => section.simplified)].filter(Boolean).join('\n\n');
}

async function copyResult() {
    const text = getCopyText();
    if (!text) { showToast('복사할 결과가 없어요.'); return; }
    try { await navigator.clipboard.writeText(text); showToast('현재 결과를 복사했어요.'); }
    catch { showToast('결과 복사에 실패했어요. 직접 선택해 복사해 주세요.'); }
}

async function readErrorMessage(response) {
    try { return optionalText((await response.json())?.message); }
    catch { return ''; }
}

async function postEasyText(text, signal) {
    const auth = window.BokdeongiAuth;
    if (!auth || typeof auth.getCsrfToken !== 'function' || typeof auth.getApiUrl !== 'function') {
        throw new EasyTextRequestError('CONFIG', '쉬운 말 번역 서비스를 설정하지 못했습니다.');
    }
    const csrf = await auth.getCsrfToken();
    const options = { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: JSON.stringify({ text }), signal };
    if (auth.getAuthenticationState?.() === 'authenticated' && typeof auth.authenticatedFetch === 'function') {
        return auth.authenticatedFetch('/api/easy-text', options);
    }
    return fetch(auth.getApiUrl('/api/easy-text'), { ...options, credentials: 'include' });
}

async function translateText() {
    if (selectedInputMode !== 'text') { showToast('이미지와 PDF 분석 기능은 아직 지원하지 않습니다.'); return; }
    if (translatorState.screen === 'LOADING') return;
    const text = $('#source-text')?.value.trim() || '';
    if (!text) { showToast('바꿀 글을 붙여 넣어 주세요.'); return; }
    if (text.length > MAX_TEXT_LENGTH) { showToast('글은 ' + MAX_TEXT_LENGTH + '자 이내로 붙여 넣어 주세요.'); return; }

    const requestId = ++translateRequestId;
    if (translateController) translateController.abort();
    const controller = new AbortController();
    translateController = controller;
    Object.assign(translatorState, { screen: 'LOADING', response: null, errorKind: null, errorMessage: '', lastSubmittedText: text });
    selectedResultTab = 'simple';
    renderResult();
    updateSubmitState();
    try {
        const response = await postEasyText(text, controller.signal);
        if (requestId !== translateRequestId) return;
        if (!response.ok) {
            const message = await readErrorMessage(response);
            throw new EasyTextRequestError('HTTP', message || '쉬운 말로 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        let payload;
        try { payload = await response.json(); }
        catch { throw new EasyTextRequestError('INVALID', '변환 결과 형식이 올바르지 않습니다. 다시 시도해 주세요.'); }
        if (!isEasyTextResponse(payload)) throw new EasyTextRequestError('INVALID', '변환 결과 형식이 올바르지 않습니다. 다시 시도해 주세요.');
        if (requestId !== translateRequestId) return;
        translatorState.screen = 'RESULT';
        translatorState.response = payload;
        renderResult();
    } catch (error) {
        if ((error && error.name === 'AbortError') || requestId !== translateRequestId) return;
        translatorState.screen = 'ERROR';
        translatorState.response = null;
        translatorState.errorKind = error instanceof EasyTextRequestError ? error.kind : 'NETWORK';
        translatorState.errorMessage = error instanceof EasyTextRequestError ? error.message : '네트워크 연결을 확인한 뒤 다시 시도해 주세요.';
        renderResult();
        console.error('쉬운 말 번역을 요청하지 못했습니다.', error);
    } finally {
        if (requestId === translateRequestId) { translateController = null; updateSubmitState(); }
    }
}

function setupInteractions() {
    $('#source-text').addEventListener('input', updateSourceTextState);
    document.addEventListener('click', (event) => {
        const inputTab = event.target.closest('[data-input-mode]');
        if (inputTab) { selectInputMode(inputTab.dataset.inputMode); return; }
        const resultTab = event.target.closest('[data-result-tab]');
        if (resultTab && !resultTab.disabled && resultIsReady()) { selectedResultTab = resultTab.dataset.resultTab; renderResult(); return; }
        if (event.target.closest('[data-translate]')) { void translateText(); return; }
        if (event.target.closest('[data-copy-result]')) void copyResult();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    updateInputPanels();
    updateSourceTextState();
    renderResult();
    setupInteractions();
});
