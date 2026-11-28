const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-list.js'), 'utf8');

function element() {
  return { innerHTML: '', textContent: '', value: '', options: [], addEventListener() {}, setAttribute() {} };
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function searchPayload(overrides = {}) {
  return {
    keyword: '', items: [], totalElements: 0, totalPages: 0,
    page: 0, size: 6, hasNext: false, suggestedHashtags: [], ...overrides
  };
}

function createHarness(search = '', searchFetch = () => response(200, searchPayload())) {
  const elements = {
    '#toast': element(), '#result-summary': element(), '#result-area': element(),
    '#benefit-search-input': element(), '#search-message': element(),
    '#filter-groups': element(), '#filter-reset': element(), '#sort-order': element(),
    '#benefit-search-form': element(), '#hashtag-suggestions': element(),
    '#filter-group-content-status': element(), '#filter-group-content-field': element(), '#filter-group-content-target': element()
  };
  const requests = [];
  const historyCalls = [];
  const window = {
    location: { search, pathname: '/benefit-list.html' },
    history: {
      pushState: (...args) => historyCalls.push({ method: 'push', args }),
      replaceState: (...args) => historyCalls.push({ method: 'replace', args })
    },
    BokdeongiAuth: { getApiUrl: (requestPath) => `http://backend.test${requestPath}` }
  };
  const context = {
    AbortController, Array, JSON, Map, Number, Object, Set, String, URLSearchParams,
    console: { error() {} },
    document: { addEventListener() {}, querySelector: (selector) => elements[selector] || null },
    fetch: async (url, options) => {
      requests.push({ url, options });
      return searchFetch(url, options);
    },
    setTimeout, clearTimeout, window
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__filterHooks = { applyKeywordFromUrl, buildRequestPath, filterGroupExpanded, hashtagState, renderFilters, requestBenefits, runSearch, searchByHashtag, state, toggleFilterGroup, updateFilterSelection };`, context);
  return { elements, historyCalls, hooks: context.__filterHooks, requests, window };
}

test('repeats selected status, field, and target parameters in the public search request', () => {
  const { hooks } = createHarness();
  const requestPath = hooks.buildRequestPath('청년', '', 1, {
    statuses: ['OPEN', 'UPCOMING'], fields: ['주거', '교육'], targets: ['청년']
  });

  assert.equal(
    requestPath,
    '/api/benefits?sort=relevance&page=0&size=6&q=%EC%B2%AD%EB%85%84&status=OPEN&status=UPCOMING&field=%EC%A3%BC%EA%B1%B0&field=%EA%B5%90%EC%9C%A1&target=%EC%B2%AD%EB%85%84'
  );
});

test('distinguishes the default status range from all statuses including CLOSED', () => {
  const { hooks } = createHarness();
  assert.doesNotMatch(hooks.buildRequestPath('', '', 1), /status=/);

  hooks.updateFilterSelection('status', 'ALL', true);
  assert.deepEqual([...hooks.state.statuses], ['OPEN', 'UPCOMING', 'CLOSED']);
  assert.match(hooks.buildRequestPath('', '', 1), /status=OPEN&status=UPCOMING&status=CLOSED/);

  hooks.updateFilterSelection('status', 'CLOSED', false);
  assert.deepEqual([...hooks.state.statuses], ['OPEN', 'UPCOMING']);
});

test('renders only server-provided field and target tags in their matching categories', () => {
  const { elements, hooks } = createHarness();
  hooks.hashtagState.screen = 'RESULT';
  hooks.hashtagState.response = {
    tags: [
      { name: '주거', category: '분야', description: null, count: 4 },
      { name: '노인', category: '대상', description: null, count: 2 },
      { name: '금융', category: '기타', description: null, count: 9 },
      { name: '주거', category: '대상', description: null, count: 1 }
    ],
    untaggedCount: 0
  };

  hooks.renderFilters();

  assert.match(elements['#filter-groups'].innerHTML, /value="주거"/);
  assert.match(elements['#filter-groups'].innerHTML, /주거 <small>\(4\)<\/small>/);
  assert.match(elements['#filter-groups'].innerHTML, /value="노인"/);
  assert.match(elements['#filter-groups'].innerHTML, /노인 <small>\(2\)<\/small>/);
  assert.doesNotMatch(elements['#filter-groups'].innerHTML, /금융|어르신|저소득층/);
  assert.match(elements['#filter-groups'].innerHTML, /주거 <small>\(1\)<\/small>/);
});

test('restores URL filter selections without issuing a request until the caller asks for one', () => {
  const { elements, hooks, requests } = createHarness('?q=%EC%B2%AD%EB%85%84&status=OPEN&status=CLOSED&field=%EC%A3%BC%EA%B1%B0&target=%EB%85%B8%EC%9D%B8');
  hooks.applyKeywordFromUrl(false);

  assert.equal(hooks.state.query, '청년');
  assert.deepEqual([...hooks.state.statuses], ['OPEN', 'CLOSED']);
  assert.deepEqual([...hooks.state.fields], ['주거']);
  assert.deepEqual([...hooks.state.targets], ['노인']);
  assert.equal(elements['#benefit-search-input'].value, '청년');
  assert.equal(requests.length, 0);
  assert.equal(hooks.filterGroupExpanded.target, true);
});

test('toggles filter groups independently without clearing selections or requesting benefits', () => {
  const { elements, hooks, requests } = createHarness();
  hooks.state.fields = ['주거'];
  hooks.renderFilters();
  assert.match(elements['#filter-groups'].innerHTML, /data-filter-toggle="target" aria-expanded="false"/);

  const attributes = {};
  const button = { dataset: { filterToggle: 'field' }, setAttribute: (name, value) => { attributes[name] = value; } };
  hooks.toggleFilterGroup(button);
  assert.equal(elements['#filter-group-content-field'].hidden, true);
  assert.equal(attributes['aria-expanded'], 'false');
  assert.deepEqual([...hooks.state.fields], ['주거']);
  assert.equal(requests.length, 0);

  hooks.renderFilters();
  assert.match(elements['#filter-groups'].innerHTML, /data-filter-toggle="field" aria-expanded="false"[^>]*>.*선택 1/);
  hooks.toggleFilterGroup(button);
  assert.equal(elements['#filter-group-content-field'].hidden, false);
  assert.equal(hooks.filterGroupExpanded.target, false);
});

test('keeps status filters usable but asks an unauthenticated visitor to log in for server tag filters', () => {
  const { elements, hooks } = createHarness();
  hooks.hashtagState.screen = 'UNAUTHENTICATED';
  hooks.renderFilters();

  assert.match(elements['#filter-groups'].innerHTML, /신청 가능/);
  assert.match(elements['#filter-groups'].innerHTML, /로그인하면 실제 분야 필터를 사용할 수 있어요/);
  assert.match(elements['#filter-groups'].innerHTML, /로그인하면 실제 대상 필터를 사용할 수 있어요/);
  assert.doesNotMatch(elements['#filter-groups'].innerHTML, /금융|어르신|저소득층/);
});

test('keeps active filters when the keyword changes and records a history entry', async () => {
  const { elements, historyCalls, hooks, requests } = createHarness();
  hooks.state.statuses = ['OPEN'];
  hooks.state.fields = ['주거'];
  hooks.state.targets = ['청년'];
  elements['#benefit-search-input'].value = '교육';

  hooks.runSearch();
  await new Promise((resolve) => setImmediate(resolve));

  assert.match(requests[0].url, /q=%EA%B5%90%EC%9C%A1/);
  assert.match(requests[0].url, /status=OPEN/);
  assert.match(requests[0].url, /field=%EC%A3%BC%EA%B1%B0/);
  assert.match(requests[0].url, /target=%EC%B2%AD%EB%85%84/);
  assert.equal(historyCalls[0].method, 'push');
});

test('keeps every filter while requesting another server page', async () => {
  const { hooks, requests } = createHarness();
  hooks.state.query = '청년';
  hooks.state.statuses = ['CLOSED'];
  hooks.state.fields = ['교육'];
  hooks.state.targets = ['노인'];

  await hooks.requestBenefits(2);

  assert.match(requests[0].url, /page=1/);
  assert.match(requests[0].url, /status=CLOSED/);
  assert.match(requests[0].url, /field=%EA%B5%90%EC%9C%A1/);
  assert.match(requests[0].url, /target=%EB%85%B8%EC%9D%B8/);
});

test('shows a filter-search 400 response as an API error without fallback data', async () => {
  const { elements, hooks } = createHarness('', () => response(400, { message: 'field 값이 올바르지 않습니다.' }));
  hooks.state.fields = ['알 수 없음'];

  await hooks.requestBenefits(1);

  assert.equal(hooks.state.screen, 'ERROR');
  assert.equal(hooks.state.errorKind, 'API');
  assert.equal(hooks.state.errorMessage, 'field 값이 올바르지 않습니다.');
  assert.match(elements['#result-area'].innerHTML, /field 값이 올바르지 않습니다/);
});
