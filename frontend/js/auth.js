(() => {
  const AUTH_STATES = new Set(['checking', 'authenticated', 'unauthenticated', 'error']);
  const configuredApiBaseUrl = window.BOKDEONGI_AUTH_API_BASE_URL;
  const apiBaseUrl = getApiBaseUrl(configuredApiBaseUrl);
  let accessToken = null;
  let accessTokenExpiresAt = null;
  let authenticationState = 'checking';
  let refreshPromise = null;

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
    return new URL(path, `${apiBaseUrl}/`).toString();
  }

  function setAuthenticationState(status) {
    if (!AUTH_STATES.has(status)) return;
    authenticationState = status;
    window.dispatchEvent(new CustomEvent('bokdeongi:auth-state-change', { detail: { status } }));
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

  async function runRefreshAuthentication() {
    setAuthenticationState('checking');
    try {
      const csrf = await getCsrfToken();
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
    if (refreshPromise) return refreshPromise;
    refreshPromise = Promise.resolve()
      .then(runRefreshAuthentication)
      .finally(() => { refreshPromise = null; });
    return refreshPromise;
  }

  class AuthRequestError extends Error {
    constructor(type, status = 0, code = null) {
      super('Authentication request failed.');
      this.type = type;
      this.status = status;
      this.code = code;
    }
  }

  window.BokdeongiAuth = Object.freeze({
    getApiUrl,
    getCsrfToken,
    getAuthenticationState: () => authenticationState,
    completeLogin(payload) {
      if (!acceptAccessToken(payload)) return false;
      setAuthenticationState('authenticated');
      return true;
    },
    clearAuthentication() {
      clearAuthentication();
      setAuthenticationState('unauthenticated');
    },
    refreshAuthentication
  });
})();
