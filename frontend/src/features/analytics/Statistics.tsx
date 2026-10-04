import { useState } from 'react';
import { Link } from 'react-router';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useResource } from '../../hooks';
import { useL, Pager } from '../shared';
import {
  analyticsSchema,
  activityDate,
  historySchema,
  periods,
  percentage,
  type Period,
  type Analytics,
} from './api';
import { DailyCharts, Heatmap } from './Charts';
import './analytics.css';

export function PeriodSwitch({
  value,
  onChange,
  all = true,
}: {
  value: Period;
  onChange: (p: Period) => void;
  all?: boolean;
}) {
  const l = useL();
  return (
    <div
      className="period-switch"
      role="group"
      aria-label={l('Период статистики', 'Статистика кезеңі')}
    >
      {periods
        .filter((p) => all || p !== 'all')
        .map((p) => (
          <button type="button" key={p} aria-pressed={value === p} onClick={() => onChange(p)}>
            {p === '7d'
              ? l('7 дней', '7 күн')
              : p === '30d'
                ? l('30 дней', '30 күн')
                : l('Всё время', 'Барлық уақыт')}
          </button>
        ))}
    </div>
  );
}
export function Summary({ data }: { data: Analytics }) {
  const l = useL(),
    s = data.summary,
    c = data.comparison;
  const delta = (n: number | null | undefined, points = false) =>
    n == null
      ? '—'
      : `${n > 0 ? '+' : ''}${n.toLocaleString(undefined, { maximumFractionDigits: 1 })}${points ? l(' п.п.', ' п.т.') : ''}`;
  const metrics = [
    {
      label: l('Решено вопросов', 'Шешілген сұрақтар'),
      value: s.questionsAnswered,
      change: c?.questionsDelta,
    },
    {
      label: l('Точность', 'Дәлдік'),
      value: percentage(s.accuracyPercent),
      change: c?.accuracyDelta,
      points: true,
    },
    {
      label: l('Доля баллов', 'Балдар үлесі'),
      value: percentage(s.pointsPercent),
      change: c?.pointsPercentDelta,
      points: true,
    },
    {
      label: l('Завершено тестов', 'Аяқталған тесттер'),
      value: s.testsCompleted,
      change: c?.testsCompletedDelta,
    },
    {
      label: l('Активных дней', 'Белсенді күндер'),
      value: s.activeDays,
      change: c?.activeDaysDelta,
    },
  ];
  return (
    <section className="panel analytics-summary" aria-labelledby="summary-heading">
      <h2 id="summary-heading">{l('Ваш учебный результат', 'Оқу нәтижеңіз')}</h2>
      <dl className="analytics-metrics">
        {metrics.map((m) => (
          <div key={m.label}>
            <dt>{m.label}</dt>
            <dd>{m.value}</dd>
            {c?.available && <dd className="metric-delta">{delta(m.change, m.points)}</dd>}
          </div>
        ))}
      </dl>
      {c && (
        <p className="muted comparison-note">
          {c.available
            ? `${l('Изменение к периоду', 'Алдыңғы кезеңмен өзгеріс')} ${c.from} — ${c.to}`
            : l(
                'Недостаточно данных для сравнения с предыдущим периодом.',
                'Алдыңғы кезеңмен салыстыруға деректер жеткіліксіз.',
              )}
        </p>
      )}
      <dl className="analytics-facts">
        {[
          [l('Полностью верно', 'Толық дұрыс'), `${s.fullyCorrectAnswers}/${s.questionsAnswered}`],
          [l('Набрано баллов', 'Жиналған балдар'), `${s.earnedPoints}/${s.maxPoints}`],
          [l('Текущая серия дней', 'Қазіргі күндер тізбегі'), s.currentStreak],
          [l('Исправлено ошибок', 'Түзетілген қателер'), s.errorsResolved],
          [l('Прочитано теорий', 'Оқылған теориялар'), s.theoriesRead],
          [l('Завершено уроков', 'Аяқталған сабақтар'), s.lessonsCompleted],
          [l('Выполнено задач плана', 'Орындалған жоспар тапсырмалары'), s.plannerTasksCompleted],
          [
            l('Отправлено ответов на задания', 'Жіберілген тапсырма жауаптары'),
            s.assignmentsSubmitted,
          ],
          [
            l('Время в тестах', 'Тесттердегі уақыт'),
            `${Math.floor(s.testTimeSecs / 60)} ${l('мин', 'мин')} ${s.testTimeSecs % 60} ${l('сек', 'сек')}`,
          ],
        ].map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <details className="analytics-data">
        <summary>{l('Как считаются показатели', 'Көрсеткіштер қалай есептеледі')}</summary>
        <p>
          {l(
            'Точность — полностью правильные ответы / все вопросы завершённых практик ЕНТ. Пропуски считаются ошибками. Частичные баллы входят в долю баллов, но не в точность. Время в тестах не включает чтение и уроки.',
            'Дәлдік — толық дұрыс жауаптар / аяқталған ҰБТ жаттығуларындағы барлық сұрақтар. Өткізілген сұрақтар қате саналады. Ішінара балдар балдар үлесіне кіреді, дәлдікке кірмейді. Тест уақыты оқу мен сабақтарды қамтымайды.',
          )}
        </p>
      </details>
    </section>
  );
}
function Topics({ data }: { data: Analytics }) {
  const l = useL();
  return (
    <section className="panel" aria-labelledby="topics-heading">
      <h2 id="topics-heading">
        {l('Что повторить, на что опереться', 'Нені қайталау, нені негізге алу')}
      </h2>
      <p className="muted">
        {l(
          'Освоение по всей истории. Сильная тема: от 3 попыток и 10 разных вопросов.',
          'Барлық тарихтағы меңгеру. Күшті тақырып: кемінде 3 әрекет пен 10 түрлі сұрақ.',
        )}
      </p>
      <div className="analytics-topic-pair">
        {[
          { title: l('Слабые темы', 'Әлсіз тақырыптар'), items: data.weakTopics, weak: true },
          { title: l('Сильные темы', 'Күшті тақырыптар'), items: data.strongTopics, weak: false },
        ].map((group) => (
          <div key={group.title}>
            <h3>{group.title}</h3>
            {group.items.length === 0 ? (
              <p className="muted">
                {l('Пока недостаточно данных.', 'Әзірге деректер жеткіліксіз.')}
              </p>
            ) : (
              <ul className="analytics-topics">
                {group.items.map((t) => (
                  <li key={t.topicId}>
                    <Link to={`/topics/${t.topicId}`}>{l(t.titleRu, t.titleKz)}</Link>
                    <span>
                      {l('Освоение', 'Меңгеру')}: {t.mastery}%
                    </span>
                    <small>
                      {l('Недавняя точность', 'Соңғы дәлдік')}: {t.recentAccuracy}%;{' '}
                      {l('попыток', 'әрекет')}: {t.attempts}
                    </small>
                    {group.weak && (
                      <Link className="text-button" to={`/topics/${t.topicId}`}>
                        {l('Повторить', 'Қайталау')}
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
export function ActivityHistory({ period, timeZone }: { period: Period; timeZone: string }) {
  const l = useL(),
    [kind, setKind] = useState('ALL'),
    [page, setPage] = useState(0);
  const labels: Record<string, string> = {
    ALL: l('Все действия', 'Барлық әрекет'),
    TEST: l('Тест', 'Тест'),
    THEORY: l('Теория', 'Теория'),
    LESSON: l('Урок', 'Сабақ'),
    ERROR_RESOLVED: l('Исправленная ошибка', 'Түзетілген қате'),
    PLANNER_TASK: l('Задача плана', 'Жоспар тапсырмасы'),
    ASSIGNMENT: l('Отправка задания', 'Тапсырма жіберу'),
  };
  const resource = useResource(
    `/statistics/me/activity?period=${period}&kind=${kind}&page=${page}`,
    historySchema,
  );
  return (
    <section className="panel" aria-labelledby="history-heading">
      <div className="analytics-section-title">
        <h2 id="history-heading">{l('История активности', 'Белсенділік тарихы')}</h2>
        <label className="field">
          <span>{l('Тип действия', 'Әрекет түрі')}</span>
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value);
              setPage(0);
            }}
          >
            {Object.entries(labels).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            {resource.data.items.length === 0 ? (
              <p>{l('За этот период действий нет.', 'Бұл кезеңде әрекет жоқ.')}</p>
            ) : (
              <ol className="analytics-history">
                {resource.data.items.map((e) => (
                  <li key={`${e.kind}:${e.id}`}>
                    <time dateTime={e.occurredAt}>
                      {activityDate(e.occurredAt, timeZone, l('ru-RU', 'kk-KZ'))}
                    </time>
                    <span>
                      <small>{labels[e.kind]}</small>
                      <Link to={e.href}>{l(e.titleRu, e.titleKz) || labels[e.kind]}</Link>
                    </span>
                  </li>
                ))}
              </ol>
            )}
            <Pager page={page} total={resource.data.total} size={20} onChange={setPage} />
          </>
        )
      )}
    </section>
  );
}
export default function Statistics() {
  const l = useL(),
    [period, setPeriod] = useState<Period>('7d');
  const resource = useResource(`/statistics/me/analytics?period=${period}`, analyticsSchema),
    data = resource.data;
  return (
    <div className="analytics-page">
      <div className="analytics-heading">
        <PageHeading
          title={l('Статистика', 'Статистика')}
          body={l('Ваш ритм обучения и следующий шаг.', 'Оқу ырғағыңыз және келесі қадам.')}
        />
        <PeriodSwitch value={period} onChange={setPeriod} />
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : (
        data && (
          <>
            <p className="analytics-range">
              {data.from} — {data.to} · {data.timeZone}
            </p>
            {data.summary.questionsAnswered === 0 && (
              <Empty
                title={l('Пока недостаточно данных.', 'Әзірге деректер жеткіліксіз.')}
                body={l(
                  data.hasPracticeHistory
                    ? 'В этом периоде нет завершённой практики. Выберите другой период или начните повторение.'
                    : 'Пройдите первую практику, чтобы появилась статистика.',
                  data.hasPracticeHistory
                    ? 'Бұл кезеңде аяқталған жаттығу жоқ. Басқа кезеңді таңдаңыз немесе қайталауды бастаңыз.'
                    : 'Статистика пайда болуы үшін алғашқы жаттығуды орындаңыз.',
                )}
                action={
                  <Link className="button" to="/practice">
                    {l('Открыть практику', 'Жаттығуды ашу')}
                  </Link>
                }
              />
            )}
            <Summary data={data} />
            <DailyCharts days={data.daily} />
            <Heatmap key={`${period}:${data.asOf}`} days={data.heatmap} />
            <section className="panel">
              <h2>{l('Результаты по предметам', 'Пәндер бойынша нәтижелер')}</h2>
              {data.subjects.length === 0 ? (
                <p>
                  {l(
                    'Практика по предметам ещё не завершена.',
                    'Пәндер бойынша жаттығу әлі аяқталмады.',
                  )}
                </p>
              ) : (
                <ul className="analytics-subjects">
                  {data.subjects.map((s) => (
                    <li key={s.subjectId}>
                      <div className="analytics-section-title">
                        <Link to={`/subjects/${s.subjectId}`}>{l(s.titleRu, s.titleKz)}</Link>
                        <strong>{percentage(s.accuracyPercent)}</strong>
                      </div>
                      <progress
                        value={s.accuracyPercent ?? 0}
                        max={100}
                        aria-label={`${l(s.titleRu, s.titleKz)}: ${l('точность', 'дәлдік')}`}
                      />
                      <p>
                        {l('Доля баллов', 'Балдар үлесі')}: {percentage(s.pointsPercent)};{' '}
                        {l('попыток', 'әрекет')}: {s.attempts}; {l('вопросов', 'сұрақ')}:{' '}
                        {s.questionsAnswered}
                      </p>
                      <small>
                        {s.accuracyDelta == null
                          ? l(
                              'Недостаточно данных для сравнения',
                              'Салыстыруға деректер жеткіліксіз',
                            )
                          : `${s.accuracyDelta > 0 ? '+' : ''}${s.accuracyDelta} ${l('п.п. к предыдущему периоду', 'п.т. алдыңғы кезеңмен')}`}
                      </small>
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <Topics data={data} />
            <ActivityHistory key={period} period={period} timeZone={data.timeZone} />
          </>
        )
      )}
    </div>
  );
}
