const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.resolve(__dirname, '../js/regions.js'), 'utf8');

function response(status, payload) {
  return { ok: status >= 200 && status < 300, json: async () => payload };
}

function createHarness(fetchImplementation) {
  const requests = [];
  const context = {
    fetch: async (url, options) => {
      requests.push({ url, options });
      return fetchImplementation();
    },
    window: { BokdeongiAuth: { getApiUrl: (requestPath) => `http://backend.test${requestPath}` } }
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(source, context);
  return { regions: context.window.BokdeongiRegions, requests };
}

test('sorts API rows, groups by sido, and keeps Sejong selectable', async () => {
  const { regions, requests } = createHarness(() => response(200, [
    { regionCode: '12110', sidoName: '전남광주통합특별시', sigunguName: '목포시', sortOrder: 30 },
    { regionCode: '36110', sidoName: '세종특별자치시', sigunguName: null, sortOrder: 10 },
    { regionCode: '28110', sidoName: '인천광역시', sigunguName: '중구', sortOrder: 20 }
  ]));

  const data = await regions.load();

  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, 'http://backend.test/api/regions');
  assert.equal(requests[0].options.credentials, 'include');
  assert.deepEqual([...data.sidos], ['세종특별자치시', '인천광역시', '전남광주통합특별시']);
  assert.equal(data.selectableFor('세종특별자치시')[0].regionCode, '36110');
  assert.equal(data.selectableFor('전남광주통합특별시')[0].regionCode, '12110');
});

test('shares one in-flight request and retains the successful list in memory', async () => {
  let resolveRequest;
  const pending = new Promise((resolve) => { resolveRequest = resolve; });
  const { regions, requests } = createHarness(() => pending);

  const first = regions.load();
  const second = regions.load();
  resolveRequest(response(200, [{ regionCode: '36110', sidoName: '세종특별자치시', sigunguName: null, sortOrder: 1 }]));
  const [one, two] = await Promise.all([first, second]);
  await regions.load();

  assert.equal(requests.length, 1);
  assert.equal(one, two);
});

test('rejects duplicate codes and unsuccessful responses instead of exposing a partial list', async () => {
  const duplicate = createHarness(() => response(200, [
    { regionCode: '11110', sidoName: '서울특별시', sigunguName: '종로구', sortOrder: 1 },
    { regionCode: '11110', sidoName: '서울특별시', sigunguName: '중구', sortOrder: 2 }
  ]));
  await assert.rejects(duplicate.regions.load(), (error) => error.code === 'duplicate');

  const failed = createHarness(() => response(500, { message: 'internal error' }));
  await assert.rejects(failed.regions.load(), (error) => error.code === 'http');
});
