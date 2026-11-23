const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-list.js'), 'utf8');

function element() {
  return {
    disabled: false,
    innerHTML: '',
    textContent: '',
    title: '',
    value: '',
    options: [],
    setAttribute() {},
    addEventListener() {}
  };
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function validPayload(overrides = {}) {
  return {
    keyword: '청년',
    items: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    size: 6,
    hasNext: false,
    suggestedHashtags: [],
    ...overrides
  };
}

function createHarness(fetchImplementation) {
  const elements = {
    '#toast': element(),
    '#result-summary': element(),
    '#result-area': element(),
    '#benefit-search-input': element(),
    '#search-message': element(),
    '#filter-groups': element(),
    '#filter-reset': element(),
    '#sort-order': element(),
    '#benefit-search-form': element()
  };
  const requests = [];
  const document = {
    addEventListener() {},
    querySelector: (selector) => elements[selector] || null
  };
  const window = {
    location: { search: '' },
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
  vm.runInContext(`${source}\n;globalThis.__benefitListHooks = { applyPeriod, buildRequestPath, card, isBenefitSearchResponse, isValidSearchQuery, normalizeQuery, parseHashtags, requestBenefits, runSearch, state };`, context);
  return { elements, hooks: context.__benefitListHooks, requests };
}

test('builds the public GET request with relevance sort and zero-based page', () => {
  const { hooks } = createHarness(() => response(200, validPayload()));

  assert.equal(
    hooks.buildRequestPath('청년 지원', '', 2),
    '/api/benefits?sort=relevance&page=1&size=6&q=%EC%B2%AD%EB%85%84+%EC%A7%80%EC%9B%90'
  );
  assert.equal(
    hooks.buildRequestPath('', '주거지원', 1),
    '/api/benefits?sort=relevance&page=0&size=6&hashtag=%EC%A3%BC%EA%B1%B0%EC%A7%80%EC%9B%90'
  );
});

test('trims a query, resets to page one, and renders only API response data', async () => {
  const payload = validPayload({
    items: [{
      id: 88,
      title: '청년 주거 지원',
      summaryWho: '청년',
      summaryWhat: '월세를 지원합니다',
      summaryHow: null,
      summaryWhen: null,
      orgName: '복지부',
      hashtags: '["청년","주거지원"]',
      eligibilityResult: null,
      applyStart: '2026-01-01',
      applyEnd: '2026-12-31'
    }],
    totalElements: 7,
    totalPages: 2,
    page: 0,
    hasNext: true
  });
  const { elements, hooks, requests } = createHarness(() => response(200, payload));
  elements['#benefit-search-input'].value = '  청년  ';
  hooks.state.currentPage = 2;

  hooks.runSearch();
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(requests.length, 1);
  assert.match(requests[0].url, /q=%EC%B2%AD%EB%85%84/);
  assert.match(requests[0].url, /page=0/);
  assert.equal(requests[0].options.credentials, 'include');
  assert.equal(requests[0].options.headers, undefined);
  assert.equal(hooks.state.currentPage, 1);
  assert.match(elements['#result-area'].innerHTML, /청년 주거 지원/);
  assert.match(elements['#result-area'].innerHTML, /benefit-detail\.html\?id=88/);
  assert.match(elements['#result-area'].innerHTML, /#주거지원/);
  assert.match(elements['#result-summary'].textContent, /7건 · 2페이지/);
});

test('does not render missing optional card fields or untrusted markup', () => {
  const { hooks } = createHarness(() => response(200, validPayload()));
  const markup = hooks.card({
    id: 7,
    title: '<img src=x>',
    summaryWho: null,
    summaryWhat: null,
    summaryHow: null,
    summaryWhen: null,
    orgName: null,
    hashtags: null,
    eligibilityResult: null,
    applyStart: null,
    applyEnd: null
  });

  assert.match(markup, /&lt;img src=x&gt;/);
  assert.doesNotMatch(markup, /신청 기간/);
  assert.doesNotMatch(markup, /benefit-card__hashtags/);
  assert.doesNotMatch(markup, /제목 정보 없음/);
});

test('accepts the actual list response shape and handles DTO hashtag strings', () => {
  const { hooks } = createHarness(() => response(200, validPayload()));
  assert.equal(hooks.isBenefitSearchResponse(validPayload()), true);
  assert.equal(hooks.isBenefitSearchResponse({ ...validPayload(), hasNext: 'false' }), false);
  assert.deepEqual([...hooks.parseHashtags('["청년", "월세"]')], ['청년', '월세']);
  assert.deepEqual([...hooks.parseHashtags('청년, 월세')], ['청년', '월세']);
  assert.equal(hooks.applyPeriod({ applyStart: null, applyEnd: '2026-12-31' }), '2026-12-31까지');
});

test('keeps a later search result when an earlier request resolves late', async () => {
  let resolveFirst;
  const first = new Promise((resolve) => { resolveFirst = resolve; });
  let call = 0;
  const { elements, hooks } = createHarness(() => {
    call += 1;
    return call === 1 ? first : response(200, validPayload({ keyword: '월세', items: [], totalElements: 0 }));
  });

  hooks.state.query = '청년';
  const pendingFirst = hooks.requestBenefits(1);
  hooks.state.query = '월세';
  const second = hooks.requestBenefits(1);
  await second;
  resolveFirst(response(200, validPayload({ keyword: '청년', totalElements: 99, totalPages: 10, hasNext: true })));
  await pendingFirst;

  assert.equal(hooks.state.response.keyword, '월세');
  assert.match(elements['#result-summary'].textContent, /0건/);
});

test('reports a network failure without falling back to mock benefits', async () => {
  const { elements, hooks } = createHarness(() => { throw new TypeError('Failed to fetch'); });
  hooks.state.query = '청년';

  await hooks.requestBenefits(1);

  assert.equal(hooks.state.screen, 'ERROR');
  assert.equal(hooks.state.errorKind, 'NETWORK');
  assert.match(elements['#result-area'].innerHTML, /네트워크에 연결하지 못했어요/);
});
