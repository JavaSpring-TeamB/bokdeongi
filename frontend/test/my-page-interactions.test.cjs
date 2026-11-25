const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'my-page.js'), 'utf8');

function createHarness() {
  const listeners = new Map();
  const calls = [];
  const document = {
    addEventListener(type, listener) {
      const registered = listeners.get(type) || [];
      registered.push(listener);
      listeners.set(type, registered);
    },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    getElementById() { return null; }
  };
  const context = vm.createContext({
    console,
    document,
    calls,
    window: {},
    sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    setTimeout() {}
  });

  vm.runInContext(`${source}
    renderSection = () => calls.push({ type: 'render', section: currentSection, mode: profileMode });
    loadConsentStatus = () => calls.push({ type: 'load-consents' });
    loadFavorites = () => calls.push({ type: 'load-favorites' });
    ensureMyPageRegions = () => calls.push({ type: 'load-regions' });
    openWithdrawalDialog = (opener) => calls.push({ type: 'withdrawal', opener });
    globalThis.__interactionHooks = { setupInteractions, calls, state: () => ({ currentSection, profileMode }) };
  `, context);

  const hooks = context.__interactionHooks;
  hooks.setupInteractions();
  return {
    calls: hooks.calls,
    state: () => JSON.parse(JSON.stringify(hooks.state())),
    click(target) {
      for (const listener of listeners.get('click') || []) listener({ target });
    }
  };
}

function clickTarget({ own = {}, ancestors = [], bodyHasProfileSection = true } = {}) {
  const nodes = [own, ...ancestors];
  if (bodyHasProfileSection) nodes.push({ dataset: { section: 'profile' }, body: true });
  return {
    closest(selector) {
      if (selector === '.my-page__menu-button[data-section]') {
        return nodes.find((node) => node.menuButton && node.dataset?.section) || null;
      }
      const attribute = selector.match(/^\[data-([a-z-]+)\]$/);
      if (attribute) {
        const key = attribute[1].replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
        return nodes.find((node) => node[key] !== undefined) || null;
      }
      return null;
    }
  };
}

test('profile edit button remains reachable through document delegation', () => {
  const harness = createHarness();
  harness.click(clickTarget({ own: { profileEdit: true } }));

  assert.deepEqual(harness.state(), { currentSection: 'profile', profileMode: 'edit' });
  assert.ok(harness.calls.some((call) => call.type === 'render' && call.mode === 'edit'));
  assert.ok(harness.calls.some((call) => call.type === 'load-regions'));
});

test('consent management button remains reachable through document delegation', () => {
  const harness = createHarness();
  harness.click(clickTarget({ own: { accountAction: true, dataset: { accountAction: '개인정보 동의 관리' } } }));

  assert.deepEqual(harness.state(), { currentSection: 'profile', profileMode: 'consents' });
  assert.ok(harness.calls.some((call) => call.type === 'load-consents'));
});

test('password change button remains reachable through document delegation', () => {
  const harness = createHarness();
  harness.click(clickTarget({ own: { accountAction: true, dataset: { accountAction: '비밀번호 변경' } } }));

  assert.deepEqual(harness.state(), { currentSection: 'profile', profileMode: 'password' });
});

test('withdrawal button opens its confirmation dialog without submitting', () => {
  const harness = createHarness();
  const button = { accountAction: true, dataset: { accountAction: '회원 탈퇴' } };
  harness.click(clickTarget({ own: button }));

  assert.equal(harness.calls.filter((call) => call.type === 'withdrawal').length, 1);
  assert.equal(harness.calls.find((call) => call.type === 'withdrawal').opener, button);
});

test('sidebar section button changes section and preserves delegated events', () => {
  const harness = createHarness();
  const menuButton = { menuButton: true, dataset: { section: 'bookmarks' } };
  harness.click(clickTarget({ own: { text: '관심 혜택' }, ancestors: [menuButton] }));

  assert.deepEqual(harness.state(), { currentSection: 'bookmarks', profileMode: 'view' });
  assert.ok(harness.calls.some((call) => call.type === 'render' && call.section === 'bookmarks'));
  assert.ok(harness.calls.some((call) => call.type === 'load-favorites'));
});

test('clicking a child inside an account button still reaches its action', () => {
  const harness = createHarness();
  const accountButton = { accountAction: true, dataset: { accountAction: '비밀번호 변경' } };
  harness.click(clickTarget({ own: { tagName: 'STRONG' }, ancestors: [accountButton] }));

  assert.equal(harness.state().profileMode, 'password');
});

test('ordinary content clicks do not select the body profile section', () => {
  const harness = createHarness();
  harness.click(clickTarget({ own: { text: '일반 영역' } }));

  assert.deepEqual(harness.state(), { currentSection: 'profile', profileMode: 'view' });
  assert.equal(harness.calls.length, 0);
});
