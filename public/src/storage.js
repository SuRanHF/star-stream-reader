// Storage module - localStorage helpers
const Storage = {
  getPlayerId() {
    return localStorage.getItem('player_id');
  },
  setPlayerId(id) {
    localStorage.setItem('player_id', String(id));
  },
  getToken() {
    return localStorage.getItem('auth_token');
  },
  setToken(token) {
    localStorage.setItem('auth_token', token);
  },
  clearToken() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('session_token');
    localStorage.removeItem('session_user');
  },
  setSession(token, username) {
    localStorage.setItem('auth_token', token);
    localStorage.setItem('session_token', token);
    localStorage.setItem('session_user', username);
  },
  getSessionUser() {
    return localStorage.getItem('session_user');
  },
  checkSession() {
    const currentToken = localStorage.getItem('auth_token');
    const sessionToken = localStorage.getItem('session_token');
    // No session stored = first load or admin-only page, allow
    if (!sessionToken) return true;
    // Token was overwritten by another tab
    if (currentToken !== sessionToken) return false;
    return true;
  },
  clear() {
    localStorage.removeItem('player_id');
    localStorage.removeItem('cached_state');
    localStorage.removeItem('auth_token');
    localStorage.removeItem('session_token');
    localStorage.removeItem('session_user');
  },
  cacheState(state) {
    try {
      localStorage.setItem('cached_state', JSON.stringify(state));
    } catch (e) { /* quota exceeded, ignore */ }
  },
  getCachedState() {
    try {
      const s = localStorage.getItem('cached_state');
      return s ? JSON.parse(s) : null;
    } catch (e) { return null; }
  }
};
