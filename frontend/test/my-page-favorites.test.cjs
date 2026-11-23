const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'my-page.js'), 'utf8');

function response(status, payload) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function favorite(benefitId = 12, overrides = {}) {
  return {
    benefitId, title: '청년 월세 지원', category: '주거·생활', orgName: '국토교통부', region: null,
    status: 'ELIGIBLE', amount: null, period: { text: '~ 2026.11.30 (D-12)' }, hashtags: ['청년', '주거'],
    applying: false, applicationId: null, appStatus: null, bookmarkedAt: null, lastChange: null, warnings: [],
    ...overrides
  };
}

function favoritesPayload(overrides = {}) {
  return { sort: 'DEADLINE', count: 2, items: [favorite()], ended: [favorite(23, { title: '종료된 지원', period: null })], ...overrides };
}

function createHarness({ authenticationState = 'authenticated', request } = {}) {
  const calls = [];
  const auth = {
    refreshAuthentication: async () => authenticationState,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }),
    authenticatedFetch: async (requestPath, options = {}) => {
      calls.push({ requestPath, options });
      return request(requestPath, options);
    }
  };
  const document = {
    addEventListener() {},
    querySelector(selector) { return selector === '#toast' ? { hidden: true, textContent: '' } : null; },
    querySelectorAll() { return []; }
  };
  const context = {
    AbortController, Array, JSON, Number, Object, String, encodeURIComponent, console: { error() {} },
    document, window: {
      BokdeongiAuth: auth,
      setTimeout() {},
      BokdeongiPagination: {
        clampPage(total, page, size) { return total ? Math.min(Math.max(page, 1), Math.ceil(total / size)) : 1; },
        markup() { return ''; }
      }
    },
    sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    setTimeout() {}
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(`${source}\nglobalThis.__favoriteHooks = { favoriteListState, favoriteCard, isFavoriteListResponse, isFavoriteToggleResponse, loadFavorites, removeFavorite, renderBookmarks };`, context);
  return { calls, hooks: context.__favoriteHooks };
}

test('loads BEN-12 favorites with the server default sort and renders active and ended cards', async () => {
  const harness = createHarness({ request: () => response(200, favoritesPayload()) });

  await harness.hooks.loadFavorites();

  assert.equal(harness.calls.length, 1);
  assert.equal(harness.calls[0].requestPath, '/api/me/favorites?sort=DEADLINE');
  assert.equal(harness.hooks.favoriteListState.screen, 'RESULT');
  const markup = harness.hooks.renderBookmarks();
  assert.match(markup, /총 2건/);
  assert.match(markup, /benefit-detail\.html\?id=12/);
  assert.match(markup, /종료된 혜택/);
  assert.match(markup, /국토교통부/);
  assert.match(markup, /#청년/);
});

test('does not send a favorites request when the user is not authenticated', async () => {
  const harness = createHarness({ authenticationState: 'unauthenticated', request: () => { throw new Error('must not request'); } });

  await harness.hooks.loadFavorites();

  assert.equal(harness.calls.length, 0);
  assert.equal(harness.hooks.favoriteListState.screen, 'UNAUTHENTICATED');
});

test('handles omitted optional card fields without rendering placeholder data', async () => {
  const sparse = favorite(31, { title: null, category: null, orgName: null, status: null, period: null, hashtags: null });
  const harness = createHarness({ request: () => response(200, favoritesPayload({ count: 1, items: [sparse], ended: [] })) });

  await harness.hooks.loadFavorites();

  const markup = harness.hooks.renderBookmarks();
  assert.match(markup, /benefit-detail\.html\?id=31/);
  assert.doesNotMatch(markup, /undefined|null/);
});

test('renders a retryable network error without falling back to dummy favorites', async () => {
  const harness = createHarness({ request: async () => { throw new TypeError('network failed'); } });

  await harness.hooks.loadFavorites();

  assert.equal(harness.hooks.favoriteListState.screen, 'ERROR');
  assert.match(harness.hooks.renderBookmarks(), /네트워크 연결을 확인/);
  assert.match(harness.hooks.renderBookmarks(), /data-retry-favorites/);
});

test('removes a favorite through DELETE with CSRF and reloads the server list', async () => {
  const payload = favoritesPayload({ count: 1, ended: [] });
  const harness = createHarness({
    request: (requestPath, options) => options.method === 'DELETE'
      ? response(200, { benefitId: 12, bookmarked: false })
      : response(200, payload)
  });
  await harness.hooks.loadFavorites();

  await harness.hooks.removeFavorite(12);

  assert.equal(harness.calls[1].requestPath, '/api/benefits/12/favorite');
  assert.equal(harness.calls[1].options.method, 'DELETE');
  assert.deepEqual({ ...harness.calls[1].options.headers }, { 'X-XSRF-TOKEN': 'csrf-token' });
  assert.equal(harness.calls[2].requestPath, '/api/me/favorites?sort=DEADLINE');
  assert.equal(harness.hooks.favoriteListState.screen, 'RESULT');
});

test('keeps the rendered server list when favorite deletion fails', async () => {
  const harness = createHarness({
    request: (requestPath, options) => options.method === 'DELETE'
      ? response(500, { message: 'failed' })
      : response(200, favoritesPayload({ ended: [], count: 1 }))
  });
  await harness.hooks.loadFavorites();

  await harness.hooks.removeFavorite(12);

  assert.equal(harness.calls.length, 2);
  assert.equal(harness.hooks.favoriteListState.screen, 'RESULT');
  assert.match(harness.hooks.renderBookmarks(), /data-remove-bookmark="12"/);
});

test('rejects malformed essential IDs and ignores a late favorites response', async () => {
  const malformed = favoritesPayload({ items: [favorite(0)], ended: [] });
  const invalidHarness = createHarness({ request: () => response(200, malformed) });
  await invalidHarness.hooks.loadFavorites();
  assert.equal(invalidHarness.hooks.favoriteListState.screen, 'ERROR');

  let resolveFirst;
  let requestCount = 0;
  const raceHarness = createHarness({
    request: () => {
      requestCount += 1;
      return requestCount === 1
        ? new Promise((resolve) => { resolveFirst = resolve; })
        : response(200, favoritesPayload({ count: 1, items: [favorite(99)], ended: [] }));
    }
  });
  const first = raceHarness.hooks.loadFavorites();
  await new Promise((resolve) => setImmediate(resolve));
  await raceHarness.hooks.loadFavorites();
  resolveFirst(response(200, favoritesPayload({ count: 1, items: [favorite(12)], ended: [] })));
  await first;

  assert.equal(raceHarness.hooks.favoriteListState.response.items[0].benefitId, 99);
});

test('checks that a favorite toggle response belongs to the requested benefit', () => {
  const harness = createHarness({ request: () => response(200, favoritesPayload()) });
  assert.equal(harness.hooks.isFavoriteToggleResponse({ benefitId: 12, bookmarked: false }, 12), true);
  assert.equal(harness.hooks.isFavoriteToggleResponse({ benefitId: 13, bookmarked: false }, 12), false);
  assert.equal(harness.hooks.isFavoriteToggleResponse({ benefitId: 12, bookmarked: 'false' }, 12), false);
});
