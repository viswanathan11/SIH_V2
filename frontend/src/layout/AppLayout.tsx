import { useState, useEffect, useRef } from 'react';
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Building2, ShieldAlert, AlertTriangle, FileSpreadsheet,
  FileCheck, Settings, UploadCloud, Wrench, Users, Cable, CheckCircle2,
  UserCheck, Search, Moon, Sun, Globe, LogOut, Radio
} from 'lucide-react';
import { useAuth } from '../lib/auth';
import { navFor, primaryRole } from '../lib/roles';
import { useI18n } from '../lib/i18n';
import { useEvents } from '../lib/events';
import { api } from '../lib/api';
import { ToastContainer } from '../components/Toast';
import { Badge } from '../components/ui';

const iconMap: Record<string, React.ReactNode> = {
  dashboard: <LayoutDashboard className="h-4 w-4" />,
  building: <Building2 className="h-4 w-4" />,
  radar: <ShieldAlert className="h-4 w-4" />,
  alert: <AlertTriangle className="h-4 w-4" />,
  report: <FileSpreadsheet className="h-4 w-4" />,
  shield: <FileCheck className="h-4 w-4" />,
  settings: <Settings className="h-4 w-4" />,
  upload: <UploadCloud className="h-4 w-4" />,
  wrench: <Wrench className="h-4 w-4" />,
  users: <Users className="h-4 w-4" />,
  plug: <Cable className="h-4 w-4" />,
  badge: <CheckCircle2 className="h-4 w-4" />,
  user: <UserCheck className="h-4 w-4" />
};

