const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-guide.js'), 'utf8');

test('guide stage obtains its benefit, missing-document check, and guide data from the application ID', () => {
  assert.match(source, /loadCurrentAssistantApplication\(\$\('#main-content'\)\)/);
  assert.match(source, /loadBenefit\(state\.application\.benefitId\)/);
  assert.match(source, /getMissingDocuments\(state\.application\.id\)/);
  assert.match(source, /getApplicationGuide\(state\.application\.id\)/);
  assert.match(source, /eligibilityConfirmed/);
  assert.match(source, /missing\.allReady/);
});

test('guide stage uses only safe official URLs and explicit user confirmation before submission', () => {
  assert.match(source, /url\.protocol === 'http:' \|\| url\.protocol === 'https:'/);
  assert.match(source, /window\.confirm/);
  assert.match(source, /updateApplicationStatus\(application\.id, 'SUBMITTED'\)/);
  assert.doesNotMatch(source, /localStorage|sessionStorage|guideSampleData/);
});

test('guide stage keeps external navigation and clipboard copying separate from completion storage', () => {
  assert.match(source, /window\.open\(apply\.dataset\.url, '_blank', 'noopener,noreferrer'\)/);
  assert.match(source, /navigator\.clipboard\?\.writeText/);
  assert.match(source, /window\.location\.assign\(applicationStatusPath\)/);
});
