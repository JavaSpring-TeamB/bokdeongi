const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const js = fs.readFileSync(path.join(__dirname, '..', 'js', 'my-page.js'), 'utf8');
const auth = fs.readFileSync(path.join(__dirname, '..', 'js', 'auth.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '..', 'my-page.html'), 'utf8');

test('My Page loads and displays independent server states for both document consents', () => {
  assert.match(js, /CONSENT_TYPES = \['OPTIONAL_INFO', 'SENSITIVE', 'DOC_PROCESS', 'DOC_RECORD'\]/);
  assert.match(js, /item\('DOC_PROCESS', '서류 처리 및 자동 검토'/);
  assert.match(js, /item\('DOC_RECORD', '서류 유효기간 기억'/);
  assert.match(js, /동의 이력 없음/);
  assert.match(js, /DOC_RECORD: null/);
  assert.match(js, /CONSENT_TYPES\.includes\(item\.consentType\)/);
});

test('document consent modal renders the shared document and only confirms after explicit action', () => {
  assert.match(html, /document-consent-dialog/);
  assert.match(js, /get\(type\)/);
  assert.match(js, /renderInto\(dialog\.querySelector\('\[data-document-consent-content\]'\), type\)/);
  assert.match(js, /data-document-consent-agree/);
  assert.match(js, /changeConsent\(type, true\)/);
});

test('DOC_PROCESS and DOC_RECORD withdrawals require distinct confirmations before changing server state', () => {
  assert.match(html, /id="consent-withdraw-dialog"/);
  assert.match(js, /동의를 철회하면 이후 서류 업로드 및 자동 검토 기능을 이용할 수 없습니다/);
  assert.match(js, /동의를 철회하면 기존에 기억한 서류 발급일 기록이 삭제됩니다/);
  assert.match(js, /openConsentWithdrawalDialog\(type, documentConsentChange\)/);
  assert.match(js, /data-consent-withdraw-confirm[\s\S]*?void changeConsent\(type, false\)/);
  assert.doesNotMatch(js, /window\.confirm\(/);
});

test('consent withdrawal modal cancels without saving and confirms the selected type', () => {
  const listeners = new Map();
  const calls = [];
  const description = { textContent: '' };
  const dialog = {
    dataset: {}, open: false,
    addEventListener(type, listener) { listeners.set(`dialog:${type}`, listener); },
    showModal() { this.open = true; },
    close() { this.open = false; listeners.get('dialog:close')?.(); }
  };
  const document = {
    querySelector(selector) {
      if (selector === '#consent-withdraw-dialog') return dialog;
      if (selector === '#consent-withdraw-dialog-description') return description;
      return null;
    },
    getElementById() { return null; },
    addEventListener(type, listener) { listeners.set(type, listener); },
    querySelectorAll() { return []; }
  };
  const context = vm.createContext({ document, window: {}, sessionStorage: { getItem() { return null; } }, setTimeout() {}, console, calls });
  vm.runInContext(`${js}
    consentLoadState = 'loaded';
    for (const type of ['OPTIONAL_INFO', 'DOC_PROCESS', 'DOC_RECORD']) consentState[type] = { agreed: true };
    changeConsent = async (type, agreed) => calls.push({ type, agreed });
    setupInteractions();
    globalThis.openTestWithdrawal = openConsentWithdrawalDialog;
  `, context);

  const click = (attribute) => listeners.get('click')({ target: { closest(selector) { return selector === `[${attribute}]` ? {} : null; } } });
  for (const [index, type] of ['OPTIONAL_INFO', 'DOC_PROCESS', 'DOC_RECORD'].entries()) {
    const opener = { isConnected: true, focused: false, focus() { this.focused = true; } };
    context.openTestWithdrawal(type, opener);
    assert.equal(dialog.open, true);
    assert.equal(dialog.dataset.consentType, type);
    click('data-consent-withdraw-close');
    assert.equal(dialog.open, false);
    assert.equal(opener.focused, true);
    assert.equal(calls.length, index);

    context.openTestWithdrawal(type, opener);
    click('data-consent-withdraw-confirm');
    assert.deepEqual(JSON.parse(JSON.stringify(calls.at(-1))), { type, agreed: false });
  }
  assert.equal(calls.length, 3);
});

test('consent updates reuse the shared authenticated CSRF API and refresh server state after 204', () => {
  assert.match(js, /auth\.updateConsent\(type, docVersion, agreed\)/);
  assert.match(js, /response\.status === 204/);
  assert.match(js, /loadConsentStatus\(\{ render: false \}\)/);
  assert.match(auth, /updateConsent\(consentType, docVersion, agreed\)/);
  assert.match(auth, /\[csrf\.headerName\]: csrf\.token/);
  assert.match(auth, /JSON\.stringify\(\{ consentType, docVersion, agreed \}\)/);
});

test('existing OPTIONAL_INFO and SENSITIVE controls remain wired', () => {
  assert.match(js, /data-optional-consent-change/);
  assert.match(js, /changeConsent\('SENSITIVE', true\)/);
  assert.match(js, /changeConsent\('SENSITIVE', false\)/);
});
