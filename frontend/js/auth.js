(() => {
  const AUTH_STATES = new Set(['checking', 'authenticated', 'unauthenticated', 'error']);
  const configuredApiBaseUrl = window.BOKDEONGI_AUTH_API_BASE_URL;
  const apiBaseUrl = getApiBaseUrl(configuredApiBaseUrl);
  let accessToken = null;
  let accessTokenExpiresAt = null;
  let authenticationState = 'checking';
  let refreshPromise = null;
  let logoutPromise = null;
  let authenticationGeneration = 0;
  let logoutPending = false;
  let accountName = null;
  let headerMessage = null;
  let profileNameRequestId = 0;

  function getApiBaseUrl(configuredValue) {
    if (typeof configuredValue !== 'string' || !configuredValue.trim()) return null;
    try {
      const url = new URL(configuredValue);
      if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error('Unsupported protocol');
      return url.origin;
    } catch {
      return null;
    }
  }

  function getApiUrl(path) {
    if (!apiBaseUrl) throw new AuthRequestError('configuration');
    if (typeof path !== 'string' || !path.startsWith('/')) throw new AuthRequestError('path');
    const url = new URL(path, `${apiBaseUrl}/`);
    if (url.origin !== apiBaseUrl) throw new AuthRequestError('path');
    return url.toString();
  }

  function setAuthenticationState(status) {
    if (!AUTH_STATES.has(status)) return;
    authenticationState = status;
    if (status !== 'authenticated') {
      accountName = null;
      profileNameRequestId += 1;
    }
    renderHeaderAuthentication();
    window.dispatchEvent(new CustomEvent('bokdeongi:auth-state-change', { detail: { status } }));
    if (status === 'authenticated') loadHeaderAccountName();
  }

  function isCurrentAuthenticationGeneration(generation) {
    return generation === authenticationGeneration && !logoutPending;
  }

  function createLink(href, label, className) {
    const link = document.createElement('a');
    link.href = href;
    link.textContent = label;
    if (className) link.className = className;
    return link;
  }

  function createLogoutButton() {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'auth-links__logout';
    button.dataset.authLogout = 'true';
    button.disabled = logoutPending;
    button.textContent = logoutPending ? '로그아웃 중…' : '로그아웃';
    return button;
  }

  function renderHeaderAuthentication() {
    if (!document.body) return;

    const isAuthenticated = authenticationState === 'authenticated';
    document.querySelectorAll('.site-nav .site-nav__link[href$="my-page.html"]').forEach((link) => {
      link.hidden = !isAuthenticated;
    });

    document.querySelectorAll('.auth-links, .mobile-auth-links').forEach((container) => {
      container.replaceChildren();
      if (authenticationState === 'checking') {
        const status = document.createElement('span');
        status.className = 'auth-links__status';
        status.setAttribute('aria-live', 'polite');
        status.textContent = '인증 확인 중…';
        container.append(status);
        return;
      }
      if (isAuthenticated) {
        const account = document.createElement('span');
        account.className = 'auth-links__account';
        const name = document.createElement('span');
        name.className = 'auth-links__user';
        name.textContent = accountName ? accountName + ' 님' : '내 계정';
        account.append(name, createLogoutButton());
        container.append(account);
        if (headerMessage) {
          const message = document.createElement('span');
          message.className = 'auth-links__message';
          message.setAttribute('role', 'status');
          message.textContent = headerMessage;
          container.append(message);
        }
        return;
      }
      container.append(
        createLink('login.html', '로그인'),
        createLink('signup-account.html', '회원가입', 'auth-links__signup')
      );
    });
  }

  function setHeaderAccountName(name) {
    accountName = authenticationState === 'authenticated' && typeof name === 'string' && name.trim() ? name.trim() : null;
    renderHeaderAuthentication();
  }

  async function loadHeaderAccountName() {
    const requestId = ++profileNameRequestId;
    const generation = authenticationGeneration;
    if (authenticationState !== 'authenticated' || !isCurrentAuthenticationGeneration(generation)) return;

    try {
      const response = await authenticatedFetch('/api/me/profile');
      if (requestId !== profileNameRequestId || !isCurrentAuthenticationGeneration(generation)) return;
      if (!response.ok) {
        setHeaderAccountName(null);
        return;
      }
      const profile = await readJson(response);
      if (requestId !== profileNameRequestId || !isCurrentAuthenticationGeneration(generation)) return;
      setHeaderAccountName(profile && typeof profile.name === 'string' ? profile.name : null);
    } catch {
      if (requestId === profileNameRequestId && isCurrentAuthenticationGeneration(generation)) {
        setHeaderAccountName(null);
      }
    }
  }

  function clearAuthentication() {
    accessToken = null;
    accessTokenExpiresAt = null;
  }

  function acceptAccessToken(payload) {
    if (!payload || typeof payload.accessToken !== 'string' || !payload.accessToken || payload.tokenType !== 'Bearer' || !Number.isFinite(payload.expiresIn)) return false;
    accessToken = payload.accessToken;
    accessTokenExpiresAt = Date.now() + (payload.expiresIn * 1000);
    return true;
  }

  async function readJson(response) {
    try {
      return await response.json();
    } catch {
      return null;
    }
  }

  async function getCsrfToken() {
    let response;
    try {
      response = await fetch(getApiUrl('/api/auth/csrf'), { credentials: 'include' });
    } catch (error) {
      if (error instanceof AuthRequestError) throw error;
      throw new AuthRequestError('network');
    }

    const payload = await readJson(response);
    if (!response.ok || !payload || typeof payload.headerName !== 'string' || typeof payload.token !== 'string') {
      throw new AuthRequestError('csrf', response.status, payload && payload.code);
    }
    return payload;
  }

  async function runRefreshAuthentication(generation) {
    if (!isCurrentAuthenticationGeneration(generation)) return authenticationState;
    setAuthenticationState('checking');
    try {
      const csrf = await getCsrfToken();
      if (!isCurrentAuthenticationGeneration(generation)) return authenticationState;
      let response;
      try {
        response = await fetch(getApiUrl('/api/auth/refresh'), {
          method: 'POST',
          credentials: 'include',
          headers: { [csrf.headerName]: csrf.token }
        });
      } catch (error) {
        if (error instanceof AuthRequestError) throw error;
        throw new AuthRequestError('network');
      }

      const payload = await readJson(response);
      if (!isCurrentAuthenticationGeneration(generation)) return authenticationState;
      if (!response.ok || !acceptAccessToken(payload)) {
        clearAuthentication();
        setAuthenticationState(response.status === 401 || (payload && payload.code === 'AUTH_004') ? 'unauthenticated' : 'error');
        return authenticationState;
      }

      setAuthenticationState('authenticated');
      return authenticationState;
    } catch {
      clearAuthentication();
      setAuthenticationState('error');
      return authenticationState;
    }
  }

  function refreshAuthentication() {
    if (logoutPending) return Promise.resolve(authenticationState);
    if (refreshPromise) return refreshPromise;
    const generation = authenticationGeneration;
    refreshPromise = Promise.resolve()
      .then(() => runRefreshAuthentication(generation))
      .finally(() => { refreshPromise = null; });
    return refreshPromise;
  }

  async function sendAuthenticatedRequest(path, options, token = accessToken) {
    if (!token) throw new AuthRequestError('unauthenticated');

    const headers = new Headers(options.headers || {});
    headers.set('Authorization', `Bearer ${token}`);

    try {
      return await fetch(getApiUrl(path), {
        ...options,
        headers,
        credentials: 'include'
      });
    } catch (error) {
      if (error instanceof AuthRequestError) throw error;
      throw new AuthRequestError('network');
    }
  }

  async function authenticatedFetch(path, options = {}) {
    if (logoutPending) throw new AuthRequestError('logout-pending');
    const { shouldRefreshOnUnauthorized, ...requestOptions } = options;
    const generation = authenticationGeneration;
    const response = await sendAuthenticatedRequest(path, requestOptions);
    if (response.status !== 401) return response;

    if (typeof shouldRefreshOnUnauthorized === 'function') {
      let shouldRefresh;
      try {
        shouldRefresh = await shouldRefreshOnUnauthorized(response.clone());
      } catch {
        shouldRefresh = true;
      }
      if (shouldRefresh === false) return response;
    }

    if (!isCurrentAuthenticationGeneration(generation)) return response;

    const status = await refreshAuthentication();
    if (status !== 'authenticated' || !isCurrentAuthenticationGeneration(generation)) return response;

    const retriedResponse = await sendAuthenticatedRequest(path, requestOptions);
    if (retriedResponse.status === 401 && isCurrentAuthenticationGeneration(generation)) {
      clearAuthentication();
      setAuthenticationState('unauthenticated');
    }
    return retriedResponse;
  }

  class AuthRequestError extends Error {
    constructor(type, status = 0, code = null) {
      super('Authentication request failed.');
      this.type = type;
      this.status = status;
      this.code = code;
    }
  }

  async function logout() {
    if (logoutPromise) return logoutPromise;
    if (!accessToken || authenticationState !== 'authenticated') {
      throw new AuthRequestError('unauthenticated');
    }

    const token = accessToken;
    const generation = ++authenticationGeneration;
    logoutPending = true;
    headerMessage = null;
    renderHeaderAuthentication();

    logoutPromise = (async () => {
      try {
        const csrf = await getCsrfToken();
        const response = await sendAuthenticatedRequest('/api/auth/logout', {
          method: 'POST',
          headers: { [csrf.headerName]: csrf.token }
        }, token);

        if (response.status !== 204) {
          throw new AuthRequestError('logout', response.status);
        }

        if (generation === authenticationGeneration) {
          clearAuthentication();
          logoutPending = false;
          setAuthenticationState('unauthenticated');
          return true;
        }
        return false;
      } catch (error) {
        if (generation === authenticationGeneration) {
          logoutPending = false;
          headerMessage = '로그아웃을 완료하지 못했어요. 다시 시도해 주세요.';
          if (authenticationState !== 'authenticated' && accessToken) {
            setAuthenticationState('authenticated');
          } else {
            renderHeaderAuthentication();
          }
        }
        if (error instanceof AuthRequestError) throw error;
        throw new AuthRequestError('network');
      } finally {
        logoutPromise = null;
      }
    })();

    return logoutPromise;
  }

  window.BokdeongiAuth = Object.freeze({
    getApiUrl,
    getCsrfToken,
    getConsentStatus() {
      return authenticatedFetch('/api/auth/consents');
    },
    async updateConsent(consentType, docVersion, agreed) {
      if (typeof consentType !== 'string' || !consentType.trim()
          || typeof docVersion !== 'string' || !docVersion.trim()
          || typeof agreed !== 'boolean') {
        throw new AuthRequestError('input');
      }
      const csrf = await getCsrfToken();
      return authenticatedFetch('/api/auth/consents', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ consentType, docVersion, agreed })
      });
    },
    getAuthenticationState: () => authenticationState,
    completeLogin(payload) {
      if (!acceptAccessToken(payload)) return false;
      authenticationGeneration += 1;
      logoutPending = false;
      headerMessage = null;
      accountName = null;
      profileNameRequestId += 1;
      setAuthenticationState('authenticated');
      return true;
    },
    clearAuthentication() {
      authenticationGeneration += 1;
      logoutPending = false;
      headerMessage = null;
      clearAuthentication();
      setAuthenticationState('unauthenticated');
    },
    refreshAuthentication,
    authenticatedFetch,
    setHeaderAccountName,
    logout
  });

  document.addEventListener('click', async (event) => {
    const logoutButton = event.target.closest('[data-auth-logout]');
    if (!logoutButton || logoutButton.disabled) return;
    event.preventDefault();
    try {
      const loggedOut = await logout();
      if (loggedOut) window.location.assign('index.html');
    } catch {
      // 서버 로그아웃 실패 시 인증 상태를 성공으로 바꾸지 않는다.
    }
  });

  function initializeHeaderAuthentication() {
    renderHeaderAuthentication();
    refreshAuthentication();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeHeaderAuthentication, { once: true });
  } else {
    initializeHeaderAuthentication();
  }
})();
