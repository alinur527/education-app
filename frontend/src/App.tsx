import { useEffect } from 'react';
import {
  createBrowserRouter,
  RouterProvider,
  Outlet,
  NavLink,
  Navigate,
  useLocation,
} from 'react-router';
import { BookOpen, ChartNoAxesCombined, House, LogOut, Settings2 } from 'lucide-react';
import { AppProvider, useApp } from './state';
import { Logo, LanguageSwitch, Loading, ErrorState, ErrorBoundary } from './components';
import {
  AuthPage,
  Dashboard,
  SubjectsPage,
  TopicsPage,
  TopicPage,
  StatisticsPage,
  ResultsPage,
  SettingsPage,
  NotFound,
} from './pages';
import { TestPage } from './TestPage';

function Protected() {
  const { user, loading, restoreError, restore } = useApp();
  const location = useLocation();
  if (loading) return <Loading />;
  if (restoreError) return <ErrorState error={restoreError} retry={() => void restore()} />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}
function Shell() {
  const { t, user, logout, languageError } = useApp();
  const location = useLocation();
  const isTest = location.pathname.startsWith('/tests/');
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  const links = [
    { to: '/', key: 'home', Icon: House },
    { to: '/subjects', key: 'subjects', Icon: BookOpen },
    { to: '/statistics', key: 'statistics', Icon: ChartNoAxesCombined },
    { to: '/settings', key: 'settings', Icon: Settings2 },
  ] as const;
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        {t('skip')}
      </a>
      <aside className="sidebar">
        <NavLink className="brand-link" to="/" aria-label={t('home')}>
          <Logo />
        </NavLink>
        <p className="sidebar-caption">{t('prep')}</p>
        <nav aria-label={t('home')}>
          {links.map(({ to, key, Icon }) => (
            <NavLink to={to} end={to === '/'} key={to}>
              <Icon size={20} />
              {t(key)}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="profile-mini">
            <span className="avatar">{user?.firstName.slice(0, 1)}</span>
            <span>
              <strong>{user?.firstName}</strong>
              <small>{t(user?.role === 'ADMIN' ? 'admin' : 'student')}</small>
            </span>
          </div>
          <button onClick={logout} className="text-button">
            <LogOut size={18} />
            {t('logout')}
          </button>
        </div>
      </aside>
      <div className="app-main">
        <header className="topbar">
          <span className="mobile-logo">
            <Logo />
          </span>
          <span className="desktop-caption">{t('prep')}</span>
          <div className="topbar-actions">
            <LanguageSwitch />
            <NavLink to="/settings" className="avatar" aria-label={t('settings')}>
              {user?.firstName.slice(0, 1)}
            </NavLink>
          </div>
        </header>
        <main
          id="main"
          className={isTest ? 'page-content test-page-content' : 'page-content'}
          tabIndex={-1}
        >
          {languageError && (
            <p className="notice" role="status">
              {t('languageError')}
            </p>
          )}
          <Outlet />
        </main>
      </div>
      {!isTest && (
        <nav className="mobile-nav" aria-label={t('home')}>
          {links.map(({ to, key, Icon }) => (
            <NavLink to={to} end={to === '/'} key={to}>
              <Icon size={21} />
              <span>{t(key)}</span>
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  );
}
export const routes = [
  { path: '/login', element: <AuthPage key="login" mode="login" /> },
  { path: '/register', element: <AuthPage key="register" mode="register" /> },
  {
    element: <Protected />,
    children: [
      {
        element: <Shell />,
        children: [
          { path: '/', element: <Dashboard /> },
          { path: '/subjects', element: <SubjectsPage /> },
          { path: '/subjects/:subjectId', element: <TopicsPage /> },
          { path: '/topics/:topicId', element: <TopicPage /> },
          { path: '/tests/:sessionId', element: <TestPage /> },
          { path: '/results/:sessionId', element: <ResultsPage /> },
          { path: '/statistics', element: <StatisticsPage /> },
          { path: '/settings', element: <SettingsPage /> },
          { path: '*', element: <NotFound /> },
        ],
      },
    ],
  },
];
const router = createBrowserRouter(routes);
export default function App() {
  return (
    <ErrorBoundary>
      <AppProvider>
        <RouterProvider router={router} />
      </AppProvider>
    </ErrorBoundary>
  );
}
