import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { AppProvider, useApp } from '../../state';
import { TOKEN_KEY } from '../../api';
import Assignments from '../courses/Assignments';
import Submissions from '../teacher/Submissions';
const assignment = '65d29420-ae07-47e1-9c8d-807198fc5e77';
const user = {
  id: 'a41cdaf2-8cd3-4b59-94e8-8a75fe45b7fd',
  email: 'files@example.org',
  firstName: 'Ученик',
  lastName: 'Тест',
  language: 'ru',
  role: 'STUDENT',
};
const file = {
  id: '98e7195e-8025-4086-a193-f1574349c090',
  assignmentId: assignment,
  originalFileName: 'answer.txt',
  mimeType: 'text/plain',
  size: 6,
  sha256: 'a'.repeat(64),
  scanStatus: 'CLEAN',
  scanAttempts: 1,
  scanMessage: 'CLEAN',
  scannedAt: '2026-10-04T03:00:00Z',
  createdAt: '2026-10-04T03:00:00Z',
  bound: false,
};
const detail = {
  id: assignment,
  content: { titleRu: 'Задание', titleKz: 'Тапсырма' },
  submission: null,
};
function response(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status });
}
function mount(
  path: string,
  handler: (url: string, init?: RequestInit) => Response | undefined,
  role = 'STUDENT',
) {
  sessionStorage.setItem(TOKEN_KEY, 'fixture');
  const fetcher = vi.fn(
    async (url: string, init?: RequestInit) =>
      handler(url, init) ||
      response(
        url.endsWith('/auth/me')
          ? { ...user, role }
          : url.endsWith('/assignments/' + assignment)
            ? detail
            : [],
      ),
  );
  vi.stubGlobal('fetch', fetcher);
  const router = createMemoryRouter(
    [
      { path: '/assignments/:id', element: <Assignments /> },
      { path: '/assignments', element: <h1>Список заданий</h1> },
      { path: '/workspace/assignments/:id', element: <Submissions /> },
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
  return fetcher;
}
describe('submission files and revisions', () => {
  it('keeps an unscanned upload quarantined and excludes it from the answer', async () => {
    let uploaded = false;
    mount('/assignments/' + assignment, (url, init) =>
      url.endsWith('/files')
        ? init?.method === 'POST'
          ? ((uploaded = true), response({ ...file, scanStatus: 'UNSCANNED' }))
          : response(uploaded ? [{ ...file, scanStatus: 'UNSCANNED' }] : [])
        : undefined,
    );
    const u = userEvent.setup();
    await u.upload(
      await screen.findByLabelText('Выбрать файл к ответу'),
      new File(['answer'], 'answer.txt', { type: 'text/plain' }),
    );
    await u.click(screen.getByRole('button', { name: 'Загрузить и проверить файл' }));
    expect(await screen.findByText(/Антивирус не настроен/)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'answer.txt' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'answer.txt' })).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Скачать' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Отправить ответ' })).toBeDisabled();
  });
  it('reuses the request key after a failed send and preserves selected clean files', async () => {
    let uploaded = false,
      sends = 0;
    const bodies: Record<string, unknown>[] = [];
    mount('/assignments/' + assignment, (url, init) => {
      if (url.endsWith('/files'))
        return init?.method === 'POST'
          ? ((uploaded = true), response(file))
          : response(uploaded ? [file] : []);
      if (url.endsWith('/submit')) {
        bodies.push(JSON.parse(init?.body as string));
        return response(
          ++sends === 1 ? { code: 'SERVER_ERROR' } : { saved: true },
          sends === 1 ? 503 : 200,
        );
      }
      return undefined;
    });
    const u = userEvent.setup();
    await u.upload(
      await screen.findByLabelText('Выбрать файл к ответу'),
      new File(['answer'], 'answer.txt', { type: 'text/plain' }),
    );
    await u.click(screen.getByRole('button', { name: 'Загрузить и проверить файл' }));
    expect(await screen.findByRole('checkbox', { name: 'answer.txt' })).toBeChecked();
    await u.click(screen.getByRole('button', { name: 'Отправить ответ' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Отправить ответ' }));
    await waitFor(() => expect(bodies).toHaveLength(2));
    expect(bodies[0]).toEqual(bodies[1]);
    expect(bodies[0]).toMatchObject({ fileIds: [file.id], revision: 0, text: '' });
    expect(bodies[0].requestKey).toMatch(/^[0-9a-f-]{36}$/);
  });
  it('retains text on stale revision and blocks unsaved navigation', async () => {
    mount('/assignments/' + assignment, (url) =>
      url.endsWith('/submit') ? response({ code: 'REVISION_CONFLICT' }, 409) : undefined,
    );
    const u = userEvent.setup();
    await u.type(await screen.findByLabelText('Ваш ответ'), 'Новая версия решения');
    await u.click(screen.getByRole('button', { name: 'Отправить ответ' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Ваш текст сохранён');
    expect(screen.getByLabelText('Ваш ответ')).toHaveValue('Новая версия решения');
    await u.click(screen.getByRole('link', { name: /Мои задания/ }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Уйти без отправки?');
  });
  it('history pagination never submits the grade form', async () => {
    const fetcher = mount(
      '/workspace/assignments/' + assignment,
      (url) =>
        url.includes('/submission-history')
          ? response({
              items: [
                {
                  id: file.id,
                  contentRevision: 1,
                  text: 'Старая версия',
                  submittedAt: '2026-10-04T03:00:00Z',
                  legacyImported: false,
                  files: [],
                  grades: [
                    {
                      score: 8,
                      maxScore: 10,
                      feedback: 'Проверено',
                      gradedAt: '2026-10-04T03:00:00Z',
                      legacyImported: false,
                    },
                  ],
                },
              ],
              page: 0,
              size: 10,
              total: 11,
            })
          : url.includes('/submissions?')
            ? response({
                items: [
                  {
                    userId: user.id,
                    firstName: 'Ученик',
                    lastName: 'Тест',
                    text: 'Текущая версия',
                    score: 8,
                    feedback: 'Проверено',
                    submittedAt: '2026-10-04T03:00:00Z',
                    revision: 2,
                    contentRevision: 1,
                  },
                ],
                page: 0,
                size: 25,
                total: 1,
              })
            : undefined,
      'TEACHER',
    );
    const u = userEvent.setup();
    await u.click(await screen.findByText('История ответов и оценок'));
    expect(await screen.findByText('Старая версия')).toBeInTheDocument();
    await u.click(
      screen
        .getAllByRole('button', { name: 'Далее' })
        .find((button) => !button.hasAttribute('disabled'))!,
    );
    await waitFor(() =>
      expect(fetcher).toHaveBeenCalledWith(
        expect.stringContaining('submission-history?page=1'),
        expect.any(Object),
      ),
    );
    expect(
      fetcher.mock.calls.some(([url, init]) => url.endsWith('/grade') && init?.method === 'POST'),
    ).toBe(false);
  });
});
