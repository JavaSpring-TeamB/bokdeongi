const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'common-ui.js'), 'utf8');

function classes() {
  const values = new Set();
  return {
    contains: (name) => values.has(name),
    add: (name) => values.add(name),
    remove: (...names) => names.forEach((name) => values.delete(name)),
    toggle(name, force) {
      if (force === undefined) force = !values.has(name);
      if (force) values.add(name);
      else values.delete(name);
      return force;
    }
  };
}

test('navigation collapses at its measured boundary and resets the open menu on expansion', () => {
  const events = {};
  const buttonAttributes = {};
  const links = Array.from({ length: 6 }, () => ({ getBoundingClientRect: () => ({ width: 100 }) }));
  let availableWidth = 900;
  let visibleLinks = 5;
  const button = {
    classList: classes(),
    getAttribute: () => 'site-navigation',
    setAttribute: (name, value) => { buttonAttributes[name] = value; }
  };
  const nav = {
    classList: classes(),
    querySelectorAll: () => links.slice(0, visibleLinks)
  };
  const brand = { getBoundingClientRect: () => ({ width: 100 }) };
  const tools = { getBoundingClientRect: () => ({ width: 200 }) };
  const inner = {
    get clientWidth() { return availableWidth; },
    querySelector(selector) {
      return { '.site-brand': brand, '.site-nav': nav, '.header-tools': tools, '.menu-toggle': button }[selector];
    }
  };
  const header = {
    classList: classes(),
    querySelector(selector) {
      return { '.site-header__inner': inner, '.site-nav': nav, '.auth-links': null }[selector];
    }
  };
  const document = {
    documentElement: { style: { setProperty() {} }, dataset: {} },
    fonts: { ready: new Promise(() => {}) },
    querySelectorAll: (selector) => selector === '.site-header' ? [header] : [],
    getElementById: () => nav,
    addEventListener: (name, callback) => { events[name] = callback; }
  };
  const window = {
    matchMedia: () => ({ matches: false }),
    addEventListener: (name, callback) => { events[name] = callback; }
  };
  vm.runInNewContext(source, {
    document, window,
    localStorage: { getItem: () => null },
    MutationObserver: class { observe() {} },
    getComputedStyle: (element) => ({ columnGap: element === nav ? '14px' : '16px' })
  });

  // Five links require 888px: 100 logo + 500 links + 56 menu gaps + 200 tools + 32 header gaps.
  assert.equal(header.classList.contains('is-nav-collapsed'), false);
  availableWidth = 887;
  events.resize();
  assert.equal(header.classList.contains('is-nav-compact'), true);
  assert.equal(header.classList.contains('is-nav-collapsed'), true);

  events.click({ target: { closest: (selector) => selector === '.menu-toggle' ? button : null }, stopImmediatePropagation() {} });
  assert.equal(nav.classList.contains('is-open'), true);
  assert.equal(buttonAttributes['aria-expanded'], 'true');

  availableWidth = 888;
  events.resize();
  assert.equal(header.classList.contains('is-nav-collapsed'), false);
  assert.equal(nav.classList.contains('is-open'), false);
  assert.equal(buttonAttributes['aria-expanded'], 'false');

  visibleLinks = 6;
  events.resize();
  assert.equal(header.classList.contains('has-account-nav'), true);
  assert.equal(header.classList.contains('is-nav-collapsed'), true);
});
