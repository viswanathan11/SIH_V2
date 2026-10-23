import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Wrench, CheckCircle2, AlertTriangle, Edit3, ArrowRight, RefreshCw } from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  StatusBadge, Modal, Input
} from '../../components/ui';

export default function WorklistPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [editRow, setEditRow] = useState<any | null>(null);
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});

  // Fetch recent batches for this institution
  const { data: batches } = useQuery({
    queryKey: ['my-batches-worklist', user?.institutionId],
    queryFn: () => api.get<{ content: any[] }>(`/uploads?institutionId=${user?.institutionId}&size=5`),
    enabled: !!user?.institutionId,
  });

  const latestBatch = batches?.content?.[0];

  // Fetch rejected/flagged rows for latest batch
  const { data: rows, isLoading } = useQuery({
    queryKey: ['staging-rows', latestBatch?.id],
    queryFn: () => api.get<any[]>(`/uploads/${latestBatch.id}/rows`),
    enabled: !!latestBatch?.id,
  });

  const resubmitMutation = useMutation({
    mutationFn: ({ rowId, raw }: { rowId: string; raw: Record<string, string> }) =>
      api.post(`/uploads/rows/${rowId}/resubmit`, { raw }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staging-rows'] });
      queryClient.invalidateQueries({ queryKey: ['my-institution-meters'] });
      queryClient.invalidateQueries({ queryKey: ['my-institution-quality'] });
      setEditRow(null);
    },
  });

  const rejectedRows = (rows || []).filter((r) => r.outcome === 'REJECTED' || r.outcome === 'FLAGGED');

  const startEdit = (row: any) => {
    setEditRow(row);
    setFieldValues({ ...row.raw });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Remediation Worklist & Inline Correction"
        subtitle="Review rejected records, inspect failed validation rules, edit fields inline and resubmit directly into verified enrolments"
      />

      <Card
        title="Discrepancy Remediation Queue"
        subtitle={latestBatch ? `Active Batch: ${latestBatch.fileName} (${latestBatch.id.substring(0, 8)}...)` : 'No active batch'}
        actions={
          <Badge color={rejectedRows.length > 0 ? 'red' : 'green'}>
            {rejectedRows.length} Items Requiring Remediation
          </Badge>
        }
      >
        <Table>
          <thead>
            <tr>
              <Th>Line #</Th>
              <Th>APAAR ID</Th>
              <Th>Student Name</Th>
              <Th>Program</Th>
              <Th>Validation Error & Reason</Th>
              <Th>Outcome</Th>
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {rejectedRows.length === 0 ? (
              <tr>
                <Td colSpan={7} className="text-center py-12 text-slate-400">
                  Worklist clear! Zero rejected rows pending remediation.
                </Td>
              </tr>
            ) : (
              rejectedRows.map((r) => {
                const firstIssue = r.issues?.[0];
                return (
                  <Tr key={r.id}>
                    <Td mono className="font-bold">Row {r.rowNo}</Td>
                    <Td mono className="text-xs">
                      {r.raw?.apaar_id || <span className="text-red-500 font-semibold">[MISSING]</span>}
                    </Td>
                    <Td className="font-medium text-xs">
                      {r.raw?.full_name || '-'}
                    </Td>
                    <Td mono className="text-xs">{r.raw?.program_code}</Td>
                    <Td className="max-w-md">
                      {firstIssue ? (
                        <div>
                          <div className="font-semibold text-xs text-red-600 dark:text-red-400 flex items-center gap-1">
                            <span className="font-mono">{firstIssue.ruleCode}</span>: {firstIssue.message}
                          </div>
                          {firstIssue.hint && (
                            <div className="text-[11px] text-slate-400 mt-0.5">Hint: {firstIssue.hint}</div>
                          )}
                        </div>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </Td>
                    <Td>
                      <StatusBadge status={r.outcome} />
                    </Td>
                    <Td>
                      <Button
                        size="sm"
                        variant="primary"
                        onClick={() => startEdit(r)}
                        icon={<Edit3 className="h-3 w-3" />}
                      >
                        Edit & Fix
                      </Button>
                    </Td>
                  </Tr>
                );
              })
            )}
          </tbody>
        </Table>
      </Card>

      {/* Edit & Resubmit Modal */}
      {editRow && (
        <Modal
          open={!!editRow}
          onClose={() => setEditRow(null)}
          title={`Remediate Record (Row #${editRow.rowNo})`}
        >
          <div className="space-y-4 max-h-[70vh] overflow-y-auto pr-1">
            {editRow.issues?.length > 0 && (
              <div className="p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 rounded-lg text-xs text-red-700 dark:text-red-300 space-y-1">
                <div className="font-bold flex items-center gap-1">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  <span>Validation Rule Errors to Resolve:</span>
                </div>
                {editRow.issues.map((iss: any, i: number) => (
                  <div key={i} className="pl-4">
                    • <strong>{iss.ruleCode}</strong>: {iss.message}
                  </div>
                ))}
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="APAAR ID (12 digits)"
                value={fieldValues.apaar_id || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, apaar_id: e.target.value })}
                placeholder="12-digit number"
              />
              <Input
                label="Full Legal Name"
                value={fieldValues.full_name || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, full_name: e.target.value })}
              />
              <Input
                label="Date of Birth"
                value={fieldValues.dob || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, dob: e.target.value })}
                placeholder="YYYY-MM-DD"
              />
              <Input
                label="Gender"
                value={fieldValues.gender || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, gender: e.target.value })}
                placeholder="M / F"
              />
              <Input
                label="Program Code"
                value={fieldValues.program_code || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, program_code: e.target.value })}
              />
              <Input
                label="Passport No (if Foreign National)"
                value={fieldValues.passport_no || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, passport_no: e.target.value })}
              />
              <Input
                label="OCI Card No (if OCI)"
                value={fieldValues.oci_card_no || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, oci_card_no: e.target.value })}
              />
              <Input
                label="Nationality"
                value={fieldValues.nationality || ''}
                onChange={(e) => setFieldValues({ ...fieldValues, nationality: e.target.value })}
              />
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-slate-200 dark:border-slate-800">
              <Button variant="secondary" onClick={() => setEditRow(null)}>Cancel</Button>
              <Button
                variant="primary"
                onClick={() => resubmitMutation.mutate({ rowId: editRow.id, raw: fieldValues })}
                loading={resubmitMutation.isPending}
                icon={<RefreshCw className="h-4 w-4" />}
              >
                Resubmit & Re-validate
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
