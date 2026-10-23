import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip,
  LineChart, Line, CartesianGrid, Legend
} from 'recharts';
import {
  Building2, Users, AlertTriangle, ShieldAlert, CheckCircle2,
  TrendingUp, Radio, ExternalLink, Activity
} from 'lucide-react';
import { api } from '../../lib/api';
import { useEvents } from '../../lib/events';
import { Card, PageHeader, Badge, SeverityBadge, Table, Th, Td, Tr, Button } from '../../components/ui';

export default function NationalDashboard() {
  const navigate = useNavigate();
  const { events } = useEvents();
  const [mapMetric, setMapMetric] = useState<'fill' | 'risk'>('fill');

  // National Summary
  const { data: summary } = useQuery({
    queryKey: ['national-summary'],
    queryFn: () => api.get<any>('/analytics/national/summary'),
    refetchInterval: 10000,
  });

  // State breakdown
  const { data: byState } = useQuery({
    queryKey: ['national-by-state'],
    queryFn: () => api.get<any[]>('/analytics/national/by-state'),
  });

  // By level
  const { data: byLevel } = useQuery({
    queryKey: ['national-by-level'],
    queryFn: () => api.get<any[]>('/analytics/national/by-level'),
  });

  // 5-year trend
  const { data: trend } = useQuery({
    queryKey: ['national-trend'],
    queryFn: () => api.get<any[]>('/analytics/national/trend'),
  });

  // Supernumerary
  const { data: supernum } = useQuery({
    queryKey: ['national-supernumerary'],
    queryFn: () => api.get<any[]>('/analytics/national/supernumerary'),
  });

  // Top Risk
  const { data: topRisk } = useQuery({
    queryKey: ['national-top-risk'],
    queryFn: () => api.get<any[]>('/analytics/national/top-risk'),
  });

  // Excess Register
  const { data: excessRegister } = useQuery({
    queryKey: ['excess-register'],
    queryFn: () => api.get<any[]>('/analytics/excess-register'),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="National AICTE Monitoring Dashboard"
        subtitle="Real-time academic seat allocation, APAAR identity verification & risk monitoring across India"
        actions={
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => navigate('/aicte/anomalies')}
              icon={<ShieldAlert className="h-4 w-4 text-orange-500" />}
            >
              Risk Watchlist ({summary?.anomaliesOpen ?? 0})
            </Button>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => navigate('/aicte/issues')}
              icon={<AlertTriangle className="h-4 w-4 text-red-500" />}
            >
              Critical Issues ({summary?.openCritical ?? 0})
            </Button>
          </div>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <Card className="p-3">
          <div className="flex items-center justify-between text-slate-500 text-xs">
            <span>Institutions</span>
            <Building2 className="h-4 w-4 text-indigo-500" />
          </div>
          <div className="mt-2 text-xl font-bold text-slate-900 dark:text-slate-100">
            {summary?.institutionsReporting ?? 0}
            <span className="text-xs font-normal text-slate-400 ml-1">/ {summary?.totalInstitutions ?? 12}</span>
          </div>
          <div className="text-[10px] text-emerald-600 dark:text-emerald-400 mt-0.5">100% Reporting</div>
        </Card>

        <Card className="p-3">
          <div className="flex items-center justify-between text-slate-500 text-xs">
            <span>Enrolment</span>
            <Users className="h-4 w-4 text-blue-500" />
          </div>
          <div className="mt-2 text-xl font-bold text-slate-900 dark:text-slate-100">
            {(summary?.totalEnrolment ?? 0).toLocaleString('en-IN')}
          </div>
          <div className="text-[10px] text-slate-400 mt-0.5">Approved: {(summary?.totalApprovedIntake ?? 0).toLocaleString('en-IN')}</div>
        </Card>

        <Card className="p-3">
          <div className="flex items-center justify-between text-slate-500 text-xs">
            <span>Fill Ratio</span>
            <TrendingUp className="h-4 w-4 text-emerald-500" />
          </div>
          <div className="mt-2 text-xl font-bold text-slate-900 dark:text-slate-100">
            {Math.round((summary?.fillRatio ?? 0) * 100)}%
          </div>
          <div className="text-[10px] text-slate-400 mt-0.5">Capacity utilization</div>
        </Card>

        <Card className="p-3">
          <div className="flex items-center justify-between text-slate-500 text-xs">
            <span>Critical Issues</span>
            <AlertTriangle className="h-4 w-4 text-red-500" />
          </div>
          <div className="mt-2 text-xl font-bold text-red-600 dark:text-red-400">
            {summary?.openCritical ?? 0}
          </div>
          <div className="text-[10px] text-red-500/80 mt-0.5">Intake/Duplicate flags</div>
        </Card>

        <Card className="p-3">
          <div className="flex items-center justify-between text-slate-500 text-xs">
            <span>Anomalies</span>
            <ShieldAlert className="h-4 w-4 text-orange-500" />
          </div>
          <div className="mt-2 text-xl font-bold text-orange-600 dark:text-orange-400">
            {summary?.anomaliesOpen ?? 0}
          </div>
          <div className="text-[10px] text-slate-400 mt-0.5">Under review: {summary?.anomaliesUnderReview ?? 0}</div>
        </Card>

        <Card className="p-3">
          <div className="flex items-center justify-between text-slate-500 text-xs">
            <span>Validation Pass</span>
            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
          </div>
          <div className="mt-2 text-xl font-bold text-emerald-600 dark:text-emerald-400">
            {summary?.validationPassRate ?? 94}%
          </div>
          <div className="text-[10px] text-slate-400 mt-0.5">APAAR identity verified</div>
        </Card>
      </div>

      {/* State Tiles & Live Feed Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* State Grid Map */}
        <Card
          className="lg:col-span-2"
          title="State Distribution & Intake Utilization"
          subtitle="Click state to inspect regional capacity and risk profile"
          actions={
            <div className="flex rounded-md border border-slate-200 dark:border-slate-800 p-0.5 text-[11px]">
              <button
                onClick={() => setMapMetric('fill')}
                className={`px-2 py-0.5 rounded font-medium ${mapMetric === 'fill' ? 'bg-indigo-600 text-white' : 'text-slate-600 dark:text-slate-300'}`}
              >
                Fill %
              </button>
              <button
                onClick={() => setMapMetric('risk')}
                className={`px-2 py-0.5 rounded font-medium ${mapMetric === 'risk' ? 'bg-indigo-600 text-white' : 'text-slate-600 dark:text-slate-300'}`}
              >
                Risk Score
              </button>
            </div>
          }
        >
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
            {byState?.map((st) => {
              const fillPct = Math.round(st.fillRatio * 100);
              const isOver = fillPct > 100;
              const isHighRisk = st.riskMax >= 60;
              const bg = mapMetric === 'fill'
                ? (isOver ? 'bg-red-50 border-red-300 text-red-900 dark:bg-red-950/40 dark:border-red-800 dark:text-red-200'
                  : fillPct >= 90 ? 'bg-emerald-50 border-emerald-300 text-emerald-900 dark:bg-emerald-950/40 dark:border-emerald-800 dark:text-emerald-200'
                  : 'bg-slate-50 border-slate-200 text-slate-800 dark:bg-slate-800/40 dark:border-slate-700 dark:text-slate-200')
                : (isHighRisk ? 'bg-orange-50 border-orange-300 text-orange-900 dark:bg-orange-950/40 dark:border-orange-800 dark:text-orange-200'
                  : 'bg-emerald-50 border-emerald-300 text-emerald-900 dark:bg-emerald-950/40 dark:border-emerald-800 dark:text-emerald-200');

              return (
                <div
                  key={st.stateCode}
                  onClick={() => navigate(`/aicte/institutions?state=${st.stateCode}`)}
                  className={`p-2.5 rounded-lg border cursor-pointer hover:shadow-md transition-all ${bg}`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-xs">{st.stateCode}</span>
                    <span className="text-[10px] font-semibold">
                      {mapMetric === 'fill' ? `${fillPct}%` : `Risk: ${Math.round(st.riskMax)}`}
                    </span>
                  </div>
                  <div className="text-[11px] truncate mt-1 font-medium">{st.state}</div>
                  <div className="text-[10px] opacity-75 mt-0.5">
                    {st.enrolled} / {st.approvedIntake} seats
                  </div>
                </div>
              );
            })}
          </div>
        </Card>

        {/* Real-time Event Feed */}
        <Card
          title="Live National Event Feed"
          subtitle="Real-time ingestion, seat caps & anomaly alerts"
          actions={
            <div className="flex items-center gap-1.5 text-[11px] text-emerald-600 dark:text-emerald-400">
              <Radio className="h-3 w-3 animate-pulse" />
              <span>Streaming</span>
            </div>
          }
        >
          <div className="space-y-2.5 max-h-[300px] overflow-y-auto pr-1">
            {events.length === 0 ? (
              <div className="text-center py-8 text-xs text-slate-400">
                Listening for real-time events...
              </div>
            ) : (
              events.slice(0, 15).map((ev) => (
                <div key={ev.id} className="p-2 rounded-lg bg-slate-50 dark:bg-slate-800/60 border border-slate-100 dark:border-slate-800 text-xs">
                  <div className="flex items-center justify-between gap-1">
                    <span className="font-semibold text-slate-800 dark:text-slate-200 truncate">{ev.type}</span>
                    <SeverityBadge severity={ev.severity} />
                  </div>
                  <div className="text-[11px] text-slate-600 dark:text-slate-300 mt-1">{ev.message}</div>
                  <div className="text-[9px] text-slate-400 font-mono mt-1 text-right">
                    {new Date(ev.ts).toLocaleTimeString()}
                  </div>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>

      {/* Analytics Charts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* 5-Year Trend */}
        <Card title="5-Year National Intake vs Enrolment Trend">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={trend} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" opacity={0.2} />
                <XAxis dataKey="academicYear" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Line type="monotone" dataKey="approvedIntake" name="Approved Intake" stroke="#6366f1" strokeWidth={2} />
                <Line type="monotone" dataKey="enrolled" name="Enrolled Students" stroke="#10b981" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </Card>

        {/* Level breakdown */}
        <Card title="Approved Intake vs Enrolment by Level (2026-27)">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={byLevel} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" opacity={0.2} />
                <XAxis dataKey="level" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Bar dataKey="approvedIntake" name="Approved Intake" fill="#818cf8" radius={[4, 4, 0, 0]} />
                <Bar dataKey="enrolled" name="Enrolled" fill="#34d399" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      {/* Excess Enrolment Register */}
      <Card
        title="Excess Enrolment Register (Regulatory Breaches)"
        subtitle="Programs where regular enrolment exceeds AICTE approved intake"
        actions={
          <Badge color="red">{excessRegister?.length ?? 0} Breaches Detected</Badge>
        }
      >
        <Table>
          <thead>
            <tr>
              <Th>Institution</Th>
              <Th>Program</Th>
              <Th right>Approved Intake</Th>
              <Th right>Regular Admitted</Th>
              <Th right>Excess Seats</Th>
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {!excessRegister || excessRegister.length === 0 ? (
              <tr>
                <Td colSpan={6} className="text-center py-6 text-slate-400">
                  No excess enrolment breaches detected across institutions.
                </Td>
              </tr>
            ) : (
              excessRegister.map((r, i) => (
                <Tr key={i}>
                  <Td>
                    <div className="font-semibold text-slate-800 dark:text-slate-200">{r.institutionName}</div>
                    <div className="text-[10px] text-slate-400 font-mono">{r.institutionCode}</div>
                  </Td>
                  <Td>
                    <div className="font-medium text-slate-700 dark:text-slate-300">{r.programName}</div>
                    <div className="text-[10px] text-slate-400 font-mono">{r.programCode}</div>
                  </Td>
                  <Td right>{r.approvedIntake}</Td>
                  <Td right className="font-semibold text-slate-900 dark:text-slate-100">{r.regular}</Td>
                  <Td right className="font-bold text-red-600 dark:text-red-400">+{r.excess}</Td>
                  <Td>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => navigate(`/aicte/institutions/${r.institutionId}`)}
                      icon={<ExternalLink className="h-3 w-3" />}
                    >
                      Audit
                    </Button>
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
