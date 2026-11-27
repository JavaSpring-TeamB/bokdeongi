const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'home.js'), 'utf8');

function hooks(elements = {}) {
  const window = {
    location: { search: '', protocol: 'http:', hostname: 'localhost' },
    addEventListener() {},
    clearTimeout() {}
  };
  const document = {
    documentElement: { dataset: {} },
    addEventListener() {},
    querySelector: (selector) => elements[selector] || null,
    querySelectorAll: (selector) => elements[`all:${selector}`] || []
  };
  const context = { window, document, URLSearchParams, Date, Number, String, Array, Object, Error, Promise, console };
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__testHooks={isHomeSummaryResponse, isHomeActivityResponse, isHomeCalendarResponse, getSeoulDateParts, weeklyActivityMarkup, activityBenefitLinks, renderWeeklySummary, loadHomeActivity, chooseWeeklyBenefits, showOtherWeeklyBenefits, renderCalendar, renderSelectedSchedules, renderDateNotices, getSchedulesForDate, getEventTone, calendarEventInfo, changeCalendarMonth, loadHomeCalendar, applicationStatusLabel, escapeHtml, eligibilityStatisticsMarkup, benefitSummaryMode, getStateMarkup, formatBenefitDataUpdatedAt, benefitDataUpdatedAtText, authState, homeSummaryState, homeActivityState, weeklyViewState, calendarState, window};`, context);
  return context.__testHooks;
}

function validActivity() {
  return {
    firstVisit: false,
    newEligibleBenefits: [{ benefitId: 17, title: '청년 월세 지원', newBadge: true }],
    weeklySummary: {
      visible: true,
      newEligibleCount: 2,
      closingSoonCount: 1,
      changedBenefitCount: 3,
      periodStart: '2026-11-23',
      periodEnd: '2026-11-29'
    }
  };
}

function validCalendar(year = 2026, month = 11, overrides = {}) {
  return {
    year,
    month,
    events: [
      { benefitId: 37, title: '청년 월세 지원', date: `${year}-${String(month).padStart(2, '0')}-05`, eventType: 'APPLY_START' },
      { benefitId: 52, title: '문화 지원', date: `${year}-${String(month).padStart(2, '0')}-12`, eventType: 'APPLY_END' },
      { benefitId: 73, title: '생활 지원', date: `${year}-${String(month).padStart(2, '0')}-19`, eventType: 'RESULT' }
    ],
    alwaysCount: 1,
    fallbackUsed: false,
    ...overrides
  };
}

function calendarDom() {
  const days = { innerHTML: '', classList: { toggle() {} } };
  const monthTitle = { textContent: '' };
  const scheduleTitle = { textContent: '' };
  const scheduleList = { innerHTML: '' };
  const notices = { innerHTML: '' };
  const navigation = [{ disabled: false }, { disabled: false }];
  return {
    elements: {
      '#calendar-days': days,
      '#calendar-month': monthTitle,
      '#selected-date-title': scheduleTitle,
      '#schedule-list': scheduleList,
      '#calendar-date-notices': notices,
      'all:[data-calendar-nav]': navigation
    },
    days,
    monthTitle,
    scheduleTitle,
    scheduleList,
    notices,
    navigation
  };
}

function calendarResponse(payload, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => payload };
}

function validSummary() {
  return {
    profileCompleted: true,
    profileStep: 3,
    eligibilityUpdating: false,
    amountSummary: {
      estimatedAmount: null,
      maxAmount: null,
      annualizedAmount: 1200000,
      unknownAmountCount: 2,
      duplicateExclusionApplied: false
    },
    closingSoon: [],
    applicationSummary: { inProgressCount: 0, completedCount: 0, items: [] },
    recommendations: [],
    nearMissBenefits: [],
    totalCount: 0,
    eligibleCount: 0,
    conditionalCount: 0,
    ineligibleCount: 0,
    benefitDataUpdatedAt: null
  };
}

test('accepts the HOME-01 DTO shape including nullable monetary fields', () => {
  assert.equal(hooks().isHomeSummaryResponse(validSummary()), true);
});

test('rejects malformed HOME-01 data instead of rendering it as a real dashboard', () => {
  const invalid = validSummary();
  invalid.amountSummary.unknownAmountCount = '2';
  assert.equal(hooks().isHomeSummaryResponse(invalid), false);
});

test('rejects HOME-01 data when a required top-level eligibility count is missing or invalid', () => {
  const missing = validSummary();
  delete missing.eligibleCount;
  assert.equal(hooks().isHomeSummaryResponse(missing), false);
  const invalid = validSummary();
  invalid.ineligibleCount = -1;
  assert.equal(hooks().isHomeSummaryResponse(invalid), false);
});

test('maps known application statuses and escapes server-provided strings', () => {
  const api = hooks();
  assert.equal(api.applicationStatusLabel('SUBMITTED'), '신청 완료');
  assert.equal(api.applicationStatusLabel('unexpected'), '상태 확인 필요');
  assert.equal(api.escapeHtml('<혜택 & "안내">'), '&lt;혜택 &amp; &quot;안내&quot;&gt;');
});

test('renders the four server-provided top-level eligibility counts including real zero', () => {
  const markup = hooks().eligibilityStatisticsMarkup({
    totalCount: 0,
    eligibleCount: 5,
    conditionalCount: 0,
    ineligibleCount: 12
  });
  assert.match(markup, />0건</);
  assert.match(markup, />5건</);
  assert.match(markup, />12건</);
});

test('keeps profile incomplete and eligibility updating states distinct from a completed zero-result summary', () => {
  const profileIncomplete = validSummary();
  profileIncomplete.profileCompleted = false;
  assert.equal(hooks().benefitSummaryMode(profileIncomplete), 'profile');
  const updating = validSummary();
  updating.eligibilityUpdating = true;
  assert.equal(hooks().benefitSummaryMode(updating), 'updating');
  assert.equal(hooks().benefitSummaryMode(validSummary()), 'ready');
});

test('uses an error state rather than displaying zero counts after a HOME-01 retrieval failure', () => {
  const markup = hooks().getStateMarkup('benefits', 'error');
  assert.match(markup, /맞춤 혜택 정보를 불러오지 못했어요/);
  assert.doesNotMatch(markup, /0건/);
});

test('formats UTC and offset HOME-01 timestamps in Korea Standard Time', () => {
  const api = hooks();
  assert.equal(api.formatBenefitDataUpdatedAt('2026-11-24T00:05:07Z'), '2026년 11월 24일 09:05:07 (KST)');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-11-24T00:05:07+00:00'), '2026년 11월 24일 09:05:07 (KST)');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-11-24T09:05:07+09:00'), '2026년 11월 24일 09:05:07 (KST)');
});

test('converts an offset timestamp across a Korean calendar date boundary', () => {
  assert.equal(hooks().formatBenefitDataUpdatedAt('2026-11-23T18:05:07Z'), '2026년 11월 24일 03:05:07 (KST)');
});

test('distinguishes no completed synchronization record from malformed or offset-free data', () => {
  const api = hooks();
  assert.equal(api.formatBenefitDataUpdatedAt(null), '정상 동기화 기록이 없습니다.');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-02-30T09:05:00'), '갱신 시각 형식을 확인할 수 없습니다.');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-11-24T09:05:00'), '갱신 시각 형식을 확인할 수 없습니다.');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-11-24T09:05:00+09:60'), '갱신 시각 형식을 확인할 수 없습니다.');
});

test('does not substitute a timestamp when HOME-01 retrieval fails', () => {
  assert.equal(hooks().benefitDataUpdatedAtText(null, 'authenticated', 'error'), '갱신 시각을 불러오지 못했어요.');
});

test('accepts HOME-02 activity DTO and rejects malformed counts, dates, and benefit IDs', () => {
  const api = hooks();
  assert.equal(api.isHomeActivityResponse(validActivity()), true);
  const invalidCount = validActivity();
  invalidCount.weeklySummary.closingSoonCount = -1;
  assert.equal(api.isHomeActivityResponse(invalidCount), false);
  const invalidDate = validActivity();
  invalidDate.weeklySummary.periodStart = '2026-02-30';
  assert.equal(api.isHomeActivityResponse(invalidDate), false);
  const invalidBenefit = validActivity();
  invalidBenefit.newEligibleBenefits[0].benefitId = -17;
  assert.equal(api.isHomeActivityResponse(invalidBenefit), false);
});

test('renders HOME-02 counts, period, and linked newly eligible benefits from server data', () => {
  const weekly = { innerHTML: '' };
  const period = { textContent: '' };
  const api = hooks({ '#weekly-summary': weekly, '#weekly-period': period });
  api.authState.status = 'authenticated';
  api.homeSummaryState.status = 'ready';
  api.homeSummaryState.data = validSummary();
  api.renderWeeklySummary(validActivity(), 'ready');
  assert.equal(period.textContent, '2026.11.23 – 2026.11.29');
  assert.match(weekly.innerHTML, /이번 주 새로 해당/);
  assert.match(weekly.innerHTML, />2건</);
  assert.match(weekly.innerHTML, />1건</);
  assert.match(weekly.innerHTML, />3건</);
  assert.match(weekly.innerHTML, /benefit-detail\.html\?id=17/);
  assert.match(weekly.innerHTML, /&fromHomeActivity=1/);
  assert.match(weekly.innerHTML, /청년 월세 지원/);
  assert.match(weekly.innerHTML, /새로 해당/);
  assert.match(weekly.innerHTML, /미확인 혜택 1건 · 현재 1건 표시/);
  assert.match(weekly.innerHTML, /맞춤 혜택 더 보기/);
  assert.match(weekly.innerHTML, /data-weekly-detail="closingSoon"/);
  assert.doesNotMatch(weekly.innerHTML, /data-weekly-detail="newEligible"|data-weekly-detail="changed"/);
});

test('renders the actual empty state when HOME-02 has no unseen eligible benefits', () => {
  const api = hooks();
  const payload = validActivity();
  payload.newEligibleBenefits = [];
  payload.weeklySummary.visible = false;
  const markup = api.weeklyActivityMarkup(payload);
  assert.match(markup, /아직 확인하지 않은 해당 혜택이 없어요/);
  assert.match(markup, /미확인 혜택 0건/);
});

test('limits the unseen eligible list to the actual 0, 1, and 4 item response sizes', () => {
  for (const size of [0, 1, 4]) {
    const api = hooks();
    const payload = validActivity();
    payload.newEligibleBenefits = Array.from({ length: size }, (_, index) => ({ benefitId: index + 1, title: `혜택 ${index + 1}`, newBadge: true }));
    const markup = api.weeklyActivityMarkup(payload);
    assert.equal((markup.match(/class="benefit-summary__link weekly-new-benefit"/g) || []).length, size);
    assert.doesNotMatch(markup, /data-weekly-more/);
    assert.match(markup, new RegExp(`미확인 혜택 ${size}건 · 현재 ${size}건 표시`));
  }
});

test('selects at most four randomly and changes to non-overlapping benefits without another request', () => {
  const weekly = { innerHTML: '' };
  const api = hooks({ '#weekly-summary': weekly, '#weekly-period': { textContent: '' } });
  api.authState.status = 'authenticated';
  const payload = validActivity();
  payload.weeklySummary.newEligibleCount = 300;
  payload.newEligibleBenefits = Array.from({ length: 278 }, (_, index) => ({ benefitId: index + 100, title: `지원 혜택 ${index + 1}`, newBadge: true }));
  api.homeActivityState.data = payload;
  api.homeActivityState.status = 'ready';
  api.renderWeeklySummary(payload, 'ready');
  const first = [...weekly.innerHTML.matchAll(/benefit-detail\.html\?id=(\d+)/g)].map((match) => match[1]);
  assert.equal(first.length, 4);
  assert.equal(new Set(first).size, 4);
  assert.match(weekly.innerHTML, /미확인 혜택 278건 · 현재 4건 표시/);
  assert.match(weekly.innerHTML, /data-weekly-more/);
  let requests = 0;
  api.window.BokdeongiAuth = { authenticatedFetch: async () => { requests += 1; } };
  api.showOtherWeeklyBenefits();
  const second = [...weekly.innerHTML.matchAll(/benefit-detail\.html\?id=(\d+)/g)].map((match) => match[1]);
  assert.equal(second.length, 4);
  assert.equal(new Set(second).size, 4);
  assert.equal(second.some((id) => first.includes(id)), false);
  assert.equal(requests, 0);
  assert.match(weekly.innerHTML, />300건</);
});

test('shows HOME-01 closing soon details only for the matching HOME-02 deadline count', () => {
  const api = hooks();
  const payload = validActivity();
  payload.weeklySummary.closingSoonCount = 7;
  api.homeSummaryState.status = 'ready';
  api.homeSummaryState.data = {
    profileCompleted: true,
    eligibilityUpdating: false,
    closingSoon: Array.from({ length: 5 }, (_, index) => ({ benefitId: index + 1, title: `마감 혜택 ${index + 1}`, applyEnd: '2026-11-30' }))
  };
  api.weeklyActivityMarkup(payload);
  api.weeklyViewState.activeDetail = 'closingSoon';
  const markup = api.weeklyActivityMarkup(payload);
  assert.match(markup, /주간 집계 7건 · 서버가 제공한 상세 목록 5건 중 4건 표시/);
  assert.match(markup, /data-weekly-detail="new"/);
  assert.equal((markup.match(/class="benefit-summary__link weekly-new-benefit"/g) || []).length, 4);
  assert.match(markup, /benefit-detail\.html\?id=1/);
  assert.doesNotMatch(markup, /benefit-detail\.html\?id=5/);
});

test('shows a deadline-specific empty state for a zero server count', () => {
  const api = hooks();
  const payload = validActivity();
  payload.weeklySummary.closingSoonCount = 0;
  api.weeklyActivityMarkup(payload);
  api.weeklyViewState.activeDetail = 'closingSoon';
  assert.match(api.weeklyActivityMarkup(payload), /7일 이내 마감되는 해당 혜택이 없어요/);
});

test('renders HOME-02 checking, unauthenticated, and failed-authentication states', () => {
  const weekly = { innerHTML: '' };
  const period = { textContent: '' };
  const api = hooks({ '#weekly-summary': weekly, '#weekly-period': period });
  api.authState.status = 'checking';
  api.renderWeeklySummary(null, 'idle');
  assert.match(weekly.innerHTML, /인증 상태를 확인하는 중/);

  api.authState.status = 'unauthenticated';
  api.renderWeeklySummary(null, 'idle');
  assert.match(weekly.innerHTML, /로그인하면 이번 주 혜택 소식/);
  assert.match(weekly.innerHTML, /data-login-required/);

  api.authState.status = 'error';
  api.renderWeeklySummary(null, 'idle');
  assert.match(weekly.innerHTML, /인증 상태를 확인하지 못했어요/);
  assert.equal(period.textContent, '');
});

test('HOME-02 requests are authenticated and isolated from HOME-01 failures', async () => {
  const api = hooks();
  api.authState.status = 'authenticated';
  const paths = [];
  api.window.BokdeongiAuth = {
    authenticatedFetch: async (path) => {
      paths.push(path);
      return { ok: true, json: async () => validActivity() };
    }
  };
  api.homeSummaryState.status = 'error';
  await api.loadHomeActivity();
  assert.deepEqual(paths, ['/api/home/activity']);
  assert.equal(api.homeActivityState.status, 'ready');
  assert.equal(api.homeSummaryState.status, 'error');
});

test('HOME-02 does not request data while unauthenticated and supports independent retry state', async () => {
  const api = hooks();
  api.authState.status = 'unauthenticated';
  let requests = 0;
  api.window.BokdeongiAuth = { authenticatedFetch: async () => { requests += 1; throw new Error('unexpected request'); } };
  await api.loadHomeActivity();
  assert.equal(requests, 0);

  api.authState.status = 'authenticated';
  api.window.BokdeongiAuth.authenticatedFetch = async () => ({ ok: false, status: 503, json: async () => null });
  await api.loadHomeActivity();
  assert.equal(api.homeActivityState.status, 'error');
  assert.equal(api.homeSummaryState.status, 'idle');
  const markup = api.getStateMarkup('weekly', 'error');
  assert.match(markup, /data-retry-section="weekly"/);
  assert.match(source, /retry\.dataset\.retrySection === 'weekly'\) void loadHomeActivity\(\{ force: true \}\)/);
});

test('uses Asia/Seoul date parts for the initial calendar month across UTC month and year boundaries', () => {
  const api = hooks();
  const decemberBoundary = api.getSeoulDateParts(new Date('2026-11-30T15:30:00Z'));
  assert.equal(decemberBoundary.year, 2026);
  assert.equal(decemberBoundary.month, 12);
  assert.equal(decemberBoundary.day, 1);
  const yearBoundary = api.getSeoulDateParts(new Date('2025-12-31T15:30:00Z'));
  assert.equal(yearBoundary.year, 2026);
  assert.equal(yearBoundary.month, 1);
  assert.equal(yearBoundary.day, 1);
});

test('validates HOME-03 response period, event date and event type', () => {
  const api = hooks();
  assert.equal(api.isHomeCalendarResponse(validCalendar(), 2026, 11), true);
  assert.equal(api.isHomeCalendarResponse(validCalendar(2026, 10), 2026, 11), false);
  const invalidType = validCalendar();
  invalidType.events[0].eventType = 'UNKNOWN';
  assert.equal(api.isHomeCalendarResponse(invalidType, 2026, 11), false);
  const wrongMonth = validCalendar();
  wrongMonth.events[0].date = '2026-12-05';
  assert.equal(api.isHomeCalendarResponse(wrongMonth, 2026, 11), false);
  const invalidAlwaysCount = validCalendar(2026, 11, { alwaysCount: -1 });
  assert.equal(api.isHomeCalendarResponse(invalidAlwaysCount, 2026, 11), false);
});

test('renders server calendar event types, dates and real benefit detail links', () => {
  const dom = calendarDom();
  const api = hooks(dom.elements);
  api.authState.status = 'authenticated';
  api.calendarState.displayedDate = new Date(2026, 10, 1);
  api.calendarState.selectedDate = '2026-11-05';
  api.calendarState.events = validCalendar().events;
  api.calendarState.alwaysCount = 0;
  api.calendarState.fallbackUsed = false;
  api.calendarState.status = 'ready';
  api.renderCalendar();

  assert.equal(dom.monthTitle.textContent, '2026년 11월');
  assert.match(dom.days.innerHTML, /data-calendar-date="2026-11-05"/);
  assert.match(dom.days.innerHTML, /has-event--start/);
  const todayParts = api.getSeoulDateParts();
  const todayKey = `${todayParts.year}-${String(todayParts.month).padStart(2, '0')}-${String(todayParts.day).padStart(2, '0')}`;
  assert.match(dom.days.innerHTML, new RegExp(`calendar-date--today[^>]*data-calendar-date="${todayKey}"`));
  assert.equal(dom.scheduleTitle.textContent, '11월 5일 일정');
  assert.match(dom.scheduleList.innerHTML, /신청 시작/);
  assert.match(dom.scheduleList.innerHTML, /benefit-detail\.html\?id=37/);
  assert.match(dom.scheduleList.innerHTML, /청년 월세 지원/);

  api.calendarState.selectedDate = '2026-11-12';
  api.renderCalendar();
  assert.match(dom.days.innerHTML, /class="calendar-date[^\"]*has-event--deadline"[^>]*data-calendar-date="2026-11-12"/);
  assert.match(dom.scheduleList.innerHTML, /신청 마감/);
  assert.match(dom.scheduleList.innerHTML, /benefit-detail\.html\?id=52/);
  api.calendarState.selectedDate = '2026-11-19';
  api.renderCalendar();
  assert.match(dom.scheduleList.innerHTML, /결과 발표/);
  assert.match(dom.scheduleList.innerHTML, /benefit-detail\.html\?id=73/);
});

test('shows server alwaysCount and fallbackUsed without inventing date events', () => {
  const dom = calendarDom();
  const api = hooks(dom.elements);
  api.authState.status = 'authenticated';
  api.calendarState.displayedDate = new Date(2026, 10, 1);
  api.calendarState.selectedDate = '2026-11-01';
  api.calendarState.events = [];
  api.calendarState.alwaysCount = 2;
  api.calendarState.fallbackUsed = true;
  api.calendarState.status = 'ready';
  api.renderCalendar();

  assert.match(dom.scheduleList.innerHTML, /등록된 일정이 없어요/);
  assert.doesNotMatch(dom.days.innerHTML, /has-event/);
  assert.match(dom.notices.innerHTML, /상시 신청 혜택 2건/);
  assert.match(dom.notices.innerHTML, /마감 임박 혜택/);
  assert.match(dom.notices.innerHTML, /대신 표시/);
});

test('requests the current HOME-03 month through authenticated API independently of other home sections', async () => {
  const api = hooks();
  api.authState.status = 'authenticated';
  api.calendarState.displayedDate = new Date(2026, 10, 1);
  const paths = [];
  api.window.BokdeongiAuth = {
    authenticatedFetch: async (path) => {
      paths.push(path);
      return calendarResponse(validCalendar());
    }
  };
  api.homeSummaryState.status = 'error';
  api.homeActivityState.status = 'ready';
  await api.loadHomeCalendar();

  assert.deepEqual(paths, ['/api/home/calendar?year=2026&month=11']);
  assert.equal(api.calendarState.status, 'ready');
  assert.equal(api.calendarState.events.length, 3);
  assert.equal(api.homeSummaryState.status, 'error');
  assert.equal(api.homeActivityState.status, 'ready');
});

test('month navigation requests adjacent months and crosses calendar years', async () => {
  const api = hooks();
  api.authState.status = 'authenticated';
  api.calendarState.displayedDate = new Date(2027, 0, 1);
  const paths = [];
  api.window.BokdeongiAuth = {
    authenticatedFetch: async (path) => {
      paths.push(path);
      const parsed = new URL(path, 'https://example.test');
      const year = Number(parsed.searchParams.get('year'));
      const month = Number(parsed.searchParams.get('month'));
      return calendarResponse(validCalendar(year, month, { events: [] }));
    }
  };

  await api.changeCalendarMonth(-1);
  assert.equal(api.calendarState.displayedDate.getFullYear(), 2026);
  assert.equal(api.calendarState.displayedDate.getMonth(), 11);
  assert.equal(api.calendarState.selectedDate, '2026-12-01');
  await api.changeCalendarMonth(1);
  assert.equal(api.calendarState.displayedDate.getFullYear(), 2027);
  assert.equal(api.calendarState.displayedDate.getMonth(), 0);
  assert.equal(api.calendarState.selectedDate, '2027-01-01');
  assert.deepEqual(paths, ['/api/home/calendar?year=2026&month=12', '/api/home/calendar?year=2027&month=1']);
});

test('does not call HOME-03 while unauthenticated and renders login guidance', async () => {
  const dom = calendarDom();
  const api = hooks(dom.elements);
  api.authState.status = 'unauthenticated';
  let requests = 0;
  api.window.BokdeongiAuth = { authenticatedFetch: async () => { requests += 1; throw new Error('unexpected request'); } };
  await api.loadHomeCalendar();
  api.renderCalendar();
  assert.equal(requests, 0);
  assert.match(dom.days.innerHTML, /로그인하면 관심 혜택과 신청 중인 혜택의 일정/);
  assert.match(dom.days.innerHTML, /data-login-required/);
  assert.ok(dom.navigation.every((button) => button.disabled));
});

test('shows independent loading and retryable error states for HOME-03', async () => {
  const dom = calendarDom();
  const api = hooks(dom.elements);
  api.authState.status = 'authenticated';
  api.window.BokdeongiAuth = {
    authenticatedFetch: async () => ({ ok: false, status: 503, json: async () => null })
  };
  const pending = new Promise(() => {});
  api.window.BokdeongiAuth.authenticatedFetch = async () => pending;
  const loading = api.loadHomeCalendar();
  assert.match(dom.days.innerHTML, /신청 일정을 불러오는 중/);
  assert.ok(dom.navigation.every((button) => !button.disabled));
  api.window.BokdeongiAuth.authenticatedFetch = async () => ({ ok: false, status: 503, json: async () => null });
  api.calendarState.requestId += 1;
  api.calendarState.status = 'idle';
  await api.loadHomeCalendar({ force: true });
  assert.equal(api.calendarState.status, 'error');
  assert.match(dom.days.innerHTML, /신청 일정을 불러오지 못했어요/);
  assert.match(dom.days.innerHTML, /data-retry-section="calendar"/);
  assert.equal(api.homeSummaryState.status, 'idle');
  assert.equal(api.homeActivityState.status, 'idle');
  void loading;
});

test('ignores a late response from a previously selected calendar month', async () => {
  const api = hooks();
  api.authState.status = 'authenticated';
  api.calendarState.displayedDate = new Date(2026, 0, 1);
  const pending = [];
  api.window.BokdeongiAuth = {
    authenticatedFetch: (path) => new Promise((resolve) => pending.push({ path, resolve }))
  };

  const january = api.loadHomeCalendar({ force: true });
  api.calendarState.displayedDate = new Date(2026, 1, 1);
  const february = api.loadHomeCalendar({ force: true });
  pending[1].resolve(calendarResponse(validCalendar(2026, 2, { events: [] })));
  await february;
  pending[0].resolve(calendarResponse(validCalendar(2026, 1)));
  await january;

  assert.equal(api.calendarState.status, 'ready');
  assert.equal(api.calendarState.displayedDate.getMonth(), 1);
  assert.equal(api.calendarState.events.length, 0);
});
