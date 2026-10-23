import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, getToken, onTokenChange, setToken, setUnauthorizedHandler, tryRefresh } from './api';
import type { AuthResponse, User } from './types';

interface AuthCtx {
  user: User | null;
  loading: boolean;
  login: (r: AuthResponse) => void;
  logout: () => Promise<void>;
  refreshMe: () => Promise<void>;
}

const Ctx = createContext<AuthCtx>({
  user: null,
  loading: true,
  login: () => {},
  logout: async () => {},
  refreshMe: async () => {},
});

const USER_KEY = 'vs_user';

function cachedUser(): User | null {
  try {
    const s = sessionStorage.getItem(USER_KEY);
    return s ? (JSON.parse(s) as User) : null;
  } catch {
    return null;
  }
}

function storeUser(u: User | null) {
  try {
    if (u) sessionStorage.setItem(USER_KEY, JSON.stringify(u));
    else sessionStorage.removeItem(USER_KEY);
  } catch {
    /* ignore */
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => (getToken() ? cachedUser() : null));
  const [loading, setLoading] = useState(true);

  const refreshMe = useCallback(async () => {
    const me = await api.get<User>('/me');
    setUser(me);
    storeUser(me);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (!getToken()) {
          // Maybe a refresh cookie is still valid.
          const ok = await tryRefresh();
          if (!ok) return;
        }
        const me = await api.get<User>('/me', { noAuthRetry: false });
        if (!cancelled) {
          setUser(me);
          storeUser(me);
        }
      } catch {
        if (!cancelled) {
          setUser(null);
          storeUser(null);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      storeUser(null);
      if (!location.pathname.startsWith('/login') && !location.pathname.startsWith('/v/')) {
        const next = encodeURIComponent(location.pathname + location.search);
        location.assign(`/login?next=${next}`);
      }
    });
    return onTokenChange((t) => {
      if (!t) {
        setUser(null);
        storeUser(null);
      }
    });
  }, []);

  const login = useCallback((r: AuthResponse) => {
    setToken(r.accessToken);
    setUser(r.user);
    storeUser(r.user);
    setLoading(false);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout', undefined, { noAuthRetry: true });
    } catch {
      /* ignore */
    }
    setToken(null);
    setUser(null);
    storeUser(null);
  }, []);

  return <Ctx.Provider value={{ user, loading, login, logout, refreshMe }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  return useContext(Ctx);
}
