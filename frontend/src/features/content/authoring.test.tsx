import { useState } from 'react';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AppProvider, useApp } from '../../state';
import { TOKEN_KEY } from '../../api';
import ContentEditor from './ContentEditor';
import ContentList from './ContentList';
import MaterialLibrary from './MaterialLibrary';
import { QuestionEditor } from './QuestionEditor';
import { RenderedContent } from './Blocks';
import type { Content, QuizQuestion } from './model';

const parentId = 'c55e12b8-e1bd-4316-934f-a4f5a0bd2111';
const childId = 'c55e12b8-e1bd-4316-934f-a4f5a0bd2222';
const emptyPage = { items: [], page: 0, size: 25, total: 0 };
const json = (data: unknown) => new Response(JSON.stringify(data));
function content(overrides: Partial<Content> = {}): Content {
  return {
    id: parentId,
    kind: 'TOPIC',
    parentId: null,
    ownerId: parentId,
    titleRu: 'Тригонометрия',
    titleKz: 'Тригонометрия',
    payload: { titleRu: 'Тригонометрия', titleKz: 'Тригонометрия', blocks: [] },
    status: 'DRAFT',
    version: 3,
    publishedVersion: 2,
    createdBy: parentId,
    updatedBy: parentId,
    createdAt: '2026-10-04T00:00:00Z',
    updatedAt: '2026-10-04T00:00:00Z',
    ...overrides,
  };
}
function mount(
  path: string,
  handler: (url: string, init?: RequestInit) => Response | undefined = () => undefined,
) {
  sessionStorage.setItem(TOKEN_KEY, 'fixture');
  const fetcher = vi.fn(
    async (url: string, init?: RequestInit) =>
      handler(url, init) ||
      json(
        url.endsWith('/auth/me')
          ? {
              id: parentId,
              email: 'editor@example.org',
              firstName: 'Редактор',
              lastName: 'Тест',
              language: 'ru',
              role: 'CONTENT_EDITOR',
            }
          : url.includes('/cms/content?')
            ? emptyPage
            : url.endsWith('/materials') ||
                url.endsWith('/history') ||
                url.endsWith('/editorial-review') ||
                url.includes('/cms/contexts?')
              ? []
              : content(),
      ),
  );
  vi.stubGlobal('fetch', fetcher);
  const router = createMemoryRouter(
    [
      { path: '/workspace/content/new', element: <ContentEditor /> },
      { path: '/workspace/content/:id', element: <ContentEditor /> },
      { path: '/workspace/content', element: <ContentList /> },
      { path: '/workspace/files', element: <MaterialLibrary /> },
    ],
    { initialEntries: [path] },
  );
  function Authenticated() {
    return useApp().loading ? null : <RouterProvider router={router} />;
  }
  render(
    <AppProvider>
      <Authenticated />
    </AppProvider>,
  );
  return { fetcher, router };
}

