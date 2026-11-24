const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-documents.js'), 'utf8');

test('documents stage obtains the benefit and structured documents from its server application', () => {
  assert.match(source, /loadCurrentAssistantApplication\(container\)/);
  assert.match(source, /loadBenefit\(state\.application\.benefitId\)/);
  assert.match(source, /getDocuments\(state\.application\.id\)/);
  assert.match(source, /\/api\/benefits\/.*benefitId/);
  assert.doesNotMatch(source, /requiredDocsText/);
});

test('documents stage blocks direct access before server eligibility completion', () => {
  assert.match(source, /state\.application\.appAnswers\?\.eligibilityConfirmed !== true/);
  assert.match(source, /assistantUrl\(1, state\.application\.id\)/);
});

test('documents stage saves only server document keys and protects unsaved changes', () => {
  assert.match(source, /form:' \+ index/);
  assert.match(source, /unknown:' \+ index/);
  assert.match(source, /updateDocumentPrepared\(state\.application\.id, key, prepared\)/);
  assert.match(source, /state\.localPrepared\.set\(key, prepared\)/);
  assert.match(source, /state\.failed\.set\(key/);
  assert.match(source, /state\.pending\.size/);
  assert.doesNotMatch(source, /docChecks/);
});

test('documents stage moves to review only after preparation is saved and uses the common application update', () => {
  assert.match(source, /function canMoveNext\(\) \{ return allPrepared\(\) && state\.pending\.size === 0 && state\.failed\.size === 0/);
  assert.match(source, /updateApplication\(state\.application\.id, \{ step: 3 \}\)/);
  assert.match(source, /assistantUrl\(3, state\.application\.id\)/);
});
