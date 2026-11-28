const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function loadPage(name, exports) {
  const source = fs.readFileSync(path.join(__dirname, '..', 'js', name), 'utf8');
  const marker = '})();';
  const at = source.lastIndexOf(marker);
  assert.ok(at > 0);
  const container = { innerHTML: '' };
  const input = { addEventListener(type, handler) { this[type] = handler; } };
  const listeners = {};
  const document = {
    querySelector(selector) { return selector === '#review-file-input' ? input : container; },
    addEventListener(type, handler) { listeners[type] = handler; }
  };
  const revoked = [];
  const context = {
    document,
    window: { addEventListener() {}, BokdeongiApplication: { assistantUrl: () => '#', parseApplicationId: Number, messageFor: (e) => e.message } },
    URL: { createObjectURL: () => 'blob:test-file', revokeObjectURL: (url) => revoked.push(url) },
    console
  };
  vm.runInNewContext(source.slice(0, at) + `globalThis.pageTest = { ${exports.join(', ')} };\n` + source.slice(at), context);
  return { ...context.pageTest, context, container, input, revoked, listeners };
}

function review(entries, states) {
  const page = loadPage('assist-review.js', ['state', 'statusFor', 'reviewDescription', 'render', 'clearPreview', 'previewMarkup']);
  Object.assign(page.state, {
    application: { id: 1 }, benefit: { title: '테스트 혜택' },
    documents: { states }, missing: { groups: [], forms: [], unknown: [], allReady: true },
    entries, selectedKey: entries[0]?.key || null
  });
  return page;
}

const ocr = { key: '1', kind: 'document', item: { ocrSupported: true }, name: '주민등록등본' };
const manual = { key: '2', kind: 'document', item: { ocrSupported: false }, name: '가족관계증명서' };
const form = { key: 'form:0', kind: 'form', name: '신청서' };
const unknown = { key: 'unknown:0', kind: 'unknown', name: '기타 확인서' };

test('OCR-only documents need a file; READY alone is not shown as a pass without reviewed', () => {
  const page = review([ocr], { 1: { prepared: true, reviewed: false, result: null } });
  assert.equal(page.statusFor(ocr)[0], '검토 필요');
  page.render();
  assert.match(page.container.innerHTML, /AI 서류 검토 대상/);
  assert.match(page.container.innerHTML, /파일 선택 후 검토/);
  assert.doesNotMatch(page.container.innerHTML, /AI 검토 지원 대상 서류가 없습니다/);
  page.state.documents.states[1] = { prepared: true, reviewed: false, result: 'READY' };
  assert.equal(page.statusFor(ocr)[0], '검토 필요');
  page.state.documents.states[1].reviewed = true;
  assert.equal(page.statusFor(ocr)[0], '검토 통과');
});

test('manual-only documents never claim an OCR pass or offer upload', () => {
  const page = review([manual], { 2: { prepared: true, reviewed: true, result: null } });
  assert.equal(page.statusFor(manual)[0], '준비 확인 완료');
  page.render();
  assert.match(page.container.innerHTML, /AI 검토 지원 대상 서류가 없습니다/);
  assert.match(page.container.innerHTML, /AI 검토 미지원 서류/);
  assert.doesNotMatch(page.container.innerHTML, /파일 선택 후 검토/);
});

test('mixed, form-only, and empty lists keep their distinct guidance', () => {
  const mixed = review([ocr, manual], { 1: { prepared: true }, 2: { prepared: true, reviewed: true } });
  mixed.render();
  assert.match(mixed.container.innerHTML, /AI 서류 검토 대상/);
  assert.match(mixed.container.innerHTML, /AI 검토 미지원 서류/);
  const forms = review([form, unknown], { 'form:0': { prepared: true }, 'unknown:0': { prepared: true } });
  forms.render();
  assert.equal(forms.statusFor(form)[0], '별도 확인 필요');
  assert.match(forms.container.innerHTML, /별도 확인이 필요한 서식 및 미분류 서류/);
  const empty = review([], {});
  empty.render();
  assert.match(empty.container.innerHTML, /필요 서류를 확인하지 못했습니다/);
});

