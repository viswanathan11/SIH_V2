import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { AlertOctagon, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';

export type ToastKind = 'success' | 'info' | 'warn' | 'error' | 'critical';

export interface ToastItem {
  id: number;
  kind: ToastKind;
  title: string;
  body?: string;
}

interface ToastCtx {
  push: (t: Omit<ToastItem, 'id'>, ttlMs?: number) => void;
}

const Ctx = createContext<ToastCtx>({ push: () => {} });

const styles: Record<ToastKind, string> = {
  success: 'border-emerald-500/60 bg-white dark:bg-slate-900',
  info: 'border-accent-500/60 bg-white dark:bg-slate-900',
  warn: 'border-amber-500/70 bg-white dark:bg-slate-900',
  error: 'border-red-500/70 bg-white dark:bg-slate-900',
  critical: 'border-red-600 bg-red-50 dark:bg-red-950',
};

function Icon({ kind }: { kind: ToastKind }) {
  const cls = 'h-5 w-5 shrink-0';
  switch (kind) {
    case 'success':
      return <CheckCircle2 className={`${cls} text-emerald-600`} />;
    case 'warn':
      return <AlertTriangle className={`${cls} text-amber-600`} />;
    case 'error':
      return <AlertTriangle className={`${cls} text-red-600`} />;
    case 'critical':
      return <AlertOctagon className={`${cls} text-red-700`} />;
    default:
      return <Info className={`${cls} text-accent-600`} />;
  }
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const seq = useRef(1);

  const dismiss = useCallback((id: number) => setItems((xs) => xs.filter((x) => x.id !== id)), []);

  const push = useCallback(
    (t: Omit<ToastItem, 'id'>, ttlMs?: number) => {
      const id = seq.current++;
      setItems((xs) => [...xs.slice(-4), { ...t, id }]);
      const ttl = ttlMs ?? (t.kind === 'critical' ? 10000 : 5000);
      setTimeout(() => dismiss(id), ttl);
    },
    [dismiss],
  );

  return (
    <Ctx.Provider value={{ push }}>
      {children}
      <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[22rem] max-w-[calc(100vw-2rem)] flex-col gap-2" aria-live="polite">
        {items.map((t) => (
          <div
            key={t.id}
            role={t.kind === 'critical' || t.kind === 'error' ? 'alert' : 'status'}
            className={`pointer-events-auto flex animate-toastin gap-3 rounded-lg border-l-4 p-3 shadow-lg ring-1 ring-slate-900/5 dark:ring-white/10 ${styles[t.kind]}`}
          >
            <Icon kind={t.kind} />
            <div className="min-w-0 flex-1">
              <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t.title}</div>
              {t.body && <div className="mt-0.5 text-sm text-slate-600 dark:text-slate-300">{t.body}</div>}
            </div>
            <button
              onClick={() => dismiss(t.id)}
              className="self-start rounded p-0.5 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200"
              aria-label="Dismiss"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export function ToastContainer() {
  return null;
}

export function useToast() {
  return useContext(Ctx);
}
