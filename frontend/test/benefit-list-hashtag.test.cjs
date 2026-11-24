const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-list.js'), 'utf8');

function element() {
  return { disabled: false, hidden: true, innerHTML: '', textContent: '', title: '', value: '', options: [], setAttribute() {}, addEventListener() {} };
}

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function searchPayload(overrides = {}) {
  return { keyword: '', items: [], totalElements: 0, totalPages: 0, page: 0, size: 6, hasNext: false, suggestedHashtags: [], ...overrides };
}

function hashtagPayload(overrides = {}) {
  return {
    tags: [
      { name: '청년', category: '대상', description: '청년 대상 혜택', count: 12 },
      { name: '주거지원', category: '분야', description: null, count: 4 }
    ],
    untaggedCount: 3,
    ...overrides
  };
}

function createHarness({ authenticationState = 'authenticated', hashtagFetch = () => response(200, hashtagPayload()), searchFetch = () => response(200, searchPayload()) } = {}) {
  const elements = {
    '#toast': element(), '#result-summary': element(), '#result-area': element(), '#benefit-search-input': element(),
    '#search-message': element(), '#filter-groups': element(), '#filter-reset': element(), '#sort-order': element(),
    '#benefit-search-form': element(), '#hashtag-suggestions': element()
  };
  const authenticatedCalls = [];
  const fetchCalls = [];
  const historyCalls = [];
  const auth = {
    refreshAuthentication: async () => authenticationState,
    authenticatedFetch: async (requestPath, options = {}) => {
      authenticatedCalls.push({ requestPath, options });
      return hashtagFetch(requestPath, options);
    },
    getApiUrl: (requestPath) => `http://backend.test${requestPath}`
  };
  const window = {
    location: { search: '', pathname: '/benefit-list.html' },
    history: { replaceState: (...args) => historyCalls.push(args) },
    BokdeongiAuth: auth
  };
  const context = {
    AbortController, Array, JSON, Map, Number, Object, Set, String, URLSearchParams, console: { error() {} },
    document: { addEventListener() {}, querySelector: (selector) => elements[selector] || null },
    fetch: async (url, options) => {
      fetchCalls.push({ url, options });
      return searchFetch(url, options);
    },
    setTimeout, clearTimeout, window
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__hashtagHooks = { buildRequestPath, displayableHashtags, hashtagState, isHashtagResponse, loadHashtags, searchByHashtag, state };`, context);
  return { authenticatedCalls, elements, fetchCalls, historyCalls, hooks: context.__hashtagHooks };
}

test('requests the authenticated hashtag API and keeps the server order while removing blank duplicates', async () => {
  const { authenticatedCalls, elements, hooks } = createHarness({
    hashtagFetch: () => response(200, hashtagPayload({
      tags: [
        { name: '청년', category: '대상', description: '<설명>', count: 12 },
        { name: ' ', category: null, description: null, count: 1 },
        { name: '청년', category: '중복', description: null, count: 99 },
        { name: '주거지원', category: '분야', description: null, count: 4 }
      ]
    }))
  });

  await hooks.loadHashtags();
  assert.equal(authenticatedCalls.length, 1);
  assert.equal(authenticatedCalls[0].requestPath, '/api/hashtags');
  assert.equal(authenticatedCalls[0].options.method, undefined);
  assert.equal(hooks.hashtagState.screen, 'RESULT');
  assert.ok(elements['#hashtag-suggestions'].innerHTML.indexOf('#청년') < elements['#hashtag-suggestions'].innerHTML.indexOf('#주거지원'));
  assert.equal((elements['#hashtag-suggestions'].innerHTML.match(/#청년/g) || []).length, 1);
  assert.match(elements['#hashtag-suggestions'].innerHTML, /&lt;설명&gt;/);
});

test('does not request the protected hashtag endpoint for an unauthenticated visitor', async () => {
  const { authenticatedCalls, elements, hooks } = createHarness({ authenticationState: 'unauthenticated' });
  await hooks.loadHashtags();
  assert.equal(authenticatedCalls.length, 0);
  assert.equal(hooks.hashtagState.screen, 'UNAUTHENTICATED');
  assert.match(elements['#hashtag-suggestions'].innerHTML, /로그인하기/);
});

test('separates empty, malformed, and network hashtag responses without showing fallback tags', async () => {
  const empty = createHarness({ hashtagFetch: () => response(200, hashtagPayload({ tags: [] })) });
  await empty.hooks.loadHashtags();
  assert.match(empty.elements['#hashtag-suggestions'].innerHTML, /표시할 해시태그가 없어요/);

  const malformed = createHarness({ hashtagFetch: () => response(200, { tags: [{ name: '청년', count: '12' }], untaggedCount: 0 }) });
  await malformed.hooks.loadHashtags();
  assert.equal(malformed.hooks.hashtagState.screen, 'ERROR');
  assert.match(malformed.elements['#hashtag-suggestions'].innerHTML, /다시 시도/);

  const network = createHarness({ hashtagFetch: () => { throw { type: 'network' }; } });
  await network.hooks.loadHashtags();
  assert.equal(network.hooks.hashtagState.screen, 'ERROR');
  assert.match(network.elements['#hashtag-suggestions'].innerHTML, /네트워크 연결/);
});

test('ignores a late hashtag response after a forced refresh', async () => {
  let resolveFirst;
  let calls = 0;
  const { hooks, elements } = createHarness({
    hashtagFetch: () => {
      calls += 1;
      return calls === 1
        ? new Promise((resolve) => { resolveFirst = resolve; })
        : response(200, hashtagPayload({ tags: [{ name: '두번째', category: null, description: null, count: 1 }] }));
    }
  });
  const first = hooks.loadHashtags();
  await new Promise((resolve) => setImmediate(resolve));
  await hooks.loadHashtags(true);
  resolveFirst(response(200, hashtagPayload({ tags: [{ name: '첫번째', category: null, description: null, count: 1 }] })));
  await first;
  assert.match(elements['#hashtag-suggestions'].innerHTML, /두번째/);
  assert.doesNotMatch(elements['#hashtag-suggestions'].innerHTML, /첫번째/);
});

test('uses a selected hashtag in the public benefit search and synchronizes its URL state', async () => {
  const { fetchCalls, historyCalls, hooks } = createHarness();
  hooks.searchByHashtag('주거 지원/청년');
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(hooks.state.query, '');
  assert.equal(hooks.state.hashtag, '주거 지원/청년');
  assert.match(fetchCalls[0].url, /hashtag=%EC%A3%BC%EA%B1%B0\+%EC%A7%80%EC%9B%90%2F%EC%B2%AD%EB%85%84/);
  assert.equal(historyCalls[0][2], '/benefit-list.html?sort=relevance&hashtag=%EC%A3%BC%EA%B1%B0+%EC%A7%80%EC%9B%90%2F%EC%B2%AD%EB%85%84');
  assert.equal(hooks.buildRequestPath('', '주거 지원/청년', 1).includes('hashtag=%EC%A3%BC%EA%B1%B0+%EC%A7%80%EC%9B%90%2F%EC%B2%AD%EB%85%84'), true);
});
