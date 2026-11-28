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

function eligibilityPayload(overrides = {}) {
  return {
    benefitId: 123,
    title: '청년 주거 지원',
    result: 'ELIGIBLE',
    summary: '모든 조건을 충족해요',
    groups: [{
      group: 0,
      alternative: false,
      title: '공통 조건',
      status: 'MET',
      conditions: [{
        field: 'AGE',
        label: '나이',
        status: 'MET',
        expected: '만 19세 이상 34세 이하',
        actual: '비공개',
        sourceQuote: '만 19세 이상 34세 이하 청년',
        checkType: null,
        guide: null
      }]
    }],
    questions: [],
    next: { message: '다음 단계에서 낼 서류를 준비해요' },
    ...overrides
  };
}

function createHarness({ authenticationState = 'authenticated', fetchImplementation = () => response(200, eligibilityPayload()) } = {}) {
  const elements = {
    '#eligibility-panel': element(),
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
  const auth = {
    refreshAuthentication: async () => authenticationState,
    authenticatedFetch: async (requestPath, options) => {
      requests.push({ requestPath, options });
      return fetchImplementation(requestPath, options);
    }
  };
  const window = {
    location: { search: '?id=123', href: 'http://frontend.test/benefit-detail.html?id=123' },
    BokdeongiAuth: auth
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
    setTimeout,
    clearTimeout,
    window
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__eligibilityHooks = { eligibilityPanelContent, eligibilityState, isEligibilityResponse, loadEligibility, resetEligibilityState, state };`, context);
  context.__eligibilityHooks.state.benefitId = 123;
  context.__eligibilityHooks.state.detail = { id: 123 };
  return { elements, hooks: context.__eligibilityHooks, requests };
}

test('uses authenticatedFetch with the actual benefit ID and renders rule-engine conditions', async () => {
  const { elements, hooks, requests } = createHarness();

  await hooks.loadEligibility();

  assert.equal(requests.length, 1);
  assert.equal(requests[0].requestPath, '/api/benefits/123/eligibility');
  assert.equal(hooks.eligibilityState.screen, 'RESULT');
  assert.match(elements['#eligibility-panel'].innerHTML, /해당 가능성이 있어요/);
  assert.match(elements['#eligibility-panel'].innerHTML, /class="eligibility-result" data-result="ELIGIBLE"/);
  assert.match(elements['#eligibility-panel'].innerHTML, /class="eligibility-badge" data-status="MET"/);
  assert.match(elements['#eligibility-panel'].innerHTML, /모든 조건을 충족해요/);
  assert.match(elements['#eligibility-panel'].innerHTML, /나이/);
  assert.match(elements['#eligibility-panel'].innerHTML, /충족/);
  assert.match(elements['#eligibility-panel'].innerHTML, /만 19세 이상 34세 이하 청년/);
  assert.doesNotMatch(elements['#eligibility-panel'].innerHTML, /비공개/);
});

test('renders CONDITIONAL, profile checks, and actual group condition statuses', async () => {
  const { elements, hooks } = createHarness({
    fetchImplementation: () => response(200, eligibilityPayload({
      result: 'CONDITIONAL',
      summary: '확인할 조건이 2개 있어요',
      groups: [{
        group: 1,
        alternative: true,
        title: '다음 중 하나 (1)',
        status: 'CHECK',
        conditions: [{
          field: 'INCOME', label: '소득', status: 'CHECK', expected: '기준 중위소득 60% 이하', actual: null,
          sourceQuote: '소득 기준을 충족해야 합니다.', checkType: 'PROFILE', guide: '소득 정보를 입력해 주세요.'
        }, {
          field: 'AGE', label: '나이', status: 'NOT_MET', expected: '만 34세 이하', actual: null,
          sourceQuote: null, checkType: null, guide: null
        }]
      }]
    }))
  });

  await hooks.loadEligibility();

  assert.match(elements['#eligibility-panel'].innerHTML, /추가 확인이 필요해요/);
  assert.match(elements['#eligibility-panel'].innerHTML, /class="eligibility-result" data-result="CONDITIONAL"/);
  assert.match(elements['#eligibility-panel'].innerHTML, /class="eligibility-badge" data-status="CHECK"/);
  assert.match(elements['#eligibility-panel'].innerHTML, /class="eligibility-badge" data-status="NOT_MET"/);
  assert.match(elements['#eligibility-panel'].innerHTML, /확인 필요/);
  assert.match(elements['#eligibility-panel'].innerHTML, /미충족/);
  assert.match(elements['#eligibility-panel'].innerHTML, /프로필 입력으로 이동/);
  assert.match(elements['#eligibility-panel'].innerHTML, /소득 정보를 입력해 주세요/);
  assert.match(elements['#eligibility-panel'].innerHTML, /근거 확인 필요/);
});

test('renders INELIGIBLE from the API without recalculating it in the client', async () => {
  const { elements, hooks } = createHarness({
    fetchImplementation: () => response(200, eligibilityPayload({ result: 'INELIGIBLE', summary: '충족하지 않는 조건이 있어요' }))
  });

  await hooks.loadEligibility();

  assert.equal(hooks.eligibilityState.response.result, 'INELIGIBLE');
  assert.match(elements['#eligibility-panel'].innerHTML, /class="eligibility-result" data-result="INELIGIBLE"/);
  assert.match(elements['#eligibility-panel'].innerHTML, /현재 조건에서는 해당하지 않아요/);
});

test('shows a login prompt for unauthenticated users without calling the eligibility endpoint', async () => {
  const { elements, hooks, requests } = createHarness({ authenticationState: 'unauthenticated' });

  await hooks.loadEligibility();

  assert.equal(requests.length, 0);
  assert.equal(hooks.eligibilityState.screen, 'UNAUTHENTICATED');
  assert.match(elements['#eligibility-panel'].innerHTML, /로그인하면 내 조건에 맞는 혜택인지 확인할 수 있어요/);
});

test('separates no-result, network, and invalid response states with retry-capable output', async () => {
  const noResult = createHarness({ fetchImplementation: () => response(404, { detail: '판정 가능한 혜택이 없습니다.' }) });
  await noResult.hooks.loadEligibility();
  assert.equal(noResult.hooks.eligibilityState.screen, 'NO_RESULT');
  assert.match(noResult.elements['#eligibility-panel'].innerHTML, /판정 결과를 확인할 수 없습니다/);

  const network = createHarness({ fetchImplementation: () => { throw { type: 'network' }; } });
  await network.hooks.loadEligibility();
  assert.equal(network.hooks.eligibilityState.screen, 'ERROR');
  assert.equal(network.hooks.eligibilityState.errorKind, 'NETWORK');
  assert.match(network.elements['#eligibility-panel'].innerHTML, /다시 시도/);

  const invalid = createHarness({ fetchImplementation: () => response(200, { benefitId: 123, result: 'ELIGIBLE' }) });
  await invalid.hooks.loadEligibility();
  assert.equal(invalid.hooks.eligibilityState.errorKind, 'INVALID');
  assert.match(invalid.elements['#eligibility-panel'].innerHTML, /형식이 올바르지 않습니다/);
});

test('reuses completed eligibility results and ignores a late result for a previous benefit', async () => {
  let resolveFirst;
  const first = new Promise((resolve) => { resolveFirst = resolve; });
  let calls = 0;
  const { hooks, requests } = createHarness({
    fetchImplementation: () => {
      calls += 1;
      return calls === 1 ? first : response(200, eligibilityPayload({ benefitId: 456 }));
    }
  });

  const pending = hooks.loadEligibility();
  hooks.state.benefitId = 456;
  hooks.resetEligibilityState(456);
  hooks.state.detail = { id: 456 };
  await hooks.loadEligibility();
  resolveFirst(response(200, eligibilityPayload({ benefitId: 123, result: 'INELIGIBLE' })));
  await pending;

  assert.equal(requests.length, 2);
  assert.equal(hooks.eligibilityState.response.benefitId, 456);
  await hooks.loadEligibility();
  assert.equal(requests.length, 2);
});

test('accepts only the actual DTO result, group, and condition status values', () => {
  const { hooks } = createHarness();
  assert.equal(hooks.isEligibilityResponse(eligibilityPayload()), true);
  assert.equal(hooks.isEligibilityResponse(eligibilityPayload({ result: 'PASS' })), false);
  assert.equal(hooks.isEligibilityResponse(eligibilityPayload({ groups: [{ ...eligibilityPayload().groups[0], status: 'PASS' }] })), false);
  assert.equal(hooks.isEligibilityResponse(eligibilityPayload({ groups: [{ ...eligibilityPayload().groups[0], conditions: [{ ...eligibilityPayload().groups[0].conditions[0], status: 'UNKNOWN' }] }] })), false);
});
