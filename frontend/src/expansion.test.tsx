import { useState, type ReactNode } from 'react';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AppProvider } from './state';
import { TestPage } from './TestPage';
import { AnswerControls } from './features/assessment/AnswerControls';
import { completeAnswer, type Answer, type Assessment } from './features/assessment/model';
import Practice from './features/assessment/Practice';
import ContentPacks from './features/admin/ContentPacks';
import BulkActions from './features/content/BulkActions';

const sessionId = '00000000-0000-4000-8000-000000000001';
const firstId = '00000000-0000-4000-8000-000000000002';
const secondId = '00000000-0000-4000-8000-000000000003';
const contentId = '00000000-0000-4000-8000-000000000004';
const options = [
  { id: 'A', textRu: 'Первый вариант', textKz: 'Бірінші нұсқа' },
  { id: 'B', textRu: 'Второй вариант', textKz: 'Екінші нұсқа' },
  { id: 'C', textRu: 'Третий вариант', textKz: 'Үшінші нұсқа' },
];
const leftOptions = [
  { id: 'L1', textRu: 'Первое понятие', textKz: 'Бірінші ұғым' },
  { id: 'L2', textRu: 'Второе понятие', textKz: 'Екінші ұғым' },
];
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });

function mount(element: ReactNode, path = '/') {
  const router = createMemoryRouter(
    [
      { path: '/', element },
      { path: '/tests/:sessionId', element: <TestPage /> },
      { path: '/results/:sessionId', element: <h1>Finished results</h1> },
    ],
    { initialEntries: [path] },
  );
  render(
    <AppProvider>
      <RouterProvider router={router} />
    </AppProvider>,
  );
  return router;
}

function AnswerHarness({ question }: { question: Assessment }) {
  const [answer, setAnswer] = useState<Answer>({});
  return (
    <>
      <AnswerControls question={question} value={answer} onChange={setAnswer} />
      <button disabled={!completeAnswer(question, answer)}>Send answer</button>
      <output data-testid="answer-value">{JSON.stringify(answer)}</output>
    </>
  );
}

function uploadFile(text: string, name = 'pack.json') {
  const file = new File([text], name, { type: 'application/json' });
  // jsdom's File lacks Blob.text; supply the browser method for this fixture only.
  Object.defineProperty(file, 'text', { value: () => Promise.resolve(text) });
  return file;
}

const batch = {
  id: 'batch-one',
  status: 'VALID',
  preview: [{ row: 1, externalKey: 'topic-algebra', action: 'CREATE' }],
  result: null,
};
const current = {
  id: contentId,
  kind: 'THEORY',
  parentId: null,
  ownerId: null,
  titleRu: 'Сохранённый урок',
  titleKz: 'Сақталған сабақ',
  payload: {
    titleRu: 'Сохранённый урок',
    titleKz: 'Сақталған сабақ',
    contentRu: 'Исправление преподавателя',
    blocks: [],
  },
  status: 'DRAFT',
  version: 7,
  publishedVersion: 3,
  createdBy: null,
  updatedBy: null,
  createdAt: '2026-10-04T00:00:00',
  updatedAt: '2026-10-04T00:00:00',
};
const candidate = {
  id: 'conflict-one',
  status: 'OPEN',
  baseRevision: 5,
  current,
  incoming: {
    titleRu: 'Вариант пакета',
    titleKz: 'Пакет нұсқасы',
    contentRu: 'Входящее объяснение',
    blocks: [],
  },
};

