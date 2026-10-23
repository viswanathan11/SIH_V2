import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  UploadCloud, AlertTriangle, CheckCircle2, TrendingUp,
  Layers, ArrowRight, ShieldCheck, Wrench
} from 'lucide-react';
import { api, getToken } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge, StatusBadge } from '../../components/ui';

export default function InstitutionDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const instId = user?.institutionId;

  // Institution Meters
  const { data: meters } = useQuery({
    queryKey: ['my-institution-meters', instId],
    queryFn: () => api.get<any[]>(`/analytics/institution/${instId}/meters`),
    enabled: !!instId,
    refetchInterval: 5000,
  });

  // Data Quality Scorecard
  const { data: quality } = useQuery({
    queryKey: ['my-institution-quality', instId],
    queryFn: () => api.get<any>(`/analytics/institution/${instId}/quality`),
    enabled: !!instId,
  });

  // Recent Upload Batches
  const { data: batches } = useQuery({
    queryKey: ['my-institution-batches', instId],
    queryFn: () => api.get<{ content: any[] }>(`/uploads?institutionId=${instId}&size=5`),
    enabled: !!instId,
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title={user?.institutionName || 'Institution Command Dashboard'}
        subtitle={`AICTE Code: ${user?.institutionCode || '-'} • Academic Year: 2026-27`}
        actions={
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="primary"
              onClick={() => navigate('/inst/upload')}
              icon={<UploadCloud className="h-4 w-4" />}
            >
              Upload Student Data
            </Button>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => navigate('/inst/worklist')}
              icon={<Wrench className="h-4 w-4" />}
            >
              Remediation Worklist ({quality?.openIssues ?? 0})
            </Button>
          </div>
        }
      />

      {/* KPI Cards: Quality score, submitted, accepted, issues */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Data Quality Score</span>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-slate-900 dark:text-slate-100">
            {quality?.qualityScore ?? 100}%
          </div>
          <div className="text-[11px] text-emerald-600 dark:text-emerald-400 mt-1">
            {quality?.accepted ?? 0} verified of {quality?.submitted ?? 0} submitted
          </div>
        </Card>

        <Card className="p-4">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Rejected Records</span>
            <AlertTriangle className="h-4 w-4 text-red-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-red-600 dark:text-red-400">
            {quality?.rejected ?? 0}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">Requires worklist correction</div>
        </Card>

        <Card className="p-4">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Flagged Under Review</span>
            <AlertTriangle className="h-4 w-4 text-amber-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-amber-600 dark:text-amber-400">
            {quality?.flagged ?? 0}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">Intake / duplicate checks</div>
        </Card>

        <Card className="p-4">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span>Open Issues</span>
            <Wrench className="h-4 w-4 text-indigo-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-slate-900 dark:text-slate-100">
            {quality?.openIssues ?? 0}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">Pending resolution</div>
        </Card>
      </div>

      {/* Program Capacity Meters */}
      <Card
        title="Live Intake Capacity Meters"
        subtitle="Monitors approved capacity utilization in real time as upload batches are ingested"
      >
        <div className="space-y-4">
          {meters?.map((m) => {
            const regular = m.regular ?? 0;
            const approved = m.approvedIntake ?? 0;
            const fillPct = approved > 0 ? (regular / approved) * 100 : 0;
            const excess = m.excess ?? 0;
            const supernum = m.supernumerary ?? 0;

            return (
              <div key={m.programId} className="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">
                <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                  <div>
                    <span className="font-bold text-sm text-slate-900 dark:text-slate-100">{m.programCode}</span>
                    <span className="text-xs text-slate-500 ml-2">{m.programName}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-500">
                      Regular: <strong>{regular}</strong> / {approved}
                    </span>
                    {excess > 0 && (
                      <Badge color="red">+{excess} EXCESS</Badge>
                    )}
                    {fillPct === 100 && (
                      <Badge color="green">100% Capacity</Badge>
                    )}
                    {fillPct >= 90 && fillPct < 100 && (
                      <Badge color="amber">Early Warning (90%)</Badge>
                    )}
                  </div>
                </div>

                {/* Progress bar with 90% and 100% threshold markers */}
                <div className="relative h-3 w-full bg-slate-200 dark:bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      fillPct > 100 ? 'bg-red-600' : fillPct >= 90 ? 'bg-amber-500' : 'bg-indigo-600'
                    }`}
                    style={{ width: `${Math.min(fillPct, 100)}%` }}
                  />
                  {/* 90% marker line */}
                  <div className="absolute top-0 bottom-0 left-[90%] w-0.5 bg-white/70 dark:bg-black/50 z-10" title="90% Alert Marker" />
                </div>

                <div className="flex items-center justify-between text-[11px] text-slate-400 mt-2">
                  <span>Supernumerary: {supernum} (FN: {m.fnOci} | TFW: {m.tfw} | CIWGC: {m.ciwgc})</span>
                  <span>Lateral: {m.lateral} / {m.approvedLateralIntake}</span>
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* Recent Upload Batches Table */}
      <Card
        title="Recent Upload Batches"
        actions={
          <Button size="sm" variant="ghost" onClick={() => navigate('/inst/upload')} icon={<ArrowRight className="h-3.5 w-3.5" />}>
            View All Uploads
          </Button>
        }
      >
        <Table>
          <thead>
            <tr>
              <Th>Batch ID</Th>
              <Th>File Name</Th>
              <Th>Source</Th>
              <Th right>Total</Th>
              <Th right>Accepted</Th>
              <Th right>Flagged</Th>
              <Th right>Rejected</Th>
              <Th>Status</Th>
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {!batches?.content || batches.content.length === 0 ? (
              <tr>
                <Td colSpan={9} className="text-center py-6 text-slate-400">
                  No upload batches recorded yet.
                </Td>
              </tr>
            ) : (
              batches.content.map((b) => (
                <Tr key={b.id}>
                  <Td mono className="font-bold text-xs">{b.id.substring(0, 8)}...</Td>
                  <Td className="font-medium text-xs">{b.fileName}</Td>
                  <Td><Badge color="slate">{b.source}</Badge></Td>
                  <Td right className="tabular-nums font-mono">{b.totalRows}</Td>
                  <Td right className="tabular-nums font-mono text-emerald-600">{b.acceptedRows}</Td>
                  <Td right className="tabular-nums font-mono text-amber-600">{b.flaggedRows}</Td>
                  <Td right className="tabular-nums font-mono text-red-600">{b.rejectedRows}</Td>
                  <Td><StatusBadge status={b.status} /></Td>
                  <Td>
                    {b.rejectedRows > 0 || b.flaggedRows > 0 ? (
                      <a
                        href={`/api/v1/uploads/${b.id}/errors.csv?access_token=${getToken()}`}
                        target="_blank"
                        className="text-xs text-indigo-600 hover:underline"
                      >
                        Error Report
                      </a>
                    ) : (
                      <span className="text-xs text-slate-400">-</span>
                    )}
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
