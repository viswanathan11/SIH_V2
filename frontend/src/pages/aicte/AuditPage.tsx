import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { ShieldCheck, ShieldAlert, CheckCircle2, RefreshCw, KeyRound, Lock } from 'lucide-react';
import { api } from '../../lib/api';
import { Card, PageHeader, Table, Th, Td, Tr, Button, Badge } from '../../components/ui';
import type { AuditEntry, ChainVerificationResult } from '../../lib/types';

export default function AuditPage() {
  const [chainResult, setChainResult] = useState<ChainVerificationResult | null>(null);

  const { data: auditPage, isLoading, refetch } = useQuery({
    queryKey: ['audit-log'],
    queryFn: () => api.get<{ content: AuditEntry[]; totalElements: number }>('/audit?size=50'),
  });

  const verifyChainMutation = useMutation({
    mutationFn: () => api.get<ChainVerificationResult>('/audit/verify-chain'),
    onSuccess: (data) => setChainResult(data),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tamper-Evident Cryptographic Audit Trail"
        subtitle="Immutable SHA-256 hash-chained log recording all data uploads, identity lookups, waivers & policy updates"
        actions={
          <Button
            size="sm"
            variant="primary"
            onClick={() => verifyChainMutation.mutate()}
            loading={verifyChainMutation.isPending}
            icon={<ShieldCheck className="h-4 w-4" />}
          >
            Verify Chain Integrity
          </Button>
        }
      />

      {/* Verification Banner */}
      {chainResult && (
        <div
          className={`p-4 rounded-xl border flex items-center justify-between gap-4 ${
            chainResult.intact
              ? 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-300 dark:border-emerald-800 text-emerald-900 dark:text-emerald-200'
              : 'bg-red-50 dark:bg-red-950/40 border-red-300 dark:border-red-800 text-red-900 dark:text-red-200'
          }`}
        >
          <div className="flex items-center gap-3">
            {chainResult.intact ? (
              <CheckCircle2 className="h-6 w-6 text-emerald-600 dark:text-emerald-400 shrink-0" />
            ) : (
              <ShieldAlert className="h-6 w-6 text-red-600 dark:text-red-400 shrink-0" />
            )}
            <div>
              <div className="font-bold text-sm">
                {chainResult.intact ? 'Audit Hash Chain Intact ✔' : 'Cryptographic Chain Compromised!'}
              </div>
              <div className="text-xs opacity-90 mt-0.5">
                {chainResult.intact
                  ? `All ${chainResult.checked} consecutive block entries verified successfully from genesis hash. Zero tampering detected.`
                  : `Integrity broken at row sequence ID #${chainResult.firstBrokenId}.`}
              </div>
            </div>
          </div>
          <Badge color={chainResult.intact ? 'green' : 'red'}>
            {chainResult.intact ? 'CRYPTOGRAPHICALLY VALID' : 'TAMPERED'}
          </Badge>
        </div>
      )}

      {/* Audit Log Table */}
      <Card title="Recorded Transaction Entries">
        <Table>
          <thead>
            <tr>
              <Th>Sequence ID</Th>
              <Th>Timestamp</Th>
              <Th>Actor Email</Th>
              <Th>Role</Th>
              <Th>Action Event</Th>
              <Th>Target Entity</Th>
              <Th>Entry Hash (SHA-256)</Th>
            </tr>
          </thead>
          <tbody>
            {!auditPage?.content || auditPage.content.length === 0 ? (
              <tr>
                <Td colSpan={7} className="text-center py-8 text-slate-400">
                  No audit log entries recorded.
                </Td>
              </tr>
            ) : (
              auditPage.content.map((entry) => (
                <Tr key={entry.id}>
                  <Td mono className="text-slate-500 font-bold">#{entry.id}</Td>
                  <Td className="text-xs text-slate-500 whitespace-nowrap">
                    {new Date(entry.ts).toLocaleString()}
                  </Td>
                  <Td className="text-xs font-medium text-slate-800 dark:text-slate-200">
                    {entry.actorEmail}
                  </Td>
                  <Td>
                    <Badge color="slate">{entry.actorRole}</Badge>
                  </Td>
                  <Td mono className="font-semibold text-xs text-indigo-600 dark:text-indigo-400">
                    {entry.action}
                  </Td>
                  <Td mono className="text-[11px] text-slate-500">
                    {entry.entityType} ({entry.entityId ? entry.entityId.substring(0, 8) + '...' : '-'})
                  </Td>
                  <Td mono className="text-[10px] text-slate-400 max-w-[140px] truncate" title={`Prev: ${entry.prevHash}\nCurrent: ${entry.hash}`}>
                    {entry.hash.substring(0, 16)}...
                  </Td>
                </Tr>
              ))
            )}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
