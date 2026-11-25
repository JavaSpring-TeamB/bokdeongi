const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-recommend.js'), 'utf8');

function createHarness({ profileResponse, profileRequest } = {}) {
  const calls = [];
  const listeners = new Map();
  const selects = {
    region: createSelect([{ value: '', textContent: '지역 전체' }, { value: '부산광역시' }, { value: '서울특별시' }, { value: '대구광역시' }]),
    category: createSelect(),
    sort: createSelect()
  };
  const document = {
    addEventListener() {},
    querySelector(selector) {
      const match = selector.match(/^\[data-filter="(region|category|sort)"\]$/);
      return match ? selects[match[1]] : null;
    },
    querySelectorAll() { return []; }
  };
  const auth = {
    getAuthenticationState() { return 'authenticated'; },
    async authenticatedFetch(url) {
      calls.push({ type: 'fetch', url });
      if (typeof profileRequest === 'function') return profileRequest(url);
      return profileResponse;
    }
  };
  const window = { BokdeongiAuth: auth, addEventListener() {} };
  const instrumented = source.replace(/\}\)\(\);\s*$/, `
    loadSelectedTab = () => calls.push({ type: 'recommend', state: { ...queryState } });
    loadSavedAnswers = () => calls.push({ type: 'answers' });
    loadHashtags = () => calls.push({ type: 'hashtags' });
    globalThis.__regionHooks = {
      queryState,
      loadProfileRegionDefault,
      setupFilters,
      handleAuthenticationState,
      state: () => ({ profileRegionSyncPending, regionFilterTouched }),
      window
    };
  })();`);
  const context = vm.createContext({
    console, document, window, calls, URLSearchParams, Set, Map, Object, Number, Array, Promise
  });
  vm.runInContext(instrumented, context);
  return { calls, hooks: context.__regionHooks, selects };
}

function createSelect(options = []) {
  const listeners = new Map();
  return {
    value: '',
    options,
    disabled: false,
    addEventListener(type, listener) { listeners.set(type, listener); },
    setAttribute() {},
    dispatch(type, target = this) { listeners.get(type)?.({ target }); }
  };
}

function okProfile(profile) {
  return { ok: true, status: 200, async json() { return profile; } };
}

test('defaults the region filter from the profile sidoName, not its regionCode', async () => {
  const harness = createHarness({
    profileResponse: okProfile({ regionCode: '26110', sidoName: '부산광역시', sigunguName: '중구' })
  });

  await harness.hooks.loadProfileRegionDefault();

  assert.equal(harness.hooks.queryState.region, '부산광역시');
  assert.equal(harness.selects.region.value, '부산광역시');
  assert.equal(harness.calls.filter((call) => call.type === 'fetch')[0].url, '/api/me/profile');
  assert.equal(harness.calls.find((call) => call.type === 'recommend').state.region, '부산광역시');
});

test('uses the latest profile region after entering the page again', async () => {
  const harness = createHarness({ profileResponse: okProfile({ sidoName: '대구광역시' }) });

  await harness.hooks.loadProfileRegionDefault();

  assert.equal(harness.hooks.queryState.region, '대구광역시');
  assert.equal(harness.calls.find((call) => call.type === 'recommend').state.region, '대구광역시');
});

test('keeps the all-regions default when the profile has no sidoName', async () => {
  const harness = createHarness({ profileResponse: okProfile({ regionCode: '26110', sidoName: null }) });

  await harness.hooks.loadProfileRegionDefault();

  assert.equal(harness.hooks.queryState.region, '');
  assert.equal(harness.calls.find((call) => call.type === 'recommend').state.region, '');
});

test('keeps the all-regions default when profile retrieval fails', async () => {
  const harness = createHarness({ profileResponse: { ok: false, status: 503 } });

  await harness.hooks.loadProfileRegionDefault();

  assert.equal(harness.hooks.queryState.region, '');
  assert.equal(harness.calls.find((call) => call.type === 'recommend').state.region, '');
});

test('does not replace a manual region choice when the profile request resolves later', async () => {
  let resolveProfile;
  const harness = createHarness({
    profileRequest: () => new Promise((resolve) => { resolveProfile = resolve; })
  });
  harness.hooks.setupFilters();
  const pending = harness.hooks.loadProfileRegionDefault();

  harness.selects.region.value = '서울특별시';
  harness.selects.region.dispatch('change');
  resolveProfile(okProfile({ sidoName: '부산광역시' }));
  await pending;

  assert.equal(harness.hooks.queryState.region, '서울특별시');
  assert.equal(harness.selects.region.value, '서울특별시');
  assert.equal(harness.calls.filter((call) => call.type === 'recommend').length, 1);
  assert.equal(harness.calls.find((call) => call.type === 'recommend').state.region, '서울특별시');
});

test('waits for profile region before initial recommendations and preserves updating flow', async () => {
  let resolveProfile;
  const harness = createHarness({
    profileRequest: () => new Promise((resolve) => { resolveProfile = resolve; })
  });

  harness.hooks.handleAuthenticationState({ detail: { status: 'authenticated' } });
  assert.equal(harness.calls.some((call) => call.type === 'recommend'), false);
  resolveProfile(okProfile({ sidoName: '부산광역시' }));
  await new Promise((resolve) => setImmediate(resolve));

  assert.equal(harness.hooks.queryState.region, '부산광역시');
  assert.equal(harness.calls.find((call) => call.type === 'recommend').state.region, '부산광역시');
  assert.equal(harness.calls.some((call) => call.type === 'answers'), true);
  assert.equal(harness.calls.some((call) => call.type === 'hashtags'), true);
});
