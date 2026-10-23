import { useState, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Papa from 'papaparse';
import {
  UploadCloud, FileSpreadsheet, CheckCircle2, AlertTriangle,
  Download, FileCheck, Layers, RefreshCw
} from 'lucide-react';
import { api, getToken } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  StatusBadge
} from '../../components/ui';
import type { Batch } from '../../lib/types';

const REQUIRED_HEADERS = [
  'apaar_id', 'institute_student_id', 'full_name', 'dob', 'gender',
  'nationality', 'residency_status', 'admission_quota', 'social_category',
  'program_code', 'entry_type', 'admission_date', 'academic_year'
];

export default function UploadPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [previewRows, setPreviewRows] = useState<any[]>([]);
  const [headerErrors, setHeaderErrors] = useState<string[]>([]);
  const [completedBatch, setCompletedBatch] = useState<Batch | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleFileChange = (selected: File) => {
    setFile(selected);
    setCompletedBatch(null);
    setErrorMsg(null);
    setHeaderErrors([]);

    if (selected.name.endsWith('.csv')) {
      Papa.parse(selected, {
        header: true,
        preview: 10,
        skipEmptyLines: true,
        complete: (results) => {
          setPreviewRows(results.data);
          const headers = (results.meta.fields || []).map((h) => h.toLowerCase().trim());
          const missing = REQUIRED_HEADERS.filter((rh) => !headers.includes(rh));
          if (missing.length > 0) {
            setHeaderErrors(missing);
          }
        },
      });
    } else {
      setPreviewRows([]);
    }
  };

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('No file selected');
      const formData = new FormData();
      formData.append('file', file);
      if (user?.institutionId) {
        formData.append('institutionId', user.institutionId);
      }
      return api.post<Batch>('/uploads', formData);
    },
    onSuccess: (data) => {
      setCompletedBatch(data);
      queryClient.invalidateQueries({ queryKey: ['my-institution-meters'] });
      queryClient.invalidateQueries({ queryKey: ['my-institution-quality'] });
    },
    onError: (err: any) => {
      setErrorMsg(err?.detail || err?.message || 'Upload processing failed');
    },
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Student Data Ingestion & APAAR Verification"
        subtitle="Batch ingestion with automated registry validation, duplicate identity checks & intake cap enforcement"
        actions={
          <Button
            size="sm"
            variant="secondary"
            onClick={() => window.open('/api/v1/uploads/template.csv', '_blank')}
            icon={<Download className="h-4 w-4" />}
          >
            Download CSV Template
          </Button>
        }
      />

      {/* Upload Dropzone */}
      <Card title="Upload Student Intake File (CSV or XLSX)">
        <div
          onClick={() => fileInputRef.current?.click()}
          className="border-2 border-dashed border-slate-300 dark:border-slate-700 hover:border-indigo-500 dark:hover:border-indigo-400 rounded-xl p-8 text-center cursor-pointer transition-colors bg-slate-50/50 dark:bg-slate-900/50"
        >
          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,.xlsx"
            className="hidden"
            onChange={(e) => e.target.files?.[0] && handleFileChange(e.target.files[0])}
          />
          <UploadCloud className="h-10 w-10 text-indigo-500 mx-auto mb-3" />
          <div className="text-sm font-semibold text-slate-800 dark:text-slate-200">
            {file ? file.name : 'Click to browse or drag and drop intake spreadsheet'}
          </div>
          <div className="text-xs text-slate-400 mt-1">
            Supports CSV or XLSX up to 20 MB (up to 50,000 records per batch)
          </div>
        </div>

        {/* Header verification alert */}
        {headerErrors.length > 0 && (
          <div className="mt-4 p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 rounded-lg text-xs text-red-700 dark:text-red-300 flex items-start gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
            <div>
              <div className="font-bold">Missing Required Standard Template Columns:</div>
              <div className="mt-0.5">{headerErrors.join(', ')}</div>
            </div>
          </div>
        )}

        {errorMsg && (
          <div className="mt-4 p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 rounded-lg text-xs text-red-700 dark:text-red-300">
            {errorMsg}
          </div>
        )}

        {file && (
          <div className="mt-4 flex items-center justify-between">
            <span className="text-xs text-slate-500">
              Selected: <strong>{file.name}</strong> ({(file.size / 1024).toFixed(1)} KB)
            </span>
            <Button
              variant="primary"
              onClick={() => uploadMutation.mutate()}
              loading={uploadMutation.isPending}
              icon={<FileCheck className="h-4 w-4" />}
            >
              Verify & Ingest Batch
            </Button>
          </div>
        )}
      </Card>

      {/* CSV Preview */}
      {previewRows.length > 0 && !completedBatch && (
        <Card title="Client Preview (First 10 Rows)">
          <div className="overflow-x-auto text-xs">
            <table className="min-w-full text-left">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50">
                  {Object.keys(previewRows[0]).slice(0, 8).map((k) => (
                    <th key={k} className="px-2.5 py-1.5 font-semibold text-slate-600 dark:text-slate-300">{k}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {previewRows.map((r, i) => (
                  <tr key={i} className="border-b border-slate-100 dark:border-slate-800/60">
                    {Object.values(r).slice(0, 8).map((v: any, j) => (
                      <td key={j} className="px-2.5 py-1.5 text-slate-700 dark:text-slate-300 truncate max-w-[140px]">{String(v)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Batch Processing Completed Result Card */}
      {completedBatch && (
        <Card
          title="Batch Processing & Regulatory Verification Outcome"
          actions={
            <StatusBadge status={completedBatch.status} />
          }
        >
          {/* Outcome Counters */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5">
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-800 border text-center">
              <div className="text-xs text-slate-500">Total Rows</div>
              <div className="text-xl font-bold font-mono">{completedBatch.totalRows}</div>
            </div>
            <div className="p-3 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 text-center">
              <div className="text-xs text-emerald-700 dark:text-emerald-300">Accepted & Verified</div>
              <div className="text-xl font-bold font-mono text-emerald-600">{completedBatch.acceptedRows}</div>
            </div>
            <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-950/40 border border-amber-200 text-center">
              <div className="text-xs text-amber-700 dark:text-amber-300">Flagged Under Review</div>
              <div className="text-xl font-bold font-mono text-amber-600">{completedBatch.flaggedRows}</div>
            </div>
            <div className="p-3 rounded-lg bg-red-50 dark:bg-red-950/40 border border-red-200 text-center">
              <div className="text-xs text-red-700 dark:text-red-300">Rejected (Worklist)</div>
              <div className="text-xl font-bold font-mono text-red-600">{completedBatch.rejectedRows}</div>
            </div>
          </div>

          {/* Download Error Report button */}
          {(completedBatch.rejectedRows > 0 || completedBatch.flaggedRows > 0) && (
            <div className="flex items-center justify-between p-3 rounded-lg bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 mb-5">
              <div className="text-xs text-red-800 dark:text-red-200">
                Discrepancies identified during ingestion. Download the detailed error report containing line numbers, failed rule codes, and correction hints.
              </div>
              <Button
                size="sm"
                variant="danger"
                onClick={() => window.open(`/api/v1/uploads/${completedBatch.id}/errors.csv?access_token=${getToken()}`, '_blank')}
                icon={<Download className="h-3.5 w-3.5" />}
              >
                Download Error Report CSV
              </Button>
            </div>
          )}

          {/* Issue Summary breakdown */}
          {completedBatch.issueSummary && completedBatch.issueSummary.length > 0 && (
            <div>
              <h4 className="text-xs font-semibold text-slate-800 dark:text-slate-200 mb-2">Discrepancy Breakdown by Rule</h4>
              <Table>
                <thead>
                  <tr>
                    <Th>Rule Code</Th>
                    <Th>Severity</Th>
                    <Th right>Count</Th>
                  </tr>
                </thead>
                <tbody>
                  {completedBatch.issueSummary.map((iss: any, i: number) => (
                    <Tr key={i}>
                      <Td mono className="font-semibold text-indigo-600">{iss.ruleCode}</Td>
                      <Td><SeverityBadge severity={iss.severity} /></Td>
                      <Td right className="tabular-nums font-mono font-bold">{iss.count}</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