describe('guided CMS authoring', () => {
  it('returns to an incomplete content step and saves the selected parent as a draft', async () => {
    const { fetcher } = mount(
      `/workspace/content/new?kind=QUESTION&parent=${parentId}`,
      (url, init) => {
        if (url.endsWith('/cms/content') && init?.method === 'POST')
          return json(
            content({
              id: childId,
              kind: 'QUESTION',
              parentId,
              payload: JSON.parse(init.body as string).payload,
            }),
          );
      },
    );
    const u = userEvent.setup();
    await u.type(await screen.findByLabelText('Название RU'), 'Чему равен sin 0?');
    await u.click(screen.getByRole('button', { name: 'Сохранить черновик' }));
    expect(
      screen.getByRole('button', { name: /2 Содержание|Содержание/, current: 'step' }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Ответ A RU')).toBeVisible();
    expect(fetcher.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
    await u.type(screen.getByLabelText('Ответ A RU'), '0');
    await u.type(screen.getByLabelText('Ответ B RU'), '1');
    await u.click(screen.getByRole('button', { name: 'Предпросмотр' }));
    expect(screen.getByText('Ответ A KZ', { selector: 'li' })).toBeVisible();
    expect(screen.getByText('Объяснение RU', { selector: 'li' })).toBeVisible();
    await u.click(screen.getByRole('button', { name: 'Сохранить черновик' }));
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(
          ([url, init]) => url.endsWith('/cms/content') && init?.method === 'POST',
        ),
      ).toBe(true),
    );
    const request = fetcher.mock.calls.find(
      ([url, init]) => url.endsWith('/cms/content') && init?.method === 'POST',
    )!;
    expect(JSON.parse(request[1]?.body as string)).toMatchObject({
      kind: 'QUESTION',
      parentId,
      payload: { titleRu: 'Чему равен sin 0?', titleKz: '' },
    });
    expect(fetcher.mock.calls.some(([url]) => url.endsWith('/transition'))).toBe(false);
  });

  it('requires confirmation before changing the type of an unsaved material', async () => {
    mount('/workspace/content/new');
    const u = userEvent.setup();
    await u.type(await screen.findByLabelText('Название RU'), 'Сохранить мой текст');
    await u.selectOptions(screen.getByLabelText('Тип материала'), 'COURSE');
    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('Несохранённое содержание будет очищено');
    await u.keyboard('{Escape}');
    expect(screen.getByLabelText('Название RU')).toHaveValue('Сохранить мой текст');
    expect(screen.getByLabelText('Тип материала')).toHaveValue('SUBJECT');
    await u.selectOptions(screen.getByLabelText('Тип материала'), 'COURSE');
    await u.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Изменить тип' }),
    );
    expect(screen.getByLabelText('Название RU')).toHaveValue('');
    expect(screen.getByLabelText('Тип материала')).toHaveValue('COURSE');
  });

  it('opens descendants with the correct parent and preserves the active published version notice', async () => {
    mount(`/workspace/content/${parentId}`, (url) =>
      url.includes(`/cms/content?parentId=${parentId}`)
        ? json({
            ...emptyPage,
            total: 1,
            items: [
              content({ id: childId, kind: 'THEORY', parentId, titleRu: 'Основные формулы' }),
            ],
          })
        : undefined,
    );
    expect(await screen.findByText(/Ученикам доступна опубликованная версия/)).toHaveTextContent(
      '2',
    );
    expect(screen.getByRole('link', { name: '+ Теория' })).toHaveAttribute(
      'href',
      `/workspace/content/new?kind=THEORY&parent=${parentId}`,
    );
    expect(await screen.findByRole('link', { name: 'Основные формулы' })).toHaveAttribute(
      'href',
      `/workspace/content/${childId}`,
    );
    expect(screen.getByRole('link', { name: 'Все материалы раздела (1)' })).toHaveAttribute(
      'href',
      `/workspace/content?parentId=${parentId}`,
    );
  });

  it('scopes search and translation gaps server-side and restores filters on navigation', async () => {
    const { fetcher, router } = mount(
      `/workspace/content?parentId=${parentId}&kind=THEORY&status=REVIEW`,
    );
    await screen.findByLabelText('Без перевода названия KZ');
    const u = userEvent.setup();
    await u.click(screen.getByLabelText('Без перевода названия KZ'));
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(
          ([url]) =>
            url.includes(`parentId=${parentId}`) &&
            url.includes('missingTranslation=true') &&
            url.includes('kind=THEORY') &&
            url.includes('status=REVIEW'),
        ),
      ).toBe(true),
    );
    expect(screen.getByRole('link', { name: '+ Создать материал' })).toHaveAttribute(
      'href',
      `/workspace/content/new?kind=THEORY&parent=${parentId}`,
    );
    await act(() => router.navigate(`/workspace/content?kind=COURSE&status=DRAFT`));
    expect(await screen.findByLabelText('Тип материала')).toHaveValue('COURSE');
    expect(screen.getByLabelText('Статус')).toHaveValue('DRAFT');
    expect(screen.getByLabelText('Без перевода названия KZ')).not.toBeChecked();
  });
});

describe('authored assignment descriptions', () => {
  it.each(['ru', 'kz'] as const)(
    'preserves %s paragraphs and code without interpreting HTML',
    (language) => {
      localStorage.setItem('education.language', language);
      const descriptionRu =
        'Разберите пример.\n\n```python\nprint(2 + 2)\n```\n\n<script>alert(1)</script>';
      const descriptionKz =
        'Мысалды талдаңыз.\n\n```python\nprint(2 + 2)\n```\n\n<script>alert(1)</script>';
      const { container } = render(
        <AppProvider>
          <RenderedContent
            payload={{
              titleRu: 'Задание',
              titleKz: 'Тапсырма',
              descriptionRu,
              descriptionKz,
              blocks: [],
            }}
          />
        </AppProvider>,
      );
      expect(
        screen.getByText(language === 'ru' ? 'Разберите пример.' : 'Мысалды талдаңыз.', {
          selector: 'p',
        }),
      ).toBeInTheDocument();
      expect(container.querySelector('pre code')).toHaveTextContent('print(2 + 2)');
      expect(container.querySelector('script')).toBeNull();
      expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument();
    },
  );
});

