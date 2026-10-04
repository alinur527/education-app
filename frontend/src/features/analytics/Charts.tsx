import { useState, useRef } from 'react';
import { useL } from '../shared';
import { type Day, percentage } from './api';

export function accuracyPath(days: Day[]) {
  let path = '',
    connected = false;
  days.forEach((day, i) => {
    if (day.accuracy === null) {
      connected = false;
      return;
    }
    path += `${connected ? 'L' : 'M'}${20 + (i * 960) / Math.max(1, days.length - 1)},${130 - day.accuracy * 1.1} `;
    connected = true;
  });
  return path;
}
export function Bars({ days, mini = false }: { days: Day[]; mini?: boolean }) {
  const highest = Math.max(1, ...days.map((d) => d.questionsAnswered)),
    width = 1000 / days.length;
  return (
    <svg
      className={mini ? 'weekly-bars' : 'activity-bars'}
      viewBox="0 0 1000 150"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      {days.map((day, i) => (
        <rect
          key={day.date}
          x={i * width + width * 0.15}
          y={145 - (day.questionsAnswered / highest) * 125}
          width={width * 0.7}
          height={(day.questionsAnswered / highest) * 125}
          rx={mini ? 4 : 2}
        />
      ))}
      <line x1="0" y1="146" x2="1000" y2="146" />
    </svg>
  );
}
export function DailyCharts({ days }: { days: Day[] }) {
  const l = useL();
  const plotted = days.slice(-90);
  return (
    <section className="panel analytics-charts" aria-labelledby="daily-heading">
      <h2 id="daily-heading">{l('Практика по дням', 'Күн сайынғы жаттығу')}</h2>
      {days.length > 90 && (
        <p className="muted">
          {l(
            'На графике последние 90 дней. Все даты доступны в таблице.',
            'Графикте соңғы 90 күн. Барлық күндер кестеде бар.',
          )}
        </p>
      )}
      <div className="analytics-chart-pair">
        <div>
          <h3>{l('Решено вопросов', 'Шешілген сұрақтар')}</h3>
          <Bars days={plotted} />
          <div className="chart-dates">
            <span>{plotted[0]?.date}</span>
            <span>{plotted.at(-1)?.date}</span>
          </div>
        </div>
        <div>
          <h3>{l('Точность ответов', 'Жауап дәлдігі')}</h3>
          <svg
            className="accuracy-line"
            viewBox="0 0 1000 150"
            aria-hidden="true"
            preserveAspectRatio="none"
          >
            <line x1="20" y1="20" x2="980" y2="20" />
            <line x1="20" y1="130" x2="980" y2="130" />
            <path data-testid="accuracy-path" d={accuracyPath(plotted)} />
            {plotted.map(
              (d, i) =>
                d.accuracy !== null && (
                  <circle
                    key={d.date}
                    cx={20 + (i * 960) / Math.max(1, plotted.length - 1)}
                    cy={130 - d.accuracy * 1.1}
                    r="4"
                  />
                ),
            )}
          </svg>
          <p className="chart-dates">
            <span>0–100%</span>
            <span>{l('Нет вопросов — нет точки', 'Сұрақ жоқ — нүкте жоқ')}</span>
          </p>
        </div>
      </div>
      <details className="analytics-data">
        <summary>{l('Данные графиков по дням', 'Графиктің күндік деректері')}</summary>
        <div className="table-scroll">
          <table>
            <caption>{l('Практика и учебные действия', 'Жаттығу және оқу әрекеттері')}</caption>
            <thead>
              <tr>
                {[
                  l('Дата', 'Күн'),
                  l('Вопросы', 'Сұрақтар'),
                  l('Верно', 'Дұрыс'),
                  l('Точность', 'Дәлдік'),
                  l('Баллы', 'Балдар'),
                  l('Тесты', 'Тесттер'),
                  l('Время тестов, сек.', 'Тест уақыты, сек.'),
                  l('Теории', 'Теориялар'),
                  l('Уроки', 'Сабақтар'),
                  l('План', 'Жоспар'),
                  l('Отправки', 'Жіберулер'),
                ].map((t) => (
                  <th key={t} scope="col">
                    {t}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {days.map((d) => (
                <tr key={d.date}>
                  <th scope="row">{d.date}</th>
                  <td>{d.questionsAnswered}</td>
                  <td>{d.fullyCorrect}</td>
                  <td>{percentage(d.accuracy)}</td>
                  <td>
                    {d.earnedPoints}/{d.maxPoints}
                  </td>
                  <td>{d.testsCompleted}</td>
                  <td>{d.testTimeSecs}</td>
                  <td>{d.theoryReads}</td>
                  <td>{d.lessonCompletions}</td>
                  <td>{d.plannerTasksCompleted}</td>
                  <td>{d.assignmentsSubmitted}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
export function Heatmap({ days }: { days: Day[] }) {
  const l = useL(),
    [selected, setSelected] = useState(days.length - 1),
    refs = useRef<(HTMLButtonElement | null)[]>([]);
  const detail = (d: Day) =>
    `${d.date}: ${l('учебных действий', 'оқу әрекеті')} ${d.studyActions}; ${l('вопросов', 'сұрақ')} ${d.questionsAnswered}; ${l('точность', 'дәлдік')} ${percentage(d.accuracy)}; ${l('тестов', 'тест')} ${d.testsCompleted}; ${l('теорий', 'теория')} ${d.theoryReads}; ${l('уроков', 'сабақ')} ${d.lessonCompletions}; ${l('задач плана', 'жоспар тапсырмасы')} ${d.plannerTasksCompleted}; ${l('отправок', 'жіберулер')} ${d.assignmentsSubmitted}`;
  return (
    <section className="panel study-heatmap" aria-labelledby="heatmap-heading">
      <h2 id="heatmap-heading">{l('12 недель обучения', '12 апталық оқу')}</h2>
      <p className="muted">
        {l(
          'Тесты, теория, уроки, план и отправки заданий. Стрелки перемещают фокус по дням.',
          'Тесттер, теория, сабақтар, жоспар және тапсырма жіберу. Көрсеткілер күндер арасында жылжытады.',
        )}
      </p>
      <div
        className="heatmap-days"
        aria-label={l('Учебные действия по дням', 'Күндік оқу әрекеттері')}
      >
        {days.map((d, i) => (
          <button
            key={d.date}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            className={`heat-cell heat-${Math.min(4, d.studyActions)}`}
            tabIndex={i === selected ? 0 : -1}
            aria-label={detail(d)}
            title={detail(d)}
            onMouseEnter={() => setSelected(i)}
            onFocus={() => setSelected(i)}
            onClick={() => setSelected(i)}
            onKeyDown={(e) => {
              const step = (
                { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -1, ArrowDown: 1 } as Record<
                  string,
                  number
                >
              )[e.key];
              if (step) {
                e.preventDefault();
                refs.current[Math.max(0, Math.min(days.length - 1, i + step))]?.focus();
              }
            }}
          />
        ))}
      </div>
      <p className="heatmap-detail" aria-live="polite">
        {days[selected] && detail(days[selected])}
      </p>
      <div className="chart-dates">
        <span>{days[0]?.date}</span>
        <span>{days.at(-1)?.date}</span>
      </div>
      <details className="analytics-data">
        <summary>{l('Текстовый список активности', 'Белсенділіктің мәтіндік тізімі')}</summary>
        <ul>
          {days.map((d) => (
            <li key={d.date}>{detail(d)}</li>
          ))}
        </ul>
      </details>
    </section>
  );
}
