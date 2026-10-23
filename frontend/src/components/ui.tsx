// Hand-written UI primitives (no shadcn CLI).
import { useEffect, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { AlertTriangle, ChevronLeft, ChevronRight, Inbox, Loader2, X } from 'lucide-react';
import { errorMessage } from '../lib/api';

export function cx(...xs: (string | false | null | undefined)[]): string {
  return xs.filter(Boolean).join(' ');
}

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
const variants: Record<Variant, string> = {
  primary: 'bg-accent-600 text-white hover:bg-accent-700 focus-visible:ring-accent-500 disabled:bg-accent-400',
  secondary:
    'bg-white text-slate-800 ring-1 ring-inset ring-slate-300 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-100 dark:ring-slate-600 dark:hover:bg-slate-700',
  ghost: 'text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800',
  danger: 'bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-500 disabled:bg-red-400',
  success: 'bg-emerald-600 text-white hover:bg-emerald-700 focus-visible:ring-emerald-500 disabled:bg-emerald-400',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  icon,
  className,
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm' | 'md'; loading?: boolean; icon?: ReactNode }) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={cx(
        'inline-flex items-center justify-center gap-1.5 rounded-md font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-70',
        size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3.5 py-2 text-sm',
        variants[variant],
        className,
      )}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : icon}
      {children}
    </button>
  );
}

export function LinkButton({ href, children, variant = 'secondary', size = 'md', icon, target, className }: {
  href: string; children: ReactNode; variant?: Variant; size?: 'sm' | 'md'; icon?: ReactNode; target?: string; className?: string;
}) {
  return (
    <a
      href={href}
      target={target}
      rel={target === '_blank' ? 'noreferrer' : undefined}
      className={cx(
        'inline-flex items-center justify-center gap-1.5 rounded-md font-medium transition-colors',
        size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3.5 py-2 text-sm',
        variants[variant],
        className,
      )}
    >
      {icon}
      {children}
    </a>
  );
}

