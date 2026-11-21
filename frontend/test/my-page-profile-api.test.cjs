const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function createHooks() {
  const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'my-page.js'), 'utf8');
  const document = {
    addEventListener() {},
    querySelector() { return null; },
    querySelectorAll() { return []; }
  };
  const context = vm.createContext({
    console,
    document,
    window: {},
    sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    setTimeout() {}
  });

  vm.runInContext(`${source}\nglobalThis.__profileApiHooks = { applyProfileResponse, changedProfileSteps, profileStepRequests, uniqueProfileCodes, putProfileStep, formatIncomeAmount };`, context);
  return context.__profileApiHooks;
}

function profile(overrides = {}) {
  return {
    name: '복덩이',
    birthDate: '2000-01-01',
    gender: 'F',
    regionCode: '11110',
    sidoName: '서울특별시',
    sigunguName: '종로구',
    householdSize: 1,
    incomeInputType: 'MONTHLY',
    medianIncomePct: 50,
    housingType: 'MONTHLY',
    hasHouse: false,
    householdTypeCodes: ['JA0404'],
    statusCodes: ['JA0320'],
    maritalStatus: 'SINGLE',
    childrenCount: 0,
    youngestChildBirthYear: null,
    welfareStatus: 'NONE',
    ...overrides
  };
}

function draft(overrides = {}) {
  return {
    birthDate: '2000-01-01',
    gender: 'F',
    regionCode: '11110',
    sidoName: '서울특별시',
    sigunguName: '종로구',
    householdSize: 1,
    incomeInputType: 'MONTHLY',
    incomeAmountText: '',
    medianIncomePctText: '50',
    housingType: 'MONTHLY',
    hasHouse: 'false',
    householdTypeCodes: ['JA0404'],
    maritalStatus: 'SINGLE',
    childrenCount: 0,
    youngestChildBirthYear: null,
    statusCodes: ['JA0320'],
    welfareStatus: 'NONE',
    ...overrides
  };
}

test('builds only the changed Step 1 request with the current DTO field names', () => {
  const hooks = createHooks();
  hooks.applyProfileResponse(profile());
  const changes = hooks.changedProfileSteps(draft({ gender: 'M' }));
  const requests = hooks.profileStepRequests(draft({ gender: 'M' }), changes);

  assert.equal(changes.step1, true);
  assert.equal(changes.step2, false);
  assert.equal(changes.step3, false);
  assert.deepEqual(JSON.parse(JSON.stringify(requests)), [{
    step: 1,
    body: {
      birthDate: '2000-01-01', gender: 'M', regionCode: '11110',
      sidoName: '서울특별시', sigunguName: '종로구', name: '복덩이'
    }
  }]);
});

test('maps hasHouse to the inverse noHouse field and omits server-managed income fields', () => {
  const hooks = createHooks();
  hooks.applyProfileResponse(profile());
  const changedDraft = draft({ housingType: 'JEONSE', incomeAmountText: '2000000' });
  const requests = hooks.profileStepRequests(changedDraft, hooks.changedProfileSteps(changedDraft));

  assert.deepEqual(JSON.parse(JSON.stringify(requests)), [{
    step: 2,
    body: {
      householdSize: 1, incomeInputType: 'MONTHLY', incomeAmount: 2000000,
      medianIncomePct: null, housingType: 'JEONSE', noHouse: true,
      householdTypeCodes: ['JA0404']
    }
  }]);
});

test('uses BRACKET medianIncomePct and deduplicates status codes before Step 3 storage', () => {
  const hooks = createHooks();
  hooks.applyProfileResponse(profile({ incomeInputType: 'UNKNOWN', medianIncomePct: null }));
  const changedDraft = draft({
    incomeInputType: 'BRACKET', incomeAmountText: '', medianIncomePctText: '78',
    statusCodes: ['JA0320', 'JA0320', 'JA0327']
  });
  const changes = hooks.changedProfileSteps(changedDraft);
  const requests = hooks.profileStepRequests(changedDraft, changes);

  assert.deepEqual(JSON.parse(JSON.stringify(requests)), [
    {
      step: 2,
      body: {
        householdSize: 1, incomeInputType: 'BRACKET', incomeAmount: null,
        medianIncomePct: 78, housingType: 'MONTHLY', noHouse: true,
        householdTypeCodes: ['JA0404']
      }
    },
    {
      step: 3,
      body: {
        statusCodes: ['JA0320', 'JA0327'], maritalStatus: 'SINGLE', childrenCount: 0,
        youngestChildBirthYear: null, welfareStatus: 'NONE'
      }
    }
  ]);
});

test('formats income amount without sending separators to the DTO request', () => {
  const hooks = createHooks();
  const input = {
    value: '2500000',
    selectionStart: 7,
    setSelectionRange(start) { this.selectionStart = start; }
  };

  hooks.formatIncomeAmount(input);

  assert.equal(input.value, '2,500,000');
  assert.equal(input.selectionStart, 9);
});

test('reissues a profile PUT once with a fresh CSRF token after a 403 response', async () => {
  const hooks = createHooks();
  const headers = [];
  let csrfCalls = 0;
  let putCalls = 0;
  const auth = {
    async getCsrfToken() {
      csrfCalls += 1;
      return { headerName: 'X-XSRF-TOKEN', token: 'csrf-' + csrfCalls };
    },
    async authenticatedFetch(url, init) {
      putCalls += 1;
      headers.push(init.headers['X-XSRF-TOKEN']);
      return {
        status: putCalls === 1 ? 403 : 200,
        ok: putCalls !== 1,
        async json() { return { profileCompleted: false }; }
      };
    }
  };

  const result = await hooks.putProfileStep(auth, { step: 2, body: { incomeInputType: 'UNKNOWN' } });

  assert.equal(result.response.ok, true);
  assert.equal(csrfCalls, 2);
  assert.deepEqual(headers, ['csrf-1', 'csrf-2']);
});