describe('expansion assessment controls', () => {
  it('allows several independent choices and disables submission when all are removed', async () => {
    mount(<AnswerHarness question={{ questionType: 'MULTIPLE_SELECT', options }} />);
    const u = userEvent.setup();
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
    await u.click(screen.getByRole('checkbox', { name: 'Первый вариант' }));
    await u.click(screen.getByRole('checkbox', { name: 'Третий вариант' }));
    expect(screen.getByRole('checkbox', { name: 'Первый вариант' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Третий вариант' })).toBeChecked();
    expect(JSON.parse(screen.getByTestId('answer-value').textContent || '{}')).toEqual({
      selectedOptionIds: ['A', 'C'],
    });
    await u.click(screen.getByRole('checkbox', { name: 'Первый вариант' }));
    expect(screen.getByRole('checkbox', { name: 'Третий вариант' })).toBeChecked();
    await u.click(screen.getByRole('checkbox', { name: 'Третий вариант' }));
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
  });

  it('requires every matching row and replaces a changed pair without duplicating its left key', async () => {
    mount(<AnswerHarness question={{ questionType: 'MATCHING', options, leftOptions }} />);
    const u = userEvent.setup();
    await u.selectOptions(screen.getByRole('combobox', { name: 'Первое понятие' }), 'A');
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
    await u.selectOptions(screen.getByRole('combobox', { name: 'Второе понятие' }), 'B');
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeEnabled();
    await u.selectOptions(screen.getByRole('combobox', { name: 'Первое понятие' }), 'C');
    const answer = JSON.parse(screen.getByTestId('answer-value').textContent || '{}');
    expect(answer.pairs).toHaveLength(2);
    expect(answer.pairs).toEqual(
      expect.arrayContaining([
        { leftId: 'L1', rightId: 'C' },
        { leftId: 'L2', rightId: 'B' },
      ]),
    );
    await u.selectOptions(screen.getByRole('combobox', { name: 'Второе понятие' }), '');
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
  });

  it('submits structured answers, keeps saved choices immutable, and exposes no correctness before finish', async () => {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith(`/tests/${sessionId}`))
        return json({
          sessionId,
          topicId: null,
          subjectId: null,
          status: 'IN_PROGRESS',
          totalQuestions: 2,
          startedAt: '2026-10-04T00:00:00',
          answers: [],
          questionIds: [firstId, secondId],
        });
      if (url.includes('/questions/')) {
        const index = Number(url.at(-1));
        return json({
          sessionId,
          index,
          totalQuestions: 2,
          questionId: index === 0 ? firstId : secondId,
          topicId: null,
          topicRu: null,
          topicKz: null,
          questionRu: index === 0 ? 'Выберите подходящие значения' : 'Сопоставьте понятия',
          questionKz: null,
          options,
          difficulty: 'medium',
          year: null,
          assessment: { questionType: index === 0 ? 'MULTIPLE_SELECT' : 'MATCHING', leftOptions },
          context: {
            titleRu: 'Текст условия',
            contentRu: 'Зафиксированный текст попытки',
          },
        });
      }
      if (url.endsWith('/answers')) {
        const body = JSON.parse(String(init?.body));
        return json({
          questionId: body.questionId,
          selectedOptionId: null,
          answer: body.answer,
          // A receipt must not render accidental server fields either.
          explanationRu: 'SECRET ANSWER EXPLANATION',
          isCorrect: true,
        });
      }
      if (url.endsWith('/finish'))
        return json({
          sessionId,
          correctAnswers: 1,
          totalQuestions: 2,
          score: 50,
          timeTakenSecs: 9,
        });
      throw new Error(`Unexpected route ${url}`);
    });
    vi.stubGlobal('fetch', fetcher);
    mount(<TestPage />, `/tests/${sessionId}`);
    const u = userEvent.setup();
    await u.click(await screen.findByRole('checkbox', { name: 'Первый вариант' }));
    await u.click(screen.getByRole('checkbox', { name: 'Третий вариант' }));
    await u.click(screen.getByRole('button', { name: 'Ответить и продолжить' }));
    await screen.findByRole('combobox', { name: 'Первое понятие' });
    const post = fetcher.mock.calls.find(([url]) => url.endsWith('/answers'));
    expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({
      questionId: firstId,
      answer: { selectedOptionIds: ['A', 'C'] },
    });
    await u.click(screen.getByRole('button', { name: 'Предыдущий вопрос' }));
    expect(await screen.findByText('Ответ сохранён')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Первый вариант' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Первый вариант' })).toBeDisabled();
    expect(screen.queryByText('SECRET ANSWER EXPLANATION')).not.toBeInTheDocument();
    expect(screen.queryByText(/Правильный ответ/)).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([url]) => url.includes('/results/'))).toBe(false);
    expect(fetcher.mock.calls.some(([url]) => url.endsWith('/finish'))).toBe(false);
    await u.click(screen.getByRole('button', { name: 'Завершить тест' }));
    const dialog = await screen.findByRole('alertdialog');
    await u.click(within(dialog).getByRole('button', { name: 'Завершить попытку' }));
    expect(await screen.findByRole('heading', { name: 'Finished results' })).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([url]) => url.endsWith('/finish'))).toHaveLength(1);
  });
});

