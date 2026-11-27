const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const frontend = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(frontend, 'my-page.html'), 'utf8');
const script = fs.readFileSync(path.join(frontend, 'js', 'my-page.js'), 'utf8');
const css = fs.readFileSync(path.join(frontend, 'css', 'my-page.css'), 'utf8');

test('removes the in-memory interest keyword feature and all of its page-specific styling', () => {
  assert.doesNotMatch(html, /관심 키워드|data-keyword-/);
  assert.doesNotMatch(script, /myPageData\.keywords|data-keyword-|renderKeywords|addKeyword|키워드를 추가했어요|키워드를 삭제했어요/);
  assert.doesNotMatch(css, /my-page__keyword/);
});

test('does not keep sample personal profile values in the My Page runtime source', () => {
  assert.doesNotMatch(script, /김복덩|bokdeongi@email\.com|2004\.11\.04|중위소득 150% 이하/);
  assert.match(script, /profile:\s*\{\s*basics:\s*\[\],\s*settings:\s*\[\]\s*\}/);
  assert.match(script, /profileLoadState = 'checking'/);
});
