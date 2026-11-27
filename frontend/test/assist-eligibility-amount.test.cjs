const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-eligibility.js'), 'utf8');
const context = { document: { addEventListener() {} }, window: {} };
vm.createContext(context);
vm.runInContext(`${source}\nglobalThis.__formatAmount = formatEligibilityAmount;`, context);

test('shows unknown for null, missing, empty, and malformed benefit amounts', () => {
  for (const value of [null, undefined, '', '   ', 'not-a-number', NaN, Infinity, -1, {}, true]) {
    assert.equal(context.__formatAmount(value), '지원 금액 정보 없음');
  }
});

test('keeps an actual zero and formats valid benefit amounts with Korean separators', () => {
  assert.equal(context.__formatAmount(0), '0원');
  assert.equal(context.__formatAmount('1250000'), '1,250,000원');
  assert.equal(context.__formatAmount(1250000), '1,250,000원');
});
