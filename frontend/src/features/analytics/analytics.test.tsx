import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { AppProvider } from '../../state';
import { TOKEN_KEY } from '../../api';
import Statistics from './Statistics';
import WeeklySummary from './WeeklySummary';
import { analyticsSchema, activityDate, type Day, type Analytics } from './api';
import { accuracyPath } from './Charts';

const day = (date: string, accuracy: number | null = null): Day => ({
  date,
  questionsAnswered: accuracy === null ? 0 : 3,
  fullyCorrect: accuracy === null ? 0 : 1,
  accuracy,
  earnedPoints: accuracy === null ? 0 : 2,
  maxPoints: accuracy === null ? 0 : 4,
  testsCompleted: accuracy === null ? 0 : 1,
  testTimeSecs: 0,
  theoryReads: 0,
  lessonCompletions: 0,
  plannerTasksCompleted: 0,
  assignmentsSubmitted: 0,
  errorsResolved: 0,
  studyActions: accuracy === null ? 0 : 1,
});
const fixture: Analytics = {
  period: '7d',
  timeZone: 'Asia/Almaty',
  asOf: '2026-10-15T08:00:00Z',
  from: '2026-10-09',
  to: '2026-10-15',
  hasPracticeHistory: false,
  summary: {
    questionsAnswered: 0,
    fullyCorrectAnswers: 0,
    accuracyPercent: null,
    earnedPoints: 0,
    maxPoints: 0,
    pointsPercent: null,
    testsCompleted: 0,
    activeDays: 0,
    currentStreak: 0,
    errorsResolved: 0,
    theoriesRead: 0,
    lessonsCompleted: 0,
    plannerTasksCompleted: 0,
    assignmentsSubmitted: 0,
    testTimeSecs: 0,
  },
  comparison: {
    available: false,
    from: '2026-10-02',
    to: '2026-10-08',
    questionsDelta: null,
    accuracyDelta: null,
    pointsPercentDelta: null,
    testsCompletedDelta: null,
    activeDaysDelta: null,
  },
  daily: [day('2026-10-09'), day('2026-10-10'), day('2026-10-11')],
  heatmap: [day('2026-10-09')],
  subjects: [],
  weakTopics: [],
  strongTopics: [],
};
function setup(language = 'ru', weekly = false, metrics = fixture) {
  sessionStorage.setItem(TOKEN_KEY, 'fixture');
  const fetcher = vi.fn(async (url: RequestInfo | URL) => {
    const path = String(url);
    if (path.includes('/auth/me'))
      return new Response(
        JSON.stringify({
          id: 'a41cdaf2-8cd3-4b59-94e8-8a75fe45b7fd',
          email: 'analytics@example.org',
          firstName: 'Тест',
          lastName: 'Тест',
          language,
          role: 'STUDENT',
        }),
      );
    if (path.includes('/statistics/me/activity'))
      return new Response(JSON.stringify({ items: [], total: 0, page: 0, size: 20 }));
    const period = new URL(path, 'http://test').searchParams.get('period') || '7d';
    return new Response(JSON.stringify({ ...metrics, period }));
  });
  vi.stubGlobal('fetch', fetcher);
  render(
    <AppProvider>
      <MemoryRouter>{weekly ? <WeeklySummary /> : <Statistics />}</MemoryRouter>
    </AppProvider>,
  );
  return fetcher;
}
describe('student analytics evidence', () => {
  it('formats accepted fixed offsets without Intl timezone errors', () => {
    for (const zone of ['UTC', 'Z', 'UT', 'UTC+05:00', '+05:30', '-04:00'])
      expect(activityDate('2026-10-15T08:00:00Z', zone, 'ru-RU')).toContain('2026');
    expect(activityDate('2026-10-15T08:00:00Z', 'UTC+05:00', 'ru-RU')).toContain('13:00');
  });
  it('does not call a returning student new when the selected period has no practice', async () => {
    setup('ru', false, { ...fixture, hasPracticeHistory: true });
    expect(
      await screen.findByText(
        'В этом периоде нет завершённой практики. Выберите другой период или начните повторение.',
      ),
    ).toBeVisible();
    expect(
      screen.queryByText('Пройдите первую практику, чтобы появилась статистика.'),
    ).not.toBeInTheDocument();
  });
  it('shows null accuracy and a useful empty state rather than a false zero score', async () => {
    setup();
    expect(await screen.findByRole('heading', { name: 'Пока недостаточно данных.' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Открыть практику' })).toHaveAttribute(
      'href',
      '/practice',
    );
    expect(screen.queryByText('0%')).not.toBeInTheDocument();
    expect(
      screen.getByText('Недостаточно данных для сравнения с предыдущим периодом.'),
    ).toBeVisible();
  });
  it('switches periods and resets server-filtered history', async () => {
    const fetcher = setup();
    await screen.findByText('Ваш учебный результат');
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: '30 дней' }));
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(([url]) => String(url).includes('/analytics?period=30d')),
      ).toBe(true),
    );
    expect(screen.getByRole('button', { name: '30 дней' })).toHaveAttribute('aria-pressed', 'true');
    await screen.findByLabelText('Тип действия');
    await u.selectOptions(screen.getByLabelText('Тип действия'), 'THEORY');
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(([url]) => String(url).includes('period=30d&kind=THEORY&page=0')),
      ).toBe(true),
    );
    await u.click(screen.getByRole('button', { name: 'Всё время' }));
    await waitFor(() =>
      expect(
        fetcher.mock.calls.some(([url]) => String(url).includes('period=all&kind=ALL&page=0')),
      ).toBe(true),
    );
  });
  it('renders different accuracy and partial-points values with accessible daily data', async () => {
    setup('ru', false, {
      ...fixture,
      summary: {
        ...fixture.summary,
        questionsAnswered: 3,
        fullyCorrectAnswers: 1,
        accuracyPercent: 33.33,
        earnedPoints: 2,
        maxPoints: 4,
        pointsPercent: 50,
      },
      daily: [day('2026-10-09', 33.33), day('2026-10-10')],
    });
    expect((await screen.findAllByText(/33[,.]3%/))[0]).toBeVisible();
    expect(screen.getByText('50%')).toBeVisible();
    await userEvent.setup().click(screen.getByText('Данные графиков по дням'));
    expect(screen.getByRole('table', { name: 'Практика и учебные действия' })).toBeVisible();
    expect(screen.getByRole('rowheader', { name: '2026-10-10' })).toBeVisible();
  });
  it('splits the line at null dates and retains actual zero-accuracy evidence', () => {
    const path = accuracyPath([
      day('2026-10-09', 100),
      day('2026-10-10'),
      day('2026-10-11', 0),
      day('2026-10-12', 50),
    ]);
    expect(path.match(/M/g)).toHaveLength(2);
    expect(path.match(/L/g)).toHaveLength(1);
    expect(path).toContain(',130');
  });
  it('has Kazakh period, chart and empty-state copy', async () => {
    setup('kz');
    expect(await screen.findByRole('button', { name: 'Барлық уақыт' })).toBeVisible();
    expect(
      await screen.findByRole('heading', { name: 'Әзірге деректер жеткіліксіз.' }),
    ).toBeVisible();
    expect(screen.getByText('Графиктің күндік деректері')).toBeVisible();
  });
  it('keeps the dashboard weekly panel compact and links to statistics', async () => {
    setup('ru', true);
    expect(await screen.findByRole('link', { name: 'Подробнее' })).toHaveAttribute(
      'href',
      '/statistics',
    );
    await waitFor(() => expect(screen.getByText('Последние 7 дней')).toBeVisible());
    expect(screen.queryByText('0%')).not.toBeInTheDocument();
    expect(screen.queryByText('История активности')).not.toBeInTheDocument();
  });
  it('validates nullable percentages at the API boundary', () => {
    expect(analyticsSchema.parse(fixture).summary.accuracyPercent).toBeNull();
    expect(
      analyticsSchema.safeParse({
        ...fixture,
        summary: { ...fixture.summary, accuracyPercent: 'missing' },
      }).success,
    ).toBe(false);
  });
});
