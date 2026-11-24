const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '..', 'js', 'application-status.js'), 'utf8');

test('application status loads only the authenticated server list and has loading and failure states', () => {
  assert.match(source, /getApplicationList\(/);
  assert.match(source, /신청 현황을 불러오는 중/);
  assert.match(source, /신청 현황을 불러올 수 없습니다/);
  assert.doesNotMatch(source, /mockApplications/);
});

test('application status uses the three server arrays without reclassifying their items', () => {
  assert.match(source, /state\.list\.inProgress/);
  assert.match(source, /state\.list\.completed/);
  assert.match(source, /state\.list\.expired/);
  assert.doesNotMatch(source, /inProgress\.filter|completed\.filter|expired\.filter/);
  assert.match(source, /summary\.expiredCount/);
});

test('application status displays server progress, completion counts, deadline data, and every documented status', () => {
  assert.match(source, /item\.completedItems \+ ' \/ ' \+ item\.totalItems/);
  assert.match(source, /item\.deadlineLabel/);
  assert.match(source, /item\.dDay/);
  assert.match(source, /item\.progressPercent/);
  assert.match(source, /CANCELED/);
  assert.doesNotMatch(source, /announcementChanged/);
});

test('application status resumes only a freshly fetched editable application at its server step', () => {
  assert.match(source, /getApplication\(id\)/);
  assert.match(source, /canEdit\(application\)/);
  assert.match(source, /assistantUrl\(application\.step, application\.id\)/);
  assert.match(source, /data-resume-application/);
  assert.match(source, /benefit-detail\.html\?id=.*item\.benefitId/);
  assert.doesNotMatch(source, /startApplication\(/);
});

test('application status confirms and serializes only the permitted cancellation and result transitions', () => {
  assert.match(source, /currentStatus === 'IN_PROGRESS' && nextStatus === 'CANCELED'/);
  assert.match(source, /currentStatus === 'SUBMITTED' && \(nextStatus === 'SELECTED' \|\| nextStatus === 'REJECTED'\)/);
  assert.match(source, /data-change-status="CANCELED"/);
  assert.match(source, /data-change-status="SELECTED"/);
  assert.match(source, /data-change-status="REJECTED"/);
  assert.doesNotMatch(source, /data-change-status="SUBMITTED"/);
  assert.match(source, /window\.confirm/);
  assert.match(source, /updateApplicationStatus\(id, nextStatus\)/);
  assert.match(source, /getApplication\(id\)/);
  assert.match(source, /state\.statusUpdatingId/);
  assert.match(source, /load\(true\)/);
});