describe('honest practice setup', () => {
  it('cannot start with an empty or insufficient bank and explains the shortened exam', async () => {
    const fetcher = vi.fn(async (url: string) =>
      url.endsWith('/practice')
        ? json({
            subjects: [
              { id: firstId, titleRu: 'Математика', titleKz: 'Математика', available: 2 },
              { id: secondId, titleRu: 'Физика', titleKz: 'Физика', available: 0 },
            ],
            topics: [
              {
                id: contentId,
                subjectId: firstId,
                titleRu: 'Линейные уравнения',
                titleKz: 'Сызықтық теңдеулер',
                difficulty: 'easy',
                available: 1,
              },
              {
                id: sessionId,
                subjectId: firstId,
                titleRu: 'Квадратные уравнения',
                titleKz: 'Квадрат теңдеулер',
                difficulty: 'medium',
                available: 1,
              },
            ],
            configuration: {
              id: 'official-2026',
              checkedAt: '2026-10-04',
              durationMinutes: 240,
              allowedProfilePairs: [{ subjects: ['Математика', 'Физика'] }],
              formatEvidence: { url: 'https://testcenter.kz/' },
            },
          })
        : json({ officialReady: false, availableTotal: 2, shortenedMaximum: 0, subjects: [] }),
    );
    vi.stubGlobal('fetch', fetcher);
    mount(<Practice />);
    const u = userEvent.setup();
    const start = await screen.findByRole('button', { name: 'Начать тренировку' });
    expect(start).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: /Физика/ })).toBeDisabled();
    await u.click(screen.getByRole('checkbox', { name: /Математика/ }));
    expect(start).toBeDisabled(); // Default ten exceeds the actual two questions.
    const count = screen.getByRole('spinbutton', { name: 'Количество вопросов (до 50)' });
    await u.clear(count);
    await u.type(count, '2');
    expect(start).toBeEnabled();
    await u.selectOptions(screen.getByRole('combobox', { name: 'Сложность' }), 'easy');
    expect(start).toBeDisabled(); // One easy question cannot satisfy a request for two.
    await u.clear(count);
    await u.type(count, '1');
    expect(start).toBeEnabled();
    await u.selectOptions(screen.getByRole('combobox', { name: 'Сложность' }), 'hard');
    expect(start).toBeDisabled();
    await u.selectOptions(screen.getByRole('combobox', { name: 'Сложность' }), '');
    expect(start).toBeEnabled();
    await u.click(screen.getByRole('button', { name: 'Тренировка с таймером' }));
    expect(await screen.findByText(/Полный пробник ЕНТ недоступен/)).toBeInTheDocument();
    expect(start).toBeDisabled();
    expect(fetcher.mock.calls.some(([url]) => url.endsWith('/practice/sessions'))).toBe(false);
  });
});

