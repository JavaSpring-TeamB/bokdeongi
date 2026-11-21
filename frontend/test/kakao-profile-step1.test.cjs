const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'signup-account.js'), 'utf8');

function buildProfileRequest(name) {
  const elements = {
    '#profile-onboarding-name': { value: name },
    '#profile-onboarding-birth-date': { value: '2000-01-01' },
    '#profile-sido': { value: '서울특별시' },
    '#profile-sigungu': { value: '11110', options: [{ text: '종로구' }], selectedIndex: 0 },
    '#profile-household-size': { value: '1' },
    '#profile-income-amount': { value: '' },
    '#profile-children-count': { value: '0' },
    '#profile-children-exact': { value: '' },
    '#profile-youngest-year': { value: '' },
    '#profile-form-error': { textContent: '', focus() {} }
  };
  const selected = {
    gender: 'M',
    housing_type: 'OWN',
    has_house: 'false',
    income_input_type: 'UNKNOWN',
    marital_status: 'SINGLE',
    welfare_status: 'NONE'
  };
  const document = {
    querySelector(selector) {
      const match = selector.match(/^\[name="([^"]+)"\]:checked$/);
      if (match) return selected[match[1]] ? { value: selected[match[1]] } : null;
      return elements[selector] || null;
    },
    querySelectorAll(selector) {
      if (selector.includes('input:checked')) return [{ value: 'NONE', dataset: { none: '' } }];
      return [];
    },
    addEventListener() {}
  };
  const window = { location: { search: '?profileOnboarding=true' } };
  const context = { window, document, URLSearchParams, Date, Number, Set, Event };
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__testHooks={profile,profileOnboarding,workflow};`, context);
  context.__testHooks.profileOnboarding.active = true;
  context.__testHooks.workflow.profileStep = 0;
  return context.__testHooks.profile();
}

test('requires a Kakao member to enter an actual name before Step 1 is created', () => {
  assert.equal(buildProfileRequest('   '), null);
});

test('sends the entered actual name in the Kakao Step 1 request', () => {
  const requests = buildProfileRequest('홍길동');
  assert.equal(requests[0].name, '홍길동');
  assert.equal(requests[0].birthDate, '2000-01-01');
});
