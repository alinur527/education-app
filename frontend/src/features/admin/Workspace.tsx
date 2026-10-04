import { NavLink, Navigate, Outlet } from 'react-router';
import { useApp } from '../../state';
import { useL } from '../shared';
export default function Workspace() {
  const { user } = useApp(),
    l = useL();
  if (!user || user.role === 'STUDENT') return <Navigate to="/" replace />;
  return (
    <div className="workspace">
      <nav className="workspace-nav" aria-label={l('Управление обучением', 'Оқуды басқару')}>
        <NavLink to="/workspace/content">{l('Контент', 'Контент')}</NavLink>
        {user.role === 'ADMIN' && (
          <NavLink to="/workspace/users">{l('Пользователи', 'Пайдаланушылар')}</NavLink>
        )}
        <NavLink to="/workspace/files">{l('Файлы', 'Файлдар')}</NavLink>
        {['TEACHER', 'ADMIN'].includes(user.role) && (
          <NavLink to="/workspace/groups">{l('Группы и ученики', 'Топтар мен оқушылар')}</NavLink>
        )}
        {['CONTENT_EDITOR', 'ADMIN'].includes(user.role) && (
          <>
            <NavLink to="/workspace/imports">
              {l('Импорт CSV / JSON', 'CSV / JSON импорты')}
            </NavLink>
            <NavLink to="/workspace/packs">{l('Обновление пакетов', 'Пакеттерді жаңарту')}</NavLink>
            <NavLink to="/workspace/sources">
              {l('Источники и права', 'Дереккөздер мен құқықтар')}
            </NavLink>
          </>
        )}
        {['TEACHER', 'ADMIN'].includes(user.role) && (
          <NavLink to="/workspace/analytics">{l('Аналитика', 'Аналитика')}</NavLink>
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
