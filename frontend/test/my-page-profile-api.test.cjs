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
  const window = {};
  const context = vm.createContext({
    console,
    document,
    window,
    sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    setTimeout() {}
  });

  vm.runInContext(`${source}\nglobalThis.__profileApiHooks = { applyProfileResponse, changedProfileSteps, profileStepRequests, uniqueProfileCodes, putProfileStep, formatIncomeAmount, renderProfile, renderProfileView, loadProfile, resetMyPageUserData, handleMyPageAuthenticationChange, myPageData, window, get profileEditorState() { return profileEditorState; }, get profileResponse() { return profileResponse; }, get profileLoadState() { return profileLoadState; }, setCurrentSection(value) { currentSection = value; } };`, context);
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

function apiResponse(status, payload) {
  return { ok: status >= 200 && status < 300, status, async json() { return payload; } };
}

test('starts with neutral profile state and shows loading instead of example user data', () => {
  const hooks = createHooks();
  const markup = hooks.renderProfile();

  assert.deepEqual(JSON.parse(JSON.stringify(hooks.myPageData.profile)), { basics: [], settings: [] });
  assert.equal(hooks.profileResponse, null);
  assert.equal(hooks.profileEditorState.sido, '');
  assert.equal(hooks.profileEditorState.householdSize, '');
  assert.equal(hooks.profileEditorState.housingType, '');
  assert.match(markup, /프로필을 불러오는 중/);
  assert.doesNotMatch(markup, /김복덩|bokdeongi@email\.com|2004\.11\.04|중위소득 150%/);
});

test('maps only the actual profile response while retaining null, empty arrays, false, and zero', () => {
  const hooks = createHooks();
  hooks.applyProfileResponse(profile({
    name: '실제 사용자', email: 'actual@example.com', birthDate: null,
    householdSize: 0, incomeInputType: 'BRACKET', medianIncomePct: 0,
    housingType: null, hasHouse: false, householdTypeCodes: [], statusCodes: [],
    maritalStatus: null, childrenCount: 0, welfareStatus: null, profileCompleted: false
  }));

  const basics = hooks.myPageData.profile.basics;
  const settings = hooks.myPageData.profile.settings;
  const markup = hooks.renderProfileView();
  assert.equal(basics.find(([, label]) => label === '이름')[2], '실제 사용자');
  assert.equal(settings.find(([, label]) => label === '가구 형태')[2], '0인 가구');
  assert.equal(settings.find(([, label]) => label === '소득 정보')[2], '중위소득 0%');
  assert.equal(hooks.profileEditorState.hasHouse, 'false');
  assert.equal(hooks.profileEditorState.childrenCount, '0');
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.profileEditorState.householdTypes)), []);
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.profileEditorState.statusCodes)), []);
  assert.match(markup, /미입력/);
  assert.doesNotMatch(markup, /undefined|null/);
});

test('clears profile and favorite data when authentication becomes unauthenticated', () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  hooks.applyProfileResponse(profile());
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'unauthenticated' } });

  assert.equal(hooks.profileResponse, null);
  assert.equal(hooks.profileLoadState, 'unauthenticated');
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.myPageData.profile)), { basics: [], settings: [] });
});

test('loads My Page profile from the authenticated API and renders that account response', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  const actualProfile = profile({ name: '서버 사용자', email: 'server@example.com' });
  const requested = [];
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    async authenticatedFetch(url) {
      requested.push(url);
      return url === '/api/me/profile' ? apiResponse(200, actualProfile) : apiResponse(503, {});
    }
  };

  await hooks.loadProfile();

  assert.deepEqual(requested, ['/api/auth/consents', '/api/me/profile']);
  assert.equal(hooks.profileLoadState, 'loaded');
  assert.match(hooks.renderProfileView(), /서버 사용자/);
  assert.match(hooks.renderProfileView(), /server@example\.com/);
});

test('does not substitute profile examples when the profile API fails', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    async authenticatedFetch() { return apiResponse(503, {}); }
  };

  await hooks.loadProfile();

  assert.equal(hooks.profileLoadState, 'error');
  assert.equal(hooks.profileResponse, null);
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.myPageData.profile)), { basics: [], settings: [] });
  assert.match(hooks.renderProfile(), /프로필 정보를 불러오지 못했어요/);
  assert.doesNotMatch(hooks.renderProfile(), /김복덩|bokdeongi@email\.com/);
});

