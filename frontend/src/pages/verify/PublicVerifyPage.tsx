import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, ShieldAlert, ShieldCheck, ExternalLink, ArrowLeft } from 'lucide-react';
import { api } from '../../lib/api';
import { Card, Badge, Button } from '../../components/ui';

export default function PublicVerifyPage() {
  const { jti } = useParams<{ jti: string }>();

  const { data: record, isLoading } = useQuery({
    queryKey: ['public-verify', jti],
    queryFn: () => api.get<any>(`/verify/${jti}`),
    enabled: !!jti,
  });

  return (
    <div className="min-h-screen bg-slate-100 dark:bg-slate-950 flex flex-col justify-center py-12 px-4 sm:px-6 lg:px-8">
      {/* Synthetic Banner */}
      <div className="max-w-md w-full mx-auto mb-4 bg-amber-500 text-white text-xs px-4 py-2 rounded-lg shadow-sm text-center font-medium">
        AICTE VidyaSetu Public Digital Verification Gateway
      </div>

      <div className="max-w-md w-full mx-auto">
        <Card className="p-6 text-center shadow-lg">
          <div className="inline-flex h-12 w-12 rounded-xl bg-indigo-600 text-white items-center justify-center font-bold text-xl shadow-md mb-3">
            VS
          </div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">
            Digital Verifiable Record
          </h2>
          <p className="text-xs text-slate-500 font-mono mt-1 break-all">
            JTI: {jti}
          </p>

          <div className="my-6 pt-6 border-t border-slate-200 dark:border-slate-800">
            {isLoading ? (
              <div className="py-8 text-xs text-slate-400">Verifying cryptographic signature against JWKS...</div>
            ) : !record || record.status === 'NOT_FOUND' ? (
              <div className="p-4 bg-red-50 dark:bg-red-950/40 rounded-xl text-red-700 dark:text-red-300">
                <ShieldAlert className="h-8 w-8 text-red-600 mx-auto mb-2" />
                <div className="font-bold text-sm">Record Not Found</div>
                <div className="text-xs mt-1">This credential identifier does not exist in the repository.</div>
              </div>
            ) : (
              <div className="space-y-4">
                {/* Big Status Badge */}
                <div className={`p-4 rounded-xl border ${
                  record.status === 'VALID'
                    ? 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-300 dark:border-emerald-800 text-emerald-900 dark:text-emerald-200'
                    : 'bg-red-50 dark:bg-red-950/40 border-red-300 dark:border-red-800 text-red-900 dark:text-red-200'
                }`}>
                  <div className="flex items-center justify-center gap-2">
                    {record.status === 'VALID' ? (
                      <CheckCircle2 className="h-6 w-6 text-emerald-600 dark:text-emerald-400" />
                    ) : (
                      <ShieldAlert className="h-6 w-6 text-red-600 dark:text-red-400" />
                    )}
                    <span className="text-lg font-bold tracking-tight">STATUS: {record.status}</span>
                  </div>
                  <div className="text-xs opacity-90 mt-1">
                    {record.signatureValid
                      ? 'Cryptographic ES256 signature verified against public JWKS authority.'
                      : 'Digital signature invalid or untrusted.'}
                  </div>
                </div>

                {/* Details */}
                <div className="text-left text-xs space-y-2 p-3 bg-slate-50 dark:bg-slate-800/50 rounded-lg">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Academic APAAR:</span>
                    <span className="font-mono font-bold">{record.apaarMasked}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Issuer Authority:</span>
                    <span className="font-semibold">{record.issuer || 'VidyaSetu Platform'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Issued On:</span>
                    <span>{record.issuedAt ? new Date(record.issuedAt).toLocaleDateString() : '-'}</span>
                  </div>
                  {record.revokedAt && (
                    <div className="flex justify-between text-red-600 font-bold">
                      <span>Revoked On:</span>
                      <span>{new Date(record.revokedAt).toLocaleDateString()}</span>
                    </div>
                  )}
                  {record.institutionAicteId && (
                    <div className="flex justify-between pt-2 border-t border-slate-200 dark:border-slate-700">
                      <span className="text-slate-500">Institution:</span>
                      <span className="font-semibold">{record.institutionAicteId}</span>
                    </div>
                  )}
                  {record.programCode && (
                    <div className="flex justify-between">
                      <span className="text-slate-500">Program:</span>
                      <span className="font-semibold">{record.programCode}</span>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className="mt-4 pt-4 border-t border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs">
            <Link to="/login" className="text-indigo-600 dark:text-indigo-400 hover:underline flex items-center gap-1">
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>Back to Portal Login</span>
            </Link>
            <a
              href="/.well-known/jwks.json"
              target="_blank"
              className="text-slate-400 hover:text-slate-600 flex items-center gap-1"
            >
              <span>JWKS Endpoint</span>
              <ExternalLink className="h-3 w-3" />
            </a>
          </div>
        </Card>
      </div>
    </div>
  );
}
