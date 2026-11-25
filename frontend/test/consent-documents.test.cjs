const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'consent-documents.js'), 'utf8');

function loadDocuments() {
  const created = [];
  const document = { createElement(tag) { const node = { tag, children: [], append(child) { this.children.push(child); }, replaceChildren() { this.children = []; } }; created.push(node); return node; } };
  const window = {};
  vm.runInNewContext(source, { window, document, Object });
  return { docs: window.BokdeongiConsentDocuments, created };
}

test('exposes DOC_PROCESS and DOC_RECORD documents at v1.0 while retaining existing signup documents', () => {
  const { docs } = loadDocuments();
  for (const type of ['TERMS', 'PRIVACY', 'OPTIONAL_INFO', 'SENSITIVE', 'DOC_PROCESS', 'DOC_RECORD']) {
    assert.equal(docs.versionFor(type), 'v1.0');
    assert.equal(docs.get(type).version, 'v1.0');
  }
  assert.equal(docs.get('DOC_PROCESS').title, '서류 처리 및 자동 검토 동의서');
  assert.equal(docs.get('DOC_RECORD').title, '서류 유효기간 기억 동의서');
});

test('DOC_PROCESS document states the confirmed OCR, masking, retention, and upload policy', () => {
  const { docs } = loadDocuments();
  const body = docs.get('DOC_PROCESS').sections.flatMap(([, paragraphs]) => paragraphs).join(' ');
  for (const phrase of ['PaddleOCR', '외부 생성형 AI로 전송하지 않습니다', '영구 저장하지 않으며', '처리가 끝난 직후 삭제', 'OCR 추출 텍스트는 저장하거나 백엔드로 전달하지 않습니다', '주소는 마스킹하지 않습니다', '서류 업로드를 이용할 수 없습니다']) assert.ok(body.includes(phrase), phrase);
});

test('DOC_RECORD document states the confirmed issue-date, expiry, withdrawal, and file policy', () => {
  const { docs } = loadDocuments();
  const body = docs.get('DOC_RECORD').sections.flatMap(([, paragraphs]) => paragraphs).join(' ');
  for (const phrase of ['서류 종류 ID와 발급일', '유효기간 및 만료 여부', '동의한 회원', '동의를 철회하면 저장된 서류 기억 기록을 삭제', '원본 파일과 OCR 추출 텍스트는 저장하지 않습니다']) assert.ok(body.includes(phrase), phrase);
  assert.doesNotMatch(body, /보유 기간|회원 탈퇴/);
});

test('renders both new consent documents through the shared renderer', () => {
  const { docs } = loadDocuments();
  for (const type of ['DOC_PROCESS', 'DOC_RECORD']) {
    const container = { children: [], replaceChildren() { this.children = []; }, append(node) { this.children.push(node); } };
    assert.equal(docs.renderInto(container, type).version, 'v1.0');
    assert.equal(container.children[0].textContent, '문서 버전: v1.0');
    assert.ok(container.children.length > 1);
  }
});