export default function AppLayout() {
  const { user, logout } = useAuth();
  const { lang, setLang, t } = useI18n();
  const { connected } = useEvents();
  const navigate = useNavigate();
  const location = useLocation();

  const [darkMode, setDarkMode] = useState(() => localStorage.getItem('vs_dark') === '1');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (darkMode) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('vs_dark', '1');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('vs_dark', '0');
    }
  }, [darkMode]);

  // Global search debouncing
  useEffect(() => {
    if (!searchQuery || searchQuery.trim().length < 2) {
      setSearchResults([]);
      setSearchOpen(false);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const res = await api.get<any[]>(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
        setSearchResults(res);
        setSearchOpen(true);
      } catch {
        setSearchResults([]);
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Close search popup on outside click
  useEffect(() => {
    const handleOutside = (e: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setSearchOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, []);

  const navItems = navFor(user);

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100 font-sans">
      {/* Persistent Synthetic Demo Banner */}
      <div className="bg-amber-600 text-white text-xs font-semibold px-4 py-1 flex items-center justify-between tracking-wide select-none shadow-sm">
        <div className="flex items-center gap-2">
          <span className="bg-amber-800 px-1.5 py-0.5 rounded text-[10px] uppercase tracking-wider">Demo Mode</span>
          <span>SYNTHETIC DEMO DATA — All student identities and institutions are simulated for AICTE evaluation.</span>
        </div>
        <div className="text-[11px] opacity-90 hidden sm:block">Academic Year: 2026-27</div>
      </div>

      {/* Top Header */}
      <header className="h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 flex items-center justify-between gap-4 sticky top-0 z-30 shadow-xs">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <div className="h-8 w-8 rounded-lg bg-indigo-600 text-white flex items-center justify-center font-bold text-sm tracking-wider shadow-sm">
              VS
            </div>
            <div>
              <span className="font-bold text-base text-slate-900 dark:text-slate-100 tracking-tight">VidyaSetu</span>
              <span className="ml-2 text-[10px] bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 px-1.5 py-0.5 rounded font-mono border border-slate-200 dark:border-slate-700">AICTE</span>
            </div>
          </div>
        </div>

        {/* Global Search Box */}
        <div ref={searchRef} className="relative flex-1 max-w-md hidden md:block">
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" />
            <input
              type="text"
              placeholder="Search by APAAR ID, student name, institution..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => { if (searchResults.length > 0) setSearchOpen(true); }}
              className="w-full bg-slate-100 dark:bg-slate-800 border-0 rounded-lg pl-9 pr-3 py-1.5 text-xs focus:ring-2 focus:ring-indigo-500 focus:bg-white dark:focus:bg-slate-900 text-slate-900 dark:text-slate-100 placeholder-slate-400"
            />
          </div>

          {searchOpen && searchResults.length > 0 && (
            <div className="absolute top-full left-0 right-0 mt-1.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg shadow-lg py-1.5 z-50 max-h-72 overflow-y-auto">
              {searchResults.map((r, i) => (
                <div
                  key={i}
                  onClick={() => {
                    navigate(r.link);
                    setSearchOpen(false);
                    setSearchQuery('');
                  }}
                  className="px-3 py-2 hover:bg-slate-100 dark:hover:bg-slate-800 cursor-pointer text-xs flex items-center justify-between"
                >
                  <div>
                    <div className="font-medium text-slate-800 dark:text-slate-200">{r.label}</div>
                    <div className="text-[11px] text-slate-500">{r.sub}</div>
                  </div>
                  <Badge color={r.type === 'INSTITUTION' ? 'accent' : 'blue'}>{r.type}</Badge>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Controls: Live stream dot, i18n, dark mode, user menu */}
        <div className="flex items-center gap-2.5">
          {/* Real-time status */}
          <div className="flex items-center gap-1.5 text-[11px] text-slate-500 mr-1" title={connected ? "Live Real-time SSE Stream Active" : "Polling fallback active"}>
            <Radio className={`h-3.5 w-3.5 ${connected ? 'text-emerald-500 animate-pulse' : 'text-slate-400'}`} />
            <span className="hidden lg:inline">{connected ? 'Live' : 'Polling'}</span>
          </div>

          {/* Lang toggle */}
          <button
            onClick={() => setLang(lang === 'en' ? 'hi' : 'en')}
            className="p-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-300 text-xs font-semibold flex items-center gap-1"
            title="Toggle Language"
          >
            <Globe className="h-3.5 w-3.5" />
            <span>{lang.toUpperCase()}</span>
          </button>

          {/* Dark mode */}
          <button
            onClick={() => setDarkMode(!darkMode)}
            className="p-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-600 dark:text-slate-300"
            title="Toggle Dark Mode"
          >
            {darkMode ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>

          <div className="h-4 w-px bg-slate-200 dark:bg-slate-800" />

          {/* User Profile & Logout */}
          <div className="flex items-center gap-2">
            <div className="text-right hidden sm:block">
              <div className="text-xs font-semibold text-slate-900 dark:text-slate-100 max-w-[140px] truncate">
                {user?.fullName || user?.email}
              </div>
              <div className="text-[10px] text-slate-500 uppercase tracking-wider font-mono">
                {primaryRole(user)}
              </div>
            </div>
            <button
              onClick={() => logout()}
              className="p-1.5 rounded-md hover:bg-red-50 dark:hover:bg-red-950/40 text-slate-500 hover:text-red-600 transition-colors"
              title="Logout"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Body with Sidebar */}
      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar Nav */}
        <aside className="w-56 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col justify-between shrink-0 p-3">
          <nav className="space-y-1 overflow-y-auto">
            {navItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.end}
                className={({ isActive }) =>
                  `flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs font-medium transition-colors ${
                    isActive
                      ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300'
                      : 'text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800/60'
                  }`
                }
              >
                {iconMap[item.icon] || <LayoutDashboard className="h-4 w-4" />}
                <span className="truncate">{t(item.label)}</span>
              </NavLink>
            ))}
          </nav>

          {/* Sidebar Footer info */}
          <div className="pt-3 border-t border-slate-100 dark:border-slate-800 text-[11px] text-slate-400">
            <div>AICTE Portal Integrable</div>
            <div className="font-mono text-[10px] text-slate-500">v1.0.0 • ES256 JWS</div>
          </div>
        </aside>

        {/* Page Content Outlet */}
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>

      <ToastContainer />
    </div>
  );
}
