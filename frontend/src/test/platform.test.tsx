import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { AppProvider, useApp } from '../state';
import { TOKEN_KEY } from '../api';
import Workspace from '../features/admin/Workspace';
import ContentEditor from '../features/content/ContentEditor';
import Courses from '../features/courses/Courses';
import Learning from '../features/mastery/Learning';
import Imports from '../features/admin/Imports';
import { contentSchema } from '../features/content/model';

const account = {
  id: 'a41cdaf2-8cd3-4b59-94e8-8a75fe45b7fd',
  email: 'fixture@example.org',
  firstName: 'Тест',
  lastName: 'Тест',
  language: 'ru',
  role: 'ADMIN',
};
const emptyPage = { items: [], page: 0, size: 25, total: 0 };
function mount(
  path: string,
  role = 'ADMIN',
  handler: (url: string, init?: RequestInit) => Response | undefined = () => undefined,
) {
  sessionStorage.setItem(TOKEN_KEY, 'fixture');
  const fetcher = vi.fn(
    async (url: string, init?: RequestInit) =>
      handler(url, init) ||
      new Response(JSON.stringify(url.endsWith('/auth/me') ? { ...account, role } : emptyPage)),
  );
  vi.stubGlobal('fetch', fetcher);
  const router = createMemoryRouter(
    [
      { path: '/', element: <h1>Student home</h1> },
      {
        path: '/workspace',
        element: <Workspace />,
        children: [
          { path: 'content/new', element: <ContentEditor /> },
          { path: 'content', element: <h1>Content list</h1> },
          { path: 'imports', element: <Imports /> },
        ],
      },
      { path: '/courses/:id', element: <Courses /> },
      { path: '/learning/errors', element: <Learning errors /> },
      { path: '/tests/:id', element: <h1>Practice session</h1> },
    ],
    { initialEntries: [path] },
  );
  // Wait for auth restoration before rendering guards, as the application's Protected route does.
  function Authenticated() {
    const { loading } = useApp();
    return loading ? null : <RouterProvider router={router} />;
  }
  render(
    <AppProvider>
      <Authenticated />
    </AppProvider>,
  );
  return { fetcher, router };
}
describe('platform workflows', () => {
  it('redirects a student away from the staff workspace', async () => {
    mount('/workspace/content', 'STUDENT');
    expect(await screen.findByRole('heading', { name: 'Student home' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Content list' })).not.toBeInTheDocument();
  });
  it('preserves a bilingual draft after conflict and confirms navigation', async () => {
    const { router, fetcher } = mount('/', 'ADMIN', (url, init) =>
      url.endsWith('/cms/content') && init?.method === 'POST'
        ? new Response('{"code":"REVISION_CONFLICT"}', { status: 409 })
        : undefined,
    );
    await waitFor(() => expect(fetcher).toHaveBeenCalled());
    await act(() => router.navigate('/workspace/content/new'));
    const u = userEvent.setup();
    await u.type(await screen.findByLabelText('Название RU'), 'Новая тема');
    await u.type(screen.getByLabelText('Название KZ'), 'Жаңа тақырып');
    await u.click(screen.getByRole('button', { name: 'Сохранить черновик' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ваш текст сохранён');
    expect(screen.getByLabelText('Название RU')).toHaveValue('Новая тема');
    expect(screen.getByLabelText('Название KZ')).toHaveValue('Жаңа тақырып');
    await u.click(screen.getByRole('link', { name: /Все материалы/ }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Уйти без сохранения?');
    await u.click(screen.getByRole('button', { name: 'Уйти' }));
    expect(await screen.findByRole('heading', { name: 'Content list' })).toBeInTheDocument();
  });
  it('opens lesson links only after confirmed enrollment', async () => {
    let enrolled = false;
    mount('/courses/course', 'STUDENT', (url, init) => {
      if (url.endsWith('/materials')) return new Response('[]');
      if (url.endsWith('/enroll') && init?.method === 'POST') {
        enrolled = true;
        return new Response('{"saved":true}');
      }
      if (url.endsWith('/courses/course'))
        return new Response(
          JSON.stringify({
            id: 'course',
            content: {
              titleRu: 'Курс',
              titleKz: 'Курс',
              visibility: 'PUBLIC',
              selfEnroll: true,
              blocks: [],
            },
            enrollment: enrolled ? 'ACTIVE' : null,
            totalLessons: 1,
            completedLessons: 0,
            modules: [
              {
                id: 'm',
                titleRu: 'Модуль',
                titleKz: 'Модуль',
                lessons: [{ id: 'l', titleRu: 'Урок', titleKz: 'Сабақ', completed: false }],
              },
            ],
          }),
        );
    });
    const u = userEvent.setup();
    await u.click(await screen.findByRole('button', { name: 'Записаться на курс' }));
    expect(await screen.findByRole('link', { name: /Урок.*Открыть урок/ })).toHaveAttribute(
      'href',
      '/lessons/l',
    );
  });
  it('starts error review using the selected real topic', async () => {
    const { fetcher } = mount('/learning/errors', 'STUDENT', (url, init) => {
      if (url.endsWith('/learning/me'))
        return new Response(
          JSON.stringify({
            questionsAnswered: 4,
            testsCompleted: 2,
            accuracy: 50,
            errorCount: 1,
            topics: [],
            subjects: [],
            errorTopics: [{ topicId: 'topic', titleRu: 'Дроби', titleKz: 'Бөлшектер', count: 1 }],
          }),
        );
      if (url.endsWith('/learning/errors/practice') && init?.method === 'POST')
        return new Response(
          JSON.stringify({
            sessionId: account.id,
            totalQuestions: 1,
            startedAt: '2026-10-04T00:00:00Z',
            subjectId: account.id,
            topicId: account.id,
            status: 'IN_PROGRESS',
          }),
        );
    });
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Повторить ошибки' }));
    expect(await screen.findByRole('heading', { name: 'Practice session' })).toBeInTheDocument();
    expect(
      fetcher.mock.calls.some(
        ([url, init]) =>
          url.endsWith('/learning/errors/practice') &&
          JSON.parse(init?.body as string).topicId === 'topic',
      ),
    ).toBe(true);
  });
  it('keeps row diagnostics for malformed import rows and prototype-like kind names', async () => {
    const { router } = mount('/', 'ADMIN', (url, init) =>
      url.endsWith('/cms/imports') && init?.method === 'POST'
        ? new Response(
            JSON.stringify({
              id: 'preview',
              fileName: 'invalid.json',
              status: 'INVALID',
              rows: [42, { kind: 'constructor', payload: { titleRu: 123 } }],
              errors: [
                { row: 1, field: 'row', code: 'INVALID_ROW', detail: 'INVALID_ROW' },
                { row: 2, field: 'kind', code: 'INVALID_ROW', detail: 'INVALID_ROW' },
              ],
              result: null,
            }),
          )
        : undefined,
    );
    await screen.findByRole('heading', { name: 'Student home' });
    await act(() => router.navigate('/workspace/imports'));
    const u = userEvent.setup();
    await u.upload(
      await screen.findByLabelText(/Файл импорта/),
      new File(['[42]'], 'invalid.json', { type: 'application/json' }),
    );
    fireEvent.submit(
      screen.getByRole('button', { name: 'Проверить и показать preview' }).closest('form')!,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('Строка 1: INVALID_ROW');
    expect(screen.getByRole('alert')).toHaveTextContent('Строка 2: INVALID_ROW');
    expect(screen.queryByRole('button', { name: 'Подтвердить импорт' })).not.toBeInTheDocument();
  });
  it('opens a valid imported Russian-only question draft for translation', () => {
    const parsed = contentSchema.parse({
      id: account.id,
      kind: 'QUESTION',
      parentId: account.id,
      ownerId: account.id,
      titleRu: 'Question',
      titleKz: '',
      payload: {
        titleRu: 'Question',
        options: [
          { id: 'A', textRu: 'One' },
          { id: 'B', textRu: 'Two', textKz: null },
        ],
        correctOptionId: 'A',
        sourceName: null,
      },
      status: 'DRAFT',
      version: 1,
      publishedVersion: null,
      createdBy: account.id,
      updatedBy: account.id,
      createdAt: '2026-10-04T00:00:00Z',
      updatedAt: '2026-10-04T00:00:00Z',
    });
    expect(parsed.payload.titleKz).toBe('');
    expect(parsed.payload.options?.[1].textKz).toBe('');
  });
});
