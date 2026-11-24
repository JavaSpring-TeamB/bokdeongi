const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'home.js'), 'utf8');

function hooks() {
  const window = {
    location: { search: '', protocol: 'http:', hostname: 'localhost' },
    addEventListener() {},
    clearTimeout() {}
  };
  const document = {
    documentElement: { dataset: {} },
    addEventListener() {},
    querySelector: () => null,
    querySelectorAll: () => []
  };
  const context = { window, document, URLSearchParams, Date, Number, String, Array, Object, Error, Promise, console };
  vm.createContext(context);
  vm.runInContext(`${source}\n;globalThis.__testHooks={isHomeSummaryResponse, applicationStatusLabel, escapeHtml, eligibilityStatisticsMarkup, benefitSummaryMode, getStateMarkup, formatBenefitDataUpdatedAt, benefitDataUpdatedAtText};`, context);
  return context.__testHooks;
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

test('formats a normal local date-time using the server-provided date and time components', () => {
  assert.equal(hooks().formatBenefitDataUpdatedAt('2026-11-24T09:05:07'), '2026년 11월 24일 09:05:07');
  assert.equal(hooks().formatBenefitDataUpdatedAt('2026-11-24T09:05'), '2026년 11월 24일 09:05');
});

test('distinguishes no completed synchronization record from malformed local date-time data', () => {
  const api = hooks();
  assert.equal(api.formatBenefitDataUpdatedAt(null), '정상 동기화 기록이 없습니다.');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-02-30T09:05:00'), '갱신 시각 형식을 확인할 수 없습니다.');
  assert.equal(api.formatBenefitDataUpdatedAt('2026-11-24T09:05:00Z'), '갱신 시각 형식을 확인할 수 없습니다.');
});

test('does not substitute a timestamp when HOME-01 retrieval fails', () => {
  assert.equal(hooks().benefitDataUpdatedAtText(null, 'authenticated', 'error'), '갱신 시각을 불러오지 못했어요.');
});
