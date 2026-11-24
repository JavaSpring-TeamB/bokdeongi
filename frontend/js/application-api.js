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
        constructor(kind, message = '', status = 0) {
            super(message);
            this.kind = kind;
            this.status = status;
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

    async function readErrorMessage(response) {
        try {
            const body = await response.json();
            const message = body?.message || body?.detail;
            return typeof message === 'string' ? message.trim() : '';
        } catch {
            return '';
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

    async function request(method, path, body, signal) {
        const auth = await requireAuth();
        const options = { method, signal };
        if (body !== undefined) {
            if (typeof auth.getCsrfToken !== 'function') {
                throw new ApplicationApiError('CONFIGURATION', '보안 토큰을 준비하지 못했습니다.');
            }
            const csrf = await auth.getCsrfToken();
            options.headers = { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token };
            options.body = JSON.stringify(body);
        }
        const response = await auth.authenticatedFetch(path, options);
        if (!response.ok) {
            throw new ApplicationApiError(errorKind(response.status), await readErrorMessage(response), response.status);
        }
        return normalizeApplication(await response.json());
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
        const previous = updates.get(id);
        const previousTail = previous ? previous.tail : Promise.resolve();
        const pending = previousTail.catch(() => undefined)
            .then(() => request('PATCH', '/api/applications/' + encodeURIComponent(id), requestBody));
        const tail = pending.catch(() => undefined);
        updates.set(id, { tail });
        tail.finally(() => {
            if (updates.get(id)?.tail === tail) updates.delete(id);
        });
        return pending;
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
        getApplication,
        isApplicationResponse,
        loadCurrentAssistantApplication,
        messageFor,
        parseApplicationId,
        startApplication,
        updateApplication
    });
})();
