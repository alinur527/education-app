import { NavLink, Navigate, Outlet } from 'react-router';
import { useApp } from '../../state';
import { useL } from '../shared';
export default function Workspace() {
  const { user } = useApp(),
    l = useL();
  if (!user || user.role === 'STUDENT') return <Navigate to="/" replace />;
  return (
    <div className="workspace">
      <nav className="workspace-nav" aria-label={l('Кабинет преподавателя', 'Оқытушы кабинеті')}>
        <NavLink to="/workspace/content">{l('Материалы', 'Материалдар')}</NavLink>
        {['TEACHER', 'ADMIN'].includes(user.role) && (
          <NavLink to="/workspace/groups">{l('Группы и ученики', 'Топтар мен оқушылар')}</NavLink>
        )}
        {['CONTENT_EDITOR', 'ADMIN'].includes(user.role) && (
          <NavLink to="/workspace/imports">{l('Импорт', 'Импорт')}</NavLink>
        )}
        {user.role === 'ADMIN' && (
          <NavLink to="/workspace/users">{l('Пользователи', 'Пайдаланушылар')}</NavLink>
        )}
      </nav>
      <Outlet />
    </div>
  );
}
export function AdminOnly() {
  return useApp().user?.role === 'ADMIN' ? (
    <Outlet />
  ) : (
    <Navigate to="/workspace/content" replace />
  );
}
export function TeacherOnly() {
  return ['ADMIN', 'TEACHER'].includes(useApp().user?.role || '') ? (
    <Outlet />
  ) : (
    <Navigate to="/workspace/content" replace />
  );
}
export function EditorOnly() {
  return ['ADMIN', 'CONTENT_EDITOR'].includes(useApp().user?.role || '') ? (
    <Outlet />
  ) : (
    <Navigate to="/workspace/content" replace />
  );
}