test('OCR failure, retry, and API error do not create a pass', () => {
  const page = review([ocr], { 1: { prepared: true, reviewed: false, result: 'MISSING' } });
  assert.equal(page.statusFor(ocr)[0], '보완 필요');
  page.state.transientResults.set('1', { result: 'RETRY', reason: '다시 촬영' });
  assert.equal(page.statusFor(ocr)[0], '재업로드 필요');
  page.state.transientResults.clear();
  page.state.error = 'OCR 서버 연결 실패';
  page.render();
  assert.match(page.container.innerHTML, /OCR 서버 연결 실패/);
  assert.doesNotMatch(page.container.innerHTML, /status-badge success">검토 통과/);
});

test('local preview is limited to the selected file and revokes its URL', () => {
  const page = review([ocr], { 1: { prepared: true } });
  page.state.preview = { key: '1', url: 'blob:test-file', type: 'application/pdf', name: 'sample.pdf' };
  assert.match(page.previewMarkup(ocr), /선택한 PDF 원본 미리보기/);
  page.clearPreview();
  assert.deepEqual(page.revoked, ['blob:test-file']);
  assert.equal(page.previewMarkup(ocr), '');
});

test('file selection previews a test file and sends it once for OCR review', async () => {
  const page = loadPage('assist-review.js', ['state', 'bindInteractions']);
  const entry = { ...ocr };
  Object.assign(page.state, { application: { id: 1 }, benefit: { title: '테스트' }, documents: { states: { 1: { prepared: true, reviewed: false } } }, missing: { groups: [], forms: [], unknown: [], allReady: false }, entries: [entry], selectedKey: '1' });
  let calls = 0;
  page.context.window.BokdeongiApplication.reviewDocument = async () => { calls++; return { result: 'RETRY', reason: '다시 촬영' }; };
  page.bindInteractions();
  const file = { name: 'sample.pdf', size: 100, type: 'application/pdf' };
  page.input.change({ target: { files: [file], value: 'sample.pdf' } });
  page.input.change({ target: { files: [file], value: 'sample.pdf' } });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(calls, 1);
  assert.equal(page.state.preview.url, 'blob:test-file');
  assert.match(page.container.innerHTML, /선택한 PDF 원본 미리보기/);
  assert.equal(page.state.transientResults.get('1').result, 'RETRY');
});

test('CONDITIONAL with no answer questions still renders CHECK guidance', () => {
  const page = loadPage('assist-eligibility.js', ['state', 'questionsMarkup']);
  page.state.eligibility = { questions: [], groups: [{ conditions: [{ status: 'CHECK', checkType: 'PROFILE', label: '소득', guide: '소득 정보를 입력하세요', sourceQuote: '중위소득 75% 이하' }] }] };
  const markup = page.questionsMarkup();
  assert.match(markup, /소득 정보를 입력하세요/);
  assert.match(markup, /프로필 정보 확인·수정/);
  assert.doesNotMatch(markup, /추가로 답할 질문이 없습니다/);
});

test('preparation labels OCR, manual, form, and unknown documents without uploading', () => {
  const page = loadPage('assist-documents.js', ['state', 'optionMarkup']);
  page.state.documents = { states: { 1: { prepared: true }, 2: { prepared: true }, 'form:0': { prepared: false }, 'unknown:0': { prepared: false } } };
  assert.match(page.optionMarkup(ocr), /AI 서류 검토 지원/);
  assert.match(page.optionMarkup(manual), /직접 준비·확인/);
  assert.match(page.optionMarkup(form), /별도 확인 필요/);
  assert.match(page.optionMarkup(unknown), /별도 확인 필요/);
  assert.doesNotMatch(page.optionMarkup(ocr), /type="file"/);
});
