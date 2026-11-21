const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'home.js'), 'utf8');

function createHarness({ status = 'authenticated', profile }) {
  const result = { redirects: [], historyUpdates: 0, profileRequests: 0 };
  const window = {
    location: {
      search: '?profileOnboarding=true',
      pathname: '/index.html',
      hash: '',
      protocol: 'http:',
      hostname: 'localhost',
      replace: (url) => result.redirects.push(url)
    },
    history: { replaceState: () => { result.historyUpdates += 1; } },
    addEventListener() {},
    clearTimeout() {},
    BokdeongiAuth: {
      authenticatedFetch: async (url) => {
        result.profileRequests += 1;
        assert.equal(url, '/api/me/profile');
        return { ok: true, json: async () => profile };
      }
    }
  };
  const document = {
    documentElement: { dataset: {} },
    querySelector: () => null,
    querySelectorAll: () => [],
    addEventListener() {}
  };
  const context = { window, document, URLSearchParams, Date, Number, Promise, console };
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__testHooks={handleProfileOnboardingRedirect,oauthResult,authState};`, context);
  context.__testHooks.authState.status = status;
  return { context, result };
}

test('starts profile onboarding only after an authenticated incomplete profile response', async () => {
  const harness = createHarness({ profile: { profileCompleted: false, profileStep: 1 } });

  await harness.context.__testHooks.handleProfileOnboardingRedirect();

  assert.equal(harness.result.profileRequests, 1);
  assert.deepEqual(harness.result.redirects, ['signup-account.html?profileOnboarding=true']);
  assert.equal(harness.result.historyUpdates, 0);
});

test('does not start onboarding when the server reports a completed or malformed profile', async () => {
  const completed = createHarness({ profile: { profileCompleted: true, profileStep: 3 } });
  await completed.context.__testHooks.handleProfileOnboardingRedirect();
  assert.deepEqual(completed.result.redirects, []);
  assert.equal(completed.result.historyUpdates, 1);

  const malformed = createHarness({ profile: { profileCompleted: false, profileStep: 'unexpected' } });
  await malformed.context.__testHooks.handleProfileOnboardingRedirect();
  assert.deepEqual(malformed.result.redirects, []);
  assert.equal(malformed.result.historyUpdates, 1);
});

test('does not request profile data for an unauthenticated direct URL visit', async () => {
  const harness = createHarness({ status: 'unauthenticated', profile: { profileCompleted: false, profileStep: 0 } });

  await harness.context.__testHooks.handleProfileOnboardingRedirect();

  assert.equal(harness.result.profileRequests, 0);
  assert.deepEqual(harness.result.redirects, []);
  assert.equal(harness.result.historyUpdates, 1);
});
