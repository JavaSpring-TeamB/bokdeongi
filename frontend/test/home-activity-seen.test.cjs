const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-detail.js'), 'utf8');

function createHarness({ search = '?id=17&fromHomeActivity=1', authenticationState = 'authenticated', status = 200, payload = { updated: true } } = {}) {
  const calls = [];
  const replacements = [];
  const auth = {
    refreshAuthentication: async () => authenticationState,
    getCsrfToken: async () => ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-test-token' }),
    authenticatedFetch: async (requestPath, options) => {
      calls.push({ requestPath, options });
      return { ok: status >= 200 && status < 300, status, json: async () => payload };
    }
  };
  const context = {
    URLSearchParams, AbortController, Set, Number, String, encodeURIComponent, console: { error() {} },
    document: { title: '혜택 상세', addEventListener() {}, querySelector() { return null; } },
    window: {
      location: { search, pathname: '/benefit-detail.html', hash: '' },
      history: { replaceState(_state, _title, url) { replacements.push(url); } },
      BokdeongiAuth: auth
    }
  };
  vm.createContext(context);
  vm.runInContext(`${source}\nglobalThis.__seenHooks = { markHomeActivityBenefitAsSeen, homeActivitySeenRequests };`, context);
  return { calls, replacements, hooks: context.__seenHooks };
}

test('marks only the home activity benefit after successful detail and sends CSRF-authenticated POST', async () => {
  const harness = createHarness();

  const marked = await harness.hooks.markHomeActivityBenefitAsSeen(17);

  assert.equal(marked, true);
  assert.equal(harness.calls.length, 1);
  assert.equal(harness.calls[0].requestPath, '/api/home/activity/benefits/17/seen');
  assert.equal(harness.calls[0].options.method, 'POST');
  assert.deepEqual({ ...harness.calls[0].options.headers }, { 'X-XSRF-TOKEN': 'csrf-test-token' });
  assert.equal(harness.replacements[0], '/benefit-detail.html?id=17');
});

test('does not send a seen request for a direct detail visit or an unauthenticated visitor', async () => {
  const direct = createHarness({ search: '?id=17' });
  assert.equal(await direct.hooks.markHomeActivityBenefitAsSeen(17), false);
  assert.equal(direct.calls.length, 0);

  const anonymous = createHarness({ authenticationState: 'unauthenticated' });
  assert.equal(await anonymous.hooks.markHomeActivityBenefitAsSeen(17), false);
  assert.equal(anonymous.calls.length, 0);
});

test('keeps the home marker when the API fails so the server can continue to report it unseen', async () => {
  const harness = createHarness({ status: 503 });

  assert.equal(await harness.hooks.markHomeActivityBenefitAsSeen(17), false);
  assert.equal(harness.replacements.length, 0);
  assert.equal(harness.hooks.homeActivitySeenRequests.has('17'), false);
});

test('keeps the home marker when the server reports that no unseen row was updated', async () => {
  const harness = createHarness({ payload: { updated: false } });

  assert.equal(await harness.hooks.markHomeActivityBenefitAsSeen(17), false);
  assert.equal(harness.replacements.length, 0);
  assert.equal(harness.hooks.homeActivitySeenRequests.has('17'), false);
});

test('does not repeat a successful seen request on the same page', async () => {
  const harness = createHarness();

  await harness.hooks.markHomeActivityBenefitAsSeen(17);
  await harness.hooks.markHomeActivityBenefitAsSeen(17);

  assert.equal(harness.calls.length, 1);
});
