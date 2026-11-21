(() => {
    const state = { status: 'idle', data: null, error: null, promise: null };

    function regionError(code) {
        const error = new Error(code);
        error.code = code;
        return error;
    }

    function normalizeRegions(payload) {
        if (!Array.isArray(payload)) throw regionError('invalid');
        if (payload.length === 0) throw regionError('empty');

        const codes = new Set();
        const rows = payload.map((item, index) => {
            if (!item || typeof item !== 'object' || typeof item.regionCode !== 'string' || !item.regionCode.trim() || typeof item.sidoName !== 'string' || !item.sidoName.trim() || (item.sigunguName !== null && (typeof item.sigunguName !== 'string' || !item.sigunguName.trim())) || !Number.isFinite(Number(item.sortOrder))) {
                throw regionError('invalid');
            }
            const regionCode = item.regionCode.trim();
            if (codes.has(regionCode)) throw regionError('duplicate');
            codes.add(regionCode);
            return Object.freeze({ regionCode, sidoName: item.sidoName.trim(), sigunguName: item.sigunguName === null ? null : item.sigunguName.trim(), sortOrder: Number(item.sortOrder), index });
        }).sort((a, b) => a.sortOrder - b.sortOrder || a.index - b.index);

        const byCode = new Map();
        const bySido = new Map();
        rows.forEach((row) => {
            byCode.set(row.regionCode, row);
            if (!bySido.has(row.sidoName)) bySido.set(row.sidoName, []);
            bySido.get(row.sidoName).push(row);
        });
        const sidos = Object.freeze([...bySido.keys()]);
        const selectableFor = (sidoName) => Object.freeze((bySido.get(sidoName) || []).filter((row) => row.sigunguName !== null || row.regionCode === '36110'));
        return Object.freeze({
            rows: Object.freeze(rows),
            sidos,
            findByCode: (regionCode) => byCode.get(regionCode) || null,
            selectableFor
        });
    }

    async function requestRegions() {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.getApiUrl !== 'function') throw regionError('configuration');
        let response;
        try {
            response = await fetch(auth.getApiUrl('/api/regions'), { credentials: 'include' });
        } catch {
            throw regionError('network');
        }
        let payload = null;
        try { payload = await response.json(); } catch { throw regionError('invalid'); }
        if (!response.ok) throw regionError('http');
        return normalizeRegions(payload);
    }

    function load({ force = false } = {}) {
        if (!force && state.status === 'ready') return Promise.resolve(state.data);
        if (state.status === 'loading') return state.promise;
        state.status = 'loading';
        state.error = null;
        state.promise = requestRegions().then((data) => {
            state.status = 'ready';
            state.data = data;
            return data;
        }).catch((error) => {
            state.status = 'error';
            state.error = error && error.code ? error : regionError('network');
            throw state.error;
        }).finally(() => { state.promise = null; });
        return state.promise;
    }

    window.BokdeongiRegions = Object.freeze({
        load,
        retry: () => load({ force: true }),
        getData: () => state.data,
        getStatus: () => state.status,
        getError: () => state.error
    });
})();
