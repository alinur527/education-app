import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useRef,
  type ReactNode,
} from 'react';
import { ApiError, authSchema, request, TOKEN_KEY, userSchema, type User } from './api';
import { ru, kz, type Language, type TranslationKey } from './i18n';

const LANGUAGE_KEY = 'education.language';
interface AppState {
  user: User | null;
  loading: boolean;
  restoreError: unknown;
  expired: boolean;
  language: Language;
  t: (key: TranslationKey) => string;
  content: (ru: string | null | undefined, kz: string | null | undefined) => string;
  setLanguage: (language: Language) => Promise<void>;
  languageError: boolean;
  authenticate: (mode: 'login' | 'register', payload: Record<string, string>) => Promise<void>;
  logout: () => void;
  restore: () => Promise<void>;
}
const Context = createContext<AppState | null>(null);
export function AppProvider({ children }: { children: ReactNode }) {
  const [language, changeLanguage] = useState<Language>(() =>
    localStorage.getItem(LANGUAGE_KEY) === 'kz' ? 'kz' : 'ru',
  );
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => Boolean(sessionStorage.getItem(TOKEN_KEY)));
  const [restoreError, setRestoreError] = useState<unknown>(null);
  const [expired, setExpired] = useState(false);
  const [languageError, setLanguageError] = useState(false);
  const languageQueue = useRef(Promise.resolve());
  useEffect(() => {
    document.documentElement.lang = language === 'kz' ? 'kk' : 'ru';
  }, [language]);
  const restore = useCallback(() => {
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (!token) return Promise.resolve();
    return request('/auth/me', userSchema)
      .then(profile => {
        if (token === sessionStorage.getItem(TOKEN_KEY)) {
          setUser(profile);
          if (!localStorage.getItem(LANGUAGE_KEY)) changeLanguage(profile.language);
        }
      })
      .catch(error => {
        if (!(error instanceof ApiError && error.status === 401)) setRestoreError(error);
      })
      .finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    void restore();
    const end = () => {
      setUser(null);
      setExpired(true);
      setRestoreError(null);
    };
    window.addEventListener('session-expired', end);
    return () => window.removeEventListener('session-expired', end);
  }, [restore]);
  async function setLanguage(next: Language) {
    changeLanguage(next);
    localStorage.setItem(LANGUAGE_KEY, next);
    setLanguageError(false);
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (user && token) {
      languageQueue.current = languageQueue.current.then(async () => {
        if (token !== sessionStorage.getItem(TOKEN_KEY)) return;
        try {
          const profile = await request('/auth/me/language', userSchema, {
            method: 'PATCH',
            body: { language: next },
          });
          if (token === sessionStorage.getItem(TOKEN_KEY)) setUser(profile);
        } catch {
          if (token === sessionStorage.getItem(TOKEN_KEY)) setLanguageError(true);
        }
      });
      await languageQueue.current;
    }
  }
  async function authenticate(mode: 'login' | 'register', payload: Record<string, string>) {
    const result = await request(`/auth/${mode}`, authSchema, {
      method: 'POST',
      body: { ...payload, ...(mode === 'register' ? { language } : {}) },
    });
    sessionStorage.setItem(TOKEN_KEY, result.token);
    setUser(result.user);
    setExpired(false);
    const preference = localStorage.getItem(LANGUAGE_KEY);
    if (!preference) changeLanguage(result.user.language);
    else if (result.user.language !== language) {
      try {
        const profile = await request('/auth/me/language', userSchema, {
          method: 'PATCH',
          body: { language },
        });
        if (sessionStorage.getItem(TOKEN_KEY) === result.token) setUser(profile);
      } catch {
        if (sessionStorage.getItem(TOKEN_KEY) === result.token) setLanguageError(true);
      }
    }
  }
  function logout() {
    sessionStorage.removeItem(TOKEN_KEY);
    setUser(null);
    setExpired(false);
  }
  const t = (key: TranslationKey) => (language === 'ru' ? ru : kz)[key];
  return (
    <Context.Provider
      value={{
        user,
        loading,
        restoreError,
        expired,
        language,
        t,
        content: (r, k) => (language === 'kz' ? k || r : r || k) || '',
        setLanguage,
        languageError,
        authenticate,
        logout,
        restore: async () => {
          setLoading(true);
          setRestoreError(null);
          await restore();
        },
      }}
    >
      {children}
    </Context.Provider>
  );
}
export function useApp() {
  const value = useContext(Context);
  if (!value) throw new Error('AppProvider required');
  return value;
}

export function errorKey(error: unknown): TranslationKey {
  if (!(error instanceof ApiError)) return 'serverError';
  if (error.code === 'NETWORK') return 'networkError';
  if (error.code === 'INVALID_RESPONSE') return 'invalidResponse';
  return (
    (
      {
        400: 'invalidInput',
        401: 'expired',
        403: 'forbidden',
        404: 'notFoundBody',
        409: 'conflict',
      } as Record<number, TranslationKey>
    )[error.status] || 'serverError'
  );
}
