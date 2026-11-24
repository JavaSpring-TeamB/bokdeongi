const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-eligibility.js'), 'utf8');

test('eligibility stage obtains the benefit from the server application, not the URL or browser storage', () => {
  assert.match(source, /loadCurrentAssistantApplication\(container\)/);
  assert.match(source, /state\.application\.benefitId/);
  assert.match(source, /\/api\/benefits\/.*benefitId/);
  assert.doesNotMatch(source, /localStorage|eligibilityItems|housing-status/);
});

test('eligibility stage uses the established eligibility and profile answer contracts', () => {
  assert.match(source, /\/api\/benefits\/.*\/eligibility/);
  assert.match(source, /\/api\/me\/profile\/answers/);
  assert.match(source, /\/api\/me\/answers/);
  assert.match(source, /JSON\.stringify\(\{ answers, benefitId: state\.application\.benefitId \}\)/);
  assert.match(source, /loadBenefitAndEligibility\(\)/);
});

test('eligibility completion refreshes rules-engine data then saves only the stage-one confirmation', () => {
  assert.match(source, /await loadBenefitAndEligibility\(\)/);
  assert.match(source, /updateApplication\(state\.application\.id, \{ step: 2, appAnswers: \{ eligibilityConfirmed: true \} \}\)/);
  assert.match(source, /assistantUrl\(2, updated\.id\)/);
  assert.match(source, /next\?\.canProceed === true/);
});
