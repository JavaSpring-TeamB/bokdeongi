const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'signup-account.js'), 'utf8');

function jsonResponse(status, payload) {
  return { status, ok: status >= 200 && status < 300, json: async () => payload };
}

function createHarness({ agreed, codes, responses }) {
  const requests = [];
  const queue = [...responses];
  const sensitiveConsent = { checked: agreed };
  const document = {
    addEventListener() {},
    querySelector(selector) {
      if (selector === '[data-signup-step="consent"] [data-consent-type="SENSITIVE"]') return sensitiveConsent;
      return null;
    },
    querySelectorAll() { return []; }
  };
  const auth = {
    getAuthenticationState: () => 'authenticated',
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (url, options = {}) => {
      requests.push({ url, options });
      return queue.shift();
    }
  };
  const context = {
    URLSearchParams,
    Set,
    Number,
    document,
    window: { location: { search: '', pathname: '/signup-account.html', hash: '' }, BokdeongiAuth: auth },
    console: { log() {}, warn() {}, error() {} }
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__step4Hooks={saveSensitiveProfile,signupState,workflow};`, context);
  context.__step4Hooks.signupState.sensitiveCodes = codes;
  return { requests, hooks: context.__step4Hooks };
}

test('stores selected sensitive codes after obtaining the server-issued consent ID', async () => {
  const harness = createHarness({
    agreed: true,
    codes: ['JA0301', 'JA0301', 'JA0328'],
    responses: [
      jsonResponse(200, { consents: [{ consentType: 'SENSITIVE', agreed: true, docVersion: 'v1.0', consentId: 17 }] }),
      jsonResponse(200, { profileStep: 4 })
    ]
  });

  await harness.hooks.saveSensitiveProfile();

  assert.equal(harness.requests[0].url, '/api/auth/consents');
  assert.equal(harness.requests[1].url, '/api/me/profile/step4');
  assert.deepEqual(JSON.parse(harness.requests[1].options.body), {
    sensitiveAgreed: true,
    consentDocVersion: 'v1.0',
    consentId: 17,
    sensitiveCodes: ['JA0301', 'JA0328']
  });
  assert.equal(harness.requests[1].options.headers['X-XSRF-TOKEN'], 'csrf-token');
  assert.equal(harness.hooks.workflow.sensitiveProfileSaved, true);
});

test('stores an explicit non-consent Step 4 request with null sensitive codes', async () => {
  const harness = createHarness({
    agreed: false,
    codes: ['JA0301'],
    responses: [
      jsonResponse(200, { consents: [{ consentType: 'SENSITIVE', agreed: false, docVersion: 'v1.0', consentId: 18 }] }),
      jsonResponse(200, { profileStep: 4 })
    ]
  });

  await harness.hooks.saveSensitiveProfile();

  assert.deepEqual(JSON.parse(harness.requests[1].options.body), {
    sensitiveAgreed: false,
    consentDocVersion: 'v1.0',
    consentId: 18,
    sensitiveCodes: null
  });
});

test('allows consent with no selected sensitive code by sending null', async () => {
  const harness = createHarness({
    agreed: true,
    codes: null,
    responses: [
      jsonResponse(200, { consents: [{ consentType: 'SENSITIVE', agreed: true, docVersion: 'v1.0', consentId: 20 }] }),
      jsonResponse(200, { profileStep: 4 })
    ]
  });

  await harness.hooks.saveSensitiveProfile();

  assert.equal(JSON.parse(harness.requests[1].options.body).sensitiveCodes, null);
});

test('does not call Step 4 when the server consent state conflicts with the signup selection', async () => {
  const harness = createHarness({
    agreed: true,
    codes: [],
    responses: [jsonResponse(200, { consents: [{ consentType: 'SENSITIVE', agreed: false, docVersion: 'v1.0', consentId: 19 }] })]
  });

  await assert.rejects(harness.hooks.saveSensitiveProfile(), /동의 상태/);
  assert.equal(harness.requests.length, 1);
});
