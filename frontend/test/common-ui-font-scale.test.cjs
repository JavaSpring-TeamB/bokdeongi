const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '..', 'js', 'common-ui.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '..', 'css', 'common.css'), 'utf8');

function load(savedValue) {
  const storage = new Map();
  if (savedValue !== null) storage.set('bokdeongi-font-scale', savedValue);
  const writes = [];
  const applied = [];
  const events = {};
  const buttons = {
    decrease: { dataset: { fontSize: 'decrease' }, disabled: false, setAttribute() {} },
    increase: { dataset: { fontSize: 'increase' }, disabled: false, setAttribute() {} }
  };
  const document = {
    documentElement: {
      dataset: {},
      style: { setProperty: (name, value) => { if (name === '--font-scale') applied.push(value); } }
    },
    querySelectorAll: (selector) => {
      if (selector === '[data-font-size]') return Object.values(buttons);
      if (selector === '[data-font-size="decrease"]') return [buttons.decrease];
      if (selector === '[data-font-size="increase"]') return [buttons.increase];
      return [];
    },
    querySelector: () => null,
    addEventListener: (name, callback) => { events[name] = callback; }
  };
  const window = {
    matchMedia: () => ({ matches: false }),
    addEventListener() {}
  };
  vm.runInNewContext(script, {
    document, window,
    localStorage: {
      getItem: (key) => storage.has(key) ? storage.get(key) : null,
      setItem: (key, value) => { storage.set(key, String(value)); writes.push([key, String(value)]); }
    }
  });
  const clickFont = (direction) => {
    events.click({
      target: { closest: (selector) => selector === '[data-font-size]' ? buttons[direction] : null },
      stopImmediatePropagation() {}
    });
  };
  return { storage, writes, applied, buttons, clickFont };
}

test('new 100% baseline has the same root size as the former 90% setting', () => {
  assert.match(css, /font-size:\s*calc\(16px \* \.9 \* var\(--font-scale\) \* var\(--page-font-scale\)\)/);
  assert.equal(Number((16 * 0.9 * 1.3).toFixed(2)), 18.72);
  assert.equal(Number((16 * 0.9 * 1.1).toFixed(2)), 15.84);
  const firstVisit = load(null);
  assert.equal(firstVisit.applied[0], 1);
  assert.equal(firstVisit.storage.has('bokdeongi-font-scale'), false);
});

test('saved percentage stays unchanged and controls step around the new baseline', () => {
  const existingUser = load('1.1');
  assert.equal(existingUser.applied[0], 1.1);
  assert.equal(existingUser.storage.get('bokdeongi-font-scale'), '1.1');
  existingUser.clickFont('decrease');
  assert.equal(existingUser.storage.get('bokdeongi-font-scale'), '1');
  existingUser.clickFont('increase');
  assert.equal(existingUser.storage.get('bokdeongi-font-scale'), '1.1');
  assert.equal(load('1.1').applied[0], 1.1);
});

test('buttons visit 80–130% in ten percent steps and stop at both limits', () => {
  const smaller = load(null);
  smaller.clickFont('decrease');
  assert.equal(smaller.storage.get('bokdeongi-font-scale'), '0.9');
  smaller.clickFont('decrease');
  assert.equal(smaller.storage.get('bokdeongi-font-scale'), '0.8');
  assert.equal(smaller.buttons.decrease.disabled, true);
  const writesAtMinimum = smaller.writes.length;
  smaller.clickFont('decrease');
  assert.equal(smaller.writes.length, writesAtMinimum);

  const larger = load(null);
  for (const scale of ['1.1', '1.2', '1.3']) {
    larger.clickFont('increase');
    assert.equal(larger.storage.get('bokdeongi-font-scale'), scale);
  }
  assert.equal(larger.buttons.increase.disabled, true);
  const writesAtMaximum = larger.writes.length;
  larger.clickFont('increase');
  assert.equal(larger.writes.length, writesAtMaximum);
  assert.equal(load('1.3').applied[0], 1.3);
});

test('out-of-range and invalid saved values are normalized once', () => {
  for (const [saved, expected] of [['0.7', '0.8'], ['1.4', '1.3'], ['invalid', '1'], ['', '1']]) {
    const state = load(saved);
    assert.equal(String(state.applied[0]), expected);
    assert.equal(state.storage.get('bokdeongi-font-scale'), expected);
    assert.equal(state.writes.length, 1);
    assert.equal(load(expected).writes.length, 0);
  }
  const inRange = load('1.05');
  assert.equal(inRange.applied[0], 1.05);
  assert.equal(inRange.writes.length, 0);
  inRange.clickFont('increase');
  assert.equal(inRange.storage.get('bokdeongi-font-scale'), '1.1');
});
