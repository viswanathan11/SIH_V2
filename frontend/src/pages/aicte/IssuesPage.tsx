import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle, Copy, CheckCircle2, ShieldCheck, Filter,
  ArrowRightLeft, FileText, Check
} from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { canWaive, canResolve } from '../../lib/roles';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  StatusBadge, Modal, Textarea, Select, Input
} from '../../components/ui';
import type { Issue, DuplicateGroup } from '../../lib/types';

export default function IssuesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'issues' | 'duplicates'>('issues');
  const [filterSeverity, setFilterSeverity] = useState('');
  const [filterStatus, setFilterStatus] = useState('OPEN');
  const [waiveIssue, setWaiveIssue] = useState<Issue | null>(null);
  const [waiveNote, setWaiveNote] = useState('');
  const [transferModal, setTransferModal] = useState<{ apaar: string; enrolments: any[] } | null>(null);

  // Issues list
  const { data: issuesPage, isLoading: issuesLoading } = useQuery({
    queryKey: ['issues', filterSeverity, filterStatus],
    queryFn: () => {
      const q = new URLSearchParams();
      if (filterSeverity) q.set('severity', filterSeverity);
      if (filterStatus) q.set('status', filterStatus);
      q.set('size', '50');
      return api.get<{ content: Issue[]; totalElements: number }>(`/issues?${q.toString()}`);
    },
  });

  // Duplicate register
  const { data: duplicates } = useQuery({
    queryKey: ['duplicate-register'],
    queryFn: () => api.get<DuplicateGroup[]>('/issues/duplicates'),
  });

  const resolveMutation = useMutation({
    mutationFn: (id: string) => api.post(`/issues/${id}/resolve`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['issues'] }),
  });

  const waiveMutation = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) =>
      api.post(`/issues/${id}/waive`, { note }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      setWaiveIssue(null);
      setWaiveNote('');
    },
  });

  const transferMutation = useMutation({
    mutationFn: ({ enrolmentId, note }: { enrolmentId: string; note: string }) =>
      api.post(`/enrolments/${enrolmentId}/status`, { status: 'TRANSFERRED', note }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['duplicate-register'] });
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      setTransferModal(null);
    },
  });

  const allowWaive = canWaive(user);
  const allowResolve = canResolve(user);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Validation & Regulatory Issue Register"
        subtitle="Verification rules, seat cap breaches & cross-institution duplicate enrolments"
      />

      {/* Tabs */}
      <div className="flex border-b border-slate-200 dark:border-slate-800">
        <button
          onClick={() => setActiveTab('issues')}
          className={`pb-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-2 transition-colors ${
            activeTab === 'issues'
              ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <AlertTriangle className="h-4 w-4" />
          <span>Validation Issues ({issuesPage?.totalElements ?? 0})</span>
        </button>
        <button
          onClick={() => setActiveTab('duplicates')}
          className={`pb-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-2 transition-colors ${
            activeTab === 'duplicates'
              ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <Copy className="h-4 w-4" />
          <span>Cross-Institution Duplicates ({duplicates?.length ?? 0})</span>
        </button>
      </div>

      {activeTab === 'issues' ? (
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
                <option value="ERROR">Error</option>
                <option value="WARN">Warning</option>
                <option value="INFO">Info</option>
              </Select>

              <Select
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
                className="text-xs w-36"
              >
                <option value="">All Statuses</option>
                <option value="OPEN">Open</option>
                <option value="ACKNOWLEDGED">Acknowledged</option>
                <option value="RESOLVED">Resolved</option>
                <option value="WAIVED">Waived</option>
              </Select>
            </div>
            <span className="text-xs text-slate-500">{issuesPage?.totalElements ?? 0} total records</span>
          </div>

          <Table>
            <thead>
              <tr>
                <Th>Rule</Th>
                <Th>Severity</Th>
                <Th>Institution</Th>
                <Th>Field</Th>
                <Th>Issue Message & Regulatory Hint</Th>
                <Th>Status</Th>
                <Th>Actions</Th>
              </tr>
            </thead>
            <tbody>
              {!issuesPage?.content || issuesPage.content.length === 0 ? (
                <tr>
                  <Td colSpan={7} className="text-center py-8 text-slate-400">
                    No issues matching criteria.
                  </Td>
                </tr>
              ) : (
                issuesPage.content.map((iss) => (
                  <Tr key={iss.id}>
                    <Td mono className="font-semibold text-indigo-600 dark:text-indigo-400">
                      {iss.ruleCode}
                    </Td>
                    <Td>
                      <SeverityBadge severity={iss.severity} />
                    </Td>
                    <Td>
                      <span className="font-mono text-xs">{iss.institutionCode}</span>
                    </Td>
                    <Td mono>{iss.field || 'record'}</Td>
                    <Td className="max-w-md">
                      <div className="text-xs font-medium text-slate-800 dark:text-slate-200">{iss.message}</div>
                      {iss.hint && <div className="text-[11px] text-slate-400 mt-0.5">{iss.hint}</div>}
                    </Td>
                    <Td>
                      <StatusBadge status={iss.status} />
                    </Td>
                    <Td>
                      <div className="flex items-center gap-1.5">
                        {allowResolve && iss.status === 'OPEN' && (
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => resolveMutation.mutate(iss.id)}
                            loading={resolveMutation.isPending}
                          >
                            Resolve
                          </Button>
                        )}
                        {allowWaive && iss.status === 'OPEN' && (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setWaiveIssue(iss)}
                          >
                            Waive
                          </Button>
                        )}
                      </div>
                    </Td>
                  </Tr>
                ))
              )}
            </tbody>
          </Table>
        </Card>
      ) : (
        /* Cross-institution duplicate register */
        <Card title="Concurrent Active Enrolments Across Multiple Institutions">
          <p className="text-xs text-slate-500 mb-4">
            Under AICTE guidelines, students may not hold concurrent regular enrolments in two institutions. Review and record approved transfer migrations.
          </p>

          <div className="space-y-4">
            {!duplicates || duplicates.length === 0 ? (
              <div className="text-center py-12 text-slate-400 text-xs">
                No active cross-institution duplicates detected.
              </div>
            ) : (
              duplicates.map((dup, idx) => (
                <div key={idx} className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-xs">
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <div className="font-bold text-sm text-slate-900 dark:text-slate-100 flex items-center gap-2">
                        <span>{dup.studentName}</span>
                        <Badge color="red">Dual Enrolment Breach</Badge>
                      </div>
                      <div className="text-xs font-mono text-slate-500 mt-0.5">APAAR: {dup.apaarMasked}</div>
                    </div>

                    <Button
                      size="sm"
                      variant="primary"
                      onClick={() => setTransferModal({ apaar: dup.apaarId || dup.apaarMasked, enrolments: dup.enrolments })}
                      icon={<ArrowRightLeft className="h-3.5 w-3.5" />}
                    >
                      Resolve Transfer
                    </Button>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-2">
                    {dup.enrolments.map((enr: any, i: number) => (
                      <div key={i} className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 text-xs">
                        <div className="font-semibold text-slate-800 dark:text-slate-200">{enr.institutionName}</div>
                        <div className="text-[11px] text-slate-500 font-mono mt-0.5">{enr.institutionCode} • {enr.programCode}</div>
                        <div className="flex items-center justify-between mt-2 pt-2 border-t border-slate-200 dark:border-slate-700">
                          <span className="text-slate-500">Admitted: {enr.admissionDate}</span>
                          <StatusBadge status={enr.status} />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        </Card>
      )}

      {/* Waive Issue Modal */}
      {waiveIssue && (
        <Modal
          open={!!waiveIssue}
          onClose={() => setWaiveIssue(null)}
          title={`Waive Validation Issue — ${waiveIssue.ruleCode}`}
        >
          <div className="space-y-4">
            <p className="text-xs text-slate-500">
              Regulatory waiver removes the discrepancy from institutional risk scoring. A formal justification must be entered for the tamper-evident audit log.
            </p>
            <Textarea
              label="Waiver Rationale / AICTE Order Reference"
              required
              rows={3}
              value={waiveNote}
              onChange={(e) => setWaiveNote(e.target.value)}
              placeholder="e.g. AICTE Executive Committee special approval order #2026/TN/098 dated 15-Aug-2026"
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setWaiveIssue(null)}>Cancel</Button>
              <Button
                variant="primary"
                onClick={() => waiveMutation.mutate({ id: waiveIssue.id, note: waiveNote })}
                loading={waiveMutation.isPending}
                disabled={!waiveNote.trim()}
              >
                Record Waiver
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Legitimate Transfer Resolution Modal */}
      {transferModal && (
        <Modal
          open={!!transferModal}
          onClose={() => setTransferModal(null)}
          title="Resolve as Legitimate Transfer Migration"
        >
          <div className="space-y-4">
            <p className="text-xs text-slate-500">
              Select which previous institution the student transferred FROM. The selected enrolment will be marked as TRANSFERRED, resolving the dual-enrolment conflict.
            </p>

            <div className="space-y-2">
              {transferModal.enrolments.map((enr: any, i: number) => (
                <div
                  key={i}
                  className="p-3 rounded-lg border border-slate-200 dark:border-slate-700 flex items-center justify-between text-xs"
                >
                  <div>
                    <div className="font-bold">{enr.institutionName}</div>
                    <div className="text-slate-400 font-mono">{enr.institutionCode} • {enr.programCode}</div>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => transferMutation.mutate({ enrolmentId: enr.enrolmentId, note: 'Approved university migration transfer' })}
                    loading={transferMutation.isPending}
                  >
                    Mark as Origin (Transferred)
                  </Button>
                </div>
              ))}
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