test('ignores a late profile response after the authenticated account has logged out', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  let resolveProfile;
  let notifyProfileRequest;
  const profileRequestStarted = new Promise((resolve) => { notifyProfileRequest = resolve; });
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    authenticatedFetch(url) {
      if (url === '/api/auth/consents') return Promise.resolve(apiResponse(200, { consents: [] }));
      notifyProfileRequest();
      return new Promise((resolve) => { resolveProfile = resolve; });
    }
  };

  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  const pendingLoad = hooks.loadProfile();
  await profileRequestStarted;
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'unauthenticated' } });
  resolveProfile(apiResponse(200, profile({ name: '이전 계정 사용자' })));
  await pendingLoad;

  assert.equal(hooks.profileLoadState, 'unauthenticated');
  assert.equal(hooks.profileResponse, null);
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.myPageData.profile)), { basics: [], settings: [] });
});

test('temporary authentication checking preserves a loaded profile without another request', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  let profileRequests = 0;
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    async authenticatedFetch(url) {
      if (url === '/api/me/profile') { profileRequests += 1; return apiResponse(200, profile()); }
      return apiResponse(200, { consents: [] });
    }
  };
  await hooks.loadProfile();
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'checking' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  hooks.setCurrentSection('bookmarks');
  hooks.setCurrentSection('profile');

  assert.equal(hooks.profileLoadState, 'loaded');
  assert.match(hooks.renderProfile(), /프로필 수정하기/);
  assert.equal(profileRequests, 1);
});

test('authenticated event starts one initial profile request when none is active', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  let profileRequests = 0;
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    async authenticatedFetch(url) {
      if (url === '/api/me/profile') { profileRequests += 1; return apiResponse(200, profile()); }
      return apiResponse(200, { consents: [] });
    }
  };
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  await new Promise(setImmediate);

  assert.equal(profileRequests, 1);
  assert.equal(hooks.profileLoadState, 'loaded');
});

test('temporary checking keeps an in-flight profile response valid', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  let resolveProfile;
  let profileRequests = 0;
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    authenticatedFetch(url) {
      if (url === '/api/me/profile') {
        profileRequests += 1;
        return new Promise((resolve) => { resolveProfile = resolve; });
      }
      return Promise.resolve(apiResponse(200, { consents: [] }));
    }
  };
  const pending = hooks.loadProfile();
  await new Promise(setImmediate);
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'checking' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  resolveProfile(apiResponse(200, profile({ name: '진행 중인 사용자' })));
  await pending;

  assert.equal(profileRequests, 1);
  assert.equal(hooks.profileLoadState, 'loaded');
  assert.match(hooks.renderProfileView(), /진행 중인 사용자/);
});

test('authentication after logout starts one new profile load and rejects the old response', async () => {
  const hooks = createHooks();
  hooks.setCurrentSection('legal');
  let resolveOldProfile;
  let profileRequests = 0;
  hooks.window.BokdeongiAuth = {
    async refreshAuthentication() { return 'authenticated'; },
    getAuthenticationState() { return 'authenticated'; },
    authenticatedFetch(url) {
      if (url !== '/api/me/profile') return Promise.resolve(apiResponse(200, { consents: [] }));
      profileRequests += 1;
      return profileRequests === 1
        ? new Promise((resolve) => { resolveOldProfile = resolve; })
        : Promise.resolve(apiResponse(200, profile({ name: '새 계정' })));
    }
  };
  const oldLoad = hooks.loadProfile();
  await new Promise(setImmediate);
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'unauthenticated' } });
  assert.equal(hooks.profileResponse, null);
  hooks.handleMyPageAuthenticationChange({ detail: { status: 'authenticated' } });
  await new Promise(setImmediate);
  resolveOldProfile(apiResponse(200, profile({ name: '이전 계정' })));
  await oldLoad;
  await new Promise(setImmediate);

  assert.equal(profileRequests, 2);
  assert.equal(hooks.profileLoadState, 'loaded');
  assert.match(hooks.renderProfileView(), /새 계정/);
  assert.doesNotMatch(hooks.renderProfileView(), /이전 계정/);
});

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
