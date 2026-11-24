const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'application-api.js'), 'utf8');

function application(overrides = {}) {
  return {
    id: 274, benefitId: 1234, appStatus: 'IN_PROGRESS', step: 2,
    appAnswers: {}, docChecks: {}, startedApiModifiedAt: null,
    announcementChanged: false, stage: '서류 준비', completedItems: 1,
    totalItems: 4, progressPercent: 25, ...overrides
  };
}

function response(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function harness(request = () => response(200, application())) {
  const requests = [];
  const auth = {
    refreshAuthentication: async () => 'authenticated',
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (url, options) => {
      requests.push({ url, options });
      return request(url, options);
    }
  };
  const window = { BokdeongiAuth: auth };
  const context = { AbortController, DOMException, Map, Number, Object, Promise, Set, String, URLSearchParams, window };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context);
  return { api: window.BokdeongiApplication, auth, requests };
}

test('starts once per benefit, reuses a returned application, and maps the server step', async () => {
  let calls = 0;
  const { api, requests } = harness(async () => {
    calls += 1;
    return response(200, application({ id: 91, step: 3 }));
  });

  const first = api.startApplication(1234);
  const second = api.startApplication('1234');
  assert.strictEqual(first, second);
  const result = await first;

  assert.equal(calls, 1);
  assert.equal(result.id, 91);
  assert.equal(result.benefitId, 1234);
  assert.equal(api.assistantUrl(result.step, result.id), 'assist-review.html?applicationId=91');
  assert.equal(requests[0].url, '/api/applications');
  assert.equal(requests[0].options.method, 'POST');
  assert.deepEqual({ ...requests[0].options.headers }, { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
  assert.equal(requests[0].options.body, JSON.stringify({ benefitId: 1234 }));
});

test('retrieves only a valid application ID and validates the server response', async () => {
  const { api, requests } = harness(() => response(200, application({ id: 275, benefitId: 900 })));
  const result = await api.getApplication(275);
  assert.equal(result.id, 275);
  assert.equal(result.benefitId, 900);
  assert.equal(requests[0].url, '/api/applications/275');

  await assert.rejects(api.getApplication('benefit-900'), (error) => error.kind === 'INPUT');
  const malformed = harness(() => response(200, { id: 1, benefitId: 2 }));
  await assert.rejects(malformed.api.getApplication(1), (error) => error.kind === 'INVALID');
});

test('saves only supplied progress fields with CSRF and preserves their omission', async () => {
  const { api, requests } = harness(() => response(200, application({ step: 3, appAnswers: { eligibilityConfirmed: true } })));
  const result = await api.updateApplication(274, { step: 3, appAnswers: { eligibilityConfirmed: true } });

  assert.equal(result.step, 3);
  assert.equal(requests[0].url, '/api/applications/274');
  assert.equal(requests[0].options.method, 'PATCH');
  assert.equal(requests[0].options.body, JSON.stringify({ step: 3, appAnswers: { eligibilityConfirmed: true } }));
  await assert.rejects(api.updateApplication(274, { step: 5 }), (error) => error.kind === 'INPUT');
  await assert.rejects(api.updateApplication(274, { step: 2, docChecks: [] }), (error) => error.kind === 'INPUT');
});

test('maps server failures without manufacturing application data', async () => {
  const conflict = harness(() => response(409, { message: '진행 중인 신청만 수정할 수 있습니다.' }));
  await assert.rejects(conflict.api.updateApplication(274, { step: 2 }), (error) => {
    assert.equal(error.kind, 'CONFLICT');
    assert.equal(error.status, 409);
    return true;
  });

  const unauthenticated = harness();
  unauthenticated.auth.refreshAuthentication = async () => 'unauthenticated';
  await assert.rejects(unauthenticated.api.startApplication(1234), (error) => error.kind === 'UNAUTHENTICATED');
});

test('serializes progress saves for the same application before sending the next request', async () => {
  const pending = [];
  const { api } = harness(() => new Promise((resolve) => pending.push(resolve)));
  const first = api.updateApplication(274, { step: 2 });
  const second = api.updateApplication(274, { step: 3 });
  await new Promise((resolve) => setImmediate(resolve));

  pending[0](response(200, application({ step: 2 })));
  assert.equal((await first).step, 2);
  await new Promise((resolve) => setImmediate(resolve));
  pending[1](response(200, application({ step: 3 })));
  assert.equal((await second).step, 3);
});