describe('content-pack editor workflow', () => {
  it('shows a valid preview and sends confirmation only after an explicit action', async () => {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/confirm'))
        return json({
          ...batch,
          status: 'IMPORTED',
          result: { created: 1, updated: 0, unchanged: 0, conflicts: 0 },
        });
      if (url.endsWith('/content-packs') && init?.method === 'POST') return json(batch);
      return json([]);
    });
    vi.stubGlobal('fetch', fetcher);
    mount(<ContentPacks />);
    const u = userEvent.setup();
    await u.upload(
      screen.getByLabelText('Пакет JSON, до 2 МБ'),
      uploadFile('{"namespace":"starter"}'),
    );
    expect(await screen.findByText('topic-algebra')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([url]) => url.endsWith('/confirm'))).toBe(false);
    await u.click(screen.getByRole('button', { name: 'Подтвердить импорт в черновики' }));
    expect(await screen.findByText(/1 \/ 0 \/ 0 \/ 0/)).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([url]) => url.endsWith('/confirm'))).toHaveLength(1);
    expect(
      screen.queryByRole('button', { name: 'Подтвердить импорт в черновики' }),
    ).not.toBeInTheDocument();
  });

  it('invalid local JSON clears an old preview and is never sent to the API', async () => {
    const fetcher = vi.fn(async (_url: string, init?: RequestInit) =>
      init?.method === 'POST' ? json(batch) : json([]),
    );
    vi.stubGlobal('fetch', fetcher);
    mount(<ContentPacks />);
    const u = userEvent.setup();
    const input = screen.getByLabelText('Пакет JSON, до 2 МБ');
    await u.upload(input, uploadFile('{}'));
    await screen.findByRole('button', { name: 'Подтвердить импорт в черновики' });
    await waitFor(() => expect(input).toBeEnabled());
    await u.upload(input, uploadFile('{broken', 'broken.json'));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Подтвердить импорт в черновики' }),
    ).not.toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
    expect(screen.getByText('broken.json')).toBeInTheDocument();
  });

  it('shows server row errors for an invalid batch without a confirmation control', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === 'POST'
          ? json({
              ...batch,
              status: 'INVALID',
              preview: [
                {
                  row: 28,
                  externalKey: 'bad-question',
                  error: 'correctOptionId D is missing from options',
                },
              ],
            })
          : json([]),
      ),
    );
    mount(<ContentPacks />);
    await userEvent.setup().upload(screen.getByLabelText('Пакет JSON, до 2 МБ'), uploadFile('{}'));
    const table = await screen.findByRole('region', { name: 'Строки пакета' });
    expect(table).toHaveTextContent('28');
    expect(table).toHaveTextContent('correctOptionId D is missing from options');
    expect(
      screen.queryByRole('button', { name: 'Подтвердить импорт в черновики' }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['Оставить текущий текст', 'KEEP_LOCAL'],
    ['Принять входящий черновик', 'USE_INCOMING_DRAFT'],
  ])('resolves a compared conflict with the displayed version: %s', async (label, decision) => {
    let resolved = false;
    const fetcher = vi.fn(async (url: string) => {
      if (url.endsWith('/resolve')) {
        resolved = true;
        return json({ ...candidate, status: 'RESOLVED' });
      }
      if (url.endsWith('/conflicts/conflict-one')) return json(candidate);
      if (url.includes('/conflicts?page='))
        return json(
          resolved ? [] : [{ id: candidate.id, contentId, baseRevision: 5, status: 'OPEN' }],
        );
      return json([]);
    });
    vi.stubGlobal('fetch', fetcher);
    mount(<ContentPacks />);
    const u = userEvent.setup();
    await u.click(await screen.findByRole('button', { name: /Сравнить версии/ }));
    expect(await screen.findByText('Исправление преподавателя')).toBeInTheDocument();
    expect(screen.getByText('Входящее объяснение')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: label }));
    expect(await screen.findByText('Нет нерешённых конфликтов.')).toBeInTheDocument();
    const resolveCall = fetcher.mock.calls.find(([url]) => url.endsWith('/resolve'));
    // The mock's network signature is widened here to inspect the request generated by the real API client.
    const request = resolveCall as unknown as [string, RequestInit];
    expect(JSON.parse(String(request[1].body))).toEqual({ decision, version: 7 });
  });

  it('keeps both versions available after a conflict-resolution 409', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.endsWith('/resolve')) return json({ code: 'REVISION_CONFLICT' }, 409);
        if (url.endsWith('/conflicts/conflict-one')) return json(candidate);
        if (url.includes('/conflicts?page='))
          return json([{ id: candidate.id, contentId, baseRevision: 5, status: 'OPEN' }]);
        return json([]);
      }),
    );
    mount(<ContentPacks />);
    const u = userEvent.setup();
    await u.click(await screen.findByRole('button', { name: /Сравнить версии/ }));
    await u.click(await screen.findByRole('button', { name: 'Принять входящий черновик' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Данные изменились');
    expect(screen.getByText('Исправление преподавателя')).toBeInTheDocument();
    expect(screen.getByText('Входящее объяснение')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Оставить текущий текст' })).toBeEnabled();
  });
});

describe('bulk transition preview validity', () => {
  const preview = {
    valid: true,
    confirmation: 'signed-preview-token',
    rows: [
      {
        id: firstId,
        titleRu: 'Первая тема',
        titleKz: 'Бірінші тақырып',
        from: 'DRAFT',
        to: 'REVIEW',
      },
    ],
    applied: false,
  };

  it('invalidates the preview when selected content revisions change', async () => {
    const fetcher = vi.fn(async () => json(preview));
    vi.stubGlobal('fetch', fetcher);
    function Harness() {
      const [version, setVersion] = useState(1);
      return (
        <>
          <button onClick={() => setVersion(2)}>Receive newer revision</button>
          <BulkActions items={[{ id: firstId, version }]} reload={() => {}} />
        </>
      );
    }
    mount(<Harness />);
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'Предпросмотр изменений' }));
    await screen.findByRole('button', { name: 'Подтвердить изменения' });
    await u.click(screen.getByRole('button', { name: 'Receive newer revision' }));
    expect(screen.queryByRole('button', { name: 'Подтвердить изменения' })).not.toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('does not revive an old target preview when its network response arrives after the target changed', async () => {
    let release!: (r: Response) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn(
        () =>
          new Promise<Response>((resolve) => {
            release = resolve;
          }),
      ),
    );
    mount(<BulkActions items={[{ id: firstId, version: 1 }]} reload={() => {}} />);
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'Предпросмотр изменений' }));
    await u.selectOptions(screen.getByRole('combobox', { name: 'Действие' }), 'PUBLISHED');
    await act(async () => {
      release(json(preview));
    });
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Предпросмотр изменений' })).toBeEnabled(),
    );
    expect(screen.queryByRole('button', { name: 'Подтвердить изменения' })).not.toBeInTheDocument();
  });
});
