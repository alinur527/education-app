import { useState } from 'react';
import { z } from 'zod';
import { PageHeading, Loading, ErrorState } from '../../components';
import { useResource } from '../../hooks';
import { useL } from '../shared';
import { PeriodSwitch } from './Statistics';
import { percentage, type Period } from './api';
const schema = z.object({
  period: z.string(),
  timeZone: z.string(),
  from: z.string(),
  to: z.string(),
  global: z.boolean(),
  studentsInScope: z.number(),
  activeStudents: z.number(),
  testsCompleted: z.number(),
  questionsAnswered: z.number(),
  accuracyPercent: z.number().nullable(),
  courseEnrollments: z.number(),
  assignmentRecipients: z.number(),
  assignmentSubmitters: z.number(),
  assignmentSubmissionRate: z.number().nullable(),
  subjects: z.array(
    z.object({
      titleRu: z.string(),
      titleKz: z.string(),
      questionsAnswered: z.number(),
      testsCompleted: z.number(),
      accuracyPercent: z.number().nullable(),
    }),
  ),
  coverage: z.array(
    z.object({
      kind: z.string(),
      published: z.number(),
      draft: z.number(),
      review: z.number(),
      archived: z.number(),
    }),
  ),
});
export default function StaffAnalytics() {
  const l = useL(),
    [period, setPeriod] = useState<Period>('7d'),
    resource = useResource(`/teacher/analytics?period=${period}`, schema),
    data = resource.data;
  const kinds: Record<string, string> = {
    SUBJECT: l('Предметы', 'Пәндер'),
    TOPIC: l('Темы', 'Тақырыптар'),
    THEORY: l('Теория', 'Теория'),
    QUESTION: l('Вопросы', 'Сұрақтар'),
    COURSE: l('Курсы', 'Курстар'),
    MODULE: l('Модули', 'Модульдер'),
    LESSON: l('Уроки', 'Сабақтар'),
    QUIZ: l('Тесты уроков', 'Сабақ тесттері'),
    ASSIGNMENT: l('Задания', 'Тапсырмалар'),
  };
  return (
    <div className="analytics-page">
      <div className="analytics-heading">
        <PageHeading title={l('Аналитика обучения', 'Оқу аналитикасы')} />
        <PeriodSwitch value={period} onChange={setPeriod} all={false} />
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
            <p>
              {data.global
                ? l(
                    'Все активные аккаунты учеников платформы.',
                    'Платформадағы барлық белсенді оқушы аккаунттары.',
                  )
                : l(
                    'Практика ЕНТ учеников ваших курсов и групп, события только ваших курсов. Личные планы учеников недоступны.',
                    'Өз курстарыңыз бен топтарыңыздағы оқушылардың ҰБТ жаттығуы, тек өз курстарыңыздың әрекеттері. Оқушылардың жеке жоспарлары қолжетімсіз.',
                  )}
            </p>
            <section className="panel">
              <h2>{l('Учебная активность', 'Оқу белсенділігі')}</h2>
              <dl className="analytics-facts">
                {[
                  [
                    l('Учеников в доступном списке', 'Қолжетімді тізімдегі оқушылар'),
                    data.studentsInScope,
                  ],
                  [l('Активных учеников', 'Белсенді оқушылар'), data.activeStudents],
                  [l('Завершено тестов', 'Аяқталған тесттер'), data.testsCompleted],
                  [l('Решено вопросов', 'Шешілген сұрақтар'), data.questionsAnswered],
                  [
                    l('Точность практики ЕНТ', 'ҰБТ жаттығуының дәлдігі'),
                    percentage(data.accuracyPercent),
                  ],
                  [l('Новые зачисления', 'Жаңа тіркелулер'), data.courseEnrollments],
                  [
                    l('Ответили на задания в периоде', 'Кезеңде тапсырмаға жауап бергендер'),
                    `${data.assignmentSubmitters}/${data.assignmentRecipients}`,
                  ],
                  [
                    l('Доля отправок', 'Жіберулер үлесі'),
                    percentage(data.assignmentSubmissionRate),
                  ],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
              <p className="muted">
                {l(
                  'Доля отправок: назначенные пары «задание + ученик» с ответом в выбранном периоде / все текущие назначенные пары. Повторные отправки не увеличивают долю.',
                  'Жіберулер үлесі: таңдалған кезеңде жауап берген «тапсырма + оқушы» жұптары / барлық қазіргі тағайындалған жұптар. Қайта жіберу үлесті көбейтпейді.',
                )}
              </p>
            </section>
            <section className="panel">
              <h2>{l('Активные предметы', 'Белсенді пәндер')}</h2>
              {data.subjects.length === 0 ? (
                <p>{l('Нет завершённых практик.', 'Аяқталған жаттығулар жоқ.')}</p>
              ) : (
                <ul className="analytics-subjects">
                  {data.subjects.map((s) => (
                    <li key={s.titleRu}>
                      <strong>{l(s.titleRu, s.titleKz)}</strong>
                      <p>
                        {s.questionsAnswered} {l('вопросов', 'сұрақ')}; {s.testsCompleted}{' '}
                        {l('попыток', 'әрекет')}; {percentage(s.accuracyPercent)}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <section className="panel">
              <h2>{l('Состояние контента', 'Контент күйі')}</h2>
              <div className="table-scroll">
                <table>
                  <caption>
                    {l(
                      'Публикации и рабочие версии могут пересекаться.',
                      'Жарияланымдар мен жұмыс нұсқалары қабаттасуы мүмкін.',
                    )}
                  </caption>
                  <thead>
                    <tr>
                      {[
                        l('Тип', 'Түрі'),
                        l('Опубликовано', 'Жарияланған'),
                        l('Черновик', 'Жоба'),
                        l('На проверке', 'Тексеруде'),
                        l('Архив', 'Мұрағат'),
                      ].map((label) => (
                        <th scope="col" key={label}>
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.coverage.map((c) => (
                      <tr key={c.kind}>
                        <th scope="row">{kinds[c.kind] || c.kind}</th>
                        <td>{c.published}</td>
                        <td>{c.draft}</td>
                        <td>{c.review}</td>
                        <td>{c.archived}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </>
        )
      )}
    </div>
  );
}
