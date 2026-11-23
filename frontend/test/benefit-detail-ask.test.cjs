const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-detail.js'), 'utf8');

function element() {
  return {
    disabled: false, hidden: true, innerHTML: '', textContent: '', title: '', value: '',
    classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, addEventListener() {}
  };
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function askPayload(overrides = {}) {
  return {
    answer: '신청은 온라인으로 할 수 있어요.\n필요 서류를 준비해 주세요.',
    foundInSource: true,
    sourceQuotes: ['신청 방법: 온라인 신청'],
    eligibility: false,
    remaining: 29,
    notice: 'AI가 공고 원문을 보고 답한 내용이에요.',
    ...overrides
  };
}

function createHarness({ authenticationState = 'authenticated', askFetch = () => response(200, askPayload()) } = {}) {
  const elements = { '#ask-panel': element(), '#toast': element() };
  const calls = [];
  const auth = {
    refreshAuthentication: async () => authenticationState,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (requestPath, options = {}) => {
      calls.push({ requestPath, options });
      return askFetch(requestPath, options);
    },
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`
  };
  const context = {
    AbortController, Array, JSON, Number, Object, Set, String, URL, URLSearchParams, setTimeout, clearTimeout,
    console: { error() {} }, window: { BokdeongiAuth: auth, location: { search: '?id=123' } },
    document: { addEventListener() {}, querySelector: (selector) => elements[selector] || null, querySelectorAll: () => [] },
    fetch: async () => response(200, { id: 123 })
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__askHooks = { askPanelContent, askState, isAskResponse, loadAskAuthentication, normalizeQuestion, resetAskState, state, submitAsk };`, context);
  context.__askHooks.state.benefitId = 123;
  context.__askHooks.state.detail = { id: 123 };
  context.__askHooks.resetAskState(123);
  return { calls, elements, hooks: context.__askHooks };
}

test('sends the actual benefit question with authenticated CSRF JSON request and renders answer fields', async () => {
  const { calls, elements, hooks } = createHarness();
  await hooks.loadAskAuthentication();
  await hooks.submitAsk('  어떤 서류가 필요한가요?  ');

  assert.equal(calls.length, 1);
  assert.equal(calls[0].requestPath, '/api/benefits/123/ask');
  assert.equal(calls[0].options.method, 'POST');
  assert.deepEqual({ ...calls[0].options.headers }, { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
  assert.equal(calls[0].options.body, JSON.stringify({ question: '어떤 서류가 필요한가요?' }));
  assert.equal(hooks.askState.screen, 'RESULT');
  assert.match(elements['#ask-panel'].innerHTML, /온라인으로 할 수 있어요/);
  assert.match(elements['#ask-panel'].innerHTML, /공고 원문 근거/);
  assert.match(elements['#ask-panel'].innerHTML, /오늘 남은 질문: 29회/);
});

test('blocks blank and overlong questions without spending a server request', async () => {
  const { calls, elements, hooks } = createHarness();
  await hooks.loadAskAuthentication();
  await hooks.submitAsk('   ');
  assert.equal(calls.length, 0);
  assert.match(elements['#ask-panel'].innerHTML, /질문을 입력해주세요/);

  await hooks.submitAsk('가'.repeat(201));
  assert.equal(calls.length, 0);
  assert.match(elements['#ask-panel'].innerHTML, /200자 이내/);
});

test('does not call the ask endpoint for an unauthenticated visitor', async () => {
  const { calls, elements, hooks } = createHarness({ authenticationState: 'unauthenticated' });
  await hooks.loadAskAuthentication();
  assert.equal(hooks.askState.screen, 'UNAUTHENTICATED');
  assert.equal(calls.length, 0);
  assert.match(elements['#ask-panel'].innerHTML, /로그인하기/);
});

test('renders no-source and eligibility signals only from the actual response', async () => {
  const { elements, hooks } = createHarness({
    askFetch: () => response(200, askPayload({ foundInSource: false, sourceQuotes: [], eligibility: true, remaining: 1 }))
  });
  await hooks.loadAskAuthentication();
  await hooks.submitAsk('제가 신청할 수 있나요?');
  assert.match(elements['#ask-panel'].innerHTML, /근거 문장이 제공되지 않았어요/);
  assert.match(elements['#ask-panel'].innerHTML, /내 자격 확인 결과/);
  assert.doesNotMatch(elements['#ask-panel'].innerHTML, /내게 맞아요/);
});

test('honors the server limit response and preserves the submitted question', async () => {
  const { elements, hooks } = createHarness({
    askFetch: () => response(429, { code: 'TOO_MANY_REQUESTS', message: '오늘 질문은 30번까지 할 수 있어요.' })
  });
  await hooks.loadAskAuthentication();
  await hooks.submitAsk('언제까지 신청하나요?');
  assert.equal(hooks.askState.screen, 'LIMITED');
  assert.equal(hooks.askState.submittedQuestion, '언제까지 신청하나요?');
  assert.match(elements['#ask-panel'].innerHTML, /오늘 사용할 수 있는 질문 횟수/);
});

test('disables additional questions when a successful response reports zero remaining', async () => {
  const { elements, hooks } = createHarness({ askFetch: () => response(200, askPayload({ remaining: 0 })) });
  await hooks.loadAskAuthentication();
  await hooks.submitAsk('신청 방법이 무엇인가요?');
  assert.equal(hooks.askState.screen, 'RESULT');
  assert.match(elements['#ask-panel'].innerHTML, /오늘 사용할 수 있는 질문 횟수/);
  assert.match(elements['#ask-panel'].innerHTML, /disabled/);
});

test('separates AI unavailability and authentication expiry from successful answers', async () => {
  const unavailable = createHarness({
    askFetch: () => response(502, { code: 'AI_UNAVAILABLE', message: '지금은 질문에 답할 수 없어요.' })
  });
  await unavailable.hooks.loadAskAuthentication();
  await unavailable.hooks.submitAsk('신청 기한은 언제인가요?');
  assert.equal(unavailable.hooks.askState.screen, 'ERROR');
  assert.match(unavailable.elements['#ask-panel'].innerHTML, /지금은 질문에 답할 수 없어요/);

  const expired = createHarness({ askFetch: () => response(401, { message: '로그인이 필요합니다.' }) });
  await expired.hooks.loadAskAuthentication();
  await expired.hooks.submitAsk('신청 기한은 언제인가요?');
  assert.equal(expired.hooks.askState.screen, 'UNAUTHENTICATED');
  assert.match(expired.elements['#ask-panel'].innerHTML, /로그인하기/);
});

test('keeps a failed question for explicit retry without claiming an answer', async () => {
  const { elements, hooks } = createHarness({ askFetch: () => { throw { type: 'network' }; } });
  await hooks.loadAskAuthentication();
  await hooks.submitAsk('휴학생도 신청할 수 있나요?');
  assert.equal(hooks.askState.screen, 'ERROR');
  assert.equal(hooks.askState.submittedQuestion, '휴학생도 신청할 수 있나요?');
  assert.match(elements['#ask-panel'].innerHTML, /같은 질문 다시 전송/);
  assert.doesNotMatch(elements['#ask-panel'].innerHTML, /복덩이 답변/);
});

test('prevents duplicate sends and ignores a late answer for a previous benefit', async () => {
  let resolveRequest;
  const { calls, hooks } = createHarness({ askFetch: () => new Promise((resolve) => { resolveRequest = resolve; }) });
  await hooks.loadAskAuthentication();
  const first = hooks.submitAsk('질문 하나');
  const second = hooks.submitAsk('질문 하나');
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(calls.length, 1);
  hooks.state.benefitId = 456;
  hooks.state.detail = { id: 456 };
  hooks.resetAskState(456);
  resolveRequest(response(200, askPayload()));
  await Promise.all([first, second]);
  assert.equal(hooks.askState.benefitId, 456);
  assert.equal(hooks.askState.response, null);
});

test('validates the exact response DTO and escapes answer markup', () => {
  const { hooks } = createHarness();
  assert.equal(hooks.isAskResponse(askPayload()), true);
  assert.equal(hooks.isAskResponse(askPayload({ remaining: '29' })), false);
  hooks.askState.screen = 'RESULT';
  hooks.askState.submittedQuestion = '<img src=x>';
  hooks.askState.response = askPayload({ answer: '<script>alert(1)</script>', sourceQuotes: ['<b>근거</b>'] });
  const markup = hooks.askPanelContent();
  assert.match(markup, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
  assert.match(markup, /&lt;b&gt;근거&lt;\/b&gt;/);
  assert.doesNotMatch(markup, /<script>/);
});
