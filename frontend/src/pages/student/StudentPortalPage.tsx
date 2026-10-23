import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import {
  UserCheck, ShieldCheck, FileText, CheckCircle2, XCircle,
  Clock, Download, History, Lock, AlertCircle, Trash2
} from 'lucide-react';
import { api, getToken } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  StatusBadge, Modal
} from '../../components/ui';
import type { Consent, ConsentRequest, IssuedRecord } from '../../lib/types';

export default function StudentPortalPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  // Queries
  const { data: studentRecord } = useQuery({
    queryKey: ['my-record'],
    queryFn: () => api.get<any>('/me/record'),
  });

  const { data: consents } = useQuery({
    queryKey: ['my-consents'],
    queryFn: () => api.get<Consent[]>('/consents'),
  });

  const { data: requests } = useQuery({
    queryKey: ['my-consent-requests'],
    queryFn: () => api.get<ConsentRequest[]>('/consent-requests'),
  });

  const { data: accessHistory } = useQuery({
    queryKey: ['my-access-history'],
    queryFn: () => api.get<any[]>('/me/access-history'),
  });

  // Mutations
  const approveMutation = useMutation({
    mutationFn: (requestId: string) => api.post(`/consent-requests/${requestId}/approve`, { days: 30 }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-consent-requests'] });
      queryClient.invalidateQueries({ queryKey: ['my-consents'] });
    },
  });

  const denyMutation = useMutation({
    mutationFn: (requestId: string) => api.post(`/consent-requests/${requestId}/deny`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-consent-requests'] });
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (consentId: string) => api.del(`/consents/${consentId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-consents'] });
    },
  });

  const enrolment = studentRecord?.enrolments?.[0];
  const record = studentRecord?.records?.[0];

  const downloadCertificate = () => {
    if (!enrolment) return;
    const token = getToken();
    window.open(`/api/v1/certificates/preview?enrolmentId=${enrolment.id}&access_token=${token}`, '_blank');
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <PageHeader
        title="Student Academic Identity & DPDP Consent Vault"
        subtitle={`APAAR ID: ${user?.apaarMasked || 'XXXX XXXX XXXX'} • Lifelong Academic Registry Account`}
        actions={
          enrolment && (
            <Button
              size="sm"
              variant="primary"
              onClick={downloadCertificate}
              icon={<Download className="h-4 w-4" />}
            >
              Download Verified Certificate (PDF)
            </Button>
          )
        }
      />

      {/* Verified Enrolment Banner */}
      <Card title="Current Verified Academic Enrolment">
        {enrolment ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-3 bg-slate-50 dark:bg-slate-800 rounded-lg text-xs space-y-1">
              <span className="text-slate-400">Awarding Institution</span>
              <div className="font-bold text-sm text-slate-800 dark:text-slate-100">{enrolment.institutionName}</div>
              <div className="text-[11px] text-slate-400 font-mono">{enrolment.institutionCode}</div>
            </div>

            <div className="p-3 bg-slate-50 dark:bg-slate-800 rounded-lg text-xs space-y-1">
              <span className="text-slate-400">Program & Student ID</span>
              <div className="font-bold text-sm text-slate-800 dark:text-slate-100">{enrolment.programName}</div>
              <div className="text-[11px] text-indigo-600 dark:text-indigo-400 font-mono font-semibold">
                Student ID: {enrolment.instituteStudentId}
              </div>
            </div>

            <div className="p-3 bg-slate-50 dark:bg-slate-800 rounded-lg text-xs space-y-1">
              <span className="text-slate-400">Verification Status</span>
              <div><StatusBadge status={enrolment.status} /></div>
              <div className="text-[11px] text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-1 mt-1">
                <CheckCircle2 className="h-3.5 w-3.5" />
                <span>APAAR Registry Matched</span>
              </div>
            </div>
          </div>
        ) : (
          <div className="text-center py-6 text-xs text-slate-400">No enrolment records loaded.</div>
        )}
      </Card>

      {/* Pending Consent Requests (Demo Step 7) */}
      <Card
        title="Pending Third-Party Verification Requests"
        subtitle="Authorize or deny third-party employers and background verification agencies from viewing your academic record"
        actions={
          <Badge color={requests && requests.filter(r => r.status === 'PENDING').length > 0 ? 'amber' : 'slate'}>
            {requests?.filter(r => r.status === 'PENDING').length ?? 0} Pending Requests
          </Badge>
        }
      >
        <div className="space-y-3">
          {!requests || requests.filter(r => r.status === 'PENDING').length === 0 ? (
            <div className="text-center py-6 text-xs text-slate-400">
              No pending consent requests at this time.
            </div>
          ) : (
            requests.filter(r => r.status === 'PENDING').map((req) => (
              <div
                key={req.id}
                className="p-4 rounded-xl border border-amber-200 dark:border-amber-800 bg-amber-50/50 dark:bg-amber-950/20 flex flex-wrap items-center justify-between gap-3 text-xs"
              >
                <div>
                  <div className="font-bold text-sm text-slate-900 dark:text-slate-100">
                    {req.requesterOrg} ({req.requesterName || 'Authorized Verifier'})
                  </div>
                  <div className="text-slate-600 dark:text-slate-300 mt-0.5">
                    Purpose: <strong>{req.purpose}</strong>
                  </div>
                  <div className="text-[11px] text-slate-400 mt-1">
                    Requested Tier: <Badge color="accent">{req.requestedScope}</Badge> • Valid for 30 days
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    size="sm"
                    variant="primary"
                    onClick={() => approveMutation.mutate(req.id)}
                    loading={approveMutation.isPending}
                    icon={<CheckCircle2 className="h-3.5 w-3.5" />}
                  >
                    Approve Disclosure
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => denyMutation.mutate(req.id)}
                    loading={denyMutation.isPending}
                    icon={<XCircle className="h-3.5 w-3.5" />}
                  >
                    Deny
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>
      </Card>

      {/* Active Consents */}
      <Card title="Active Verification Authorizations">
        <Table>
          <thead>
            <tr>
              <Th>Authorized Organization</Th>
              <Th>Disclosure Scope</Th>
              <Th>Granted Date</Th>
              <Th>Expires On</Th>
              <Th>Status</Th>
              <Th>Action</Th>
            </tr>
          </thead>
          <tbody>
            {!consents || consents.length === 0 ? (
              <tr>
                <Td colSpan={6} className="text-center py-6 text-slate-400">
                  No active consents.
                </Td>
              </tr>
            ) : (
              consents.map((c) => (
                <Tr key={c.id}>
                  <Td className="font-semibold">{c.granteeName || c.granteeId}</Td>
                  <Td><Badge color="accent">{c.scope}</Badge></Td>
                  <Td className="text-xs text-slate-500">{new Date(c.grantedAt).toLocaleDateString()}</Td>
                  <Td className="text-xs text-slate-500">{c.expiresAt ? new Date(c.expiresAt).toLocaleDateString() : 'Never'}</Td>
                  <Td>
                    <Badge color={c.active ? 'green' : 'slate'}>{c.active ? 'ACTIVE' : 'REVOKED'}</Badge>
                  </Td>
                  <Td>
                    {c.active && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => revokeMutation.mutate(c.id)}
                        loading={revokeMutation.isPending}
                        icon={<Trash2 className="h-3.5 w-3.5 text-red-500" />}
                      >
                        Revoke
                      </Button>
                    )}
                  </Td>
                </Tr>
              ))
            )}
          </tbody>
        </Table>
      </Card>

      {/* Verification Access History */}
      <Card title="Access Audit History (Who Looked Up My Credentials)">
        <Table>
          <thead>
            <tr>
              <Th>Date & Time</Th>
              <Th>Organization</Th>
              <Th>Verifier</Th>
              <Th>Purpose</Th>
              <Th>Tier Disclosed</Th>
              <Th>Result</Th>
            </tr>
          </thead>
          <tbody>
            {!accessHistory || accessHistory.length === 0 ? (
              <tr>
                <Td colSpan={6} className="text-center py-6 text-slate-400">
                  Zero external lookups recorded.
                </Td>
              </tr>
            ) : (
              accessHistory.map((h, i) => (
                <Tr key={i}>
                  <Td className="text-xs text-slate-500">{new Date(h.ts).toLocaleString()}</Td>
                  <Td className="font-semibold text-xs">{h.requesterOrg}</Td>
                  <Td className="text-xs">{h.requesterName}</Td>
                  <Td className="text-xs max-w-xs truncate">{h.purpose}</Td>
                  <Td><Badge color="accent">{h.scopeGranted}</Badge></Td>
                  <Td><Badge color="green">{h.result}</Badge></Td>
                </Tr>
              ))
            )}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
