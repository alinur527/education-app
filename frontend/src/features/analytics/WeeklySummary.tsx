import { Link } from 'react-router';
import { useResource } from '../../hooks';
import { ErrorState } from '../../components';
import { useL } from '../shared';
import { analyticsSchema, percentage } from './api';
import { Bars } from './Charts';
import './analytics.css';
export default function WeeklySummary() {
  const l = useL(),
    resource = useResource('/statistics/me/analytics?period=7d', analyticsSchema),
    s = resource.data?.summary;
  return (
    <section className="panel weekly-summary">
      <div className="analytics-section-title">
        <h2>{l('Эта неделя', 'Осы апта')}</h2>
        <Link to="/statistics">{l('Подробнее', 'Толығырақ')}</Link>
      </div>
      {resource.loading ? (
        <p role="status">{l('Загрузка…', 'Жүктелуде…')}</p>
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : (
        s && (
          <>
            <dl className="weekly-facts">
              {[
                [l('Вопросы', 'Сұрақтар'), s.questionsAnswered],
                [l('Точность', 'Дәлдік'), percentage(s.accuracyPercent)],
                [l('Активные дни', 'Белсенді күндер'), s.activeDays],
                [l('Серия дней', 'Күндер тізбегі'), s.currentStreak],
              ].map(([label, value]) => (
                <div key={label}>
                  <dt>{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
            <Bars days={resource.data!.daily} mini />
            <p className="muted">{l('Последние 7 дней', 'Соңғы 7 күн')}</p>
            <span className="sr-only">
              {resource.data!.daily.map((d) => `${d.date}: ${d.questionsAnswered}`).join('; ')}
            </span>
          </>
        )
      )}
    </section>
  );
}
