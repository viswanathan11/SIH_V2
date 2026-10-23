import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Building2, Search, Filter, ShieldAlert, ArrowRight } from 'lucide-react';
import { api } from '../../lib/api';
import { Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge, Input } from '../../components/ui';
import type { InstitutionSummary } from '../../lib/types';

export default function InstitutionsPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [filterState, setFilterState] = useState(params.get('state') || '');
  const [search, setSearch] = useState('');

  const { data: institutions, isLoading } = useQuery({
    queryKey: ['institutions'],
    queryFn: () => api.get<InstitutionSummary[]>('/institutions'),
  });

  const filtered = (institutions || []).filter((inst) => {
    if (filterState && inst.stateCode !== filterState && inst.state !== filterState) return false;
    if (search.trim()) {
      const q = search.toLowerCase();
      return inst.name.toLowerCase().includes(q) || inst.aicteId.toLowerCase().includes(q) || inst.state.toLowerCase().includes(q);
    }
    return true;
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Approved Institutions Directory"
        subtitle="AICTE monitored universities and engineering colleges reporting student data"
      />

      <Card>
        {/* Filters bar */}
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div className="flex items-center gap-3 flex-1 max-w-md">
            <Input
              placeholder="Filter by institution name, code, state..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="text-xs"
            />
          </div>

          <div className="flex items-center gap-2">
            {filterState && (
              <Badge color="accent">
                State: {filterState}
                <button onClick={() => setFilterState('')} className="ml-1 hover:text-red-500">×</button>
              </Badge>
            )}
            <span className="text-xs text-slate-500">{filtered.length} institutions</span>
          </div>
        </div>

        <Table>
          <thead>
            <tr>
              <Th>AICTE ID</Th>
              <Th>Institution Name</Th>
              <Th>State & District</Th>
              <Th right>Approved Intake</Th>
              <Th right>Enrolled</Th>
              <Th right>Fill Ratio</Th>
              <Th>Risk Level</Th>
              <Th right>Open Issues</Th>
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((inst) => {
              const fillPct = Math.round(inst.fillRatio * 100);
              return (
                <Tr key={inst.id} onClick={() => navigate(`/aicte/institutions/${inst.id}`)}>
                  <Td mono>{inst.aicteId}</Td>
                  <Td>
                    <div className="font-semibold text-slate-800 dark:text-slate-200">{inst.name}</div>
                    <div className="text-[10px] text-slate-400">{inst.institutionType}</div>
                  </Td>
                  <Td>
                    <div>{inst.state}</div>
                    <div className="text-[10px] text-slate-400">{inst.district}</div>
                  </Td>
                  <Td right>{inst.approvedIntake}</Td>
                  <Td right className="font-semibold">{inst.enrolled}</Td>
                  <Td right className={fillPct > 100 ? 'text-red-600 font-bold' : ''}>
                    {fillPct}%
                  </Td>
                  <Td>
                    <SeverityBadge severity={inst.riskLevel} />
                  </Td>
                  <Td right>
                    {inst.openCritical > 0 ? (
                      <span className="font-bold text-red-600">{inst.openIssues} ({inst.openCritical} crit)</span>
                    ) : (
                      inst.openIssues
                    )}
                  </Td>
                  <Td>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={(e) => { e.stopPropagation(); navigate(`/aicte/institutions/${inst.id}`); }}
                      icon={<ArrowRight className="h-3.5 w-3.5" />}
                    >
                      View
                    </Button>
                  </Td>
                </Tr>
              );
            })}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
