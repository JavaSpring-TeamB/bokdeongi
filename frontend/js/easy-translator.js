const $ = (selector) => document.querySelector(selector);
const MAX_TEXT_LENGTH = 5000;
const translatorState = { screen: 'IDLE', response: null, errorKind: null, errorCode: null, errorMessage: '', lastSubmittedText: '' };
let selectedInputMode = 'text';
let selectedResultTab = 'simple';
let translateRequestId = 0;
let translateController = null;
const selectedFiles = { image: null, pdf: null };
let translatorAccessState = 'checking';

class EasyTextRequestError extends Error {
    constructor(kind, message, code = null) { super(message); this.kind = kind; this.code = code; }
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
        const loading = element('section', 'translator-loading', undefined);
        loading.setAttribute('role', 'status'); loading.setAttribute('aria-live', 'polite');
        loading.append(element('span', 'translator-loading__spinner', ''));
        loading.append(element('h2', '', '쉬운 말로 바꾸고 있어요!'));
        loading.append(element('p', '', '복잡한 내용을 이해하기 쉽게 정리하고 있어요. 잠시만 기다려 주세요.'));
        const bar = element('div', 'translator-loading__track', ''); bar.setAttribute('aria-hidden', 'true'); bar.append(element('span', 'translator-loading__indicator', ''));
        loading.append(bar, element('p', 'translator-loading__note', '문서의 길이에 따라 시간이 걸릴 수 있어요. 변환 중에는 새로고침하지 말아 주세요.'));
        result.append(loading);
    } else if (translatorState.screen === 'ERROR') {
        result.append(resultMessage('translator-empty-state', translatorState.errorMessage || '쉬운 말로 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.', 'alert'));
        if (translatorState.errorCode === 'PERSONAL_DOCUMENT') {
            result.append(resultMessage('translator-error-hint', '개인 서류는 번역할 수 없어요. 공공기관 고지서나 안내문으로 바꿔 다시 시도해 주세요.'));
        }
        if (translatorState.errorKind === 'NETWORK' || translatorState.errorCode === 'AI_UNAVAILABLE') {
            const retry = element('button', 'bk-btn bk-btn--secondary bk-btn--md', '다시 시도');
            retry.type = 'button';
            retry.dataset.retryTranslation = '';
            result.append(resultMessage('translator-error-hint', '잠시 후 다시 시도해 주세요.'), retry);
        }
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
    const file = selectedFiles[selectedInputMode];
    const hasInput = selectedInputMode === 'text' ? sourceText.length > 0 : Boolean(file);
    if (button) { button.disabled = translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING' || !hasInput; button.textContent = translatorState.screen === 'LOADING' ? '변환 중...' : '쉬운 말로 바꾸기 →'; }
    document.querySelectorAll('[data-input-mode]').forEach((tab) => { tab.disabled = translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING'; });
    const source = $('#source-text'); if (source) source.disabled = translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING';
    ['image-input', 'pdf-input'].forEach((id) => { const input = $('#' + id); if (input) input.disabled = translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING'; });
    const controls = $('[data-translator-controls]'); if (controls) controls.hidden = translatorAccessState === 'unauthenticated';
    const gate = $('[data-translator-access-gate]');
    if (gate) {
        gate.hidden = translatorAccessState === 'authenticated'; gate.replaceChildren();
        if (translatorAccessState === 'unauthenticated') {
            const icon = element('span', 'translator-login-required__icon', '🔒'); icon.setAttribute('aria-hidden', 'true');
            const heading = element('h2', '', '로그인이 필요해요.'); const copy = element('p', '', '로그인 후 쉬운 말 번역기를 이용할 수 있어요.');
            const login = element('a', 'bk-btn bk-btn--primary bk-btn--md', '로그인하기'); login.href = 'login.html'; gate.append(icon, heading, copy, login);
        } else if (translatorAccessState === 'error') gate.append(element('p', '', '로그인 상태를 확인하지 못했어요. 페이지를 새로고침하거나 다시 로그인해 주세요.'));
        else gate.append(element('p', '', '로그인 상태를 확인하고 있어요.'));
    }
}

function resetTranslationResult() {
    translateRequestId += 1;
    if (translateController) translateController.abort();
    translateController = null;
    translatorState.screen = 'IDLE';
    translatorState.response = null;
    translatorState.errorKind = null;
    translatorState.errorCode = null;
    translatorState.errorMessage = '';
    translatorState.lastSubmittedText = '';
    selectedResultTab = 'simple';
}

function selectInputMode(mode) {
    if (translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING') return;
    if (!['text', 'image', 'pdf'].includes(mode)) return;
    const previousMode = selectedInputMode;
    if (previousMode !== mode && previousMode !== 'text') clearSelectedFile(previousMode, false);
    selectedInputMode = mode;
    document.querySelectorAll('[data-input-mode]').forEach((tab) => {
        const selected = tab.dataset.inputMode === mode;
        tab.classList.toggle('is-active', selected);
        tab.setAttribute('aria-selected', String(selected));
    });
    updateInputPanels();
    if (previousMode !== mode) { resetTranslationResult(); renderResult(); }
    updateSubmitState();
}

function formatFileSize(bytes) {
    if (bytes < 1024) return bytes + 'B';
    if (bytes < 1024 * 1024) return Math.ceil(bytes / 1024) + 'KB';
    return (bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '') + 'MB';
}

function updateFilePanel(mode) {
    const file = selectedFiles[mode];
    const name = $('[data-file-name="' + mode + '"]');
    const remove = $('[data-file-remove="' + mode + '"]');
    if (name) name.textContent = file ? file.name + ' (' + formatFileSize(file.size) + ')' : '선택된 파일 없음';
    if (remove) remove.hidden = !file;
}

function clearSelectedFile(mode, reset = true) {
    if (!Object.hasOwn(selectedFiles, mode)) return;
    selectedFiles[mode] = null;
    const input = $('#' + mode + '-input');
    if (input) input.value = '';
    updateFilePanel(mode);
    if (reset && selectedInputMode === mode) { resetTranslationResult(); renderResult(); }
    updateSubmitState();
}

function selectFiles(mode, files) {
    if (translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING') return;
    if (!Object.hasOwn(selectedFiles, mode)) return;
    const selected = Array.from(files || []);
    if (selected.length !== 1) { clearSelectedFile(mode); showToast('파일은 한 개만 선택해 주세요.'); return; }
    const file = selected[0];
    const validationMessage = validateEasyTextFile(file);
    if (validationMessage) { clearSelectedFile(mode); showToast(validationMessage); return; }
    selectedFiles[mode] = file;
    const input = $('#' + mode + '-input');
    if (input) input.value = '';
    updateFilePanel(mode);
    if (selectedInputMode === mode) { resetTranslationResult(); renderResult(); }
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

async function readError(response) {
    try {
        const body = await response.json();
        return { message: optionalText(body?.message), code: optionalText(body?.code) || null };
    } catch { return { message: '', code: null }; }
}

async function postEasyText(text, signal) {
    const auth = window.BokdeongiAuth;
    if (!auth || typeof auth.getCsrfToken !== 'function' || typeof auth.getApiUrl !== 'function') {
        throw new EasyTextRequestError('CONFIG', '쉬운 말 번역 서비스를 설정하지 못했습니다.');
    }
    const csrf = await auth.getCsrfToken();
    const options = { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: JSON.stringify({ text }), signal };
    return auth.authenticatedFetch('/api/easy-text', options);
}

function validateEasyTextFile(file) {
    if (!file || Array.isArray(file) || typeof file.name !== 'string' || !Number.isFinite(file.size) || file.size <= 0) {
        return '바꿀 JPG, PNG 또는 PDF 파일을 선택해 주세요.';
    }
    if (file.size > 10 * 1024 * 1024) return '파일은 10MB 이하만 올릴 수 있어요.';
    const mimeByExtension = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', pdf: 'application/pdf' };
    const extension = file.name.match(/\.([^.]+)$/)?.[1]?.toLowerCase();
    const expectedType = mimeByExtension[extension];
    if (!expectedType || (file.type && file.type !== expectedType)) {
        return 'JPG, PNG, PDF 파일만 올릴 수 있어요.';
    }
    return '';
}

async function postEasyTextFile(file, signal) {
    const auth = window.BokdeongiAuth;
    if (!auth || typeof auth.getCsrfToken !== 'function' || typeof auth.getApiUrl !== 'function') {
        throw new EasyTextRequestError('CONFIG', '쉬운 말 번역 서비스를 설정하지 못했습니다.');
    }
    const csrf = await auth.getCsrfToken();
    const body = new FormData();
    body.append('file', file);
    const options = { method: 'POST', headers: { [csrf.headerName]: csrf.token }, body, signal };
    return auth.authenticatedFetch('/api/easy-text/file', options);
}

async function requireTranslatorLogin() {
    const auth = window.BokdeongiAuth;
    if (!auth || typeof auth.refreshAuthentication !== 'function' || typeof auth.getAuthenticationState !== 'function') return false;
    await auth.refreshAuthentication();
    return auth.getAuthenticationState() === 'authenticated';
}

async function runTranslation(request, lastSubmittedText) {
    if (translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING') return;
    const requestId = ++translateRequestId;
    if (translateController) translateController.abort();
    const controller = new AbortController();
    translateController = controller;
    Object.assign(translatorState, { screen: 'LOADING', response: null, errorKind: null, errorCode: null, errorMessage: '', lastSubmittedText });
    selectedResultTab = 'simple';
    renderResult();
    updateSubmitState();
    try {
        if (!await requireTranslatorLogin()) throw new EasyTextRequestError('UNAUTHENTICATED', '로그인 후 쉬운 말 번역기를 이용할 수 있어요.');
        const response = await request(controller.signal);
        if (requestId !== translateRequestId) return;
        if (!response.ok) {
            const error = await readError(response);
            if (response.status === 401) throw new EasyTextRequestError('UNAUTHENTICATED', '로그인 후 쉬운 말 번역기를 이용할 수 있어요.');
            throw new EasyTextRequestError('HTTP', error.message || '쉬운 말로 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.', error.code);
        }
        let payload;
        try { payload = await response.json(); }
        catch { throw new EasyTextRequestError('INVALID', '변환 결과 형식이 올바르지 않습니다. 다시 시도해 주세요.'); }
        if (!isEasyTextResponse(payload)) throw new EasyTextRequestError('INVALID', '변환 결과 형식이 올바르지 않습니다. 다시 시도해 주세요.');
        if (requestId !== translateRequestId) return;
        translatorState.screen = 'RESULT';
        translatorState.response = payload;
        if (selectedInputMode !== 'text') clearSelectedFile(selectedInputMode, false);
        renderResult();
    } catch (error) {
        if ((error && error.name === 'AbortError') || requestId !== translateRequestId) return;
        translatorState.screen = 'ERROR';
        translatorState.response = null;
        translatorState.errorCode = error instanceof EasyTextRequestError ? error.code : null;
        translatorState.errorKind = translatorState.errorCode === 'PERSONAL_DOCUMENT' ? 'PERSONAL_DOCUMENT'
            : error instanceof EasyTextRequestError ? error.kind : 'NETWORK';
        translatorState.errorMessage = error instanceof EasyTextRequestError ? error.message : '네트워크 연결을 확인한 뒤 다시 시도해 주세요.';
        if (translatorState.errorKind === 'UNAUTHENTICATED') translatorAccessState = 'unauthenticated';
        renderResult();
        console.error('쉬운 말 번역을 요청하지 못했습니다.', error);
    } finally {
        if (requestId === translateRequestId) { translateController = null; updateSubmitState(); }
    }
}

async function translateText() {
    if (translatorAccessState !== 'authenticated') return;
    if (selectedInputMode !== 'text') { showToast('이미지와 PDF 분석 기능은 아직 지원하지 않습니다.'); return; }
    const text = $('#source-text')?.value.trim() || '';
    if (!text) { showToast('바꿀 글을 붙여 넣어 주세요.'); return; }
    if (text.length > MAX_TEXT_LENGTH) { showToast('글은 ' + MAX_TEXT_LENGTH + '자 이내로 붙여 넣어 주세요.'); return; }
    return runTranslation((signal) => postEasyText(text, signal), text);
}

async function translateFile(file) {
    if (translatorAccessState !== 'authenticated') return;
    const validationMessage = validateEasyTextFile(file);
    if (validationMessage) { showToast(validationMessage); return; }
    return runTranslation((signal) => postEasyTextFile(file, signal), '');
}

async function translateSelectedFile() {
    const file = selectedFiles[selectedInputMode];
    if (!file) { showToast('번역할 파일을 선택해 주세요.'); return; }
    return translateFile(file);
}

async function retryTranslation() {
    if (selectedInputMode === 'text') return translateText();
    return translateSelectedFile();
}

function setupInteractions() {
    $('#source-text').addEventListener('input', updateSourceTextState);
    ['image', 'pdf'].forEach((mode) => {
        const input = $('#' + mode + '-input');
        input?.addEventListener('change', () => selectFiles(mode, input.files));
        const dropZone = $('[data-file-drop-zone="' + mode + '"]');
        if (!dropZone) return;
        dropZone.addEventListener('dragover', (event) => { event.preventDefault(); if (translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING') return; dropZone.classList.add('is-dragover'); });
        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('is-dragover'));
        dropZone.addEventListener('drop', (event) => { event.preventDefault(); dropZone.classList.remove('is-dragover'); if (translatorAccessState !== 'authenticated' || translatorState.screen === 'LOADING') return; selectFiles(mode, event.dataTransfer?.files); });
    });
    document.addEventListener('click', (event) => {
        const inputTab = event.target.closest('[data-input-mode]');
        if (inputTab) { selectInputMode(inputTab.dataset.inputMode); return; }
        const resultTab = event.target.closest('[data-result-tab]');
        if (resultTab && !resultTab.disabled && resultIsReady()) { selectedResultTab = resultTab.dataset.resultTab; renderResult(); return; }
        const removeFile = event.target.closest('[data-file-remove]');
        if (removeFile) { clearSelectedFile(removeFile.dataset.fileRemove); return; }
        if (event.target.closest('[data-retry-translation]')) { void retryTranslation(); return; }
        if (event.target.closest('[data-translate]')) { void (selectedInputMode === 'text' ? translateText() : translateSelectedFile()); return; }
        if (event.target.closest('[data-copy-result]')) void copyResult();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    updateSubmitState();
    updateInputPanels();
    updateSourceTextState();
    renderResult();
    setupInteractions();
    void initializeTranslatorAccess();
});

async function initializeTranslatorAccess() {
    translatorAccessState = 'checking'; updateSubmitState();
    const auth = window.BokdeongiAuth;
    if (!auth || typeof auth.refreshAuthentication !== 'function' || typeof auth.getAuthenticationState !== 'function') {
        translatorAccessState = 'error'; updateSubmitState(); return;
    }
    try {
        await auth.refreshAuthentication();
        const status = auth.getAuthenticationState();
        translatorAccessState = status === 'authenticated' ? 'authenticated' : status === 'unauthenticated' ? 'unauthenticated' : 'error';
    } catch { translatorAccessState = 'error'; }
    updateSubmitState();
}
