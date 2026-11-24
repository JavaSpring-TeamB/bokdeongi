(() => {
    const STEP_PATHS = Object.freeze({
        1: 'assist-eligibility.html',
        2: 'assist-documents.html',
        3: 'assist-review.html',
        4: 'assist-guide.html'
    });
    const requests = new Map();
    const updates = new Map();

    class ApplicationApiError extends Error {
        constructor(kind, message = '', status = 0, code = null) {
            super(message);
            this.kind = kind;
            this.status = status;
            this.code = code;
        }
    }

    function parseApplicationId(value) {
        if (!/^[1-9]\d*$/.test(String(value || ''))) return null;
        const id = Number(value);
        return Number.isSafeInteger(id) ? id : null;
    }

    function isObject(value) {
        return value !== null && typeof value === 'object' && !Array.isArray(value);
    }

    function isApplicationResponse(value) {
        return isObject(value)
            && parseApplicationId(value.id) !== null
            && parseApplicationId(value.benefitId) !== null
            && typeof value.appStatus === 'string' && value.appStatus.trim()
            && Number.isInteger(value.step) && value.step >= 1 && value.step <= 4
            && isObject(value.appAnswers)
            && isObject(value.docChecks)
            && (value.startedApiModifiedAt === null || typeof value.startedApiModifiedAt === 'string')
            && typeof value.announcementChanged === 'boolean'
            && typeof value.stage === 'string'
            && Number.isInteger(value.completedItems)
            && Number.isInteger(value.totalItems)
            && Number.isInteger(value.progressPercent);
    }

    function normalizeApplication(value) {
        if (!isApplicationResponse(value)) {
            throw new ApplicationApiError('INVALID', '신청 정보 응답 형식이 올바르지 않습니다.');
        }
        return { ...value, id: Number(value.id), benefitId: Number(value.benefitId) };
    }

    async function readError(response) {
        try {
            const body = await response.json();
            const message = body?.message || body?.detail;
            return {
                message: typeof message === 'string' ? message.trim() : '',
                code: typeof body?.code === 'string' ? body.code : null
            };
        } catch {
            return { message: '', code: null };
        }
    }

    function errorKind(status) {
        return ({ 400: 'INPUT', 401: 'UNAUTHENTICATED', 403: 'FORBIDDEN', 404: 'NOT_FOUND', 409: 'CONFLICT' })[status] || 'SERVER';
    }

    async function requireAuth() {
        const auth = window.BokdeongiAuth;
        if (!auth || typeof auth.refreshAuthentication !== 'function' || typeof auth.authenticatedFetch !== 'function') {
            throw new ApplicationApiError('CONFIGURATION', '신청 서비스를 설정하지 못했습니다.');
        }
        if (await auth.refreshAuthentication() !== 'authenticated') {
            throw new ApplicationApiError('UNAUTHENTICATED', '로그인 후 신청 도우미를 이용할 수 있습니다.', 401);
        }
        return auth;
    }

    async function fetchJson(method, path, body, signal) {
        const auth = await requireAuth();
        const options = { method, signal };
        if (body !== undefined) {
            if (typeof auth.getCsrfToken !== 'function') {
                throw new ApplicationApiError('CONFIGURATION', '보안 토큰을 준비하지 못했습니다.');
            }
            const csrf = await auth.getCsrfToken();
            const multipart = typeof FormData !== 'undefined' && body instanceof FormData;
            options.headers = multipart ? { [csrf.headerName]: csrf.token } : { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token };
            options.body = multipart ? body : JSON.stringify(body);
        }
        const response = await auth.authenticatedFetch(path, options);
        if (!response.ok) {
            const error = await readError(response);
            throw new ApplicationApiError(errorKind(response.status), error.message, response.status, error.code);
        }
        return response.json();
    }

    function calendarFileName(contentDisposition, applicationId) {
        const fallback = 'application-' + applicationId + '.ics';
        if (typeof contentDisposition !== 'string') return fallback;
        const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition);
        const quoted = /filename\s*=\s*"([^"]+)"/i.exec(contentDisposition);
        const plain = /filename\s*=\s*([^;\s]+)/i.exec(contentDisposition);
        let name = encoded?.[1] || quoted?.[1] || plain?.[1] || '';
        try {
            name = encoded ? decodeURIComponent(name) : name;
        } catch {
            return fallback;
        }
        return /^[^\\/:*?"<>|]+\.ics$/i.test(name) ? name : fallback;
    }

    async function fetchCalendar(applicationId, signal) {
        try {
            const auth = await requireAuth();
            const response = await auth.authenticatedFetch(
                '/api/applications/' + encodeURIComponent(applicationId) + '/calendar.ics',
                { method: 'GET', signal }
            );
            if (!response.ok) {
                const error = await readError(response);
                throw new ApplicationApiError(errorKind(response.status), error.message, response.status, error.code);
            }
            const contentType = response.headers?.get('Content-Type') || '';
            if (!/^text\/calendar(?:\s*;|$)/i.test(contentType) || typeof response.blob !== 'function') {
                throw new ApplicationApiError('INVALID', '캘린더 파일 응답 형식이 올바르지 않습니다.');
            }
            const blob = await response.blob();
            if (typeof Blob === 'undefined' || !(blob instanceof Blob)) {
                throw new ApplicationApiError('INVALID', '캘린더 파일을 확인하지 못했습니다.');
            }
            return {
                blob,
                fileName: calendarFileName(response.headers?.get('Content-Disposition'), applicationId),
                contentType
            };
        } catch (error) {
            if (error instanceof ApplicationApiError) throw error;
            throw new ApplicationApiError('NETWORK', '캘린더 파일을 다운로드하지 못했습니다.');
        }
    }

    async function request(method, path, body, signal) {
        return normalizeApplication(await fetchJson(method, path, body, signal));
    }

    function isDocumentKey(value) {
        return /^(?:[1-9]\d*|form:[0-9]+|unknown:[0-9]+)$/.test(String(value || ''));
    }

    function isDocumentState(value) {
        return isObject(value)
            && typeof value.prepared === 'boolean'
            && typeof value.reviewed === 'boolean'
            && (value.result === null || typeof value.result === 'string')
            && (value.reason === null || typeof value.reason === 'string')
            && (value.issueDate === null || typeof value.issueDate === 'string')
            && (value.documentTypeId === null || parseApplicationId(value.documentTypeId) !== null);
    }

    function documentKeys(documents) {
        const keys = new Set();
        if (!Array.isArray(documents.groups) || !Array.isArray(documents.forms) || !Array.isArray(documents.unknown)) return null;
        for (const group of documents.groups) {
            if (!isObject(group) || !Array.isArray(group.items)) return null;
            for (const item of group.items) {
                if (item?.documentTypeId === null || item?.documentTypeId === undefined) continue;
                const id = parseApplicationId(item?.documentTypeId);
                if (id === null) return null;
                keys.add(String(id));
            }
        }
        for (let index = 0; index < documents.forms.length; index += 1) keys.add('form:' + index);
        for (let index = 0; index < documents.unknown.length; index += 1) keys.add('unknown:' + index);
        return keys;
    }

    function isDocumentsResponse(value) {
        if (!isObject(value) || !isObject(value.documents) || !isObject(value.states)) return false;
        const documents = value.documents;
        if (!Array.isArray(documents.groups) || !Array.isArray(documents.forms)
            || !Array.isArray(documents.unknown) || !Array.isArray(documents.contacts)
            || !Number.isInteger(documents.total) || documents.total < 0) return false;
        const keys = documentKeys(documents);
        return keys !== null && Object.keys(value.states).length === keys.size
            && Object.entries(value.states).every(([key, state]) => keys.has(key) && isDocumentState(state));
    }

    function normalizeDocuments(value) {
        if (!isDocumentsResponse(value)) {
            throw new ApplicationApiError('INVALID', '서류 준비 응답 형식이 올바르지 않습니다.');
        }
        return value;
    }

    const OCR_RESULTS = new Set(['READY', 'MISSING', 'INVALID', 'RETRY']);

    function isReviewResponse(value) {
        return isObject(value)
            && parseApplicationId(value.documentTypeId) !== null
            && OCR_RESULTS.has(value.result)
            && typeof value.ocrResult === 'string'
            && (value.recognizedType === null || typeof value.recognizedType === 'string')
            && (value.issueDate === null || typeof value.issueDate === 'string')
            && typeof value.reason === 'string'
            && Array.isArray(value.foundFields) && value.foundFields.every((field) => typeof field === 'string')
            && Array.isArray(value.missingFields) && value.missingFields.every((field) => typeof field === 'string');
    }

    function normalizeReview(value) {
        if (!isReviewResponse(value)) {
            throw new ApplicationApiError('INVALID', '서류 검토 응답 형식이 올바르지 않습니다.');
        }
        return { ...value, documentTypeId: Number(value.documentTypeId) };
    }

    function isMissingDocumentsResponse(value) {
        return isObject(value) && Array.isArray(value.groups) && Array.isArray(value.forms)
            && Array.isArray(value.unknown) && typeof value.allReady === 'boolean';
    }

    function normalizeMissingDocuments(value) {
        if (!isMissingDocumentsResponse(value)) {
            throw new ApplicationApiError('INVALID', '부족 서류 응답 형식이 올바르지 않습니다.');
        }
        return value;
    }

    const APPLICATION_STATUSES = new Set(['SUBMITTED', 'SELECTED', 'REJECTED', 'CANCELED']);

    function isGuideResponse(value) {
        return isObject(value)
            && (value.method === null || typeof value.method === 'string')
            && (value.applyUrl === null || typeof value.applyUrl === 'string')
            && (value.siteName === null || typeof value.siteName === 'string')
            && (value.detailUrl === null || typeof value.detailUrl === 'string')
            && Array.isArray(value.steps) && value.steps.every((step) => isObject(step) && Number.isInteger(step.order)
                && (step.method === null || typeof step.method === 'string') && typeof step.text === 'string')
            && Array.isArray(value.notes) && value.notes.every((note) => typeof note === 'string')
            && Array.isArray(value.copyCards) && value.copyCards.every((card) => isObject(card)
                && typeof card.key === 'string' && typeof card.label === 'string' && (card.value === null || typeof card.value === 'string'))
            && (value.visit === null || isObject(value.visit))
            && Array.isArray(value.contacts);
    }

    function normalizeGuide(value) {
        if (!isGuideResponse(value)) {
            throw new ApplicationApiError('INVALID', '신청 안내 응답 형식이 올바르지 않습니다.');
        }
        return value;
    }

    function isApplicationListItem(value) {
        return isObject(value) && parseApplicationId(value.id) !== null && parseApplicationId(value.benefitId) !== null
            && typeof value.benefitName === 'string' && typeof value.appStatus === 'string' && typeof value.stage === 'string'
            && (value.step === null || Number.isInteger(value.step)) && Number.isInteger(value.completedItems)
            && Number.isInteger(value.totalItems) && Number.isInteger(value.progressPercent)
            && (value.deadline === null || typeof value.deadline === 'string')
            && (value.deadlineLabel === null || typeof value.deadlineLabel === 'string')
            && (value.dDay === null || Number.isInteger(value.dDay));
    }

    function isApplicationListResponse(value) {
        return isObject(value) && isObject(value.summary) && Number.isInteger(value.summary.totalCount)
            && Number.isInteger(value.summary.inProgressCount) && Number.isInteger(value.summary.completedCount)
            && Number.isInteger(value.summary.expiredCount) && Array.isArray(value.inProgress)
            && Array.isArray(value.completed) && Array.isArray(value.expired)
            && value.inProgress.every(isApplicationListItem) && value.completed.every(isApplicationListItem)
            && value.expired.every(isApplicationListItem);
    }

    function normalizeApplicationList(value) {
        if (!isApplicationListResponse(value)) throw new ApplicationApiError('INVALID', '신청 목록 응답 형식이 올바르지 않습니다.');
        return value;
    }

    function isReviewFile(file) {
        return isObject(file) && typeof file.name === 'string' && Number.isFinite(file.size) && file.size > 0;
    }

    function validateReviewFile(file) {
        if (!isReviewFile(file)) return '올릴 서류 파일을 선택해 주세요.';
        if (file.size > 10 * 1024 * 1024) return '10MB 이하 파일만 올릴 수 있어요.';
        const allowedTypes = new Set(['application/pdf', 'image/jpeg', 'image/png']);
        const extension = /\.(pdf|jpe?g|png)$/i.test(file.name);
        if ((file.type && !allowedTypes.has(file.type)) || (!file.type && !extension)) {
            return 'JPG, PNG, PDF 파일만 올릴 수 있어요.';
        }
        return '';
    }

    function deduplicate(key, factory) {
        if (requests.has(key)) return requests.get(key);
        const pending = Promise.resolve().then(factory).finally(() => requests.delete(key));
        requests.set(key, pending);
        return pending;
    }

    function startApplication(benefitId) {
        const id = parseApplicationId(benefitId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '혜택 정보를 확인할 수 없습니다.'));
        return deduplicate('start:' + id, () => request('POST', '/api/applications', { benefitId: id }));
    }

    function getApplication(applicationId) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        return deduplicate('get:' + id, () => request('GET', '/api/applications/' + encodeURIComponent(id)));
    }

    function getApplicationList(options = {}) {
        const requestList = async () => normalizeApplicationList(await fetchJson('GET', '/api/applications'));
        return options.force === true ? requestList() : deduplicate('list', requestList);
    }

    function getDocuments(applicationId) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        return deduplicate('documents:' + id, async () => normalizeDocuments(
            await fetchJson('GET', '/api/applications/' + encodeURIComponent(id) + '/documents')
        ));
    }

    function getMissingDocuments(applicationId) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        return deduplicate('missing:' + id, async () => normalizeMissingDocuments(
            await fetchJson('GET', '/api/applications/' + encodeURIComponent(id) + '/missing')
        ));
    }

    function getApplicationGuide(applicationId) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        return deduplicate('guide:' + id, async () => normalizeGuide(
            await fetchJson('GET', '/api/applications/' + encodeURIComponent(id) + '/guide')
        ));
    }

    function downloadApplicationCalendar(applicationId, signal) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        return deduplicate('calendar:' + id, () => fetchCalendar(id, signal));
    }

    function enqueueUpdate(applicationId, factory) {
        const previous = updates.get(applicationId);
        const previousTail = previous ? previous.tail : Promise.resolve();
        const pending = previousTail.catch(() => undefined).then(factory);
        const tail = pending.catch(() => undefined);
        updates.set(applicationId, { tail });
        tail.finally(() => {
            if (updates.get(applicationId)?.tail === tail) updates.delete(applicationId);
        });
        return pending;
    }

    function updateApplication(applicationId, payload) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        if (!isObject(payload) || !Number.isInteger(payload.step) || payload.step < 1 || payload.step > 4) {
            return Promise.reject(new ApplicationApiError('INPUT', '진행 단계는 1~4 사이여야 합니다.'));
        }
        if ((payload.appAnswers !== undefined && !isObject(payload.appAnswers))
            || (payload.docChecks !== undefined && !isObject(payload.docChecks))) {
            return Promise.reject(new ApplicationApiError('INPUT', '저장할 추가 정보는 객체여야 합니다.'));
        }
        const requestBody = { step: payload.step };
        if (payload.appAnswers !== undefined) requestBody.appAnswers = payload.appAnswers;
        if (payload.docChecks !== undefined) requestBody.docChecks = payload.docChecks;

        // 요청 취소는 이미 서버에서 처리 중인 PATCH의 순서를 되돌릴 수 없다.
        // 같은 신청 건의 저장은 직렬화해 서버 병합 순서도 화면의 입력 순서와 맞춘다.
        return enqueueUpdate(id, () => request('PATCH', '/api/applications/' + encodeURIComponent(id), requestBody));
    }

    function updateDocumentPrepared(applicationId, documentKey, prepared) {
        const id = parseApplicationId(applicationId);
        if (id === null) return Promise.reject(new ApplicationApiError('INPUT', '신청 ID가 올바르지 않습니다.'));
        if (!isDocumentKey(documentKey) || typeof prepared !== 'boolean') {
            return Promise.reject(new ApplicationApiError('INPUT', '서류 준비 상태가 올바르지 않습니다.'));
        }
        const path = '/api/applications/' + encodeURIComponent(id) + '/documents/' + encodeURIComponent(documentKey);
        return enqueueUpdate(id, async () => normalizeDocuments(await fetchJson('PATCH', path, { prepared })));
    }

    function updateApplicationStatus(applicationId, status) {
        const id = parseApplicationId(applicationId);
        if (id === null || !APPLICATION_STATUSES.has(status)) {
            return Promise.reject(new ApplicationApiError('INPUT', '변경할 신청 상태가 올바르지 않습니다.'));
        }
        return enqueueUpdate(id, () => request('PATCH', '/api/applications/' + encodeURIComponent(id) + '/status', { status }));
    }

    function reviewDocument(applicationId, documentTypeId, file) {
        const id = parseApplicationId(applicationId);
        const typeId = parseApplicationId(documentTypeId);
        if (id === null || typeId === null) return Promise.reject(new ApplicationApiError('INPUT', '서류 종류를 확인해 주세요.'));
        const fileError = validateReviewFile(file);
        if (fileError) return Promise.reject(new ApplicationApiError('INPUT', fileError));
        const formData = new FormData();
        formData.append('file', file);
        formData.append('documentTypeId', String(typeId));
        return enqueueUpdate(id, async () => normalizeReview(await fetchJson(
            'POST', '/api/applications/' + encodeURIComponent(id) + '/documents', formData
        )));
    }

    function assistantUrl(step, applicationId) {
        const path = STEP_PATHS[step];
        const id = parseApplicationId(applicationId);
        return path && id !== null ? path + '?applicationId=' + encodeURIComponent(id) : null;
    }

    function canEdit(application) {
        return application?.appStatus === 'IN_PROGRESS' && Number.isInteger(application.step);
    }

    function messageFor(error) {
        if (error instanceof ApplicationApiError && error.message) return error.message;
        const messages = {
            INPUT: '신청 정보를 확인해주세요.',
            UNAUTHENTICATED: '로그인 후 신청 도우미를 이용할 수 있습니다.',
            FORBIDDEN: '신청 정보를 조회하거나 수정할 권한이 없습니다.',
            NOT_FOUND: '신청 정보를 찾을 수 없습니다.',
            CONFLICT: '현재 신청 상태에서는 수정할 수 없습니다.',
            INVALID: '신청 정보 응답을 확인하지 못했습니다.',
            NETWORK: '네트워크 연결을 확인한 뒤 다시 시도해주세요.'
        };
        return messages[error?.kind] || '신청 정보를 처리하지 못했습니다. 잠시 후 다시 시도해주세요.';
    }

    function renderAssistantError(container, error) {
        if (!container) return;
        container.replaceChildren();
        const section = document.createElement('section');
        section.className = 'card empty-state';
        const title = document.createElement('h2');
        title.textContent = error?.kind === 'UNAUTHENTICATED' ? '로그인이 필요해요.' : '신청 정보를 열 수 없어요.';
        const description = document.createElement('p');
        description.textContent = messageFor(error);
        const link = document.createElement('a');
        link.className = 'bk-btn bk-btn--secondary bk-btn--md';
        link.href = error?.kind === 'UNAUTHENTICATED' ? 'login.html' : 'benefit-list.html';
        link.textContent = error?.kind === 'UNAUTHENTICATED' ? '로그인하기' : '혜택 목록으로 이동';
        section.append(title, description, link);
        container.append(section);
    }

    async function loadCurrentAssistantApplication(container) {
        const applicationId = parseApplicationId(new URLSearchParams(window.location.search).get('applicationId'));
        if (applicationId === null) {
            const error = new ApplicationApiError('INPUT', '신청 ID가 없거나 올바르지 않습니다.');
            renderAssistantError(container, error);
            return null;
        }
        try {
            const application = await getApplication(applicationId);
            if (!canEdit(application)) {
                const error = new ApplicationApiError('CONFLICT', '완료되었거나 취소된 신청은 신청 도우미에서 수정할 수 없습니다.');
                renderAssistantError(container, error);
                return null;
            }
            return application;
        } catch (error) {
            renderAssistantError(container, error);
            return null;
        }
    }

    window.BokdeongiApplication = Object.freeze({
        ApplicationApiError,
        assistantUrl,
        canEdit,
        downloadApplicationCalendar,
        getApplication,
        getApplicationGuide,
        getApplicationList,
        getDocuments,
        getMissingDocuments,
        isApplicationResponse,
        isApplicationListResponse,
        isDocumentsResponse,
        isMissingDocumentsResponse,
        isReviewResponse,
        loadCurrentAssistantApplication,
        messageFor,
        parseApplicationId,
        reviewDocument,
        startApplication,
        updateApplication,
        updateApplicationStatus,
        updateDocumentPrepared,
        validateReviewFile
    });
})();
