const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-detail.js'), 'utf8');

function element() {
  return {
    hidden: false,
    innerHTML: '',
    textContent: '',
    setAttribute() {},
    replaceChildren() { this.innerHTML = ''; },
    addEventListener() {}
  };
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function easyPayload(overrides = {}) {
  return {
    status: 'DONE',
    text: '지원 대상이면 온라인으로 신청할 수 있습니다.\n마감일을 확인해 주세요.',
    summary: {
      who: '청년',
      what: '월세를 지원합니다.',
      how: '온라인으로 신청합니다.',
      when: '2026년 12월 31일까지'
    },
    generatedAt: '2026-11-23T12:00:00',
    notice: 'AI가 바꾼 문장이에요. 정확한 내용은 원문을 확인하세요.',
    ...overrides
  };
}

function createHarness(fetchImplementation) {
  const elements = {
    '#panel-easy': element(),
    '#detail-page': element(),
    '#detail-banner-copy': element(),
    '#toast': element()
  };
  const requests = [];
  const document = {
    addEventListener() {},
    querySelector: (selector) => elements[selector] || null,
    querySelectorAll: () => []
  };
  const window = {
    location: { search: '?id=123', href: 'http://frontend.test/benefit-detail.html?id=123' },
    BokdeongiAuth: { getApiUrl: (requestPath) => `http://backend.test${requestPath}` }
  };
  const context = {
    AbortController,
    Array,
    JSON,
    Number,
    Object,
    Set,
    String,
    URL,
    URLSearchParams,
    console: { error() {} },
    document,
    fetch: async (url, options) => {
      requests.push({ url, options });
      return fetchImplementation(url, options);
    },
    setTimeout,
    clearTimeout,
    window
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__easyHooks = { easyPanelContent, easyState, isEasyResponse, loadEasy, resetEasyState, state };`, context);
  context.__easyHooks.state.benefitId = 123;
  context.__easyHooks.state.detail = { id: 123 };
  return { elements, hooks: context.__easyHooks, requests };
}

test('requests the public easy API once and renders all DONE response fields safely', async () => {
  const { elements, hooks, requests } = createHarness(() => response(200, easyPayload({
    text: '<img src=x>\n쉬운 말 본문',
    notice: '<script>alert(1)</script>'
  })));

  await hooks.loadEasy();

  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, 'http://backend.test/api/benefits/123/easy');
  assert.equal(requests[0].options.credentials, 'include');
  assert.equal(requests[0].options.headers, undefined);
  assert.equal(hooks.easyState.screen, 'DONE');
  assert.match(elements['#panel-easy'].innerHTML, /청년/);
  assert.match(elements['#panel-easy'].innerHTML, /월세를 지원합니다/);
  assert.match(elements['#panel-easy'].innerHTML, /온라인으로 신청합니다/);
  assert.match(elements['#panel-easy'].innerHTML, /2026년 12월 31일까지/);
  assert.match(elements['#panel-easy'].innerHTML, /생성 시각: 2026-11-23 12:00:00/);
  assert.match(elements['#panel-easy'].innerHTML, /&lt;img src=x&gt;/);
  assert.match(elements['#panel-easy'].innerHTML, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
});

test('reuses a DONE result on tab re-entry and refetches only when forced', async () => {
  const { hooks, requests } = createHarness(() => response(200, easyPayload()));

  await hooks.loadEasy();
  await hooks.loadEasy();
  assert.equal(requests.length, 1);

  await hooks.loadEasy(true);
  assert.equal(requests.length, 2);
});

test('renders NOT_READY as a normal state without a fake easy summary', async () => {
  const { elements, hooks } = createHarness(() => response(200, easyPayload({
    status: 'NOT_READY', text: null, summary: null, generatedAt: null
  })));

  await hooks.loadEasy();

  assert.equal(hooks.easyState.screen, 'NOT_READY');
  assert.match(elements['#panel-easy'].innerHTML, /아직 준비되지 않았어요/);
  assert.match(elements['#panel-easy'].innerHTML, /data-open-original/);
  assert.doesNotMatch(elements['#panel-easy'].innerHTML, /누가 받을 수 있나요/);
});

test('renders FAILED separately from a network request failure', async () => {
  const failed = createHarness(() => response(200, easyPayload({
    status: 'FAILED', text: null, summary: null, generatedAt: null, notice: '생성에 실패했습니다.'
  })));
  await failed.hooks.loadEasy();

  assert.equal(failed.hooks.easyState.screen, 'FAILED');
  assert.match(failed.elements['#panel-easy'].innerHTML, /제공하지 못했어요/);
  assert.match(failed.elements['#panel-easy'].innerHTML, /생성에 실패했습니다/);

  const network = createHarness(() => { throw new TypeError('Failed to fetch'); });
  await network.hooks.loadEasy();
  assert.equal(network.hooks.easyState.screen, 'ERROR');
  assert.equal(network.hooks.easyState.errorKind, 'NETWORK');
  assert.match(network.elements['#panel-easy'].innerHTML, /네트워크 연결을 확인/);
});

test('handles malformed API responses without rendering them as AI content', async () => {
  const { elements, hooks } = createHarness(() => response(200, { status: 'DONE', text: [] }));

  await hooks.loadEasy();

  assert.equal(hooks.easyState.screen, 'ERROR');
  assert.equal(hooks.easyState.errorKind, 'INVALID');
  assert.match(elements['#panel-easy'].innerHTML, /결과 형식이 올바르지 않습니다/);
});

test('shares an in-flight easy request and ignores its late response after detail changes', async () => {
  let resolveFirst;
  const first = new Promise((resolve) => { resolveFirst = resolve; });
  const { hooks, requests } = createHarness(() => first);

  const requestOne = hooks.loadEasy();
  const requestTwo = hooks.loadEasy();
  assert.equal(requests.length, 1);

  hooks.state.benefitId = 456;
  hooks.resetEasyState(456);
  resolveFirst(response(200, easyPayload({ text: '이전 혜택 결과' })));
  await Promise.all([requestOne, requestTwo]);

  assert.equal(hooks.easyState.benefitId, 456);
  assert.equal(hooks.easyState.response, null);
});

test('accepts only the documented status values and nullable response fields', () => {
  const { hooks } = createHarness(() => response(200, easyPayload()));
  assert.equal(hooks.isEasyResponse(easyPayload()), true);
  assert.equal(hooks.isEasyResponse(easyPayload({ status: 'UNKNOWN' })), false);
  assert.equal(hooks.isEasyResponse(easyPayload({ summary: { who: 3 } })), false);
  assert.equal(hooks.isEasyResponse(easyPayload({ generatedAt: {} })), false);
});
