const Api = (() => {
    const TOKEN_KEY = 'token';
    const USER_KEY = 'user';

    const getToken = () => localStorage.getItem(TOKEN_KEY);
    const setSession = (auth) => {
        localStorage.setItem(TOKEN_KEY, auth.token);
        localStorage.setItem(USER_KEY, JSON.stringify({
            userId: auth.userId, fullname: auth.fullname, email: auth.email
        }));
    };
    const getUser = () => JSON.parse(localStorage.getItem(USER_KEY) || '{}');
    const clear = () => { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); };

    const requireAuth = () => {
        if (!getToken()) window.location.replace('/login.html');
    };

    async function request(path, options = {}) {
        const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
        const token = getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        const res = await fetch(path, { ...options, headers });
        if (res.status === 401) {
            clear();
            window.location.replace('/login.html');
            throw new Error('Unauthorized');
        }
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) {
            const msg = data && data.message ? data.message : ('Request failed (' + res.status + ')');
            throw new Error(msg);
        }
        return data;
    }

    return {
        getToken, setSession, getUser, clear, requireAuth,
        get: (p) => request(p),
        post: (p, body) => request(p, { method: 'POST', body: JSON.stringify(body) }),
        put: (p, body) => request(p, { method: 'PUT', body: JSON.stringify(body) }),
    };
})();
