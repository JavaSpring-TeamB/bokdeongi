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

function documents(overrides = {}) {
  return {
    documents: {
      groups: [{ place: '정부24', url: 'https://www.gov.kr', items: [{ documentTypeId: 31, name: '주민등록등본', issuer: '행정복지센터', onlineAvailable: true, issueMethod: '온라인', validityDays: 90, orDocs: [] }] }],
      forms: ['신청서'], unknown: ['추가 확인 서류'], contacts: [], total: 3
    },
    states: {
      '31': { prepared: false, reviewed: false, result: null, reason: null, issueDate: null, documentTypeId: null },
      'form:0': { prepared: false, reviewed: false, result: null, reason: null, issueDate: null, documentTypeId: null },
      'unknown:0': { prepared: false, reviewed: false, result: null, reason: null, issueDate: null, documentTypeId: null }
    },
    ...overrides
  };
}

function review(overrides = {}) {
  return {
    documentTypeId: 31, result: 'READY', ocrResult: 'PASS', recognizedType: '주민등록등본',
    issueDate: '2026-11-24', reason: '서류가 확인됐어요.', foundFields: ['세대주'], missingFields: [], ...overrides
  };
}

function missing(overrides = {}) {
  return { groups: [], forms: [], unknown: [], allReady: true, ...overrides };
}

function guide(overrides = {}) {
  return { method: 'ONLINE', applyUrl: 'https://www.gov.kr/apply', siteName: '정부24', detailUrl: null,
    steps: [{ order: 1, method: 'ONLINE', text: '로그인합니다.' }], notes: [], copyCards: [], visit: null, contacts: [], ...overrides };
}

function applicationList(overrides = {}) {
  const item = { id: 1, benefitId: 2, benefitName: '청년 지원', appStatus: 'IN_PROGRESS', stage: '준비 중', step: 1, completedItems: 0, totalItems: 4, progressPercent: 0, deadline: null, deadlineLabel: null, dDay: null };
  return { summary: { totalCount: 1, inProgressCount: 1, completedCount: 0, expiredCount: 0 }, inProgress: [item], completed: [], expired: [], ...overrides };
}

