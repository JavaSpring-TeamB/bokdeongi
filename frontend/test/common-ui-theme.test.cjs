const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const commonUi = fs.readFileSync(path.join(__dirname, '..', 'js', 'common-ui.js'), 'utf8');
const documents = fs.readFileSync(path.join(__dirname, '..', 'js', 'assist-documents.js'), 'utf8');

function loadDocumentsPage(savedTheme, systemDark) {
  const listeners = new Map();
  const values = new Map(savedTheme ? [['bokdeongi-theme', savedTheme]] : []);
  const attributes = new Map();
  const button = {
    innerHTML: '',
    setAttribute(name, value) { attributes.set(name, value); }
  };
  const document = {
    documentElement: { dataset: {}, style: { setProperty() {} } },
    querySelectorAll(selector) { return selector === '[data-theme-toggle]' ? [button] : []; },
    querySelector() { return null; },
    addEventListener(name, callback) {
      if (!listeners.has(name)) listeners.set(name, []);
      listeners.get(name).push(callback);
    }
  };
  const window = {
    matchMedia() { return { matches: systemDark }; },
    addEventListener() {}
  };
  const localStorage = {
    getItem(key) { return values.get(key) ?? null; },
    setItem(key, value) { values.set(key, value); }
  };
  const context = vm.createContext({ document, window, localStorage, Map, Set, URL });
  vm.runInContext(commonUi, context);
  vm.runInContext(documents, context);
  // The documents page registers a second DOM-ready handler for unavailable links.
  listeners.get('DOMContentLoaded').at(-1)();
  return { document, attributes, values, listeners };
}

test('documents page keeps the system dark theme until the user chooses otherwise', () => {
  const page = loadDocumentsPage(null, true);
  assert.equal(page.document.documentElement.dataset.theme, 'dark');
  assert.equal(page.attributes.get('aria-pressed'), 'true');
  assert.equal(page.values.has('bokdeongi-theme'), false);
});

test('documents page restores a saved theme and retains it after a theme click and reload', () => {
  const page = loadDocumentsPage('light', true);
  assert.equal(page.document.documentElement.dataset.theme, 'light');
  let stopped = false;
  const event = {
    target: { closest(selector) { return selector === '[data-theme-toggle]' ? {} : null; } },
    stopImmediatePropagation() { stopped = true; }
  };
  for (const listener of page.listeners.get('click')) {
    listener(event);
    if (stopped) break;
  }
  assert.equal(page.document.documentElement.dataset.theme, 'dark');
  assert.equal(page.attributes.get('aria-pressed'), 'true');
  assert.equal(page.values.get('bokdeongi-theme'), 'dark');

  const reloaded = loadDocumentsPage(page.values.get('bokdeongi-theme'), false);
  assert.equal(reloaded.document.documentElement.dataset.theme, 'dark');
});
