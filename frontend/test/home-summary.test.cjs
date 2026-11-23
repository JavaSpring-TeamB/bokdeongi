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
  vm.runInContext(`${source}\n;globalThis.__testHooks={isHomeSummaryResponse, applicationStatusLabel, escapeHtml, eligibilityStatisticsMarkup};`, context);
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
    nearMissBenefits: []
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

test('maps known application statuses and escapes server-provided strings', () => {
  const api = hooks();
  assert.equal(api.applicationStatusLabel('SUBMITTED'), '신청 완료');
  assert.equal(api.applicationStatusLabel('unexpected'), '상태 확인 필요');
  assert.equal(api.escapeHtml('<혜택 & "안내">'), '&lt;혜택 &amp; &quot;안내&quot;&gt;');
});

test('renders four unavailable statistics when HOME-01 has no eligibility summary', () => {
  const markup = hooks().eligibilityStatisticsMarkup(undefined);
  assert.match(markup, /전체/);
  assert.match(markup, /해당 없음/);
  assert.equal((markup.match(/>—</g) || []).length, 4);
});

test('renders a real zero count when a future eligibility summary provides zero', () => {
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
