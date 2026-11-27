const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'password-reset.js'), 'utf8');

function loadApi() {
  const document = { addEventListener() {}, querySelector() { return null; }, querySelectorAll() { return []; } };
  const window = { BOKDEONGI_AUTH_API_BASE_URL: 'http://localhost:8080', setInterval, clearInterval, addEventListener() {} };
  const context = vm.createContext({ window, document, URL, Date, TextEncoder, setTimeout, clearTimeout, console });
  vm.runInContext(source, context);
  return window.BokdeongiPasswordReset;
}

test('validates the backend 8 to 64 character and 72 UTF-8 byte password limits', () => {
  const api = loadApi();
  assert.match(api.validatePassword('short', 'short'), /8~64자/);
  assert.equal(api.validatePassword('12345678', '12345678'), '');
  assert.equal(api.validatePassword('a'.repeat(64), 'a'.repeat(64)), '');
  assert.match(api.validatePassword('a'.repeat(65), 'a'.repeat(65)), /8~64자/);
  assert.match(api.validatePassword('가'.repeat(25), '가'.repeat(25)), /72바이트/);
  assert.match(api.validatePassword('Password1!', 'Password2!'), /일치하지 않습니다/);
});

test('formats server supplied countdown seconds without losing leading zeroes', () => {
  const api = loadApi();
  assert.equal(api.formatTime(300), '5:00');
  assert.equal(api.formatTime(61), '1:01');
  assert.equal(api.formatTime(9), '0:09');
});

test('keeps reset token in page memory and never uses browser storage or URL parameters', () => {
  assert.match(source, /resetToken: null/);
  assert.doesNotMatch(source, /localStorage|sessionStorage|URLSearchParams|console\.(log|debug)/);
});

test('login page opens the reset flow without changing login and Kakao handlers', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'login.html'), 'utf8');
  const login = fs.readFileSync(path.join(__dirname, '..', 'js', 'login.js'), 'utf8');
  assert.match(html, /href="password-reset\.html"/);
  assert.match(login, /requestLogin\(email, password\)/);
  assert.match(login, /startKakaoLogin\(\)/);
  assert.doesNotMatch(login, /비밀번호 재설정은 P1 기능으로 준비 중입니다/);
});
