import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { AppProvider, useApp } from '../../state';
import { TOKEN_KEY } from '../../api';
import { StudyPage, NotesPage, NotificationsPage } from './index';
import { zonedInstant, windowFor, localInput } from './model';
const account = {
  id: 'a41cdaf2-8cd3-4b59-94e8-8a75fe45b7fd',
  email: 'study@example.org',
  firstName: 'Тест',
  lastName: 'План',
  language: 'ru',
  role: 'STUDENT',
};
const profile = {
  goal: 'Мой учебный план',
  targetDate: '2026-10-30',
  availableDays: [1, 2, 3, 4, 5],
  minutesPerDay: 60,
  timeZone: 'Asia/Almaty',
  selectedSubjects: [],
  revision: 1,
};
const prefs = {
  enabled: true,
  plans: true,
  deadlines: true,
  grades: true,
  materials: true,
  quietEnabled: true,
  quietStart: '22:00',
  quietEnd: '08:00',
  revision: 0,
};
const note = {
  id: '98e7195e-8025-4086-a193-f1574349c090',
  targetKind: 'TOPIC',
  targetId: '6b2911d2-39cd-43e3-808d-4f705248cf60',
  title: 'Моя формула',
  body: 'Исходная заметка',
  bookmarked: true,
  cardFront: 'Вопрос карточки',
  cardBack: 'Сохранённый ответ',
  nextReviewAt: '2026-10-04T13:00:00Z',
  intervalDays: 0,
  reviewCount: 0,
  revision: 1,
  available: false,
  url: null,
  updatedAt: '2026-10-04T05:00:00Z',
};
const emptyPage = { items: [], page: 0, size: 25, total: 0 };
function response(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status });
}
function mount(path: string, handler: (url: string, init?: RequestInit) => Response | undefined) {
  sessionStorage.setItem(TOKEN_KEY, 'fixture');
  const fetcher = vi.fn(
    async (url: string, init?: RequestInit) =>
      handler(url, init) ||
      response(
        url.endsWith('/auth/me')
          ? account
          : url.endsWith('/study/profile')
            ? profile
            : url.endsWith('/subjects')
              ? []
              : url.endsWith('/notifications/preferences')
                ? prefs
                : url.includes('/study/tasks?')
                  ? { ...emptyPage, size: 50, unscheduled: 0 }
                  : emptyPage,
      ),
  );
  vi.stubGlobal('fetch', fetcher);
  const router = createMemoryRouter(
    [
      { path: '/study', element: <StudyPage /> },
      { path: '/notes', element: <NotesPage /> },
      { path: '/notifications', element: <NotificationsPage /> },
      { path: '/topics/:id', element: <h1>Тема открыта</h1> },
    ],
    { initialEntries: [path] },
  );
  function Auth() {
    const { loading } = useApp();
    return loading ? null : <RouterProvider router={router} />;
  }
  render(
    <AppProvider>
      <Auth />
    </AppProvider>,
  );
  return { fetcher, router };
}
describe('study workspace', () => {
  it('converts the profile wall clock independently of device time zone', () => {
    expect(zonedInstant('2026-10-04T18:00', 'Asia/Almaty')).toBe('2026-10-04T13:00:00.000Z');
    expect(localInput('2026-10-04T13:00:00Z', 'Asia/Almaty')).toBe('2026-10-04T18:00');
    expect(windowFor('2026-10-04', 'week')).toEqual({ from: '2026-09-28', to: '2026-10-04' });
    expect(windowFor('2028-02-19', 'month').to).toBe('2028-02-29');
    expect(() => zonedInstant('2026-03-29T02:30', 'Europe/Berlin')).toThrow();
  });
  it('preserves note text after conflict and prevents accidental navigation', async () => {
    mount('/notes', (url, init) =>
      url.includes('/study/notes?')
        ? response({ ...emptyPage, items: [note], total: 1 })
        : url.includes('/study/notes/') && init?.method === 'PUT'
          ? response({ code: 'REVISION_CONFLICT' }, 409)
          : undefined,
    );
    const u = userEvent.setup();
    await u.click(await screen.findByRole('button', { name: 'Редактировать' }));
    const body = screen.getByLabelText('Моя заметка');
    await u.type(body, ' и новое объяснение');
    await u.click(screen.getByRole('button', { name: 'Сохранить запись' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ваш текст сохранён');
    expect(body).toHaveValue('Исходная заметка и новое объяснение');
    await u.click(screen.getByRole('link', { name: 'Мой план' }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Уйти без сохранения?');
    await u.click(screen.getByRole('button', { name: 'Остаться' }));
    expect(body).toBeInTheDocument();
  });
  it('reveals only the saved private answer and sends a versioned review', async () => {
    const { fetcher } = mount('/notes?review=true', (url, init) =>
      url.includes('/study/notes?')
        ? response({ ...emptyPage, items: [note], total: 1 })
        : url.endsWith('/review') && init?.method === 'POST'
          ? response({ ...note, intervalDays: 3, reviewCount: 1, revision: 2 })
          : undefined,
    );
    const u = userEvent.setup();
    expect(
      await screen.findByText('Материал недоступен. Ваш текст и карточка сохранены.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Сохранённый ответ')).not.toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Показать ответ' }));
    expect(screen.getByText('Сохранённый ответ')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Вспомнил' }));
    await waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        '/api/study/notes/' + note.id + '/review',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ rating: 'GOOD', revision: 1 }),
        }),
      ),
    );
  });
  it('moves a task using keyboard-operable controls and the profile zone', async () => {
    const task = {
      id: note.id,
      kind: 'READ',
      targetKind: 'TOPIC',
      targetId: note.targetId,
      titleRu: 'Теория',
      titleKz: 'Теория',
      reason: 'UNREAD_THEORY',
      scheduledAt: '2026-10-04T13:00:00Z',
      durationMinutes: 20,
      status: 'PLANNED',
      pinned: false,
      manuallyMoved: false,
      revision: 3,
      available: true,
      url: '/topics/' + note.targetId,
    };
    const { fetcher } = mount('/study', (url, init) =>
      url.includes('/study/tasks?')
        ? response({ ...emptyPage, items: [task], size: 50, total: 1, unscheduled: 0 })
        : url.endsWith('/study/tasks/' + task.id) && init?.method === 'PATCH'
          ? response({ ...task, revision: 4, manuallyMoved: true })
          : undefined,
    );
    const u = userEvent.setup();
    await u.click(await screen.findByRole('button', { name: 'Перенести' }));
    fireEvent.change(screen.getByLabelText('Новая дата и время · Asia/Almaty'), {
      target: { value: '2026-10-05T19:15' },
    });
    await u.click(screen.getByRole('button', { name: 'Применить перенос' }));
    await waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        '/api/study/tasks/' + task.id,
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({ scheduledAt: '2026-10-05T14:15:00.000Z', revision: 3 }),
        }),
      ),
    );
  });
  it('updates notification preferences and keeps unavailable targets disabled', async () => {
    const { fetcher } = mount('/notifications', (url, init) =>
      url.includes('/study/notifications?')
        ? response({
            ...emptyPage,
            unread: 1,
            total: 1,
            items: [
              {
                id: note.id,
                kind: 'GRADE',
                titleRu: 'Решение',
                titleKz: 'Шешім',
                read: false,
                createdAt: '2026-10-04T05:00:00Z',
                available: false,
              },
            ],
          })
        : url.endsWith('/notifications/preferences') && init?.method === 'PUT'
          ? response({ ...prefs, plans: false, revision: 1 })
          : undefined,
    );
    const u = userEvent.setup();
    expect(await screen.findByRole('button', { name: 'Открыть' })).toBeDisabled();
    await u.click(screen.getByText('Настроить уведомления'));
    await u.click(screen.getByLabelText('Учебный план'));
    await u.click(screen.getByRole('button', { name: 'Сохранить настройки' }));
    await waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        '/api/study/notifications/preferences',
        expect.objectContaining({
          method: 'PUT',
          body: JSON.stringify({ ...prefs, plans: false }),
        }),
      ),
    );
  });
});
