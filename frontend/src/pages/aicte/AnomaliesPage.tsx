import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ShieldAlert, Sparkles, RefreshCw, CheckCircle, XCircle,
  HelpCircle, Eye, ArrowUpRight
} from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { canReviewAnomaly } from '../../lib/roles';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  StatusBadge, Drawer, Textarea, Select
} from '../../components/ui';
import type { Anomaly } from '../../lib/types';

export default function AnomaliesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [selectedAnomaly, setSelectedAnomaly] = useState<Anomaly | null>(null);
  const [reviewNote, setReviewNote] = useState('');
  const [filterSeverity, setFilterSeverity] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  const { data: anomalies, isLoading } = useQuery({
    queryKey: ['anomalies', filterStatus, filterSeverity],
    queryFn: () => {
      const q = new URLSearchParams();
      if (filterStatus) q.set('status', filterStatus);
      if (filterSeverity) q.set('severity', filterSeverity);
      return api.get<Anomaly[]>(`/anomalies?${q.toString()}`);
    },
  });

  const recomputeMutation = useMutation({
    mutationFn: () => api.post('/anomalies/recompute', {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['anomalies'] });
      queryClient.invalidateQueries({ queryKey: ['national-summary'] });
    },
  });

  const reviewMutation = useMutation({
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) =>
      api.post(`/anomalies/${id}/review`, { status, note }),
    onSuccess: (updated: Anomaly) => {
      queryClient.invalidateQueries({ queryKey: ['anomalies'] });
      setSelectedAnomaly(updated);
    },
  });

  const canReview = canReviewAnomaly(user);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Intelligent Anomaly Detection & Risk Watchlist"
        subtitle="Unsupervised Isolation Forest + robust statistics + rule detectors identifying fraud and capacity breaches"
        actions={
          canReview && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => recomputeMutation.mutate()}
              loading={recomputeMutation.isPending}
              icon={<RefreshCw className="h-4 w-4" />}
            >
              Recompute Engine
            </Button>
          )
        }
      />

      <Card>
        {/* Filters */}
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div className="flex items-center gap-2">
            <Select
              value={filterSeverity}
              onChange={(e) => setFilterSeverity(e.target.value)}
              className="text-xs w-36"
            >
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
            </Select>

            <Select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
              className="text-xs w-36"
            >
              <option value="">All Statuses</option>
              <option value="OPEN">Open</option>
              <option value="UNDER_REVIEW">Under Review</option>
              <option value="CONFIRMED">Confirmed</option>
              <option value="DISMISSED">Dismissed</option>
            </Select>
          </div>

          <span className="text-xs text-slate-500">{anomalies?.length ?? 0} anomalies detected</span>
        </div>

        <Table>
          <thead>
            <tr>
              <Th>Institution</Th>
              <Th>Anomaly Kind</Th>
              <Th>Severity</Th>
              <Th right>Anomaly Score</Th>
              <Th>Status</Th>
              <Th>Explanation</Th>
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {!anomalies || anomalies.length === 0 ? (
              <tr>
                <Td colSpan={7} className="text-center py-8 text-slate-400">
                  No anomalies flagged for current filter.
                </Td>
              </tr>
            ) : (
              anomalies.map((a) => (
                <Tr key={a.id} onClick={() => { setSelectedAnomaly(a); setReviewNote(a.reviewNote || ''); }}>
                  <Td>
                    <div className="font-semibold text-slate-800 dark:text-slate-200">{a.institutionName}</div>
                    <div className="text-[10px] text-slate-400 font-mono">{a.institutionCode} {a.programCode && `• ${a.programCode}`}</div>
                  </Td>
                  <Td>
                    <span className="font-mono text-xs">{a.kind}</span>
                  </Td>
                  <Td>
                    <SeverityBadge severity={a.severity} />
                  </Td>
                  <Td right className="font-mono font-semibold">
                    {Math.round(a.score * 100)}/100
                  </Td>
                  <Td>
                    <StatusBadge status={a.status} />
                  </Td>
                  <Td className="max-w-md truncate text-xs text-slate-600 dark:text-slate-300">
                    {a.explanation}
                  </Td>
                  <Td>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={(e) => { e.stopPropagation(); setSelectedAnomaly(a); setReviewNote(a.reviewNote || ''); }}
                      icon={<Eye className="h-3.5 w-3.5" />}
                    >
                      Explain
                    </Button>
                  </Td>
                </Tr>
              ))
            )}
          </tbody>
        </Table>
      </Card>

      {/* Anomaly Explain Drawer */}
      {selectedAnomaly && (
        <Drawer
          open={!!selectedAnomaly}
          onClose={() => setSelectedAnomaly(null)}
          title={`Anomaly Explanation — ${selectedAnomaly.kind}`}
          subtitle={`${selectedAnomaly.institutionName} (${selectedAnomaly.institutionCode})`}
        >
          <div className="space-y-5">
            {/* Score & Severity banner */}
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700">
              <div>
                <div className="text-[11px] text-slate-500 font-medium">Model Outlier Score</div>
                <div className="text-2xl font-bold font-mono text-slate-900 dark:text-slate-100">
                  {Math.round(selectedAnomaly.score * 100)} / 100
                </div>
              </div>
              <div className="text-right">
                <SeverityBadge severity={selectedAnomaly.severity} />
                <div className="mt-1">
                  <StatusBadge status={selectedAnomaly.status} />
                </div>
              </div>
            </div>

            {/* Explanation box */}
            <div className="p-3.5 rounded-lg bg-indigo-50/70 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-800 text-xs">
              <div className="flex items-center gap-1.5 font-semibold text-indigo-900 dark:text-indigo-200 mb-1">
                <Sparkles className="h-3.5 w-3.5 text-indigo-600" />
                <span>Deterministic Model Diagnostic</span>
              </div>
              <p className="text-indigo-800 dark:text-indigo-300 leading-relaxed">
                {selectedAnomaly.explanation}
              </p>
            </div>

            {/* Contributing features list */}
            <div>
              <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2">
                Contributing Factor Details
              </h4>
              <div className="space-y-2">
                {selectedAnomaly.reasons?.map((r, i) => (
                  <div key={i} className="p-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-xs">
                    <div className="flex items-center justify-between font-medium">
                      <span className="font-mono text-indigo-600 dark:text-indigo-400">{r.feature}</span>
                      <span className="font-bold">{String(r.value)}</span>
                    </div>
                    {r.text && <div className="text-slate-500 mt-1">{r.text}</div>}
                  </div>
                ))}
              </div>
            </div>

            {/* Review Workflow Form */}
            {canReview && (
              <div className="pt-4 border-t border-slate-200 dark:border-slate-800 space-y-3">
                <h4 className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                  AICTE Regulatory Action & Audit Note
                </h4>
                <Textarea
                  placeholder="Record formal review findings or resolution rationale..."
                  value={reviewNote}
                  onChange={(e) => setReviewNote(e.target.value)}
                  rows={3}
                />
                <div className="flex flex-wrap gap-2 pt-1">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => reviewMutation.mutate({ id: selectedAnomaly.id, status: 'UNDER_REVIEW', note: reviewNote })}
                    loading={reviewMutation.isPending}
                  >
                    Mark Under Review
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => reviewMutation.mutate({ id: selectedAnomaly.id, status: 'CONFIRMED', note: reviewNote })}
                    loading={reviewMutation.isPending}
                    icon={<CheckCircle className="h-3.5 w-3.5" />}
                  >
                    Confirm Anomaly
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => reviewMutation.mutate({ id: selectedAnomaly.id, status: 'DISMISSED', note: reviewNote })}
                    loading={reviewMutation.isPending}
                    icon={<XCircle className="h-3.5 w-3.5" />}
                  >
                    Dismiss
                  </Button>
                </div>
              </div>
            )}
          </div>
        </Drawer>
      )}
    </div>
  );
}
