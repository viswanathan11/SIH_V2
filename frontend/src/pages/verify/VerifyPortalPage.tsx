import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  CheckCircle2, AlertTriangle, ShieldCheck, Search, QrCode,
  Lock, RefreshCw, ExternalLink, Clock, User
} from 'lucide-react';
import { api } from '../../lib/api';
import { Card, PageHeader, Button, Input, Select, Badge, SeverityBadge, StatusBadge } from '../../components/ui';
import type { VerificationLookupResult } from '../../lib/types';

export default function VerifyPortalPage() {
  const [apaarId, setApaarId] = useState('910000000001');
  const [dob, setDob] = useState('2008-03-14');
  const [tier, setTier] = useState<'L1' | 'L2' | 'L3'>('L2');
  const [purpose, setPurpose] = useState('Employment Background Check');
  const [rawJws, setRawJws] = useState('');

  const [lookupResult, setLookupResult] = useState<VerificationLookupResult | null>(null);
  const [jwsResult, setJwsResult] = useState<any | null>(null);

  const lookupMutation = useMutation({
    mutationFn: () =>
      api.post<VerificationLookupResult>('/verify/lookup', {
        apaarId,
        dob,
        requestedTier: tier,
        purpose,
      }),
    onSuccess: (data) => {
      setLookupResult(data);
      setJwsResult(null);
    },
  });

  const jwsMutation = useMutation({
    mutationFn: () => api.post('/verify/jws', { jws: rawJws.trim() }),
    onSuccess: (data) => {
      setJwsResult(data);
      setLookupResult(null);
    },
  });

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <PageHeader
        title="Authorized Academic Verification Portal"
        subtitle="Selective-disclosure verification for employers, regulatory councils & universities with student consent governance"
      />

      {/* Verification Query Form */}
      <Card title="Verify Candidate Credentials (APAAR + DOB Challenge)">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Input
            label="Candidate APAAR ID (12 digits)"
            required
            value={apaarId}
            onChange={(e) => setApaarId(e.target.value)}
            placeholder="e.g. 910000000001"
          />

          <Input
            label="Date of Birth Challenge"
            type="date"
            required
            value={dob}
            onChange={(e) => setDob(e.target.value)}
          />

          <div>
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
              Selective Disclosure Tier
            </label>
            <Select
              value={tier}
              onChange={(e) => setTier(e.target.value as any)}
              className="text-xs"
            >
              <option value="L1">Tier L1 — Institution, Program & Enrolment Status Only</option>
              <option value="L2">Tier L2 — Full Name, Student ID & Academic Details (Consent/Clearance)</option>
              <option value="L3">Tier L3 — Full Personal Profile & Identity Attributes</option>
            </Select>
          </div>

          <Input
            label="Verification Purpose (Logged in Audit Chain)"
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
          />
        </div>

        <div className="mt-4 flex justify-end">
          <Button
            variant="primary"
            onClick={() => lookupMutation.mutate()}
            loading={lookupMutation.isPending}
            icon={<ShieldCheck className="h-4 w-4" />}
          >
            Verify Candidate Record
          </Button>
        </div>
      </Card>

      {/* Lookup Result Card */}
      {lookupResult && (
        <Card
          title="Digital Verification Record Result"
          actions={
            <Badge color={lookupResult.result === 'FOUND' ? 'green' : 'red'}>
              {lookupResult.result}
            </Badge>
          }
        >
          {lookupResult.result === 'DOB_MISMATCH' && (
            <div className="p-4 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 rounded-lg text-xs text-red-700 dark:text-red-300 flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 shrink-0" />
              <div>
                <span className="font-bold">Date of Birth Challenge Failed:</span> The supplied DOB does not match the official record for this APAAR ID. Verification access denied.
              </div>
            </div>
          )}

          {lookupResult.result === 'NOT_FOUND' && (
            <div className="p-4 bg-slate-50 dark:bg-slate-800 rounded-lg text-xs text-slate-500 text-center">
              No registered student found for this APAAR ID in the repository.
            </div>
          )}

          {lookupResult.result === 'FOUND' && (
            <div className="space-y-4">
              {/* Consent alert if required */}
              {lookupResult.consentRequired && (
                <div className="p-4 bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800 rounded-xl text-xs text-amber-900 dark:text-amber-200 flex items-start justify-between gap-3">
                  <div className="flex items-start gap-2.5">
                    <Clock className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
                    <div>
                      <div className="font-bold text-sm">Student Consent Required for Tier {lookupResult.tierRequested}</div>
                      <div className="mt-1 opacity-90">
                        Under DPDP Act 2023 principles, disclosing candidate personal details requires active student authorization.
                        A digital consent request has been transmitted to the student's portal.
                      </div>
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => lookupMutation.mutate()}
                    loading={lookupMutation.isPending}
                    icon={<RefreshCw className="h-3.5 w-3.5" />}
                  >
                    Re-check
                  </Button>
                </div>
              )}

              {/* Scoped Information Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                <div className="p-3 rounded-lg border border-slate-200 dark:border-slate-800">
                  <span className="text-slate-400">APAAR ID</span>
                  <div className="font-mono font-bold text-sm mt-0.5">{lookupResult.data?.apaarMasked}</div>
                </div>

                <div className="p-3 rounded-lg border border-slate-200 dark:border-slate-800">
                  <span className="text-slate-400">Enrolment Status</span>
                  <div className="mt-0.5"><StatusBadge status={lookupResult.data?.status} /></div>
                </div>

                <div className="p-3 rounded-lg border border-slate-200 dark:border-slate-800">
                  <span className="text-slate-400">Awarding Institution</span>
                  <div className="font-semibold text-slate-800 dark:text-slate-200 mt-0.5">
                    {lookupResult.data?.institutionName} ({lookupResult.data?.institutionAicteId})
                  </div>
                </div>

                <div className="p-3 rounded-lg border border-slate-200 dark:border-slate-800">
                  <span className="text-slate-400">Enrolled Program</span>
                  <div className="font-semibold text-slate-800 dark:text-slate-200 mt-0.5">
                    {lookupResult.data?.programName} ({lookupResult.data?.programCode})
                  </div>
                </div>

                {/* L2 Fields */}
                {lookupResult.data?.fullName && (
                  <div className="p-3 rounded-lg border border-emerald-200 dark:border-emerald-800 bg-emerald-50/40 dark:bg-emerald-950/20 sm:col-span-2">
                    <span className="text-emerald-700 dark:text-emerald-300 font-semibold">Tier L2 Verified Personal Credentials:</span>
                    <div className="flex flex-wrap gap-6 mt-1 text-slate-800 dark:text-slate-200">
                      <div>Legal Name: <strong>{lookupResult.data.fullName}</strong></div>
                      <div>Student ID: <strong className="font-mono">{lookupResult.data.instituteStudentId}</strong></div>
                      {lookupResult.data.dob && <div>DOB: <strong>{lookupResult.data.dob}</strong></div>}
                    </div>
                  </div>
                )}
              </div>

              {/* Digital Records list with link to public verification */}
              {lookupResult.records && lookupResult.records.length > 0 && (
                <div className="pt-3 border-t border-slate-200 dark:border-slate-800">
                  <div className="text-xs font-semibold text-slate-700 dark:text-slate-300 mb-2">
                    Linked Cryptographic Verifiable Records:
                  </div>
                  {lookupResult.records.map((rec) => (
                    <div key={rec.id} className="p-2.5 rounded-lg border border-slate-200 dark:border-slate-800 flex items-center justify-between text-xs">
                      <div>
                        <span className="font-mono font-semibold text-indigo-600 dark:text-indigo-400">JTI: {rec.jti}</span>
                        <div className="text-[10px] text-slate-400">Issued: {new Date(rec.issuedAt).toLocaleDateString()}</div>
                      </div>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => window.open(`/v/${rec.jti}`, '_blank')}
                        icon={<ExternalLink className="h-3 w-3" />}
                      >
                        Public Verify View
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
