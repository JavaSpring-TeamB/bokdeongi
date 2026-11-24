const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'easy-translator.js'), 'utf8');

function node() {
  const value = {
    children: [], textContent: '', hidden: false, disabled: false, value: '', files: null, dataset: {}, attributes: {}, handlers: {},
    classList: { toggle() {}, add() {}, remove() {} }, addEventListener(type, handler) { this.handlers[type] = handler; }, setAttribute(name, text) { this.attributes[name] = text; },
    removeAttribute(name) { delete this.attributes[name]; }, append(...children) { this.children.push(...children); },
    replaceChildren(...children) { this.children = children; this.textContent = ''; }, querySelector() { return null; }
  };
  return value;
}

function allText(value) {
  if (typeof value === 'string') return value;
  return String(value.textContent || '') + value.children.map(allText).join('');
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function payload(overrides = {}) {
  return {
    status: 'DONE', summary: '신청 조건을 확인해 주세요.', masked: false, remaining: 19,
    notice: 'AI가 바꾼 문장이에요. 정확한 내용은 원문과 발급 기관에 확인하세요',
    sections: [{ category: '조건', original: '원문 조건', simplified: '쉬운 조건', action: '서류를 확인해 주세요.', caution: '마감일을 확인해 주세요.' }],
    ...overrides
  };
}

function createHarness({ request, authenticationState = 'unauthenticated' } = {}) {
  const elements = {
    '#source-text': node(), '[data-character-count]': node(), '[data-translate]': node(),
    '#translation-result': node(), '[data-copy-result]': node(), '#toast': node(),
    '#image-input': node(), '#pdf-input': node(),
    '[data-file-name="image"]': node(), '[data-file-name="pdf"]': node(),
    '[data-file-remove="image"]': node(), '[data-file-remove="pdf"]': node(),
    '[data-file-drop-zone="image"]': node(), '[data-file-drop-zone="pdf"]': node()
  };
  const tabs = ['simple', 'compare', 'evidence'].map((resultTab) => ({ ...node(), dataset: { resultTab } }));
  const inputTabs = ['text', 'image', 'pdf'].map((inputMode) => ({ ...node(), dataset: { inputMode } }));
  const inputPanels = ['text', 'image', 'pdf'].map((mode) => ({ ...node(), id: mode + '-input-panel' }));
  const calls = [];
  class FakeFormData {
    constructor() { this.values = []; }
    append(name, value) { this.values.push([name, value]); }
  }
  const auth = {
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    getAuthenticationState: () => authenticationState,
    authenticatedFetch: async (requestPath, options) => { calls.push({ requestPath, options, authenticated: true }); return request(requestPath, options); }
  };
  const document = {
    handlers: {}, addEventListener(type, handler) { this.handlers[type] = handler; }, createElement: node, createTextNode: (text) => ({ textContent: text, children: [] }),
    querySelector: (selector) => elements[selector] || null,
    querySelectorAll: (selector) => selector === '[data-result-tab]' ? tabs : selector === '[data-input-mode]' ? inputTabs : selector === '.translator-input-panel' ? inputPanels : []
  };
  const context = {
    AbortController, Array, FormData: FakeFormData, JSON, Number, Object, String, console: { error() {} }, document,
    window: { BokdeongiAuth: auth, setTimeout() {} }, navigator: { clipboard: { writeText: async () => {} } },
    fetch: async (url, options) => { calls.push({ url, options, authenticated: false }); return request(url, options); }
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\nglobalThis.__easyTextHooks = { translatorState, isEasyTextResponse, translateText, translateFile, translateSelectedFile, retryTranslation, validateEasyTextFile, updateSourceTextState, selectInputMode, selectFiles, clearSelectedFile, getCopyText, setupInteractions };`, context);
  return { calls, elements, hooks: context.__easyTextHooks };
}

test('sends the actual EASY-02 DTO with CSRF and renders API text safely', async () => {
  const harness = createHarness({ request: () => response(200, payload({ masked: true, summary: '<img src=x>' })) });
  harness.elements['#source-text'].value = '  복지 안내문  ';

  await harness.hooks.translateText();

  assert.equal(harness.calls.length, 1);
  assert.equal(harness.calls[0].url, 'http://backend.test/api/easy-text');
  assert.equal(harness.calls[0].options.method, 'POST');
  assert.equal(harness.calls[0].options.headers['Content-Type'], 'application/json');
  assert.equal(harness.calls[0].options.headers['X-XSRF-TOKEN'], 'csrf-token');
  assert.deepEqual(JSON.parse(harness.calls[0].options.body), { text: '복지 안내문' });
  assert.equal(harness.hooks.translatorState.screen, 'RESULT');
  assert.match(allText(harness.elements['#translation-result']), /개인정보를 가리고 보냈어요/);
  assert.match(allText(harness.elements['#translation-result']), /<img src=x>/);
  assert.equal('innerHTML' in harness.elements['#translation-result'], false);
});

test('hides empty action and caution groups without manufacturing text', async () => {
  const harness = createHarness({ request: () => response(200, payload({ sections: [{ category: '조건', original: '원문', simplified: '쉬운 말', action: null, caution: '' }] })) });
  harness.elements['#source-text'].value = '안내문';
  await harness.hooks.translateText();
  const text = allText(harness.elements['#translation-result']);
  assert.doesNotMatch(text, /해야 할 일|주의할 점/);
});

test('does not show the masking notice when the API reports masked false', async () => {
  const harness = createHarness({ request: () => response(200, payload({ masked: false })) });
  harness.elements['#source-text'].value = '안내문';
  await harness.hooks.translateText();
  assert.doesNotMatch(allText(harness.elements['#translation-result']), /개인정보를 가리고 보냈어요/);
});

test('uses authenticatedFetch for a signed-in request', async () => {
  const harness = createHarness({ authenticationState: 'authenticated', request: () => response(200, payload()) });
  harness.elements['#source-text'].value = '안내문';
  await harness.hooks.translateText();
  assert.equal(harness.calls[0].authenticated, true);
  assert.equal(harness.calls[0].requestPath, '/api/easy-text');
});

test('posts one anonymous JPG, PNG, or PDF as multipart to the EASY-02 file endpoint and reuses the response flow', async () => {
  const file = { name: 'notice.JPG', type: 'image/jpeg', size: 128 };
  const harness = createHarness({ request: () => response(200, payload({ summary: '파일 결과' })) });
  await harness.hooks.translateFile(file);
  assert.equal(harness.calls.length, 1);
  assert.equal(harness.calls[0].authenticated, false);
  assert.equal(harness.calls[0].url, 'http://backend.test/api/easy-text/file');
  assert.equal(harness.calls[0].options.method, 'POST');
  assert.equal(harness.calls[0].options.headers['X-XSRF-TOKEN'], 'csrf-token');
  assert.equal(harness.calls[0].options.headers['Content-Type'], undefined);
  assert.deepEqual(harness.calls[0].options.body.values, [['file', file]]);
  assert.equal(harness.hooks.translatorState.screen, 'RESULT');
  assert.equal(harness.hooks.translatorState.response.summary, '파일 결과');
});

test('validates the one-file contract and preserves a 422 UNREADABLE server code and message', async () => {
  const invalid = createHarness({ request: () => { throw new Error('must not request'); } });
  await invalid.hooks.translateFile({ name: 'notice.webp', type: 'image/webp', size: 1 });
  await invalid.hooks.translateFile({ name: 'notice.pdf', type: 'application/pdf', size: 10 * 1024 * 1024 + 1 });
  assert.equal(invalid.calls.length, 0);

  const unreadable = createHarness({ request: () => response(422, { code: 'UNREADABLE', message: '글자를 읽지 못했어요.' }) });
  await unreadable.hooks.translateFile({ name: 'notice.pdf', type: 'application/pdf', size: 1024 });
  assert.equal(unreadable.hooks.translatorState.screen, 'ERROR');
  assert.equal(unreadable.hooks.translatorState.errorCode, 'UNREADABLE');
  assert.equal(unreadable.hooks.translatorState.errorMessage, '글자를 읽지 못했어요.');
});

test('activates image and PDF tabs only after one valid file is selected, and clears the file for reselection', () => {
  const harness = createHarness({ request: () => response(200, payload()) });
  const file = { name: 'notice.png', type: 'image/png', size: 2048 };
  harness.hooks.selectInputMode('image');
  harness.hooks.selectFiles('image', [{ name: 'notice.webp', type: 'image/webp', size: 1 }]);
  harness.hooks.selectFiles('image', [{ name: 'large.png', type: 'image/png', size: 10 * 1024 * 1024 + 1 }]);
  harness.hooks.selectFiles('image', [file, file]);
  assert.equal(harness.elements['[data-file-name="image"]'].textContent, '선택된 파일 없음');
  harness.hooks.selectFiles('image', [file]);
  assert.equal(harness.elements['[data-file-name="image"]'].textContent, 'notice.png (2KB)');
  assert.equal(harness.elements['[data-file-remove="image"]'].hidden, false);
  assert.equal(harness.elements['[data-translate]'].disabled, false);
  harness.hooks.clearSelectedFile('image');
  assert.equal(harness.elements['[data-file-name="image"]'].textContent, '선택된 파일 없음');
  assert.equal(harness.elements['[data-file-remove="image"]'].hidden, true);
  assert.equal(harness.elements['[data-translate]'].disabled, true);
  harness.hooks.selectFiles('image', [file]);
  assert.equal(harness.elements['[data-translate]'].disabled, false);
});

test('accepts a dropped file and sends the current selected file through translateFile', async () => {
  const file = { name: 'notice.pdf', type: 'application/pdf', size: 1024 };
  const harness = createHarness({ request: () => response(200, payload({ summary: '드롭 결과' })) });
  harness.hooks.setupInteractions();
  const drop = harness.elements['[data-file-drop-zone="pdf"]'].handlers.drop;
  let prevented = false;
  drop({ preventDefault() { prevented = true; }, dataTransfer: { files: [file] } });
  assert.equal(prevented, true);
  harness.hooks.selectInputMode('pdf');
  await harness.hooks.translateSelectedFile();
  assert.equal(harness.calls[0].url, 'http://backend.test/api/easy-text/file');
  assert.equal(harness.hooks.translatorState.response.summary, '드롭 결과');
});

test('clears a file and ignores its late response when the user changes tabs', async () => {
  let resolveRequest;
  const harness = createHarness({ request: () => new Promise((resolve) => { resolveRequest = resolve; }) });
  const file = { name: 'notice.jpg', type: 'image/jpeg', size: 1024 };
  harness.hooks.selectInputMode('image');
  harness.hooks.selectFiles('image', [file]);
  const pending = harness.hooks.translateSelectedFile();
  await new Promise((resolve) => setImmediate(resolve));
  harness.hooks.selectInputMode('text');
  resolveRequest(response(200, payload({ summary: '늦은 파일 결과' })));
  await pending;
  assert.equal(harness.hooks.translatorState.screen, 'IDLE');
  assert.equal(harness.hooks.translatorState.response, null);
  assert.equal(harness.elements['[data-file-name="image"]'].textContent, '선택된 파일 없음');
});

test('preserves the AI_UNAVAILABLE message, allows retry, and releases a file after a completed response', async () => {
  let count = 0;
  const file = { name: 'notice.pdf', type: 'application/pdf', size: 1024 };
  const harness = createHarness({ request: () => {
    count += 1;
    return count === 1
      ? response(502, { code: 'AI_UNAVAILABLE', message: 'OCR 서비스를 사용할 수 없어요.' })
      : response(200, payload({ summary: '재시도 결과' }));
  } });
  harness.hooks.selectInputMode('pdf');
  harness.hooks.selectFiles('pdf', [file]);
  await harness.hooks.translateSelectedFile();
  assert.equal(harness.hooks.translatorState.errorMessage, 'OCR 서비스를 사용할 수 없어요.');
  assert.equal(harness.hooks.translatorState.errorCode, 'AI_UNAVAILABLE');
  assert.match(allText(harness.elements['#translation-result']), /잠시 후 다시 시도해 주세요/);
  await harness.hooks.retryTranslation();
  assert.equal(harness.hooks.translatorState.response.summary, '재시도 결과');
  assert.equal(harness.elements['[data-file-name="pdf"]'].textContent, '선택된 파일 없음');
});

test('renders an invalid EASY-02 response as an error instead of a translation result', async () => {
  const harness = createHarness({ request: () => response(200, { status: 'DONE' }) });
  harness.elements['#source-text'].value = '안내문';
  await harness.hooks.translateText();
  assert.equal(harness.hooks.translatorState.screen, 'ERROR');
  assert.equal(harness.hooks.translatorState.response, null);
  assert.match(allText(harness.elements['#translation-result']), /변환 결과 형식이 올바르지 않습니다/);
});

test('renders FAILED with the summary only', async () => {
  const harness = createHarness({ request: () => response(200, payload({ status: 'FAILED', summary: '변환에 실패했어요.', sections: [], masked: false })) });
  harness.elements['#source-text'].value = '안내문';
  await harness.hooks.translateText();
  const text = allText(harness.elements['#translation-result']);
  assert.match(text, /변환에 실패했어요/);
  assert.doesNotMatch(text, /AI가 바꾼 문장|오늘 남은 횟수|원문 조건/);
});

test('keeps input and displays the exact server message for 400, 429, and 502', async () => {
  for (const status of [400, 429, 502]) {
    const message = `server-${status} message`;
    const harness = createHarness({ request: () => response(status, { message }) });
    harness.elements['#source-text'].value = '수정할 원문';
    await harness.hooks.translateText();
    assert.equal(harness.hooks.translatorState.screen, 'ERROR');
    assert.equal(harness.hooks.translatorState.errorMessage, message);
    assert.equal(harness.elements['#source-text'].value, '수정할 원문');
  }
});

test('blocks blank and too-long input without an API request', async () => {
  const blank = createHarness({ request: () => { throw new Error('must not request'); } });
  blank.elements['#source-text'].value = '   ';
  await blank.hooks.translateText();
  assert.equal(blank.calls.length, 0);

  const long = createHarness({ request: () => { throw new Error('must not request'); } });
  long.elements['#source-text'].value = '가'.repeat(5001);
  await long.hooks.translateText();
  assert.equal(long.calls.length, 0);
});

test('does not call an API while an unsupported image or PDF mode is selected', async () => {
  const harness = createHarness({ request: () => { throw new Error('must not request'); } });
  harness.elements['#source-text'].value = '안내문';
  harness.hooks.selectInputMode('image');
  await harness.hooks.translateText();
  harness.hooks.selectInputMode('pdf');
  await harness.hooks.translateText();
  assert.equal(harness.calls.length, 0);
});

test('prevents duplicate submissions and ignores a late previous response', async () => {
  let resolveFirst;
  let count = 0;
  const harness = createHarness({ request: () => {
    count += 1;
    return count === 1 ? new Promise((resolve) => { resolveFirst = resolve; }) : response(200, payload({ summary: '두 번째 결과' }));
  } });
  harness.elements['#source-text'].value = '첫 번째';
  const first = harness.hooks.translateText();
  await harness.hooks.translateText();
  assert.equal(harness.calls.length, 1);
  harness.elements['#source-text'].value = '두 번째';
  harness.hooks.updateSourceTextState();
  const second = harness.hooks.translateText();
  resolveFirst(response(200, payload({ summary: '이전 결과' })));
  await Promise.all([first, second]);
  assert.equal(harness.hooks.translatorState.response.summary, '두 번째 결과');
});

test('distinguishes network failures and accepts only the actual response shape', async () => {
  const network = createHarness({ request: async () => { throw new TypeError('offline'); } });
  network.elements['#source-text'].value = '안내문';
  await network.hooks.translateText();
  assert.equal(network.hooks.translatorState.errorKind, 'NETWORK');
  assert.match(allText(network.elements['#translation-result']), /네트워크 연결/);

  assert.equal(network.hooks.isEasyTextResponse(payload()), true);
  assert.equal(network.hooks.isEasyTextResponse(payload({ status: 'UNKNOWN' })), false);
  assert.equal(network.hooks.isEasyTextResponse(payload({ masked: 'true' })), false);
  assert.equal(network.hooks.isEasyTextResponse(payload({ sections: [] })), true);
});
