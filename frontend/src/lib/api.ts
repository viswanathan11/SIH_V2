// Thin fetch wrapper: bearer token in memory + sessionStorage, single refresh retry on 401.
import type { AuthResponse } from './types';

export const API_BASE = '/api/v1';
const TOKEN_KEY = 'vs_access_token';

let accessToken: string | null = null;
try {
  accessToken = sessionStorage.getItem(TOKEN_KEY);
} catch {
  /* sessionStorage unavailable (tests / SSR) */
}

type Listener = (token: string | null) => void;
const listeners = new Set<Listener>();

export function getToken(): string | null {
  return accessToken;
}

export function setToken(token: string | null) {
  accessToken = token;
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
  listeners.forEach((l) => l(token));
}

export function onTokenChange(l: Listener): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}

let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(h: () => void) {
  unauthorizedHandler = h;
}

export class ApiError extends Error {
  status: number;
  title?: string;
  detail?: string;
  body?: unknown;
  constructor(status: number, message: string, body?: any) {
    super(message);
    this.status = status;
    this.body = body;
    this.title = body?.title;
    this.detail = body?.detail;
  }
}

let refreshing: Promise<boolean> | null = null;

/** Try POST /auth/refresh once (deduplicated across concurrent callers). */
export function tryRefresh(): Promise<boolean> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await fetch(`${API_BASE}/auth/refresh`, { method: 'POST', credentials: 'include' });
        if (!res.ok) return false;
        const data = (await res.json()) as AuthResponse;
        if (!data?.accessToken) return false;
        setToken(data.accessToken);
        return true;
      } catch {
        return false;
      } finally {
        setTimeout(() => (refreshing = null), 0);
      }
    })();
  }
  return refreshing;
}

export interface RequestOpts {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  /** Do not attempt refresh / redirect on 401 (used for auth endpoints & public calls). */
  noAuthRetry?: boolean;
  raw?: boolean;
  signal?: AbortSignal;
}

function buildUrl(path: string): string {
  if (path.startsWith('http') || path.startsWith('/api') || path.startsWith('/.well-known')) return path;
  return `${API_BASE}${path.startsWith('/') ? '' : '/'}${path}`;
}

export async function request<T = any>(path: string, opts: RequestOpts = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...(opts.headers || {}) };
  let body: BodyInit | undefined;
  if (opts.body instanceof FormData) {
    body = opts.body;
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(opts.body);
  }
  if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

  const res = await fetch(buildUrl(path), {
    method: opts.method || (body ? 'POST' : 'GET'),
    headers,
    body,
    credentials: 'include',
    signal: opts.signal,
  });

  if (res.status === 401 && !opts.noAuthRetry) {
    if (!retried && (await tryRefresh())) return request<T>(path, opts, true);
    setToken(null);
    unauthorizedHandler?.();
    throw new ApiError(401, 'Session expired — please sign in again');
  }

  if (!res.ok) {
    let payload: any = undefined;
    try {
      payload = await res.json();
    } catch {
      /* not json */
    }
    const msg = payload?.detail || payload?.title || payload?.message || `${res.status} ${res.statusText}`;
    throw new ApiError(res.status, msg, payload);
  }

  if (opts.raw) return res as unknown as T;
  if (res.status === 204) return undefined as T;
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('json')) return (await res.json()) as T;
  return (await res.text()) as unknown as T;
}

export const api = {
  get: <T = any>(path: string, opts?: RequestOpts) => request<T>(path, { ...opts, method: 'GET' }),
  post: <T = any>(path: string, body?: unknown, opts?: RequestOpts) =>
    request<T>(path, { ...opts, method: 'POST', body: body ?? {} }),
  put: <T = any>(path: string, body?: unknown, opts?: RequestOpts) => request<T>(path, { ...opts, method: 'PUT', body }),
  del: <T = any>(path: string, opts?: RequestOpts) => request<T>(path, { ...opts, method: 'DELETE' }),
};

/** Build a query string from an object, skipping empty values. */
export function qs(params: Record<string, string | number | boolean | undefined | null>): string {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  const s = p.toString();
  return s ? `?${s}` : '';
}

/** URL for direct browser navigation (PDF / CSV) carrying ?access_token=. */
export function withToken(path: string): string {
  const url = buildUrl(path);
  if (!accessToken) return url;
  return `${url}${url.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(accessToken)}`;
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return String(e);
}