export function Card({ title, actions, children, className, bodyClassName, subtitle }: {
  title?: ReactNode; subtitle?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string; bodyClassName?: string;
}) {
  return (
    <section className={cx('rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900', className)}>
      {(title || actions) && (
        <header className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 py-3 dark:border-slate-800">
          <div className="min-w-0">
            {title && <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{subtitle}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={cx('p-4', bodyClassName)}>{children}</div>
    </section>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

const tone: Record<string, string> = {
  slate: 'bg-slate-100 text-slate-700 ring-slate-300 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-600',
  accent: 'bg-accent-50 text-accent-700 ring-accent-200 dark:bg-accent-900/40 dark:text-accent-200 dark:ring-accent-700',
  blue: 'bg-sky-50 text-sky-700 ring-sky-200 dark:bg-sky-900/40 dark:text-sky-200 dark:ring-sky-700',
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-900/40 dark:text-emerald-200 dark:ring-emerald-700',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200 dark:bg-amber-900/40 dark:text-amber-200 dark:ring-amber-700',
  orange: 'bg-orange-50 text-orange-700 ring-orange-200 dark:bg-orange-900/40 dark:text-orange-200 dark:ring-orange-700',
  red: 'bg-red-50 text-red-700 ring-red-200 dark:bg-red-900/40 dark:text-red-200 dark:ring-red-700',
  crit: 'bg-red-600 text-white ring-red-700',
};

export function Badge({ children, color = 'slate', className, title }: { children: ReactNode; color?: keyof typeof tone | string; className?: string; title?: string }) {
  return (
    <span title={title} className={cx('inline-flex items-center gap-1 whitespace-nowrap rounded-md px-1.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset', tone[color] ?? tone.slate, className)}>
      {children}
    </span>
  );
}

const sevColor: Record<string, string> = {
  INFO: 'blue', LOW: 'green', WARN: 'amber', MEDIUM: 'amber', ERROR: 'orange', HIGH: 'orange', CRITICAL: 'crit',
};
export function SeverityBadge({ severity }: { severity?: string }) {
  if (!severity) return null;
  return <Badge color={sevColor[severity] ?? 'slate'}>{severity}</Badge>;
}

const statusColor: Record<string, string> = {
  ACCEPTED: 'green', VALIDATED: 'green', VERIFIED: 'green', COMPLETED: 'green', RESOLVED: 'green', VALID: 'green', APPROVED: 'green', ACTIVE: 'green', DISMISSED: 'slate',
  FLAGGED: 'amber', WARN: 'amber', ACKNOWLEDGED: 'blue', UNDER_REVIEW: 'blue', PROCESSING: 'blue', QUEUED: 'slate', SUBMITTED: 'slate', PENDING: 'amber',
  REJECTED: 'red', FAILED: 'red', REVOKED: 'red', EXPIRED: 'orange', CONFIRMED: 'red', DENIED: 'red', OPEN: 'orange', DISABLED: 'slate', LOCKED: 'red',
  WAIVED: 'slate', WITHDRAWN: 'slate', TRANSFERRED: 'accent', GRADUATED: 'accent', RESUBMITTED: 'accent',
  REGULAR: 'slate', SUPERNUMERARY: 'accent', EXCESS: 'red',
};
export function StatusBadge({ status }: { status?: string }) {
  if (!status) return null;
  return <Badge color={statusColor[status] ?? 'slate'}>{status.replace(/_/g, ' ')}</Badge>;
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse rounded-md bg-slate-200 dark:bg-slate-800', className)} />;
}

export function SkeletonRows({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex gap-3">
          {Array.from({ length: cols }).map((__, j) => (
            <Skeleton key={j} className="h-5 flex-1" />
          ))}
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ title, hint, icon, action }: { title: string; hint?: ReactNode; icon?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-4 py-10 text-center">
      <div className="rounded-full bg-slate-100 p-3 text-slate-400 dark:bg-slate-800">{icon ?? <Inbox className="h-6 w-6" />}</div>
      <div className="text-sm font-medium text-slate-700 dark:text-slate-200">{title}</div>
      {hint && <div className="max-w-md text-sm text-slate-500 dark:text-slate-400">{hint}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/50 dark:text-red-200">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="flex-1">
        <div className="font-medium">Could not load data</div>
        <div className="mt-0.5 opacity-90">{errorMessage(error)}</div>
      </div>
      {onRetry && (
        <Button size="sm" variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
}

/** Render loading / error / empty boilerplate around a query. */
export function QueryState<T>({ q, children, empty, skeleton }: {
  q: { isLoading: boolean; isError: boolean; error: unknown; data: T | undefined; refetch: () => unknown };
  children: (data: T) => ReactNode;
  empty?: { when: (d: T) => boolean; title: string; hint?: ReactNode; action?: ReactNode };
  skeleton?: ReactNode;
}) {
  if (q.isLoading) return <>{skeleton ?? <SkeletonRows />}</>;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  if (q.data === undefined) return null;
  if (empty && empty.when(q.data)) return <EmptyState title={empty.title} hint={empty.hint} action={empty.action} />;
  return <>{children(q.data)}</>;
}

export function Table({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cx('overflow-x-auto', className)}>
      <table className="min-w-full text-left text-sm">{children}</table>
    </div>
  );
}
export function Th({ children, className, right }: { children?: ReactNode; className?: string; right?: boolean }) {
  return (
    <th className={cx('whitespace-nowrap border-b border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:border-slate-800 dark:bg-slate-800/50 dark:text-slate-400', right && 'text-right', className)}>
      {children}
    </th>
  );
}
export function Td({ children, className, right, mono, colSpan, title }: { children?: ReactNode; className?: string; right?: boolean; mono?: boolean; colSpan?: number; title?: string }) {
  return (
    <td title={title} colSpan={colSpan} className={cx('border-b border-slate-100 px-3 py-2 align-top text-slate-700 dark:border-slate-800 dark:text-slate-300', right && 'text-right tabular-nums', mono && 'font-mono text-xs', className)}>
      {children}
    </td>
  );
}
export function Tr({ children, onClick, className }: { children: ReactNode; onClick?: () => void; className?: string }) {
  return (
    <tr onClick={onClick} className={cx(onClick && 'cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/60', className)}>
      {children}
    </tr>
  );
}

export function Pagination({ page, totalPages, onPage, total }: { page: number; totalPages: number; onPage: (p: number) => void; total?: number }) {
  if (totalPages <= 1 && total === undefined) return null;
  return (
    <div className="flex items-center justify-between gap-2 px-1 pt-3 text-xs text-slate-500">
      <span>{total !== undefined ? `${total.toLocaleString('en-IN')} total` : ''}</span>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => onPage(page - 1)} icon={<ChevronLeft className="h-3.5 w-3.5" />}>
          Prev
        </Button>
        <span>
          Page {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <Button size="sm" variant="secondary" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
          Next <ChevronRight className="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>
  );
}

function useEscape(open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, onClose]);
}

export function Drawer({ open, onClose, title, subtitle, children, footer, width = 'max-w-xl' }: {
  open: boolean; onClose: () => void; title: ReactNode; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode; width?: string;
}) {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex justify-end" role="dialog" aria-modal="true">
      <div className="absolute inset-0 animate-fadein bg-slate-900/40" onClick={onClose} />
      <div className={cx('relative flex h-full w-full animate-slidein flex-col bg-white shadow-2xl dark:bg-slate-900', width)}>
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3 dark:border-slate-800">
          <div>
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">{title}</h2>
            {subtitle && <p className="text-xs text-slate-500 dark:text-slate-400">{subtitle}</p>}
          </div>
          <button onClick={onClose} className="rounded p-1 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Close">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-5">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-slate-200 px-5 py-3 dark:border-slate-800">{footer}</div>}
      </div>
    </div>
  );
}

export function Modal({ open, onClose, title, children, footer }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode; footer?: ReactNode }) {
  useEscape(open, onClose);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0 animate-fadein bg-slate-900/50" onClick={onClose} />
      <div className="relative w-full max-w-lg animate-fadein rounded-xl bg-white shadow-2xl dark:bg-slate-900">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3 dark:border-slate-800">
          <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">{title}</h2>
          <button onClick={onClose} className="rounded p-1 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Close">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="max-h-[70vh] overflow-y-auto p-5">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-slate-200 px-5 py-3 dark:border-slate-800">{footer}</div>}
      </div>
    </div>
  );
}

export function Tabs<T extends string>({ tabs, value, onChange }: { tabs: { value: T; label: ReactNode }[]; value: T; onChange: (v: T) => void }) {
  return (
    <div className="mb-4 flex gap-1 overflow-x-auto border-b border-slate-200 dark:border-slate-800" role="tablist">
      {tabs.map((t) => (
        <button
          key={t.value}
          role="tab"
          aria-selected={value === t.value}
          onClick={() => onChange(t.value)}
          className={cx(
            '-mb-px whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium transition-colors',
            value === t.value
              ? 'border-accent-600 text-accent-700 dark:text-accent-300'
              : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
          )}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

const inputCls =
  'block w-full rounded-md border-0 bg-white px-3 py-2 text-sm text-slate-900 ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-accent-500 dark:bg-slate-800 dark:text-slate-100 dark:ring-slate-700';

export function Input({ label, className, ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: ReactNode }) {
  if (label) {
    return (
      <label className="block">
        <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-400">{label}</span>
        <input {...props} className={cx(inputCls, className)} />
      </label>
    );
  }
  return <input {...props} className={cx(inputCls, className)} />;
}
export function Textarea({ label, className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: ReactNode }) {
  if (label) {
    return (
      <label className="block">
        <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-400">{label}</span>
        <textarea {...props} className={cx(inputCls, className)} />
      </label>
    );
  }
  return <textarea {...props} className={cx(inputCls, className)} />;
}
export function Select({ children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...props} className={cx(inputCls, 'pr-8', props.className)}>
      {children}
    </select>
  );
}
export function Field({ label, children, hint, className }: { label: ReactNode; children: ReactNode; hint?: ReactNode; className?: string }) {
  return (
    <label className={cx('block', className)}>
      <span className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-400">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}

export function KpiCard({ label, value, sub, tone: t = 'default', icon, loading }: {
  label: string; value: ReactNode; sub?: ReactNode; tone?: 'default' | 'danger' | 'warn' | 'good'; icon?: ReactNode; loading?: boolean;
}) {
  const ring = { default: '', danger: 'border-l-4 border-l-red-500', warn: 'border-l-4 border-l-amber-500', good: 'border-l-4 border-l-emerald-500' }[t];
  return (
    <div className={cx('rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900', ring)}>
      <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
        <span>{label}</span>
        <span className="text-slate-400">{icon}</span>
      </div>
      {loading ? <Skeleton className="mt-2 h-7 w-24" /> : <div className="mt-1 text-2xl font-semibold tabular-nums text-slate-900 dark:text-slate-50">{value}</div>}
      {sub && <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">{sub}</div>}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cx('h-4 w-4 animate-spin', className)} />;
}

export function ProgressBar({ value, max, className, color = 'bg-accent-600' }: { value: number; max: number; className?: string; color?: string }) {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0;
  return (
    <div className={cx('h-2.5 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800', className)}>
      <div className={cx('h-full rounded-full transition-all duration-500', color)} style={{ width: `${pct}%` }} />
    </div>
  );
}

export function DescList({ items }: { items: [ReactNode, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-4 gap-y-2 text-sm sm:grid-cols-2">
      {items.map(([k, v], i) => (
        <div key={i} className="min-w-0">
          <dt className="text-xs text-slate-500 dark:text-slate-400">{k}</dt>
          <dd className="truncate font-medium text-slate-800 dark:text-slate-200">{v ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
