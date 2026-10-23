import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Building2, ShieldAlert, AlertTriangle, Users, Layers,
  UploadCloud, Edit3, ArrowLeft, CheckCircle2
} from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  Modal, Input
} from '../../components/ui';

export default function InstitutionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [editProg, setEditProg] = useState<any | null>(null);
  const [newIntake, setNewIntake] = useState(120);

  const { data: inst } = useQuery({
    queryKey: ['institution', id],
    queryFn: () => api.get<any>(`/institutions/${id}`),
    enabled: !!id,
  });

  const { data: risk } = useQuery({
    queryKey: ['institution-risk', id],
    queryFn: () => api.get<any>(`/institutions/${id}/risk`),
    enabled: !!id,
  });

  const { data: meters } = useQuery({
    queryKey: ['institution-meters', id],
    queryFn: () => api.get<any[]>(`/analytics/institution/${id}/meters`),
    enabled: !!id,
  });

  const updateIntakeMutation = useMutation({
    mutationFn: (progId: string) =>
      api.put(`/programs/${progId}`, { approvedIntake: newIntake }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['institution', id] });
      queryClient.invalidateQueries({ queryKey: ['institution-meters', id] });
      setEditProg(null);
    },
  });

  if (!inst) return <div className="p-8 text-center text-xs text-slate-400">Loading institution profile...</div>;

  const canManage = user?.permissions?.includes('institution:manage') || user?.roles?.includes('AICTE_ADMIN') || user?.roles?.includes('SUPER_ADMIN');

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 mb-2">
        <Button size="sm" variant="ghost" onClick={() => navigate(-1)} icon={<ArrowLeft className="h-4 w-4" />}>
          Back
        </Button>
      </div>

      <PageHeader
        title={inst.name}
        subtitle={`${inst.aicteId} • ${inst.state}, ${inst.district} • ${inst.institutionType}`}
        actions={
          <div className="flex items-center gap-2">
            <SeverityBadge severity={inst.riskLevel} />
            <Badge color="blue">{inst.status}</Badge>
          </div>
        }
      />

      {/* Top Cards: Metrics & Risk breakdown */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card title="Capacity & Enrolment (2026-27)">
          <div className="space-y-3">
            <div className="flex justify-between items-center text-xs">
              <span className="text-slate-500">Approved Intake</span>
              <span className="font-bold">{inst.approvedIntake} seats</span>
            </div>
            <div className="flex justify-between items-center text-xs">
              <span className="text-slate-500">Actual Enrolment</span>
              <span className="font-bold text-slate-900 dark:text-slate-100">{inst.enrolled}</span>
            </div>
            <div className="flex justify-between items-center text-xs">
              <span className="text-slate-500">Capacity Fill Ratio</span>
              <span className={`font-bold ${inst.fillRatio > 1.0 ? 'text-red-600' : 'text-emerald-600'}`}>
                {Math.round(inst.fillRatio * 100)}%
              </span>
            </div>
            <div className="flex justify-between items-center text-xs pt-2 border-t border-slate-100 dark:border-slate-800">
              <span className="text-slate-500">Open Critical Discrepancies</span>
              <span className="font-bold text-red-600">{inst.openCritical}</span>
            </div>
          </div>
        </Card>

        <Card title="Intelligent Risk Assessment" className="md:col-span-2">
          <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center">
            <div className="text-center p-3 rounded-xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 min-w-[110px]">
              <div className="text-2xl font-black text-slate-900 dark:text-slate-100">
                {Math.round(risk?.riskScore ?? 0)}
              </div>
              <div className="text-[10px] uppercase tracking-wider font-semibold mt-0.5">
                <SeverityBadge severity={risk?.level} />
              </div>
            </div>

            <div className="flex-1 space-y-1.5 text-xs">
              <div className="font-semibold text-slate-800 dark:text-slate-200">Contributing Factor Signals:</div>
              <ul className="list-disc pl-4 space-y-1 text-slate-600 dark:text-slate-400">
                {risk?.topReasons?.map((r: string, i: number) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </div>
          </div>
        </Card>
      </div>

      {/* Program Meters & Capacity Table */}
      <Card
        title="Approved Programs & Real-time Intake Meters"
        subtitle="Monitors Regular seats, Supernumerary pool (FN/OCI/CIWGC/TFW), and Excess Admissions"
      >
        <Table>
          <thead>
            <tr>
              <Th>Program Code</Th>
              <Th>Program Name</Th>
              <Th right>Approved Intake</Th>
              <Th>Intake Utilization</Th>
              <Th right>Supernumerary</Th>
              <Th right>Excess</Th>
              {canManage && <Th>Action</Th>}
            </tr>
          </thead>
          <tbody>
            {meters?.map((m) => {
              const regular = m.regular ?? 0;
              const approved = m.approvedIntake ?? 0;
              const fillPct = approved > 0 ? (regular / approved) * 100 : 0;
              const excess = m.excess ?? 0;
              const supernum = m.supernumerary ?? 0;

              return (
                <Tr key={m.programId}>
                  <Td mono>{m.programCode}</Td>
                  <Td>
                    <div className="font-medium text-slate-800 dark:text-slate-200">{m.programName}</div>
                    <div className="text-[10px] text-slate-400">{m.level} • Lateral: {m.lateral}/{m.approvedLateralIntake}</div>
                  </Td>
                  <Td right className="font-semibold">{approved}</Td>
                  <Td>
                    <div className="w-48">
                      <div className="flex justify-between text-[10px] mb-1">
                        <span>{regular} / {approved}</span>
                        <span className={fillPct > 100 ? 'text-red-600 font-bold' : ''}>{Math.round(fillPct)}%</span>
                      </div>
                      <div className="h-2 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all ${
                            fillPct > 100 ? 'bg-red-600' : fillPct >= 90 ? 'bg-amber-500' : 'bg-indigo-600'
                          }`}
                          style={{ width: `${Math.min(fillPct, 100)}%` }}
                        />
                      </div>
                    </div>
                  </Td>
                  <Td right>
                    <span className="font-mono text-xs">{supernum}</span>
                    <div className="text-[9px] text-slate-400">FN: {m.fnOci} | TFW: {m.tfw}</div>
                  </Td>
                  <Td right>
                    {excess > 0 ? (
                      <span className="font-bold text-red-600">+{excess}</span>
                    ) : (
                      <span className="text-slate-400">0</span>
                    )}
                  </Td>
                  {canManage && (
                    <Td>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => { setEditProg(m); setNewIntake(m.approvedIntake); }}
                        icon={<Edit3 className="h-3 w-3" />}
                      >
                        Adjust
                      </Button>
                    </Td>
                  )}
                </Tr>
              );
            })}
          </tbody>
        </Table>
      </Card>

      {/* Edit Intake Modal */}
      {editProg && (
        <Modal
          open={!!editProg}
          onClose={() => setEditProg(null)}
          title={`Adjust Approved Intake — ${editProg.programCode}`}
        >
          <div className="space-y-4">
            <p className="text-xs text-slate-500">
              Modifying approved intake generates an official AICTE regulatory amendment and updates the audit trail.
            </p>
            <Input
              label="Approved Intake Seats"
              type="number"
              value={newIntake}
              onChange={(e) => setNewIntake(parseInt(e.target.value) || 0)}
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setEditProg(null)}>Cancel</Button>
              <Button
                variant="primary"
                onClick={() => updateIntakeMutation.mutate(editProg.programId)}
                loading={updateIntakeMutation.isPending}
              >
                Save Approval
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
