const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-documents.js'), 'utf8');

test('documents stage obtains the benefit ID from the server application and renders only benefit detail data', () => {
  assert.match(source, /loadCurrentAssistantApplication\(container\)/);
  assert.match(source, /loadBenefit\(application\.benefitId\)/);
  assert.match(source, /\/api\/benefits\/.*benefitId/);
  assert.match(source, /benefit\.requiredDocsText/);
  assert.doesNotMatch(source, /documentsData|resident|housing-status/);
});

test('documents stage blocks direct access before server eligibility completion', () => {
  assert.match(source, /application\.appAnswers\?\.eligibilityConfirmed !== true/);
  assert.match(source, /assistantUrl\(1, application\.id\)/);
});

test('documents stage does not invent a structured-document endpoint or unsafe document save request', () => {
  assert.doesNotMatch(source, /\/api\/applications\/.*\/documents/);
  assert.doesNotMatch(source, /docChecks/);
  assert.match(source, /구조화된 필요 서류 목록/);
  assert.match(source, /서류 검토로 이동<\/button>/);
});
