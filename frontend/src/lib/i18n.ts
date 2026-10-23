// Minimal i18n scaffolding: `en` complete, `hi` partial (nav + dashboard titles). Missing keys fall back to `en`.
import { createContext, createElement, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type Lang = 'en' | 'hi';

export const en: Record<string, string> = {
  'app.name': 'VidyaSetu',
  'app.tagline': 'APAAR-based student data management & verification',
  'app.demoBanner': 'SYNTHETIC DEMO DATA — all institutions, students and APAAR IDs are fictional',
  'nav.national': 'National dashboard',
  'nav.institutions': 'Institutions',
  'nav.anomalies': 'Anomalies',
  'nav.issues': 'Issue register',
  'nav.reports': 'Reports',
  'nav.audit': 'Audit log',
  'nav.admin': 'Administration',
  'nav.instDashboard': 'Dashboard',
  'nav.upload': 'Upload data',
  'nav.worklist': 'Worklist',
  'nav.students': 'Students',
  'nav.integration': 'Integration',
  'nav.verify': 'Verify',
  'nav.me': 'My record',
  'section.national': 'AICTE',
  'section.institution': 'Institution',
  'section.verify': 'Verification',
  'section.student': 'Student',
  'title.national': 'National enrolment dashboard',
  'title.institution': 'Institution dashboard',
  'title.institutions': 'Institutions',
  'title.anomalies': 'Anomaly watchlist',
  'title.issues': 'Issue register',
  'title.reports': 'Reports',
  'title.audit': 'Audit log',
  'title.admin': 'Administration',
  'title.upload': 'Upload student data',
  'title.worklist': 'Remediation worklist',
  'title.students': 'Students',
  'title.integration': 'Integration',
  'title.verify': 'Verify a student',
  'title.me': 'My academic record',
  'kpi.reporting': 'Institutions reporting',
  'kpi.enrolment': 'Total enrolment',
  'kpi.fill': 'National fill ratio',
  'kpi.critical': 'Open CRITICAL issues',
  'kpi.anomalies': 'Anomalies under review',
  'kpi.passRate': 'Validation pass rate',
  'common.search': 'Search APAAR, student ID or institution…',
  'common.logout': 'Sign out',
  'common.live': 'Live',
  'common.polling': 'Polling',
  'common.offline': 'Offline',
  'common.loading': 'Loading…',
  'common.empty': 'Nothing here yet',
  'common.retry': 'Retry',
  'login.title': 'Sign in to VidyaSetu',
  'login.staff': 'Staff / verifier',
  'login.student': 'Student',
  'login.demo': 'Demo access',
};

export const hi: Record<string, string> = {
  'app.tagline': 'APAAR आधारित छात्र डेटा प्रबंधन एवं सत्यापन',
  'app.demoBanner': 'कृत्रिम डेमो डेटा — सभी संस्थान, छात्र और APAAR आईडी काल्पनिक हैं',
  'nav.national': 'राष्ट्रीय डैशबोर्ड',
  'nav.institutions': 'संस्थान',
  'nav.anomalies': 'विसंगतियाँ',
  'nav.issues': 'समस्या रजिस्टर',
  'nav.reports': 'रिपोर्ट',
  'nav.audit': 'ऑडिट लॉग',
  'nav.admin': 'प्रशासन',
  'nav.instDashboard': 'डैशबोर्ड',
  'nav.upload': 'डेटा अपलोड',
  'nav.worklist': 'कार्य सूची',
  'nav.students': 'छात्र',
  'nav.integration': 'एकीकरण',
  'nav.verify': 'सत्यापन',
  'nav.me': 'मेरा रिकॉर्ड',
  'section.national': 'एआईसीटीई',
  'section.institution': 'संस्थान',
  'section.verify': 'सत्यापन',
  'section.student': 'छात्र',
  'title.national': 'राष्ट्रीय नामांकन डैशबोर्ड',
  'title.institution': 'संस्थान डैशबोर्ड',
  'title.institutions': 'संस्थान',
  'title.anomalies': 'विसंगति निगरानी सूची',
  'title.issues': 'समस्या रजिस्टर',
  'title.reports': 'रिपोर्ट',
  'title.audit': 'ऑडिट लॉग',
  'title.admin': 'प्रशासन',
  'title.upload': 'छात्र डेटा अपलोड करें',
  'title.worklist': 'सुधार कार्य सूची',
  'title.students': 'छात्र',
  'title.integration': 'एकीकरण',
  'title.verify': 'छात्र का सत्यापन करें',
  'title.me': 'मेरा शैक्षणिक रिकॉर्ड',
  'kpi.reporting': 'रिपोर्ट करने वाले संस्थान',
  'kpi.enrolment': 'कुल नामांकन',
  'kpi.fill': 'राष्ट्रीय भराव अनुपात',
  'kpi.critical': 'खुली गंभीर समस्याएँ',
  'kpi.anomalies': 'समीक्षाधीन विसंगतियाँ',
  'kpi.passRate': 'सत्यापन सफलता दर',
  'common.logout': 'साइन आउट',
};

const dicts: Record<Lang, Record<string, string>> = { en, hi };

export function translate(lang: Lang, key: string): string {
  return dicts[lang]?.[key] ?? en[key] ?? key;
}

interface I18nCtx {
  lang: Lang;
  setLang: (l: Lang) => void;
  t: (key: string) => string;
}

const Ctx = createContext<I18nCtx>({ lang: 'en', setLang: () => {}, t: (k) => translate('en', k) });

function initialLang(): Lang {
  try {
    const v = localStorage.getItem('vs_lang');
    if (v === 'hi' || v === 'en') return v;
  } catch {
    /* ignore */
  }
  return 'en';
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang);
  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    try {
      localStorage.setItem('vs_lang', l);
    } catch {
      /* ignore */
    }
    document.documentElement.lang = l;
  }, []);
  const value = useMemo(() => ({ lang, setLang, t: (k: string) => translate(lang, k) }), [lang, setLang]);
  return createElement(Ctx.Provider, { value }, children);
}

export function useI18n() {
  return useContext(Ctx);
}
