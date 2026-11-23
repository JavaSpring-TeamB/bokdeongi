const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'easy-translator.js'), 'utf8');

function node() {
  const value = {
    children: [], textContent: '', hidden: false, disabled: false, value: '', dataset: {}, attributes: {},
    classList: { toggle() {} }, addEventListener() {}, setAttribute(name, text) { this.attributes[name] = text; },
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
    '#translation-result': node(), '[data-copy-result]': node(), '#toast': node()
  };
  const tabs = ['simple', 'compare', 'evidence'].map((resultTab) => ({ ...node(), dataset: { resultTab } }));
  const calls = [];
  const auth = {
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    getAuthenticationState: () => authenticationState,
    authenticatedFetch: async (requestPath, options) => { calls.push({ requestPath, options, authenticated: true }); return request(requestPath, options); }
  };
  const document = {
    addEventListener() {}, createElement: node, createTextNode: (text) => ({ textContent: text, children: [] }),
    querySelector: (selector) => elements[selector] || null,
    querySelectorAll: (selector) => selector === '[data-result-tab]' ? tabs : []
  };
  const context = {
    AbortController, Array, JSON, Number, Object, String, console: { error() {} }, document,
    window: { BokdeongiAuth: auth, setTimeout() {} }, navigator: { clipboard: { writeText: async () => {} } },
    fetch: async (url, options) => { calls.push({ url, options, authenticated: false }); return request(url, options); }
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\nglobalThis.__easyTextHooks = { translatorState, isEasyTextResponse, translateText, updateSourceTextState, selectInputMode, getCopyText };`, context);
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
