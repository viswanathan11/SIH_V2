import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import {
  Users, Search, ShieldCheck, FileText, CheckCircle2,
  ExternalLink, QrCode, Download, Eye
} from 'lucide-react';
import { api, getToken } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import {
  Card, PageHeader, Table, Th, Td, Tr, Button, Badge, SeverityBadge,
  StatusBadge, Modal, Input
} from '../../components/ui';
import type { Enrolment, IssuedRecord } from '../../lib/types';

export default function StudentsPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [selectedEnrolment, setSelectedEnrolment] = useState<Enrolment | null>(null);
  const [issuedRecordModal, setIssuedRecordModal] = useState<IssuedRecord | null>(null);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['students-enrolments', user?.institutionId, search],
    queryFn: () => {
      const q = new URLSearchParams();
      if (user?.institutionId) q.set('institutionId', user.institutionId);
      if (search.trim()) q.set('q', search.trim());
      q.set('size', '50');
      return api.get<{ content: Enrolment[]; totalElements: number }>(`/enrolments?${q.toString()}`);
    },
  });

  const issueRecordMutation = useMutation({
    mutationFn: (enrolmentId: string) =>
      api.post<IssuedRecord>('/records/issue', { enrolmentId }),
    onSuccess: (record) => {
      setIssuedRecordModal(record);
      queryClient.invalidateQueries({ queryKey: ['students-enrolments'] });
    },
  });

  const openCertificate = (enrolmentId: string) => {
    const token = getToken();
    const url = `/api/v1/certificates/preview?enrolmentId=${enrolmentId}&access_token=${token}`;
    window.open(url, '_blank');
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Student Enrolments & Verifiable Academic Records"
        subtitle="Manage admitted students, issue tamper-evident JWS verifiable credentials with QR codes and certificates"
      />

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div className="max-w-md w-full">
            <Input
              placeholder="Search by student name, APAAR ID, or student ID..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="text-xs"
            />
          </div>
          <span className="text-xs text-slate-500">
            {pageData?.totalElements ?? 0} students enrolled
          </span>
        </div>

        <Table>
          <thead>
            <tr>
              <Th>Student ID</Th>
              <Th>APAAR ID</Th>
              <Th>Full Name</Th>
              <Th>Program</Th>
              <Th>Admission Date</Th>
              <Th>Quota & Seat</Th>
              <Th>Status</Th>
              <Th>Actions</Th>
            </tr>
          </thead>
          <tbody>
            {!pageData?.content || pageData.content.length === 0 ? (
              <tr>
                <Td colSpan={8} className="text-center py-8 text-slate-400">
                  No students found.
                </Td>
              </tr>
            ) : (
              pageData.content.map((enr) => (
                <Tr key={enr.id} onClick={() => setSelectedEnrolment(enr)}>
                  <Td mono className="font-semibold text-xs">{enr.instituteStudentId || '-'}</Td>
                  <Td mono className="text-xs text-indigo-600 dark:text-indigo-400 font-bold">
                    {enr.apaarId || enr.apaarMasked}
                  </Td>
                  <Td className="font-medium text-xs">{enr.fullName}</Td>
                  <Td mono className="text-xs">{enr.programCode}</Td>
                  <Td className="text-xs text-slate-500">{enr.admissionDate || '-'}</Td>
                  <Td>
                    <Badge color={enr.seatClass === 'EXCESS' ? 'red' : enr.seatClass === 'SUPERNUMERARY' ? 'accent' : 'slate'}>
                      {enr.seatClass}
                    </Badge>
                  </Td>
                  <Td><StatusBadge status={enr.status} /></Td>
                  <Td>
                    <div className="flex items-center gap-1.5" onClick={(e) => e.stopPropagation()}>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => issueRecordMutation.mutate(enr.id)}
                        loading={issueRecordMutation.isPending}
                        icon={<QrCode className="h-3 w-3" />}
                        title="Issue signed verifiable record with QR code"
                      >
                        Issue Record
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => openCertificate(enr.id)}
                        icon={<FileText className="h-3 w-3" />}
                        title="Download signed enrolment certificate PDF"
                      >
                        Certificate
                      </Button>
                    </div>
                  </Td>
                </Tr>
              ))
            )}
          </tbody>
        </Table>
      </Card>

      {/* Verifiable Record QR Modal */}
      {issuedRecordModal && (
        <Modal
          open={!!issuedRecordModal}
          onClose={() => setIssuedRecordModal(null)}
          title="Verifiable Student Record Issued (ES256)"
        >
          <div className="space-y-4 text-center">
            <div className="p-4 bg-white dark:bg-slate-900 rounded-xl inline-block border border-slate-200 dark:border-slate-800 shadow-sm mx-auto">
              <QRCodeSVG
                value={`${window.location.origin}/v/${issuedRecordModal.jti}`}
                size={180}
                level="M"
              />
            </div>

            <div>
              <div className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                Offline-Verifiable Digital Record JTI
              </div>
              <div className="font-mono text-xs text-slate-500 mt-0.5 break-all">
                {issuedRecordModal.jti}
              </div>
              <div className="text-[11px] text-emerald-600 dark:text-emerald-400 font-semibold mt-1 flex items-center justify-center gap-1">
                <CheckCircle2 className="h-3.5 w-3.5" />
                <span>Signed with AICTE Repository Platform Private Key</span>
              </div>
            </div>

            <div className="flex justify-center gap-2 pt-2 border-t border-slate-200 dark:border-slate-800">
              <Button
                variant="secondary"
                onClick={() => window.open(`/v/${issuedRecordModal.jti}`, '_blank')}
                icon={<ExternalLink className="h-4 w-4" />}
              >
                Open Public Verify Link
              </Button>
              <Button variant="primary" onClick={() => setIssuedRecordModal(null)}>
                Done
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
