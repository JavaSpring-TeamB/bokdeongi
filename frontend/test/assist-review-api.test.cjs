const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-review.js'), 'utf8');

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
