import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Cable, ExternalLink, Key, Check, Copy, Code, Terminal } from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { Card, PageHeader, Table, Th, Td, Tr, Button, Badge } from '../../components/ui';

export default function IntegrationPage() {
  const { user } = useAuth();
  const [copied, setCopied] = useState(false);

  const { data: clients } = useQuery({
    queryKey: ['my-api-clients', user?.institutionId],
    queryFn: () => api.get<any[]>(`/api-clients?institutionId=${user?.institutionId}`),
    enabled: !!user?.institutionId,
  });

  const hmacSnippet = `# HMAC-SHA256 Client Batch Upload Example
CANONICAL="POST\\n/api/v1/students/batch\\n\${TIMESTAMP}\\n\${BODY_SHA256}"
SIGNATURE=$(echo -en "$CANONICAL" | openssl dgst -sha256 -hmac "$CLIENT_SECRET" -hex | cut -d' ' -f2)

curl -X POST "http://localhost:8080/api/v1/students/batch" \\
  -H "Content-Type: application/json" \\
  -H "X-Key-Id: ${clients?.[0]?.keyId || 'demo-erp-tn001'}" \\
  -H "X-Timestamp: \$(date +%s)" \\
  -H "X-Signature: \$SIGNATURE" \\
  -d @students.json`;

  const copyCode = () => {
    navigator.clipboard.writeText(hmacSnippet);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Institution ERP & Machine Integration"
        subtitle="Automate student batch uploads directly from Campus ERP systems using HMAC-SHA256 authenticated REST APIs"
        actions={
          <Button
            size="sm"
            variant="secondary"
            onClick={() => window.open('/swagger-ui.html', '_blank')}
            icon={<ExternalLink className="h-4 w-4" />}
          >
            Swagger UI & OpenAPI Docs
          </Button>
        }
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* API Credentials */}
        <Card title="Institutional API Credentials">
          <p className="text-xs text-slate-500 mb-4">
            Use these machine credentials to configure automated nightly batch uploads from your university Student Information System.
          </p>

          <Table>
            <thead>
              <tr>
                <Th>Client Name</Th>
                <Th>Key ID</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {!clients || clients.length === 0 ? (
                <tr>
                  <Td colSpan={3} className="text-center py-6 text-slate-400">
                    No machine clients registered.
                  </Td>
                </tr>
              ) : (
                clients.map((c) => (
                  <Tr key={c.id}>
                    <Td className="font-semibold text-xs">{c.name}</Td>
                    <Td mono className="font-bold text-xs text-indigo-600 dark:text-indigo-400">{c.keyId}</Td>
                    <Td><Badge color="green">{c.status}</Badge></Td>
                  </Tr>
                ))
              )}
            </tbody>
          </Table>
        </Card>

        {/* Integration Instructions */}
        <Card
          title="HMAC-SHA256 Signing Reference"
          actions={
            <Button size="sm" variant="ghost" onClick={copyCode} icon={copied ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}>
              {copied ? 'Copied' : 'Copy Bash'}
            </Button>
          }
        >
          <pre className="p-3 bg-slate-900 text-slate-100 rounded-lg text-[11px] font-mono overflow-x-auto leading-relaxed">
            {hmacSnippet}
          </pre>
        </Card>
      </div>
    </div>
  );
}
