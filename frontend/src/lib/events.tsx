// Global real-time event stream: SSE via fetch-event-source, falls back to polling /events/recent every 5 s.
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { useQueryClient } from '@tanstack/react-query';
import { API_BASE, api, getToken, tryRefresh } from './api';
import { useAuth } from './auth';
import { useToast } from '../components/Toast';
import { invalidationKeys, mergeEvents, toastFor } from './eventsLogic';
import type { VsEvent } from './types';

export type StreamStatus = 'live' | 'polling' | 'off';

type Sub = (e: VsEvent) => void;

interface EventsCtx {
  events: VsEvent[];
  status: StreamStatus;
  connected: boolean;
  subscribe: (fn: Sub) => () => void;
}

const Ctx = createContext<EventsCtx>({ events: [], status: 'off', connected: false, subscribe: () => () => {} });

class FatalSseError extends Error {}

export function EventsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const { push } = useToast();
  const [events, setEvents] = useState<VsEvent[]>([]);
  const [status, setStatus] = useState<StreamStatus>('off');
  const subs = useRef(new Set<Sub>());
  const seen = useRef(new Set<string>());
  const pendingInvalidations = useRef(new Set<string>());
  const flushTimer = useRef<number | null>(null);

  const subscribe = useCallback((fn: Sub) => {
    subs.current.add(fn);
    return () => {
      subs.current.delete(fn);
    };
  }, []);

  const scheduleInvalidate = useCallback(
    (keys: string[]) => {
      keys.forEach((k) => pendingInvalidations.current.add(k));
      if (flushTimer.current) return;
      flushTimer.current = window.setTimeout(() => {
        const ks = Array.from(pendingInvalidations.current);
        pendingInvalidations.current.clear();
        flushTimer.current = null;
        ks.forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
      }, 800);
    },
    [qc],
  );

  const handle = useCallback(
    (e: VsEvent, quiet = false) => {
      if (!e || !e.type || e.type === 'HEARTBEAT') return;
      const id = e.id || `${e.type}-${e.ts}-${e.message}`;
      if (seen.current.has(id)) return;
      seen.current.add(id);
      const ev = { ...e, id };
      subs.current.forEach((fn) => fn(ev));
      if (ev.type !== 'VALIDATION_PROGRESS') {
        setEvents((xs) => mergeEvents(xs, [ev]));
      }
      if (!quiet) {
        const t = toastFor(ev);
        if (t) push(t);
      }
      scheduleInvalidate(invalidationKeys(ev.type));
    },
    [push, scheduleInvalidate],
  );

  useEffect(() => {
    if (!user) {
      setStatus('off');
      setEvents([]);
      seen.current.clear();
      return;
    }
    let disposed = false;
    const ctrl = new AbortController();
    let pollTimer: number | null = null;
    let retrySseTimer: number | null = null;

    const poll = async (initial = false) => {
      try {
        const list = await api.get<VsEvent[]>('/events/recent');
        if (disposed) return;
        const sorted = [...(list || [])].sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime());
        sorted.forEach((e) => handle(e, initial));
      } catch {
        /* ignore */
      }
    };

    const startPolling = () => {
      if (disposed || pollTimer) return;
      setStatus('polling');
      pollTimer = window.setInterval(() => poll(false), 5000);
      // try SSE again later
      retrySseTimer = window.setTimeout(() => {
        if (disposed) return;
        if (pollTimer) window.clearInterval(pollTimer);
        pollTimer = null;
        startSse();
      }, 60000);
    };

    const startSse = () => {
      if (disposed) return;
      let refreshedOnce = false;
      fetchEventSource(`${API_BASE}/stream/events`, {
        signal: ctrl.signal,
        openWhenHidden: true,
        headers: { Authorization: `Bearer ${getToken() ?? ''}`, Accept: 'text/event-stream' },
        async onopen(res) {
          const ct = res.headers.get('content-type') || '';
          if (res.ok && ct.includes('text/event-stream')) {
            setStatus('live');
            return;
          }
          if (res.status === 401 && !refreshedOnce) {
            refreshedOnce = true;
            await tryRefresh();
          }
          throw new FatalSseError(`SSE open failed: ${res.status}`);
        },
        onmessage(msg) {
          if (!msg.data) return;
          try {
            const data = JSON.parse(msg.data) as VsEvent;
            if (!data.type && msg.event) data.type = msg.event;
            handle(data);
          } catch {
            /* ignore malformed */
          }
        },
        onclose() {
          // server closed: fall back to polling (and retry SSE later)
          throw new FatalSseError('closed');
        },
        onerror(err) {
          throw err instanceof FatalSseError ? err : new FatalSseError(String(err));
        },
      }).catch(() => {
        if (!disposed) startPolling();
      });
    };

    poll(true).then(startSse);

    return () => {
      disposed = true;
      ctrl.abort();
      if (pollTimer) window.clearInterval(pollTimer);
      if (retrySseTimer) window.clearTimeout(retrySseTimer);
    };
  }, [user?.id, handle]); // eslint-disable-line react-hooks/exhaustive-deps

  const connected = status === 'live' || status === 'polling';
  return <Ctx.Provider value={{ events, status, connected, subscribe }}>{children}</Ctx.Provider>;
}

export function useEvents() {
  return useContext(Ctx);
}

/** Subscribe to raw events (including VALIDATION_PROGRESS) for the lifetime of a component. */
export function useEventSubscription(fn: Sub) {
  const { subscribe } = useEvents();
  const ref = useRef(fn);
  ref.current = fn;
  useEffect(() => subscribe((e) => ref.current(e)), [subscribe]);
}
