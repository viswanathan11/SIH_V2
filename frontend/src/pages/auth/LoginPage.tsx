import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { LogIn, KeyRound, UserCheck, ShieldCheck, Sparkles, Building, Briefcase } from 'lucide-react';
import { api, setToken } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { homeFor } from '../../lib/roles';
import { Button, Input, Card, Badge } from '../../components/ui';
import type { AuthResponse, DemoAccount } from '../../lib/types';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const [tab, setTab] = useState<'staff' | 'student'>('staff');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [apaarId, setApaarId] = useState('');
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load demo accounts
  const { data: demoAccounts } = useQuery({
    queryKey: ['demo-accounts'],
    queryFn: () => api.get<DemoAccount[]>('/public/demo-accounts'),
    staleTime: 600000,
  });

  const handleStaffLogin = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await api.post<AuthResponse>('/auth/login', { email, password });
      login(res);
      const next = params.get('next') || homeFor(res.user);
      navigate(next);
    } catch (err: any) {
      setError(err?.detail || err?.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRequestOtp = async () => {
    if (!apaarId.trim()) return;
    setError(null);
    setLoading(true);
    try {
      await api.post('/auth/student/otp-request', { apaarId: apaarId.trim() });
      setOtpSent(true);
      setOtp('123456'); // Pre-fill demo OTP
    } catch (err: any) {
      setError(err?.detail || 'Failed to request OTP');
    } finally {
      setLoading(false);
    }
  };

  const handleStudentLogin = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await api.post<AuthResponse>('/auth/student/otp-verify', { apaarId: apaarId.trim(), otp });
      login(res);
      navigate('/me');
    } catch (err: any) {
      setError(err?.detail || err?.message || 'Student login failed');
    } finally {
      setLoading(false);
    }
  };

  const quickLogin = async (account: DemoAccount) => {
    setError(null);
    setLoading(true);
    try {
      if (account.role === 'STUDENT') {
        const res = await api.post<AuthResponse>('/auth/student/otp-verify', {
          apaarId: account.apaarId,
          otp: account.otp || '123456'
        });
        login(res);
        navigate('/me');
      } else {
        const res = await api.post<AuthResponse>('/auth/login', {
          email: account.email,
          password: account.password || 'Demo@12345'
        });
        login(res);
        navigate(homeFor(res.user));
      }
    } catch (err: any) {
      setError(err?.detail || 'Quick login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-100 dark:bg-slate-950 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      {/* Synthetic Banner */}
      <div className="max-w-md w-full mx-auto mb-4 bg-amber-500 text-white text-xs px-4 py-2 rounded-lg shadow-sm text-center font-medium">
        Demo Environment — AICTE APAAR Student Data Verification System
      </div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md text-center">
        <div className="inline-flex h-12 w-12 rounded-xl bg-indigo-600 text-white items-center justify-center font-bold text-xl shadow-md">
          VS
        </div>
        <h2 className="mt-3 text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
          VidyaSetu Portal
        </h2>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          Authorized Student Record Management & Verification
        </p>
      </div>

      <div className="mt-6 sm:mx-auto sm:w-full sm:max-w-md">
        <Card className="p-6">
          {/* Tabs */}
          <div className="flex border-b border-slate-200 dark:border-slate-800 mb-5">
            <button
              onClick={() => { setTab('staff'); setError(null); }}
              className={`flex-1 pb-2.5 text-xs font-semibold text-center border-b-2 transition-colors ${
                tab === 'staff'
                  ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Institutional & AICTE Staff
            </button>
            <button
              onClick={() => { setTab('student'); setError(null); }}
              className={`flex-1 pb-2.5 text-xs font-semibold text-center border-b-2 transition-colors ${
                tab === 'student'
                  ? 'border-indigo-600 text-indigo-600 dark:text-indigo-400'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              Student Portal (APAAR)
            </button>
          </div>

          {error && (
            <div className="mb-4 p-2.5 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 rounded-md text-xs text-red-700 dark:text-red-300">
              {error}
            </div>
          )}

          {tab === 'staff' ? (
            <form onSubmit={handleStaffLogin} className="space-y-4">
              <Input
                label="Official Email Address"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="e.g. admin.tn001@demo.vidyasetu.example"
              />
              <Input
                label="Password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
              <Button type="submit" loading={loading} className="w-full mt-2" icon={<LogIn className="h-4 w-4" />}>
                Sign In
              </Button>
            </form>
          ) : (
            <form onSubmit={handleStudentLogin} className="space-y-4">
              <Input
                label="12-digit APAAR ID"
                required
                value={apaarId}
                onChange={(e) => setApaarId(e.target.value)}
                placeholder="e.g. 910000000001"
              />

              {otpSent ? (
                <>
                  <Input
                    label="One-Time Password (OTP)"
                    required
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                    placeholder="Demo OTP: 123456"
                  />
                  <Button type="submit" loading={loading} className="w-full mt-2" icon={<UserCheck className="h-4 w-4" />}>
                    Verify & Enter Portal
                  </Button>
                </>
              ) : (
                <Button
                  type="button"
                  onClick={handleRequestOtp}
                  loading={loading}
                  className="w-full mt-2"
                  icon={<KeyRound className="h-4 w-4" />}
                >
                  Send OTP to Registered Mobile
                </Button>
              )}
            </form>
          )}

          {/* Quick Demo Access Switcher */}
          {demoAccounts && demoAccounts.length > 0 && (
            <div className="mt-6 pt-5 border-t border-slate-200 dark:border-slate-800">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 mb-3">
                <Sparkles className="h-3.5 w-3.5 text-amber-500" />
                <span>1-Click Demo Accounts (Judges / Reviewers)</span>
              </div>
              <div className="grid grid-cols-1 gap-1.5 max-h-56 overflow-y-auto pr-1">
                {demoAccounts.map((acc, idx) => (
                  <button
                    key={idx}
                    type="button"
                    onClick={() => quickLogin(acc)}
                    disabled={loading}
                    className="flex items-center justify-between p-2 rounded-lg border border-slate-200 dark:border-slate-700 hover:bg-indigo-50 dark:hover:bg-slate-800 text-left text-xs transition-colors group"
                  >
                    <div className="truncate">
                      <div className="font-semibold text-slate-800 dark:text-slate-200 group-hover:text-indigo-600 dark:group-hover:text-indigo-400">
                        {acc.label}
                      </div>
                      <div className="text-[10px] text-slate-400 font-mono">
                        {acc.role === 'STUDENT' ? `APAAR: ${acc.apaarId}` : acc.email}
                      </div>
                    </div>
                    <Badge color="slate" className="shrink-0 text-[10px]">{acc.group}</Badge>
                  </button>
                ))}
              </div>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
