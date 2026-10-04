import { Link } from 'react-router';
import { useResource } from '../../hooks';
import { ErrorState } from '../../components';
import { useL } from '../shared';
import { calendarSchema, dayInZone, deadlinesSchema, profileSchema, shiftedDay } from './model';
export default function Today() {
  const p = useResource('/study/profile', profileSchema);
  return p.data ? <TodayItems zone={p.data.timeZone} /> : null;
}
function TodayItems({ zone }: { zone: string }) {
  const l = useL(),
    day = dayInZone(new Date(), zone),
    tasks = useResource(`/study/tasks?from=${day}&to=${day}`, calendarSchema),
    deadlines = useResource(
      `/study/deadlines?from=${day}&to=${shiftedDay(day, 7)}`,
      deadlinesSchema,
    );
  return (
    <section className="today-panel">
      <div className="section-heading">
        <h2>{l('План на сегодня', 'Бүгінгі жоспар')}</h2>
        <Link className="text-link" to="/study">
          {l('Календарь', 'Күнтізбе')}
        </Link>
      </div>
      {tasks.error ? (
        <ErrorState error={tasks.error} retry={tasks.reload} />
      ) : (
        tasks.data && (
          <>
            {tasks.data.items
              .filter((t) => t.status === 'PLANNED')
              .slice(0, 3)
              .map((t) => (
                <div className="today-task" key={t.id}>
                  {t.available && t.url ? (
                    <Link to={t.url}>{l(t.titleRu, t.titleKz)}</Link>
                  ) : (
                    <span>
                      {l(t.titleRu, t.titleKz)} · {l('Недоступно', 'Қолжетімсіз')}
                    </span>
                  )}
                  <small>
                    {t.durationMinutes} {l('мин', 'мин')}
                  </small>
                </div>
              ))}
            {!tasks.data.items.some((t) => t.status === 'PLANNED') && (
              <p>
                {l(
                  'На сегодня нет запланированных занятий. Настройте цель и удобный график в календаре.',
                  'Бүгінге жоспарланған сабақтар жоқ. Күнтізбеде мақсатыңыз бен ыңғайлы кестені баптаңыз.',
                )}
              </p>
            )}
          </>
        )
      )}
      {deadlines.data?.items
        .filter((d) => !d.submitted)
        .slice(0, 1)
        .map((d) => (
          <p key={d.id}>
            <strong>{l('Ближайшее задание: ', 'Жақын тапсырма: ')}</strong>
            <Link to={d.url}>{l(d.titleRu, d.titleKz)}</Link> · {dayInZone(d.dueAt, zone)}
          </p>
        ))}
    </section>
  );
}
