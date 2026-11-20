const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(
  path.resolve(__dirname, '../js/signup-account.js'),
  'utf8'
);

class FakeElement {
  constructor() {
    this.hidden = false;
    this.disabled = false;
    this.textContent = '';
    this.attributes = new Map();
    this.classList = { add() {}, remove() {}, toggle() {} };
  }

  focus() {}
  setAttribute(name, value) { this.attributes.set(name, String(value)); }
  removeAttribute(name) { this.attributes.delete(name); }
  querySelector() { return null; }
}

function createHarness(responses) {
  const elements = new Map();
  const completeStep = new FakeElement();
  const getElement = (selector) => {
    if (!elements.has(selector)) elements.set(selector, new FakeElement());
    return elements.get(selector);
  };

  const document = {
    body: new FakeElement(),
    title: '복덩이',
    addEventListener() {},
    querySelector(selector) {
      if (selector === '[data-signup-step="complete"]') return completeStep;
      return getElement(selector);
    },
    querySelectorAll(selector) {
      return selector === '[data-signup-step]' ? [completeStep] : [];
    }
  };

  const requests = [];
  const queue = [...responses];
  const context = {
    URLSearchParams,
    document,
    fetch: async (url, options = {}) => {
      requests.push({ url, options });
      const response = queue.shift();
      if (response instanceof Error) throw response;
      return response;
    },
    window: {
      location: { search: '?kakaoSignup=true', pathname: '/signup-account.html', hash: '', assign() {} },
      history: { replaceState() {} },
      addEventListener() {}
    },
    console: { log() {}, warn() {}, error() {} }
  };
  context.window.BokdeongiAuth = {
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`,
    getAuthenticationState: () => 'unauthenticated'
  };
  context.globalThis = context;

  vm.createContext(context);
  vm.runInContext(
    `${source}\n;globalThis.__testHooks={kakaoSignup,signupState,submitKakaoSignup};`,
    context
  );

  context.__testHooks.kakaoSignup.active = true;
  context.__testHooks.signupState.consents = [
    { consentType: 'TERMS', docVersion: '1.0', agreed: true },
    { consentType: 'PRIVACY', docVersion: '1.0', agreed: true },
    { consentType: 'OPTIONAL_INFO', docVersion: '1.0', agreed: false },
    { consentType: 'SENSITIVE', docVersion: '1.0', agreed: false }
  ];

  return { context, elements, requests };
}

function jsonResponse(status, payload) {
  return { status, ok: status >= 200 && status < 300, json: async () => payload };
}

test('uses Kakao CSRF and sends only consents on a successful signup', async () => {
  const harness = createHarness([
    jsonResponse(200, { headerName: 'X-XSRF-TOKEN', token: 'test-csrf' }),
    jsonResponse(201, { userId: 1, email: null, message: 'ok' })
  ]);

  await harness.context.__testHooks.submitKakaoSignup();

  assert.equal(harness.requests.length, 2);
  assert.equal(harness.requests[0].url, 'http://backend.test/api/auth/kakao/csrf');
  assert.equal(harness.requests[0].options.credentials, 'include');
  assert.equal(harness.requests[1].url, 'http://backend.test/api/auth/kakao/signup');
  assert.equal(harness.requests[1].options.credentials, 'include');
  assert.equal(harness.requests[1].options.headers['X-XSRF-TOKEN'], 'test-csrf');
  assert.equal(harness.requests[1].options.headers.Authorization, undefined);
  assert.deepEqual(
    JSON.parse(harness.requests[1].options.body),
    { consents: harness.context.__testHooks.signupState.consents }
  );
  assert.equal(harness.context.__testHooks.kakaoSignup.completed, true);
});

test('blocks another POST after a CSRF-protected signup failure', async () => {
  const harness = createHarness([
    jsonResponse(200, { headerName: 'X-XSRF-TOKEN', token: 'test-csrf' }),
    jsonResponse(403, { code: 'COMMON_001' })
  ]);

  await harness.context.__testHooks.submitKakaoSignup();
  await harness.context.__testHooks.submitKakaoSignup();

  assert.equal(harness.requests.length, 2);
  assert.equal(harness.context.__testHooks.kakaoSignup.retryRequired, true);
  assert.equal(harness.elements.get('#kakao-signup-restart').hidden, false);
});

test('does not send a POST when Kakao CSRF issuance fails', async () => {
  const harness = createHarness([jsonResponse(403, { code: 'COMMON_001' })]);

  await harness.context.__testHooks.submitKakaoSignup();

  assert.equal(harness.requests.length, 1);
  assert.equal(harness.context.__testHooks.kakaoSignup.retryRequired, true);
});

test('does not retry a signup POST whose network outcome is unknown', async () => {
  const harness = createHarness([
    jsonResponse(200, { headerName: 'X-XSRF-TOKEN', token: 'test-csrf' }),
    new Error('network')
  ]);

  await harness.context.__testHooks.submitKakaoSignup();
  await harness.context.__testHooks.submitKakaoSignup();

  assert.equal(harness.requests.length, 2);
  assert.equal(harness.context.__testHooks.kakaoSignup.outcomeUnknown, true);
});
