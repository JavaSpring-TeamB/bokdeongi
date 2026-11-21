const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

function createHooks() {
  const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'benefit-recommend.js'), 'utf8');
  const instrumented = source.replace(/\}\)\(\);\s*$/, 'globalThis.__answerHooks = { applySavedAnswers, savedAnswerForQuestion, loadSavedAnswers, questionSelections, savedAnswersByKey, getState: () => ({ answerLoadState, answerLoadErrorStatus }), window };\n})();');
  const document = {
    addEventListener() {},
    querySelector() { return null; },
    querySelectorAll() { return []; }
  };
  const window = {};
  const context = vm.createContext({ console, document, window, URLSearchParams, Set, Map, Object, Number, Array });
  vm.runInContext(instrumented, context);
  return context.__answerHooks;
}

test('restores valid Y, N, and UNSURE answers by question key', () => {
  const hooks = createHooks();
  assert.equal(hooks.applySavedAnswers([
    { key: 'training_completed', value: 'Y', answeredAt: null, needsReconfirmation: false },
    { key: 'live_apart_from_parents', value: 'N', answeredAt: null, needsReconfirmation: false },
    { key: 'housing_uncertain', value: 'UNSURE', answeredAt: null, needsReconfirmation: false }
  ]), true);

  assert.deepEqual(JSON.parse(JSON.stringify(hooks.savedAnswerForQuestion('card:training_completed:0', 'training_completed'))), { value: 'Y', saved: true });
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.savedAnswerForQuestion('card:live_apart_from_parents:0', 'live_apart_from_parents'))), { value: 'N', saved: true });
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.savedAnswerForQuestion('card:housing_uncertain:0', 'housing_uncertain'))), { value: 'UNSURE', saved: true });
});

test('keeps an unsaved selection when the answer lookup arrives later', () => {
  const hooks = createHooks();
  hooks.questionSelections.set('17:training_completed:0', 'N');
  hooks.applySavedAnswers([{ key: 'training_completed', value: 'Y', answeredAt: null, needsReconfirmation: false }]);

  assert.deepEqual(JSON.parse(JSON.stringify(hooks.savedAnswerForQuestion('17:training_completed:0', 'training_completed'))), { value: 'N', saved: false });
});

test('loads the profile answer collection once and ignores malformed entries', async () => {
  const hooks = createHooks();
  let calls = 0;
  hooks.window.BokdeongiAuth = {
    getAuthenticationState() { return 'authenticated'; },
    async authenticatedFetch(url) {
      calls += 1;
      assert.equal(url, '/api/me/profile/answers');
      return {
        ok: true,
        status: 200,
        async json() {
          return [
            { key: 'valid_answer', value: 'UNSURE', answeredAt: null, needsReconfirmation: false },
            { key: 'invalid_answer', value: 'MAYBE', answeredAt: null, needsReconfirmation: false }
          ];
        }
      };
    }
  };

  await hooks.loadSavedAnswers();

  assert.equal(calls, 1);
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.savedAnswerForQuestion('1:valid_answer:0', 'valid_answer'))), { value: 'UNSURE', saved: true });
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.savedAnswerForQuestion('1:invalid_answer:0', 'invalid_answer'))), { saved: false });
  assert.deepEqual(JSON.parse(JSON.stringify(hooks.getState())), { answerLoadState: 'ready', answerLoadErrorStatus: null });
});
