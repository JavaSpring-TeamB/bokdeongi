const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const listSource = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-list.js'), 'utf8');
const detailSource = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-detail.js'), 'utf8');

function element() {
  return {
    disabled: false, hidden: true, innerHTML: '', textContent: '', title: '', value: '', options: [],
    classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, addEventListener() {}
  };
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function listPayload(items) {
  return { keyword: '청년', items, totalElements: items.length, totalPages: 1, page: 0, size: 6, hasNext: false, suggestedHashtags: [] };
}

function item(id = 123) {
  return { id, title: '청년 주거 지원', summaryWho: null, summaryWhat: null, summaryHow: null, summaryWhen: null, orgName: null, hashtags: null, eligibilityResult: null, applyStart: null, applyEnd: null };
}

function createListHarness({ authenticationState = 'authenticated', favoriteFetch } = {}) {
  const elements = {
    '#toast': element(), '#result-summary': element(), '#result-area': element(), '#benefit-search-input': element(),
    '#search-message': element(), '#filter-groups': element(), '#filter-reset': element(), '#sort-order': element(), '#benefit-search-form': element()
  };
  const calls = [];
  const auth = {
    refreshAuthentication: async () => authenticationState,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (requestPath, options = {}) => {
      calls.push({ requestPath, options });
      return favoriteFetch(requestPath, options);
    },
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`
  };
  const context = {
    AbortController, Array, JSON, Map, Number, Object, Set, String, URLSearchParams, setTimeout, clearTimeout,
    console: { error() {} }, window: { BokdeongiAuth: auth, location: { search: '' } },
    document: { addEventListener() {}, querySelector: (selector) => elements[selector] || null },
    fetch: async () => response(200, listPayload([]))
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${listSource}\n;globalThis.__hooks = { card, favoriteButtonMarkup, favoriteState, isFavoriteResponse, loadFavoriteStates, resetFavoriteStates, state, toggleFavorite };`, context);
  context.__hooks.state.response = listPayload([item()]);
  context.__hooks.state.screen = 'RESULT';
  return { calls, elements, hooks: context.__hooks };
}

function createDetailHarness({ authenticationState = 'authenticated', favoriteFetch } = {}) {
  const elements = { '#toast': element(), '#save-button': element(), '#favorite-status': element() };
  const calls = [];
  const auth = {
    refreshAuthentication: async () => authenticationState,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (requestPath, options = {}) => {
      calls.push({ requestPath, options });
      return favoriteFetch(requestPath, options);
    },
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`
  };
  const context = {
    AbortController, Array, JSON, Number, Object, Set, String, URL, URLSearchParams, setTimeout, clearTimeout,
    console: { error() {} }, window: { BokdeongiAuth: auth, location: { search: '?id=123', href: 'http://frontend.test/benefit-detail.html?id=123' } },
    document: { addEventListener() {}, querySelector: (selector) => elements[selector] || null, querySelectorAll: () => [] },
    fetch: async () => response(200, { id: 123 })
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${detailSource}\n;globalThis.__hooks = { favoriteState, isFavoriteResponse, loadFavorite, resetFavoriteState, state, toggleFavorite };`, context);
  context.__hooks.state.benefitId = 123;
  context.__hooks.state.detail = { id: 123 };
  context.__hooks.resetFavoriteState(123);
  return { calls, elements, hooks: context.__hooks };
}

test('loads list favorite states from the API and sends CSRF on save', async () => {
  const harness = createListHarness({
    favoriteFetch: (requestPath, options) => response(200, { benefitId: 123, bookmarked: options.method === 'POST' })
  });

  await harness.hooks.loadFavoriteStates([item()]);
  assert.equal(harness.calls[0].requestPath, '/api/benefits/123/favorite');
  assert.equal(harness.calls[0].options.method, undefined);
  assert.match(harness.hooks.card(item()), /관심 혜택 저장/);

  await harness.hooks.toggleFavorite(123);
  assert.equal(harness.calls[1].options.method, 'POST');
  assert.deepEqual({ ...harness.calls[1].options.headers }, { 'X-XSRF-TOKEN': 'csrf-token' });
  assert.equal(harness.hooks.favoriteState.byBenefitId.get('123').bookmarked, true);
});

test('prevents duplicate list toggles and preserves state when a save fails', async () => {
  let resolvePost;
  const harness = createListHarness({
    favoriteFetch: (requestPath, options) => options.method === 'POST'
      ? new Promise((resolve) => { resolvePost = resolve; })
      : response(200, { benefitId: 123, bookmarked: false })
  });
  await harness.hooks.loadFavoriteStates([item()]);
  const first = harness.hooks.toggleFavorite(123);
  const second = harness.hooks.toggleFavorite(123);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(harness.calls.filter((call) => call.options.method === 'POST').length, 1);
  resolvePost(response(500, { message: 'failed' }));
  await Promise.all([first, second]);
  assert.equal(harness.hooks.favoriteState.byBenefitId.get('123').bookmarked, false);
});

test('does not request list favorites for unauthenticated users', async () => {
  const harness = createListHarness({ authenticationState: 'unauthenticated', favoriteFetch: () => { throw new Error('must not request'); } });
  await harness.hooks.loadFavoriteStates([item()]);
  assert.equal(harness.calls.length, 0);
  assert.equal(harness.hooks.favoriteState.authState, 'UNAUTHENTICATED');
  assert.match(harness.hooks.card(item()), /로그인 필요/);
});

test('ignores a late list favorite response after search results change', async () => {
  let resolveFirst;
  const harness = createListHarness({
    favoriteFetch: (requestPath) => requestPath.includes('/123/')
      ? new Promise((resolve) => { resolveFirst = resolve; })
      : response(200, { benefitId: 456, bookmarked: true })
  });
  const first = harness.hooks.loadFavoriteStates([item(123)]);
  await new Promise((resolve) => setImmediate(resolve));
  harness.hooks.resetFavoriteStates();
  harness.hooks.state.response = listPayload([item(456)]);
  await harness.hooks.loadFavoriteStates([item(456)]);
  resolveFirst(response(200, { benefitId: 123, bookmarked: true }));
  await first;
  assert.equal(harness.hooks.favoriteState.byBenefitId.has('123'), false);
  assert.equal(harness.hooks.favoriteState.byBenefitId.get('456').bookmarked, true);
});

test('ignores a late list favorite save after the result page changes', async () => {
  let resolveSave;
  const harness = createListHarness({
    favoriteFetch: (requestPath, options) => options.method === 'POST'
      ? new Promise((resolve) => { resolveSave = resolve; })
      : response(200, { benefitId: 123, bookmarked: false })
  });
  await harness.hooks.loadFavoriteStates([item(123)]);
  const save = harness.hooks.toggleFavorite(123);
  await new Promise((resolve) => setImmediate(resolve));
  harness.hooks.resetFavoriteStates();
  resolveSave(response(200, { benefitId: 123, bookmarked: true }));
  await save;
  assert.equal(harness.hooks.favoriteState.byBenefitId.has('123'), false);
});

test('loads and toggles the detail favorite state with authenticated CSRF requests', async () => {
  const harness = createDetailHarness({
    favoriteFetch: (requestPath, options) => response(200, { benefitId: 123, bookmarked: options.method === 'DELETE' ? false : options.method === 'POST' })
  });
  await harness.hooks.loadFavorite();
  assert.equal(harness.hooks.favoriteState.bookmarked, false);
  await harness.hooks.toggleFavorite();
  assert.equal(harness.calls[1].requestPath, '/api/benefits/123/favorite');
  assert.equal(harness.calls[1].options.method, 'POST');
  assert.deepEqual({ ...harness.calls[1].options.headers }, { 'X-XSRF-TOKEN': 'csrf-token' });
  assert.equal(harness.hooks.favoriteState.bookmarked, true);
});

test('keeps detail state on a failed delete', async () => {
  let call = 0;
  const harness = createDetailHarness({
    favoriteFetch: (requestPath, options) => {
      call += 1;
      if (call === 1) return response(200, { benefitId: 123, bookmarked: true });
      if (options.method === 'DELETE') return response(500, { message: 'failed' });
      return response(200, { benefitId: 123, bookmarked: true });
    }
  });
  await harness.hooks.loadFavorite();
  await harness.hooks.toggleFavorite();
  assert.equal(harness.hooks.favoriteState.screen, 'RESULT');
  assert.equal(harness.hooks.favoriteState.bookmarked, true);
});

test('handles favorite authentication expiry without treating it as an unsaved result', async () => {
  const harness = createDetailHarness({ favoriteFetch: () => response(401, { message: 'expired' }) });
  await harness.hooks.loadFavorite();
  assert.equal(harness.hooks.favoriteState.screen, 'UNAUTHENTICATED');
  assert.equal(harness.hooks.favoriteState.bookmarked, null);
});

test('accepts only the exact favorite toggle response for the requested benefit', () => {
  const harness = createDetailHarness({ favoriteFetch: () => response(200, { benefitId: 123, bookmarked: false }) });
  assert.equal(harness.hooks.isFavoriteResponse({ benefitId: 123, bookmarked: true }, 123), true);
  assert.equal(harness.hooks.isFavoriteResponse({ benefitId: 456, bookmarked: true }, 123), false);
  assert.equal(harness.hooks.isFavoriteResponse({ benefitId: 123, bookmarked: 'true' }, 123), false);
});
