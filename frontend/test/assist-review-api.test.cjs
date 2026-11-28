const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-review.js'), 'utf8');
const html = fs.readFileSync(path.join(__dirname, '..', 'assist-review.html'), 'utf8');

test('review rows keep keyboard selection while only uploadable documents show an action button', () => {
  const at = source.lastIndexOf('})();');
  const listeners = {};
  const container = { innerHTML: '' };
  const fileInput = { addEventListener() {} };
  const focusButton = { dataset: { documentKey: '1' }, focused: false, focus() { this.focused = true; } };
  const document = {
    addEventListener(type, listener) { listeners[type] = listener; },
    querySelector(selector) { return selector === '#review-file-input' ? fileInput : container; },
    querySelectorAll() { return [focusButton]; }
  };
  const context = { document, window: { addEventListener() {}, BokdeongiApplication: { assistantUrl: () => '#' } }, console };
  vm.runInNewContext(source.slice(0, at) + 'globalThis.reviewHooks = { state, documentItemMarkup, bindInteractions };\n' + source.slice(at), context);
  const { state, documentItemMarkup, bindInteractions } = context.reviewHooks;
  state.documents = { states: { 1: { prepared: true }, 2: { prepared: true }, 'form:0': { prepared: true }, 'unknown:0': { prepared: true } } };
  const entries = [
    { key: '1', kind: 'document', name: 'OCR 서류', item: { ocrSupported: true } },
    { key: '2', kind: 'document', name: '직접 확인 서류', item: { ocrSupported: false } },
    { key: 'form:0', kind: 'form', name: '신청서' },
    { key: 'unknown:0', kind: 'unknown', name: '확인 서류' }
  ];
  for (const entry of entries) {
    const markup = documentItemMarkup(entry);
    assert.match(markup, /data-document-row=/);
    assert.match(markup, /<button class="review-document__select" type="button" data-document-key=/);
    assert.doesNotMatch(markup, />검토 결과 보기<\/button>/);
    assert.equal(markup.includes('data-row-action='), entry.key === '1');
  }
  Object.assign(state, { application: { id: 1 }, benefit: { title: '테스트 혜택' }, missing: { allReady: true, groups: [], forms: [], unknown: [] }, entries, selectedKey: '1' });
  bindInteractions();
  listeners.click({ target: { closest(selector) { return selector === '[data-document-row]' ? { dataset: { documentRow: 'form:0' } } : null; } } });
  assert.equal(state.selectedKey, 'form:0');
  assert.match(container.innerHTML, /신청서/);
  listeners.click({ target: { closest(selector) { return selector === '[data-document-key]' ? focusButton : null; } } });
  assert.equal(state.selectedKey, '1');
  assert.equal(focusButton.focused, true);
});

test('review stage loads its application, benefit, documents, and server missing-document state', () => {
  assert.match(source, /loadCurrentAssistantApplication\(\$\('#assist-content'\)\)/);
  assert.match(source, /loadBenefit\(state\.application\.benefitId\)/);
  assert.match(source, /getDocuments\(state\.application\.id\)/);
  assert.match(source, /getMissingDocuments\(state\.application\.id\)/);
  assert.match(source, /eligibilityConfirmed !== true/);
  assert.match(source, /allPrepared\(\)/);
  assert.match(source, /state\.entries\.length > 0 && !allPrepared\(\)/);
});

test('review stage distinguishes OCR items from form and unknown keys without inventing document IDs', () => {
  assert.match(source, /entry\.item\?\.ocrSupported/);
  assert.match(source, /key: 'form:' \+ index/);
  assert.match(source, /key: 'unknown:' \+ index/);
  assert.match(source, /reviewDocument\(state\.application\.id, entry\.documentTypeId, file\)/);
  assert.doesNotMatch(source, /documentTypeId:.*form:/);
});

test('review stage keeps RETRY transient, refreshes server state for saved OCR results, and gates step four by allReady', () => {
  assert.match(source, /result\.result === 'RETRY'/);
  assert.match(source, /state\.transientResults\.set\(entry\.key, result\)/);
  assert.match(source, /await refreshServerState\(\)/);
  assert.match(source, /state\.missing\.allReady/);
  assert.match(source, /updateApplication\(state\.application\.id, \{ step: 4 \}\)/);
  assert.match(source, /assistantUrl\(4, state\.application\.id\)/);
});

test('review stage does not retain files or use mock OCR data', () => {
  assert.doesNotMatch(source, /sessionStorage|localStorage|reviewDocuments|setTimeout/);
  assert.match(source, /CONSENT_REQUIRED/);
  assert.match(source, /UNREADABLE/);
  assert.match(source, /AI_UNAVAILABLE/);
});

test('DOC_PROCESS consent is checked and explicitly saved before OCR file selection', () => {
  assert.match(html, /js\/consent-documents\.js/);
  assert.match(html, /doc-process-consent-dialog/);
  assert.match(source, /getConsentStatus\(\)/);
  assert.match(source, /consentType === 'DOC_PROCESS'/);
  assert.match(source, /agreed === true && item\.docVersion === docProcessDocument\(\)\?\.version/);
  assert.match(source, /updateConsent\('DOC_PROCESS', version, true\)/);
  assert.match(source, /status !== 204/);
  assert.match(source, /CONSENT_REQUIRED/);
  assert.match(source, /renderInto\(\$\('#doc-process-consent-content'\), 'DOC_PROCESS'\)/);
});

test('OCR upload action is restricted to prepared, OCR-supported document entries and server allReady still gates next step', () => {
  assert.match(source, /entry\.kind === 'document' && entry\.item\?\.ocrSupported && isPrepared\(entry\)/);
  assert.match(source, /if \(!entry\?\.item\?\.ocrSupported \|\| !isPrepared\(entry\) \|\| state\.uploadingKey\) return/);
  assert.match(html, /accept="\.pdf,\.jpg,\.jpeg,\.png"/);
  assert.match(source, /state\.missing\.allReady/);
  assert.match(source, /이 서류는 사진 OCR 검토 대상이 아닙니다/);
});
