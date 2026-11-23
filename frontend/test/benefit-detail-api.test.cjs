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

function detailPayload(overrides = {}) {
  return {
    id: 123,
    title: '청년 주거 지원',
    status: 'ACTIVE',
    ended: false,
    summaryWho: '청년',
    summaryWhat: '월세를 지원합니다',
    summaryHow: null,
    summaryWhen: null,
    maxAmount: 200000,
    amountPeriod: 'MONTHLY',
    targetText: '만 19세 이상 34세 이하 청년',
    applyStart: '2026-01-01',
    applyEnd: '2026-12-31',
    applyPeriodText: '2026년 1월 1일 ~ 12월 31일',
    contentText: '월세를 지원합니다.',
    criteriaText: '소득 기준을 충족해야 합니다.',
    applyMethodText: '공식 사이트에서 온라인으로 신청합니다.',
    requiredDocsText: '신청서 및 증빙 서류',
    hashtags: '["청년","주거지원"]',
    detailUrl: 'https://agency.example/notice',
    onlineApplyUrl: 'https://apply.example/form',
    syncedAt: '2026-11-23T10:30:00',
    missingFields: [],
    ...overrides
  };
}

function createHarness(fetchImplementation) {
  const elements = {
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
  vm.runInContext(`${source}\n;globalThis.__benefitDetailHooks = { amountText, getPageMarkup, isBenefitDetailResponse, isSafeExternalUrl, loadDetail, parseBenefitId, parseHashtags, periodText, setup, state };`, context);
  return { elements, hooks: context.__benefitDetailHooks, requests, window };
}

test('accepts only positive, safe benefit IDs from the URL', () => {
  const { hooks } = createHarness(() => response(200, detailPayload()));
  assert.equal(hooks.parseBenefitId('123'), 123);
  assert.equal(hooks.parseBenefitId('0'), null);
  assert.equal(hooks.parseBenefitId('-2'), null);
  assert.equal(hooks.parseBenefitId('1.2'), null);
  assert.equal(hooks.parseBenefitId('99999999999999999999'), null);
});

test('requests the public detail API for the real benefit ID and renders DTO fields', async () => {
  const { elements, hooks, requests } = createHarness(() => response(200, detailPayload()));

  await hooks.loadDetail(123);

  assert.ok(requests.length >= 1);
  assert.equal(requests[0].url, 'http://backend.test/api/benefits/123');
  assert.equal(requests[0].options.credentials, 'include');
  assert.equal(requests[0].options.headers, undefined);
  assert.equal(hooks.state.screen, 'RESULT');
  assert.match(elements['#detail-banner-copy'].innerHTML, /청년 주거 지원/);
  assert.match(elements['#detail-page'].innerHTML, /월세를 지원합니다/);
  assert.match(elements['#detail-page'].innerHTML, /200,000원/);
  assert.match(elements['#detail-page'].innerHTML, /2026-01-01 ~ 2026-12-31/);
  assert.match(elements['#detail-page'].innerHTML, /https:\/\/agency\.example\/notice/);
  assert.match(elements['#detail-page'].innerHTML, /https:\/\/apply\.example\/form/);
});

test('does not substitute mock data for missing values or unsafe links', () => {
  const { hooks } = createHarness(() => response(200, detailPayload()));
  const markup = hooks.getPageMarkup(detailPayload({
    title: '<img src=x>',
    targetText: null,
    maxAmount: null,
    applyStart: null,
    applyEnd: null,
    applyPeriodText: null,
    requiredDocsText: null,
    onlineApplyUrl: 'javascript:alert(1)',
    detailUrl: 'javascript:alert(1)',
    missingFields: ['maxAmount', 'targetText', 'applyPeriodText', 'requiredDocsText']
  }));

  assert.match(markup, /공고 확인 필요/);
  assert.match(markup, /공식 공고문 없음/);
  assert.match(markup, /공식 신청 링크 없음/);
  assert.match(markup, /공식 공고문에서 확인해 주세요/);
  assert.doesNotMatch(markup, /javascript:alert/);
  assert.doesNotMatch(markup, /주민등록등본/);
  assert.doesNotMatch(markup, /만 19세 이상 34세 이하 청년/);
});

test('disables the application link when the API marks a benefit as ended', () => {
  const { hooks } = createHarness(() => response(200, detailPayload()));
  const markup = hooks.getPageMarkup(detailPayload({ ended: true, status: 'EXPIRED' }));

  assert.match(markup, /종료된 혜택입니다/);
  assert.doesNotMatch(markup, /href="https:\/\/apply\.example\/form"/);
});

test('renders a not-found state for API 404 without a detail fallback', async () => {
  const { elements, hooks } = createHarness(() => response(404, { detail: '존재하지 않는 혜택입니다.' }));

  await hooks.loadDetail(404);

  assert.equal(hooks.state.screen, 'ERROR');
  assert.equal(hooks.state.errorKind, 'NOT_FOUND');
  assert.match(elements['#detail-page'].innerHTML, /혜택 정보를 찾을 수 없어요/);
  assert.doesNotMatch(elements['#detail-page'].innerHTML, /청년 주거 지원/);
});

test('renders a network error and keeps no old benefit response', async () => {
  const { elements, hooks } = createHarness(() => { throw new TypeError('Failed to fetch'); });
  hooks.state.detail = detailPayload();

  await hooks.loadDetail(123);

  assert.equal(hooks.state.screen, 'ERROR');
  assert.equal(hooks.state.errorKind, 'NETWORK');
  assert.equal(hooks.state.detail, null);
  assert.match(elements['#detail-page'].innerHTML, /네트워크에 연결하지 못했어요/);
});

test('does not allow a late response for an earlier ID to overwrite the current detail', async () => {
  let resolveFirst;
  const first = new Promise((resolve) => { resolveFirst = resolve; });
  let call = 0;
  const { hooks } = createHarness(() => {
    call += 1;
    return call === 1 ? first : response(200, detailPayload({ id: 2, title: '두 번째 혜택' }));
  });

  const pendingFirst = hooks.loadDetail(1);
  const second = hooks.loadDetail(2);
  await second;
  resolveFirst(response(200, detailPayload({ id: 1, title: '첫 번째 혜택' })));
  await pendingFirst;

  assert.equal(hooks.state.detail.id, 2);
  assert.equal(hooks.state.detail.title, '두 번째 혜택');
});

test('renders separate missing and invalid ID states before requesting the API', () => {
  const missing = createHarness(() => response(200, detailPayload()));
  missing.window.location.search = '';
  missing.hooks.setup();
  assert.equal(missing.hooks.state.errorKind, 'ID_MISSING');
  assert.match(missing.elements['#detail-page'].innerHTML, /혜택 ID가 없어요/);

  const invalid = createHarness(() => response(200, detailPayload()));
  invalid.window.location.search = '?id=not-a-number';
  invalid.hooks.setup();
  assert.equal(invalid.hooks.state.errorKind, 'ID_INVALID');
  assert.match(invalid.elements['#detail-page'].innerHTML, /잘못된 혜택 ID예요/);
});

test('validates the DTO minimum shape and parses API hashtag strings', () => {
  const { hooks } = createHarness(() => response(200, detailPayload()));
  assert.equal(hooks.isBenefitDetailResponse(detailPayload()), true);
  assert.equal(hooks.isBenefitDetailResponse({ title: 'missing id' }), false);
  assert.equal(hooks.isSafeExternalUrl('https://example.com'), true);
  assert.equal(hooks.isSafeExternalUrl('javascript:alert(1)'), false);
  assert.deepEqual([...hooks.parseHashtags('["청년", "주거지원"]')], ['청년', '주거지원']);
  assert.equal(hooks.amountText(detailPayload()), '200,000원 / MONTHLY');
  assert.equal(hooks.periodText(detailPayload()), '2026-01-01 ~ 2026-12-31');
});