function response(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function harness(request = () => response(200, application())) {
  const requests = [];
  class FakeFormData {
    constructor() { this.values = []; }
    append(name, value) { this.values.push([name, value]); }
  }
  const auth = {
    refreshAuthentication: async () => 'authenticated',
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (url, options) => {
      requests.push({ url, options });
      return request(url, options);
    }
  };
  const window = { BokdeongiAuth: auth };
  const context = { AbortController, DOMException, FormData: FakeFormData, Map, Number, Object, Promise, Set, String, URLSearchParams, window };
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

test('loads documents and patches a URL-encoded server document key with CSRF', async () => {
  const { api, requests } = harness((url, options) => {
    if (options.method === 'GET') return response(200, documents());
    return response(200, documents({ states: { ...documents().states, 'form:0': { prepared: true, reviewed: true, result: null, reason: null, issueDate: null, documentTypeId: null } } }));
  });

  const loaded = await api.getDocuments(274);
  assert.equal(loaded.states['31'].prepared, false);
  const saved = await api.updateDocumentPrepared(274, 'form:0', true);
  assert.equal(saved.states['form:0'].prepared, true);
  assert.equal(requests[0].url, '/api/applications/274/documents');
  assert.equal(requests[0].options.method, 'GET');
  assert.equal(requests[1].url, '/api/applications/274/documents/form%3A0');
  assert.equal(requests[1].options.method, 'PATCH');
  assert.equal(requests[1].options.body, JSON.stringify({ prepared: true }));
  assert.deepEqual({ ...requests[1].options.headers }, { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
  await assert.rejects(api.updateDocumentPrepared(274, 'document:31', true), (error) => error.kind === 'INPUT');
  await assert.rejects(api.updateDocumentPrepared(274, '31', 'true'), (error) => error.kind === 'INPUT');
});

test('serializes document and application PATCH requests for the same application', async () => {
  const pending = [];
  const { api } = harness(() => new Promise((resolve) => pending.push(resolve)));
  const documentSave = api.updateDocumentPrepared(274, '31', true);
  const progressSave = api.updateApplication(274, { step: 3 });
  await new Promise((resolve) => setImmediate(resolve));

  pending[0](response(200, documents()));
  await documentSave;
  await new Promise((resolve) => setImmediate(resolve));
  pending[1](response(200, application({ step: 3 })));
  assert.equal((await progressSave).step, 3);
});

test('keeps the server document validation error instead of manufacturing a prepared state', async () => {
  const { api } = harness(() => response(400, { message: '이 신청에 필요한 서류가 아니에요: 31' }));
  await assert.rejects(api.updateDocumentPrepared(274, '31', true), (error) => {
    assert.equal(error.kind, 'INPUT');
    assert.equal(error.status, 400);
    assert.equal(error.message, '이 신청에 필요한 서류가 아니에요: 31');
    return true;
  });
});

test('uploads only a valid OCR file as multipart without a JSON content type', async () => {
  const file = { name: '등본.png', type: 'image/png', size: 1024 };
  const { api, requests } = harness(() => response(200, review()));
  const result = await api.reviewDocument(274, 31, file);

  assert.equal(result.result, 'READY');
  assert.equal(requests[0].url, '/api/applications/274/documents');
  assert.equal(requests[0].options.method, 'POST');
  assert.deepEqual({ ...requests[0].options.headers }, { 'X-XSRF-TOKEN': 'csrf-token' });
  assert.deepEqual(requests[0].options.body.values, [['file', file], ['documentTypeId', '31']]);
  await assert.rejects(api.reviewDocument(274, 'form:0', file), (error) => error.kind === 'INPUT');
  await assert.rejects(api.reviewDocument(274, 31, { name: 'scan.gif', type: 'image/gif', size: 1 }), (error) => error.kind === 'INPUT');
  await assert.rejects(api.reviewDocument(274, 31, { name: 'big.pdf', type: 'application/pdf', size: 10 * 1024 * 1024 + 1 }), (error) => error.kind === 'INPUT');
});

test('preserves OCR error codes and validates the missing-document response', async () => {
  const blocked = harness(() => response(403, { code: 'CONSENT_REQUIRED', message: '서류 처리에 동의해야 서류를 올릴 수 있어요.' }));
  await assert.rejects(blocked.api.reviewDocument(274, 31, { name: '등본.pdf', type: 'application/pdf', size: 10 }), (error) => {
    assert.equal(error.kind, 'FORBIDDEN');
    assert.equal(error.code, 'CONSENT_REQUIRED');
    return true;
  });

  const loaded = harness(() => response(200, missing({ allReady: false, groups: [{ issuer: '정부24', documents: [] }] })));
  assert.equal((await loaded.api.getMissingDocuments(274)).allReady, false);
  const malformed = harness(() => response(200, { groups: [] }));
  await assert.rejects(malformed.api.getMissingDocuments(274), (error) => error.kind === 'INVALID');
});

test('loads guide data and serializes the documented status DTO with other application saves', async () => {
  const { api, requests } = harness((url, options) => {
    if (options.method === 'GET') return response(200, guide());
    return response(200, application({ appStatus: 'SUBMITTED', step: 4 }));
  });
  assert.equal((await api.getApplicationGuide(274)).method, 'ONLINE');
  const updated = await api.updateApplicationStatus(274, 'SUBMITTED');
  assert.equal(updated.appStatus, 'SUBMITTED');
  assert.equal(requests[0].url, '/api/applications/274/guide');
  assert.equal(requests[1].url, '/api/applications/274/status');
  assert.equal(requests[1].options.body, JSON.stringify({ status: 'SUBMITTED' }));
  await assert.rejects(api.updateApplicationStatus(274, 'IN_PROGRESS'), (error) => error.kind === 'INPUT');
  const malformed = harness(() => response(200, { method: 'ONLINE' }));
  await assert.rejects(malformed.api.getApplicationGuide(274), (error) => error.kind === 'INVALID');
});

test('loads and validates the server-classified application lists without client filtering', async () => {
  const { api, requests } = harness(() => response(200, applicationList()));
  const result = await api.getApplicationList();
  assert.equal(result.inProgress[0].benefitName, '청년 지원');
  assert.equal(requests[0].url, '/api/applications');
  assert.equal(requests[0].options.method, 'GET');
  const malformed = harness(() => response(200, { summary: {}, inProgress: [], completed: [], expired: [] }));
  await assert.rejects(malformed.api.getApplicationList(), (error) => error.kind === 'INVALID');
});

test('forces a fresh list request after a status mutation instead of sharing an older list response', async () => {
  const { api, requests } = harness(() => response(200, applicationList()));
  await Promise.all([api.getApplicationList(), api.getApplicationList({ force: true })]);
  assert.equal(requests.length, 2);
  assert.equal(requests[0].url, '/api/applications');
  assert.equal(requests[1].url, '/api/applications');
});
