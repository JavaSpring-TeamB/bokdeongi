const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.join(__dirname, '..');
const js = fs.readFileSync(path.join(root, 'js', 'assist-documents.js'), 'utf8');
const api = fs.readFileSync(path.join(root, 'js', 'application-api.js'), 'utf8');
const html = fs.readFileSync(path.join(root, 'assist-documents.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'css', 'assist-documents.css'), 'utf8');

test('document records are requested only after current DOC_RECORD consent and matching version are confirmed', () => {
  assert.match(html, /js\/consent-documents\.js/);
  assert.match(js, /getConsentStatus\(\)/);
  assert.match(js, /versionFor\('DOC_RECORD'\)/);
  assert.match(js, /consent\?\.agreed !== true \|\| consent\.docVersion !== version/);
  assert.match(js, /getDocumentRecords\(\)/);
  assert.match(js, /error\?\.code === 'CONSENT_REQUIRED'/);
});

test('record DTO values render from the API and match current documents by documentTypeId', () => {
  assert.match(api, /\/api\/me\/document-records/);
  assert.match(api, /isDocumentRecordsResponse/);
  assert.match(js, /entry\.kind === 'document' && Number\(entry\.item\.documentTypeId\) === Number\(record\.documentTypeId\)/);
  assert.match(js, /record\.issueDate/);
  assert.match(js, /record\.validityDays/);
  assert.match(js, /record\.expiresAt/);
  assert.match(js, /record\.valid/);
  assert.match(js, /저장된 서류 기록이 없습니다\./);
  assert.match(js, /유효기간 기준 없음/);
  assert.match(css, /remembered-records/);
});

test('consent withdrawal or retrieval failure clears record data and never updates application preparation or OCR state', () => {
  assert.match(js, /state\.records = \[\];\s*state\.recordsStatus = 'checking'/);
  assert.match(js, /state\.records = \[\];\s*if \(error\?\.code === 'CONSENT_REQUIRED'\)/);
  assert.match(js, /visibilitychange/);
  assert.doesNotMatch(js, /records\.forEach\([^)]*updateDocumentPrepared/);
  assert.doesNotMatch(js, /records\.forEach\([^)]*state\.localPrepared/);
});

test('issue-date registration is available only for real document IDs after DOC_RECORD consent', () => {
  assert.match(js, /recordConsentStatus !== 'agreed'/);
  assert.match(js, /recordRegistrationMarkup\(entry\)/);
  assert.match(js, /type="date" data-record-issue-date=/);
  assert.match(js, /data-record-submit=/);
  assert.match(js, /saveDocumentRecord\(event\.currentTarget\.dataset\.recordSubmit\)/);
  assert.match(js, /my-page\.html/);
  assert.match(js, /isValidDocumentIssueDate\(issueDate\)/);
  assert.match(js, /recordSavingKey !== null/);
});

test('successful registration refreshes saved records and does not alter preparation or review status', () => {
  assert.match(api, /registerDocumentRecord\(documentTypeId, issueDate\)/);
  assert.match(api, /\/api\/me\/document-records/);
  assert.match(api, /method: 'POST'/);
  assert.match(api, /JSON\.stringify\(\{ documentTypeId: id, issueDate \}\)/);
  assert.match(api, /response\.status !== 204/);
  assert.match(js, /registerDocumentRecord\(Number\(key\), issueDate\)/);
  assert.match(js, /loadDocumentRecords\(\{ force: true \}\)/);
  assert.match(js, /저장된 기록은 신청 준비 완료나 OCR 검토 상태를 바꾸지 않습니다/);
  assert.match(js, /state\.records = \[\];\s*state\.recordsStatus = 'consent-required'/);
});
