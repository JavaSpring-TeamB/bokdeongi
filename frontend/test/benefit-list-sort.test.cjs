const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-list.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '..', 'benefit-list.html'), 'utf8');

function element() {
  return {
    innerHTML: '', textContent: '', value: '', title: '', options: [
      { value: 'relevance', disabled: false }, { value: 'deadline', disabled: false },
      { value: 'amount', disabled: false }, { value: 'easy', disabled: false }
    ],
    addEventListener() {}, setAttribute() {}
  };
}

function payload(overrides = {}) {
  return {
    keyword: '', items: [], totalElements: 0, totalPages: 0,
    page: 0, size: 6, hasNext: false, suggestedHashtags: [], ...overrides
  };
}

function response(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function createHarness(search = '', fetchImplementation = () => response(200, payload())) {
  const elements = {
    '#toast': element(), '#result-summary': element(), '#result-area': element(),
    '#benefit-search-input': element(), '#search-message': element(), '#filter-groups': element(),
    '#filter-reset': element(), '#sort-order': element(), '#benefit-search-form': element(),
    '#hashtag-suggestions': element()
  };
  const historyCalls = [];
  const requests = [];
  const documentEvents = {};
  const windowEvents = {};
  const window = {
    location: { search, pathname: '/benefit-list.html' },
    history: {
      pushState: (...args) => historyCalls.push({ method: 'push', args }),
      replaceState: (...args) => historyCalls.push({ method: 'replace', args })
    },
    BokdeongiAuth: { getApiUrl: (requestPath) => `http://backend.test${requestPath}` },
    addEventListener: (name, callback) => { windowEvents[name] = callback; }
  };
  const context = {
    AbortController, Array, JSON, Map, Number, Object, Set, String, URLSearchParams,
    console: { error() {} },
    document: {
      addEventListener: (name, callback) => { documentEvents[name] = callback; },
      querySelector: (selector) => elements[selector] || null
    },
    fetch: async (url, options) => { requests.push({ url, options }); return fetchImplementation(url, options); },
    setTimeout, clearTimeout, window
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__sortHooks = { applyKeywordFromUrl, buildRequestPath, changeSort, normalizeSort, requestBenefits, state };`, context);
  return { documentEvents, elements, historyCalls, hooks: context.__sortHooks, requests, window, windowEvents };
}

test('renders four enabled sort options and removes the relevance-only notice', () => {
  for (const value of ['relevance', 'deadline', 'amount', 'easy']) {
    assert.match(html, new RegExp(`<option value="${value}">`));
  }
  assert.doesNotMatch(html, /현재 관련도순만 제공됩니다/);
});

test('builds requests with each supported sort and safely falls back to relevance', () => {
  const { hooks } = createHarness();
  hooks.state.statuses = ['OPEN'];
  hooks.state.fields = ['주거'];
  hooks.state.targets = ['청년'];

  for (const sort of ['relevance', 'deadline', 'amount', 'easy']) {
    hooks.state.sort = sort;
    const request = hooks.buildRequestPath('청년', '주거지원', 2);
    assert.match(request, new RegExp(`sort=${sort}`));
    assert.match(request, /page=1/);
    assert.match(request, /q=%EC%B2%AD%EB%85%84/);
    assert.match(request, /hashtag=%EC%A3%BC%EA%B1%B0%EC%A7%80%EC%9B%90/);
    assert.match(request, /status=OPEN/);
    assert.match(request, /field=%EC%A3%BC%EA%B1%B0/);
    assert.match(request, /target=%EC%B2%AD%EB%85%84/);
  }

  hooks.state.sort = 'invalid';
  assert.match(hooks.buildRequestPath('', '', 1), /sort=relevance/);
});

test('changes sort on the first page while keeping filters and server item order', async () => {
  const { elements, historyCalls, hooks, requests } = createHarness('', () => response(200, payload({
    items: [{ id: 20, title: 'server-first' }, { id: 10, title: 'server-second' }], totalElements: 2, totalPages: 1
  })));
  hooks.state.query = '청년';
  hooks.state.hashtag = '주거지원';
  hooks.state.statuses = ['OPEN'];
  hooks.state.fields = ['주거'];
  hooks.state.targets = ['청년'];
  hooks.state.currentPage = 3;

  hooks.changeSort('deadline');
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(hooks.state.sort, 'deadline');
  assert.equal(hooks.state.currentPage, 1);
  assert.equal(elements['#sort-order'].value, 'deadline');
  assert.match(requests[0].url, /sort=deadline/);
  assert.match(requests[0].url, /page=0/);
  assert.match(requests[0].url, /q=%EC%B2%AD%EB%85%84/);
  assert.match(requests[0].url, /hashtag=%EC%A3%BC%EA%B1%B0%EC%A7%80%EC%9B%90/);
  assert.match(requests[0].url, /status=OPEN/);
  assert.match(requests[0].url, /field=%EC%A3%BC%EA%B1%B0/);
  assert.match(requests[0].url, /target=%EC%B2%AD%EB%85%84/);
  assert.match(historyCalls[0].args[2], /sort=deadline/);
  assert.ok(elements['#result-area'].innerHTML.indexOf('server-first') < elements['#result-area'].innerHTML.indexOf('server-second'));

  hooks.changeSort('deadline');
  assert.equal(requests.length, 1);
});

test('restores URL sort with the existing search conditions and defaults invalid values', () => {
  const valid = createHarness('?q=%EC%B2%AD%EB%85%84&hashtag=%EC%A3%BC%EA%B1%B0&status=OPEN&field=%EC%A3%BC%EA%B1%B0&target=%EC%B2%AD%EB%85%84&sort=easy');
  valid.hooks.applyKeywordFromUrl(false);
  assert.equal(valid.hooks.state.sort, 'easy');
  assert.equal(valid.elements['#sort-order'].value, 'easy');
  assert.equal(valid.hooks.state.query, '청년');
  assert.equal(valid.hooks.state.hashtag, '');
  assert.deepEqual([...valid.hooks.state.statuses], ['OPEN']);
  assert.deepEqual([...valid.hooks.state.fields], ['주거']);
  assert.deepEqual([...valid.hooks.state.targets], ['청년']);

  const invalid = createHarness('?hashtag=%EC%A3%BC%EA%B1%B0&sort=unknown');
  invalid.hooks.applyKeywordFromUrl(false);
  assert.equal(invalid.hooks.state.sort, 'relevance');
  assert.equal(invalid.elements['#sort-order'].value, 'relevance');
  assert.equal(invalid.hooks.state.hashtag, '주거');
});

test('restores sort on browser navigation through the registered popstate handler', async () => {
  const navigation = createHarness('?sort=amount');
  navigation.documentEvents.DOMContentLoaded();
  assert.equal(navigation.elements['#sort-order'].value, 'amount');

  navigation.window.location.search = '?q=%EC%B2%AD%EB%85%84&sort=easy';
  navigation.windowEvents.popstate();
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(navigation.hooks.state.sort, 'easy');
  assert.equal(navigation.elements['#sort-order'].value, 'easy');
  assert.match(navigation.requests[0].url, /sort=easy/);
  assert.match(navigation.requests[0].url, /q=%EC%B2%AD%EB%85%84/);
});

test('shows the server sort error without creating fallback results', async () => {
  const { elements, hooks } = createHarness('', () => response(400, { message: '지원하지 않는 정렬 방식입니다.' }));
  hooks.changeSort('amount');
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(hooks.state.screen, 'ERROR');
  assert.equal(hooks.state.errorKind, 'API');
  assert.equal(hooks.state.errorMessage, '지원하지 않는 정렬 방식입니다.');
  assert.match(elements['#result-area'].innerHTML, /지원하지 않는 정렬 방식입니다/);
});
