import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { request } from '../../api';
import { Empty, ErrorState, Loading, PageHeading } from '../../components';
import { useApp } from '../../state';
import { Feedback, Field, Pager, useAction, useL } from '../shared';
import {
  calendarSchema,
  dayInZone,
  deadlinesSchema,
  localInput,
  openedSchema,
  planSchema,
  profileSchema,
  shiftedDay,
  taskSchema,
  windowFor,
  zonedInstant,
  type Task,
} from './model';
import { StudyProfile } from './StudyProfile';
import { useStudyResource } from './useStudyResource';
import './study.css';

const reasons: Record<string, [string, string]> = {
  RECENT_ERRORS: ['Есть ошибки в последних попытках', 'Соңғы әрекеттерде қателер бар'],
  UNREAD_THEORY: ['Теория ещё не прочитана', 'Теория әлі оқылмаған'],
  WEAK_TOPIC: ['Освоение темы ниже 70%', 'Тақырыпты меңгеру 70%-дан төмен'],
  TOPIC_PRACTICE: ['Практика выбранного предмета', 'Таңдалған пән бойынша жаттығу'],
  UNFINISHED_LESSON: ['Урок курса ещё не завершён', 'Курс сабағы әлі аяқталмаған'],
  ASSIGNMENT_DUE: ['Назначено вашей группе', 'Сіздің тобыңызға берілген'],
  PERSONAL_CARD: ['Пора повторить личную карточку', 'Жеке карточканы қайталау уақыты'],
};
const kinds: Record<string, [string, string]> = {
  ERROR_REVIEW: ['Ошибки', 'Қателер'],
  READ: ['Теория', 'Теория'],
  PRACTICE: ['Практика', 'Жаттығу'],
  LESSON: ['Урок', 'Сабақ'],
  ASSIGNMENT: ['Задание', 'Тапсырма'],
  REPETITION: ['Повторение', 'Қайталау'],
};
function TaskRow({ task, zone, reload }: { task: Task; zone: string; reload: () => void }) {
  const l = useL(),
    { language } = useApp(),
    action = useAction(),
    navigate = useNavigate();
  const [moving, setMoving] = useState(false);
  const [date, setDate] = useState(
    task.scheduledAt ? localInput(task.scheduledAt, zone) : dayInZone(new Date(), zone) + 'T18:00',
  );
  const change = (body: object) =>
    void action.run(async () => {
      await request('/study/tasks/' + task.id, taskSchema, {
        method: 'PATCH',
        body: { ...body, revision: task.revision },
      });
      setMoving(false);
      reload();
    });
  const time = task.scheduledAt
    ? new Intl.DateTimeFormat(language === 'ru' ? 'ru-RU' : 'kk-KZ', {
        timeZone: zone,
        hour: '2-digit',
        minute: '2-digit',
      }).format(new Date(task.scheduledAt))
    : '—';
  return (
    <article className={'study-task study-task-' + task.status.toLowerCase()}>
      <div className="study-task-clock">
        <strong>{time}</strong>
        <span>
          {task.durationMinutes} {l('мин', 'мин')}
        </span>
      </div>
      <div className="study-task-body">
        <div className="study-task-meta">
          {l(...(kinds[task.kind] || ['Задача', 'Тапсырма']))}
          {task.pinned && <span> · {l('Закреплено', 'Бекітілген')}</span>}
          {task.manuallyMoved && <span> · {l('Перенесено вами', 'Сіз ауыстырдыңыз')}</span>}
        </div>
        <h3>{l(task.titleRu, task.titleKz)}</h3>
        <p>{l(...(reasons[task.reason] || ['Личный план', 'Жеке жоспар']))}</p>
        {!task.available && (
          <p className="study-unavailable">
            {l(
              'Материал больше недоступен. Личная задача сохранена.',
              'Материал енді қолжетімсіз. Жеке тапсырма сақталды.',
            )}
          </p>
        )}
        {task.status === 'COMPLETED' ? (
          <span className="study-status">{l('Выполнено', 'Орындалды')}</span>
        ) : task.status === 'SKIPPED' ? (
          <span className="study-status">{l('Пропущено', 'Өткізілді')}</span>
        ) : null}
        <div className="study-actions">
          <button
            className="button small"
            disabled={!task.available || action.busy}
            onClick={() =>
              void action.run(async () => {
                const result = await request('/study/tasks/' + task.id + '/open', openedSchema, {
                  method: 'POST',
                });
                navigate(result.url);
              })
            }
          >
            {l('Открыть', 'Ашу')}
          </button>
          <button
            className="button secondary small"
            disabled={action.busy}
            onClick={() =>
              change({ status: task.status === 'COMPLETED' ? 'PLANNED' : 'COMPLETED' })
            }
          >
            {task.status === 'COMPLETED'
              ? l('Вернуть в план', 'Жоспарға қайтару')
              : l('Готово', 'Дайын')}
          </button>
          <button
            className="button secondary small"
            disabled={action.busy}
            aria-pressed={task.pinned}
            onClick={() => change({ pinned: !task.pinned })}
          >
            {task.pinned ? l('Открепить', 'Босату') : l('Закрепить', 'Бекіту')}
          </button>
          <button
            className="button secondary small"
            disabled={action.busy}
            aria-expanded={moving}
            onClick={() => setMoving((v) => !v)}
          >
            {l('Перенести', 'Ауыстыру')}
          </button>
          {task.status === 'PLANNED' && (
            <button
              className="button secondary small"
              disabled={action.busy}
              onClick={() => change({ status: 'SKIPPED' })}
            >
              {l('Пропустить', 'Өткізу')}
            </button>
          )}
          {task.status === 'SKIPPED' && (
            <button
              className="button secondary small"
              disabled={action.busy}
              onClick={() => change({ status: 'PLANNED' })}
            >
              {l('Вернуть в план', 'Жоспарға қайтару')}
            </button>
          )}
        </div>
        {moving && (
          <form
            className="study-move"
            onSubmit={(e) => {
              e.preventDefault();
              void action.run(async () => {
                await request('/study/tasks/' + task.id, taskSchema, {
                  method: 'PATCH',
                  body: { scheduledAt: zonedInstant(date, zone), revision: task.revision },
                });
                setMoving(false);
                reload();
              });
            }}
          >
            <Field label={l('Новая дата и время', 'Жаңа күн мен уақыт') + ' · ' + zone}>
              <input
                type="datetime-local"
                required
                value={date}
                disabled={action.busy}
                onChange={(e) => setDate(e.target.value)}
              />
            </Field>
            <button className="button secondary" disabled={action.busy}>
              {l('Применить перенос', 'Ауыстыруды қолдану')}
            </button>
          </form>
        )}
        <Feedback action={action} />
      </div>
    </article>
  );
}
export function StudyPage() {
  const l = useL();
  const profile = useStudyResource('/study/profile', profileSchema);
  return (
    <div className="study-page">
      <PageHeading
        title={l('Мой план', 'Менің жоспарым')}
        body={l(
          'Учитесь в своём темпе. Здесь — реальные материалы, сроки и следующие шаги.',
          'Өз қарқыныңызбен оқыңыз. Мұнда нақты материалдар, мерзімдер және келесі қадамдар бар.',
        )}
      />
      {profile.error ? (
        <ErrorState error={profile.error} retry={profile.reload} />
      ) : !profile.data ? (
        <Loading />
      ) : (
        <StudyWorkspace profile={profile.data} reloadProfile={profile.reload} />
      )}
    </div>
  );
}
function StudyWorkspace({
  profile,
  reloadProfile,
}: {
  profile: import('./model').Profile;
  reloadProfile: () => void;
}) {
  const l = useL(),
    { language } = useApp(),
    action = useAction();
  const [mode, setMode] = useState<'day' | 'week' | 'month'>('week'),
    [day, setDay] = useState(() => dayInZone(new Date(), profile.timeZone)),
    [page, setPage] = useState(0),
    [deadlinePage, setDeadlinePage] = useState(0),
    [waiting, setWaiting] = useState(false),
    [result, setResult] = useState<import('zod').infer<typeof planSchema> | null>(null);
  const range = windowFor(day, mode);
  const tasks = useStudyResource(
    `/study/tasks?from=${range.from}&to=${range.to}&page=${page}&unscheduled=${waiting}`,
    calendarSchema,
  );
  const deadlines = useStudyResource(
    `/study/deadlines?from=${range.from}&to=${range.to}&page=${deadlinePage}`,
    deadlinesSchema,
  );
  const groups = new Map<string, Task[]>();
  for (const task of tasks.data?.items || []) {
    const key = task.scheduledAt ? dayInZone(task.scheduledAt, profile.timeZone) : '';
    groups.set(key, [...(groups.get(key) || []), task]);
  }
  function shift(direction: number) {
    if (mode === 'month') {
      const d = new Date(range.from + 'T12:00:00Z');
      d.setUTCMonth(d.getUTCMonth() + direction);
      setDay(d.toISOString().slice(0, 10));
    } else setDay(shiftedDay(day, direction * (mode === 'day' ? 1 : 7)));
    setPage(0);
    setDeadlinePage(0);
  }
  return (
    <>
      <div className="study-nav">
        <Link to="/notes">{l('Заметки и карточки', 'Жазбалар мен карточкалар')}</Link>
        <Link to="/notifications">{l('Уведомления', 'Хабарламалар')}</Link>
      </div>
      <StudyProfile key={profile.revision} profile={profile} onSaved={reloadProfile} />
      <div className="study-plan-line">
        <p>
          {profile.goal || l('Задайте цель в учебном профиле', 'Оқу профилінде мақсат қойыңыз')}
          <small>
            {l('До', 'Дейін')} {profile.targetDate} · {profile.timeZone}
          </small>
        </p>
        <button
          className="button"
          disabled={action.busy || profile.revision === 0}
          onClick={() =>
            void action.run(async () => {
              setResult(await request('/study/plan/recompute', planSchema, { method: 'POST' }));
              tasks.reload();
            })
          }
        >
          {action.busy
            ? l('Рассчитываем…', 'Есептелуде…')
            : l('Составить / обновить план', 'Жоспарды құру / жаңарту')}
        </button>
      </div>
      <Feedback action={action} />
      {result && (
        <div className={result.capacityWarning ? 'study-warning' : 'study-summary'} role="status">
          <strong>
            {result.capacityWarning
              ? l('Времени для всего плана недостаточно', 'Бүкіл жоспарға уақыт жеткіліксіз')
              : l('План обновлён', 'Жоспар жаңартылды')}
          </strong>
          <p>
            {l('Запланировано', 'Жоспарланды')}: {result.planned} ·{' '}
            {l('Сохранено ваших задач', 'Сақталған тапсырмаларыңыз')}: {result.preserved} ·{' '}
            {l('Без даты', 'Күнсіз')}: {result.unscheduled}
          </p>
          <p>
            {result.requiredMinutes} / {result.capacityMinutes}{' '}
            {l(
              'минут (задачи / доступное время). Измените срок или нагрузку в профиле при необходимости.',
              'минут (тапсырмалар / қолжетімді уақыт). Қажет болса, профильдегі мерзімді немесе жүктемені өзгертіңіз.',
            )}
          </p>
        </div>
      )}
      <section aria-label={l('Календарь', 'Күнтізбе')}>
        <div className="study-toolbar">
          <div
            className="study-segments"
            role="group"
            aria-label={l('Вид календаря', 'Күнтізбе көрінісі')}
          >
            {(['day', 'week', 'month'] as const).map((m, i) => (
              <button
                key={m}
                aria-pressed={mode === m}
                onClick={() => {
                  setMode(m);
                  setPage(0);
                  setDeadlinePage(0);
                }}
              >
                {l(
                  ...[
                    ['Сегодня', 'Бүгін'],
                    ['Неделя', 'Апта'],
                    ['Месяц', 'Ай'],
                  ][i],
                )}
              </button>
            ))}
          </div>
          <div className="study-date-nav">
            <button
              className="button secondary small"
              aria-label={l('Предыдущий период', 'Алдыңғы кезең')}
              onClick={() => shift(-1)}
            >
              ‹
            </button>
            <Field label={l('Дата календаря', 'Күнтізбе күні')}>
              <input
                type="date"
                required
                value={day}
                onChange={(e) => {
                  if (e.target.value) {
                    setDay(e.target.value);
                    setPage(0);
                    setDeadlinePage(0);
                  }
                }}
              />
            </Field>
            <button
              className="button secondary small"
              aria-label={l('Следующий период', 'Келесі кезең')}
              onClick={() => shift(1)}
            >
              ›
            </button>
            <button
              className="button secondary small"
              onClick={() => {
                setDay(dayInZone(new Date(), profile.timeZone));
                setPage(0);
                setDeadlinePage(0);
              }}
            >
              {l('К сегодня', 'Бүгінге')}
            </button>
          </div>
        </div>
        <p className="study-muted">
          {range.from} — {range.to} · {profile.timeZone}
        </p>
        {deadlines.error ? (
          <ErrorState error={deadlines.error} retry={deadlines.reload} />
        ) : (
          !!deadlines.data?.total && (
            <section className="study-deadlines">
              <h2>{l('Сроки заданий', 'Тапсырма мерзімдері')}</h2>
              {deadlines.data.items.map((d) => (
                <Link to={d.url} key={d.id}>
                  <time dateTime={d.dueAt}>
                    {new Intl.DateTimeFormat(language === 'ru' ? 'ru-RU' : 'kk-KZ', {
                      timeZone: profile.timeZone,
                      dateStyle: 'short',
                      timeStyle: 'short',
                    }).format(new Date(d.dueAt))}
                  </time>
                  <span>{l(d.titleRu, d.titleKz)}</span>
                  <small>
                    {d.submitted ? l('Отправлено', 'Жіберілді') : l('К сдаче', 'Тапсыру керек')}
                  </small>
                </Link>
              ))}
              {deadlines.data.total > 25 && (
                <Pager
                  page={deadlinePage}
                  total={deadlines.data.total}
                  onChange={setDeadlinePage}
                />
              )}
            </section>
          )
        )}
        <div className="study-list-heading">
          <h2>{l('Учебные задачи', 'Оқу тапсырмалары')}</h2>
          <label>
            <input
              type="checkbox"
              checked={waiting}
              onChange={(e) => {
                setWaiting(e.target.checked);
                setPage(0);
              }}
            />
            {l('Без даты', 'Күнсіз')} ({tasks.data?.unscheduled ?? 0})
          </label>
        </div>
        {tasks.error ? (
          <ErrorState error={tasks.error} retry={tasks.reload} />
        ) : !tasks.data ? (
          <Loading />
        ) : tasks.data.items.length === 0 ? (
          <Empty
            title={l('Здесь пока нет задач', 'Мұнда әзірге тапсырмалар жоқ')}
            body={
              waiting
                ? l('Все задачи распределены по датам.', 'Барлық тапсырмалар күндерге бөлінген.')
                : l(
                    'Сохраните профиль и составьте план, либо выберите другой период.',
                    'Профильді сақтап, жоспар құрыңыз немесе басқа кезеңді таңдаңыз.',
                  )
            }
          />
        ) : (
          <div aria-busy={tasks.loading}>
            {[...groups].map(([date, items]) => (
              <section className="study-day" key={date}>
                <h3 className="study-day-label">
                  {date
                    ? new Intl.DateTimeFormat(language === 'ru' ? 'ru-RU' : 'kk-KZ', {
                        dateStyle: 'full',
                        timeZone: 'UTC',
                      }).format(new Date(date + 'T12:00:00Z'))
                    : l('Нужно выбрать время', 'Уақыт таңдау керек')}
                </h3>
                {items.map((task) => (
                  <TaskRow
                    key={task.id + ':' + task.revision}
                    task={task}
                    zone={profile.timeZone}
                    reload={tasks.reload}
                  />
                ))}
              </section>
            ))}
          </div>
        )}
        {tasks.data && tasks.data.total > 50 && (
          <Pager size={50} page={page} total={tasks.data.total} onChange={setPage} />
        )}
      </section>
    </>
  );
}