describe('material library', () => {
  it('keeps ownership links and search state when an authorized download fails', async () => {
    const file = {
      id: childId,
      contentId: parentId,
      titleRu: 'Python tutorial',
      titleKz: 'Python tutorial',
      originalFileName: 'tutorial.pdf',
      mimeType: 'application/pdf',
      size: 630898,
      published: false,
      scanStatus: 'CLEAN',
      contentTitleRu: 'Введение в Python',
      contentTitleKz: 'Python тіліне кіріспе',
      contentKind: 'LESSON',
    };
    const { fetcher, router } = mount('/workspace/files?q=python&page=1', (url) => {
      if (url.endsWith('/download')) return new Response('', { status: 403 });
      if (url.includes('/cms/materials?'))
        return json({
          items: [
            file,
            { ...file, id: parentId, titleRu: 'Заблокированный файл', scanStatus: 'INFECTED' },
          ],
          page: Number(new URL(url, 'http://localhost').searchParams.get('page')),
          size: 25,
          total: 51,
        });
    });
    const u = userEvent.setup();
    const download = await screen.findByRole('button', { name: 'Скачать: Python tutorial' });
    expect(screen.getAllByRole('link', { name: 'Введение в Python' })[0]).toHaveAttribute(
      'href',
      `/workspace/content/${parentId}`,
    );
    expect(screen.getByRole('button', { name: 'Скачать: Заблокированный файл' })).toBeDisabled();
    await u.click(download);
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    const request = fetcher.mock.calls.find(([url]) =>
      url.endsWith(`/materials/${childId}/download`),
    );
    expect(request?.[1]?.headers).toEqual({ Authorization: 'Bearer fixture' });
    expect(screen.getByRole('searchbox')).toHaveValue('python');
    expect(router.state.location.search).toBe('?q=python&page=1');
    expect(screen.getByRole('button', { name: 'Скачать: Python tutorial' })).toBeEnabled();
    await u.click(screen.getByRole('button', { name: 'Далее' }));
    await waitFor(() => expect(router.state.location.search).toBe('?q=python&page=2'));
    await u.clear(screen.getByRole('searchbox'));
    await u.type(screen.getByRole('searchbox'), 'pdf');
    await waitFor(() =>
      expect(fetcher.mock.calls.some(([url]) => url.endsWith('/cms/materials?q=pdf&page=0'))).toBe(
        true,
      ),
    );
    expect(router.state.location.search).toBe('?q=pdf');
  });
});

describe('question answer key editing', () => {
  const options = ['A', 'B', 'C'].map((id) => ({ id, textRu: id, textKz: id }));
  function Harness({ type }: { type: 'MULTIPLE_SELECT' | 'MATCHING' }) {
    const [q, setQ] = useState<QuizQuestion>({
      titleRu: 'Вопрос',
      titleKz: 'Сұрақ',
      questionType: type,
      options,
      correctOptionIds: type === 'MULTIPLE_SELECT' ? ['A', 'C'] : undefined,
      leftOptions: type === 'MATCHING' ? [{ id: 'L1', textRu: 'Один', textKz: 'Бір' }] : undefined,
      correctPairs: type === 'MATCHING' ? [{ leftId: 'L1', rightId: 'C' }] : undefined,
    });
    return (
      <>
        <QuestionEditor value={q} onChange={setQ} />
        <output data-testid="edited-key">{JSON.stringify(q)}</output>
      </>
    );
  }
  it.each(['MULTIPLE_SELECT', 'MATCHING'] as const)(
    'clears deleted options from %s keys',
    async (type) => {
      render(
        <AppProvider>
          <Harness type={type} />
        </AppProvider>,
      );
      await userEvent.setup().click(screen.getByRole('button', { name: 'Удалить ответ C' }));
      const q = JSON.parse(screen.getByTestId('edited-key').textContent!);
      expect(q.options.map((o: { id: string }) => o.id)).toEqual(['A', 'B']);
      if (type === 'MULTIPLE_SELECT') expect(q.correctOptionIds).toEqual(['A']);
      else expect(q.correctPairs).toEqual([]);
    },
  );
});
