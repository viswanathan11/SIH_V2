import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Key, Sliders, Plus, Check, Copy, Shield, AlertCircle } from 'lucide-react';
import { api } from '../../lib/api';
import { Card, PageHeader, Table, Th, Td, Tr, Button, Badge, Modal, Input, Select } from '../../components/ui';
import type { PolicyConfig, ApiClient } from '../../lib/types';

export default function AdminPage() {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'users' | 'apiclients' | 'policy'>('users');

  // Create User modal state
  const [userModalOpen, setUserModalOpen] = useState(false);
  const [userEmail, setUserEmail] = useState('');
  const [userFullName, setUserFullName] = useState('');
  const [userRole, setUserRole] = useState('INSTITUTION_OPERATOR');
  const [userPassword, setUserPassword] = useState('Demo@12345');

  // Create API Client modal state
  const [clientModalOpen, setClientModalOpen] = useState(false);
  const [clientName, setClientName] = useState('');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [createdSecret, setCreatedSecret] = useState<{ keyId: string; secret: string } | null>(null);

  // Edit Policy state
  const [editPolicyKey, setEditPolicyKey] = useState<string | null>(null);
  const [editPolicyVal, setEditPolicyVal] = useState('');

  // Queries
  const { data: users } = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => api.get<any[]>('/users'),
  });

  const { data: apiClients } = useQuery({
    queryKey: ['admin-api-clients'],
    queryFn: () => api.get<ApiClient[]>('/api-clients'),
  });

  const { data: policies } = useQuery({
    queryKey: ['admin-policy'],
    queryFn: () => api.get<PolicyConfig[]>('/policy'),
  });

  // Mutations
  const createUserMutation = useMutation({
    mutationFn: () => api.post('/users', { email: userEmail, fullName: userFullName, password: userPassword, role: userRole }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setUserModalOpen(false);
      setUserEmail('');
      setUserFullName('');
    },
  });

  const createClientMutation = useMutation({
    mutationFn: () => api.post<any>('/api-clients', { name: clientName, webhookUrl, scopes: ['student:write', 'upload:create', 'upload:read'] }),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['admin-api-clients'] });
      setClientModalOpen(false);
      setCreatedSecret({ keyId: data.keyId, secret: data.secret });
      setClientName('');
      setWebhookUrl('');
    },
  });

  const updatePolicyMutation = useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      api.put(`/policy/${key}`, { value }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-policy'] });
      setEditPolicyKey(null);
    },
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="AICTE Administration & Policy Governance"
        subtitle="Manage authorized staff accounts, machine API clients (HMAC), and national policy thresholds"
      />

      {/* Tabs */}
      <div className="flex border-b border-slate-200 dark:border-slate-800">
        <button
          onClick={() => setActiveTab('users')}
          className={`pb-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-2 ${
            activeTab === 'users'
              ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <Users className="h-4 w-4" />
          <span>Users & RBAC ({users?.length ?? 0})</span>
        </button>
        <button
          onClick={() => setActiveTab('apiclients')}
          className={`pb-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-2 ${
            activeTab === 'apiclients'
              ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <Key className="h-4 w-4" />
          <span>API Clients & Webhooks ({apiClients?.length ?? 0})</span>
        </button>
        <button
          onClick={() => setActiveTab('policy')}
          className={`pb-2.5 px-4 text-xs font-semibold border-b-2 flex items-center gap-2 ${
            activeTab === 'policy'
              ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          <Sliders className="h-4 w-4" />
          <span>Policy Configuration ({policies?.length ?? 0})</span>
        </button>
      </div>

      {activeTab === 'users' && (
        <Card
          title="Authorized User Accounts"
          actions={
            <Button size="sm" variant="primary" onClick={() => setUserModalOpen(true)} icon={<Plus className="h-4 w-4" />}>
              Add User
            </Button>
          }
        >
          <Table>
            <thead>
              <tr>
                <Th>Full Name</Th>
                <Th>Email Address</Th>
                <Th>Assigned Role</Th>
                <Th>Institution</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {users?.map((u) => (
                <Tr key={u.id}>
                  <Td className="font-semibold text-slate-800 dark:text-slate-200">{u.fullName}</Td>
                  <Td className="text-xs">{u.email}</Td>
                  <Td>
                    {u.roles?.map((r: string) => (
                      <Badge key={r} color="accent" className="mr-1">{r}</Badge>
                    ))}
                  </Td>
                  <Td mono className="text-xs">{u.institutionCode || 'National / Platform'}</Td>
                  <Td><Badge color="green">{u.status}</Badge></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}

      {activeTab === 'apiclients' && (
        <Card
          title="Machine API Clients (HMAC Auth for Institutional ERPs)"
          actions={
            <Button size="sm" variant="primary" onClick={() => setClientModalOpen(true)} icon={<Plus className="h-4 w-4" />}>
              Register ERP Client
            </Button>
          }
        >
          <Table>
            <thead>
              <tr>
                <Th>Client Name</Th>
                <Th>Key ID</Th>
                <Th>Institution</Th>
                <Th>Allowed Scopes</Th>
                <Th>Webhook URL</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {apiClients?.map((c) => (
                <Tr key={c.id}>
                  <Td className="font-semibold">{c.name}</Td>
                  <Td mono className="font-semibold text-xs text-indigo-600 dark:text-indigo-400">{c.keyId}</Td>
                  <Td mono className="text-xs">{c.institutionCode || '-'}</Td>
                  <Td className="text-[11px] text-slate-500">{Array.isArray(c.scopes) ? c.scopes.join(', ') : c.scopes}</Td>
                  <Td className="text-xs max-w-xs truncate">{c.webhookUrl || 'Not configured'}</Td>
                  <Td><Badge color="green">{c.status}</Badge></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}

      {activeTab === 'policy' && (
        <Card title="AICTE Regulatory Policy Defaults & Risk Thresholds">
          <Table>
            <thead>
              <tr>
                <Th>Policy Configuration Key</Th>
                <Th>Configured Value</Th>
                <Th>Regulatory Description</Th>
                <Th>Updated By</Th>
                <Th>Action</Th>
              </tr>
            </thead>
            <tbody>
              {policies?.map((p) => (
                <Tr key={p.key}>
                  <Td mono className="font-semibold text-xs text-slate-800 dark:text-slate-200">{p.key}</Td>
                  <Td mono className="font-bold text-xs text-indigo-600 dark:text-indigo-400">{String(p.value)}</Td>
                  <Td className="text-xs text-slate-600 dark:text-slate-400 max-w-md">{p.description}</Td>
                  <Td className="text-[11px] text-slate-400">{p.updatedBy}</Td>
                  <Td>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => { setEditPolicyKey(p.key); setEditPolicyVal(String(p.value)); }}
                    >
                      Edit
                    </Button>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}

      {/* Secret Reveal Modal */}
      {createdSecret && (
        <Modal
          open={!!createdSecret}
          onClose={() => setCreatedSecret(null)}
          title="API Client Credentials Created"
        >
          <div className="space-y-4">
            <div className="p-3 bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800 rounded-lg text-xs text-amber-800 dark:text-amber-300">
              Copy this client secret now. It will NEVER be displayed again in plaintext.
            </div>
            <div>
              <label className="text-xs text-slate-500">Key ID</label>
              <div className="font-mono text-xs font-bold p-2 bg-slate-100 dark:bg-slate-800 rounded">{createdSecret.keyId}</div>
            </div>
            <div>
              <label className="text-xs text-slate-500">HMAC Secret</label>
              <div className="font-mono text-xs font-bold p-2 bg-slate-100 dark:bg-slate-800 rounded select-all break-all">{createdSecret.secret}</div>
            </div>
            <div className="flex justify-end pt-2">
              <Button variant="primary" onClick={() => setCreatedSecret(null)}>Done</Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Edit Policy Modal */}
      {editPolicyKey && (
        <Modal
          open={!!editPolicyKey}
          onClose={() => setEditPolicyKey(null)}
          title={`Edit Policy Parameter — ${editPolicyKey}`}
        >
          <div className="space-y-4">
            <Input
              label="Policy Parameter Value"
              value={editPolicyVal}
              onChange={(e) => setEditPolicyVal(e.target.value)}
            />
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setEditPolicyKey(null)}>Cancel</Button>
              <Button
                variant="primary"
                onClick={() => updatePolicyMutation.mutate({ key: editPolicyKey, value: editPolicyVal })}
                loading={updatePolicyMutation.isPending}
              >
                Save Parameter
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Add User Modal */}
      {userModalOpen && (
        <Modal
          open={userModalOpen}
          onClose={() => setUserModalOpen(false)}
          title="Create New User Account"
        >
          <div className="space-y-3">
            <Input label="Full Name" required value={userFullName} onChange={(e) => setUserFullName(e.target.value)} />
            <Input label="Official Email" type="email" required value={userEmail} onChange={(e) => setUserEmail(e.target.value)} />
            <Input label="Password" type="password" required value={userPassword} onChange={(e) => setUserPassword(e.target.value)} />
            <div>
              <label className="block text-xs font-semibold mb-1">Role</label>
              <Select value={userRole} onChange={(e) => setUserRole(e.target.value)} className="text-xs">
                <option value="AICTE_ADMIN">AICTE Administrator</option>
                <option value="AICTE_ANALYST">AICTE Analyst</option>
                <option value="AICTE_AUDITOR">AICTE Auditor</option>
                <option value="INSTITUTION_ADMIN">Institution Administrator</option>
                <option value="INSTITUTION_OPERATOR">Institution Operator</option>
                <option value="REGULATOR">Regulator</option>
              </Select>
            </div>
            <div className="flex justify-end gap-2 pt-3">
              <Button variant="secondary" onClick={() => setUserModalOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={() => createUserMutation.mutate()} loading={createUserMutation.isPending}>
                Create User
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Add API Client Modal */}
      {clientModalOpen && (
        <Modal
          open={clientModalOpen}
          onClose={() => setClientModalOpen(false)}
          title="Register New ERP API Client"
        >
          <div className="space-y-3">
            <Input label="Client Application Name" required value={clientName} onChange={(e) => setClientName(e.target.value)} placeholder="e.g. Campus ERP Batch Ingester" />
            <Input label="Webhook URL (Optional)" value={webhookUrl} onChange={(e) => setWebhookUrl(e.target.value)} placeholder="https://erp.college.edu/webhooks/vidyasetu" />
            <div className="flex justify-end gap-2 pt-3">
              <Button variant="secondary" onClick={() => setClientModalOpen(false)}>Cancel</Button>
              <Button variant="primary" onClick={() => createClientMutation.mutate()} loading={createClientMutation.isPending}>
                Register Client
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
