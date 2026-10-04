import { Link, useNavigate } from 'react-router';
import { z } from 'zod';
import { request, startSchema } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, useAction, Feedback } from '../shared';
export const learningSchema = z.object({
  questionsAnswered: z.number(),
  testsCompleted: z.number(),
  accuracy: z.number(),
  errorCount: z.number(),
  continueTopic: z
    .object({ topicId: z.string(), titleRu: z.string(), titleKz: z.string().nullable() })
    .nullish(),
  topics: z.array(
    z.object({
      topicId: z.string(),
      subjectId: z.string(),
      titleRu: z.string(),
      titleKz: z.string().nullable(),
      subjectRu: z.string(),
      subjectKz: z.string(),
      attempts: z.number(),
      bestScore: z.number(),
      lastScore: z.number(),
      recentAccuracy: z.number(),
      theoryTotal: z.number(),
      theoryRead: z.number(),
      mastery: z.number(),
    }),
  ),
  subjects: z.array(
    z.object({
      id: z.string(),
      titleRu: z.string(),
      titleKz: z.string(),
      mastery: z.number(),
      topics: z.number(),
    }),
  ),
  errorTopics: z.array(
    z.object({
      topicId: z.string(),
      titleRu: z.string().nullable(),
      titleKz: z.string().nullable(),
      count: z.number(),
    }),
  ),
});
export function LearningSummary() {
  const l = useL(),
    r = useResource('/learning/me', learningSchema);
  if (r.loading) return <Loading />;
  if (r.error) return <ErrorState error={r.error} retry={r.reload} />;
  const a = r.data!;
  return (
    <section className="editor-section">
      <div className="section-heading">
        <h2>{l('Ваш следующий шаг', 'Келесі қадамыңыз')}</h2>
        <Link className="text-link" to="/learning">
          {l('Освоение тем', 'Тақырыптарды меңгеру')}
        </Link>
      </div>
      <div className="progress-summary">
        <div>
          <strong>{a.questionsAnswered}</strong>
          <span>{l('Вопросов в завершённых попытках', 'Аяқталған талпыныстардағы сұрақтар')}</span>
        </div>
        <div>
          <strong>{a.accuracy}%</strong>
          <span>{l('Правильных ответов', 'Дұрыс жауаптар')}</span>
        </div>
        <div>
          <strong>{a.errorCount}</strong>
          <span>{l('Вопросов на повторение', 'Қайталау сұрақтары')}</span>
        </div>
      </div>
      {a.errorCount > 0 ? (
        <Link className="button secondary" to="/learning/errors">
          {l('Работа над ошибками', 'Қателермен жұмыс')}
        </Link>
      ) : (
        <p>
          {l(
            'После практики здесь появятся вопросы для повторения.',
            'Жаттығудан кейін қайталау сұрақтары осында пайда болады.',
          )}
        </p>
      )}
      {a.continueTopic && (
        <Link className="activity-link" to={`/topics/${a.continueTopic.topicId}`}>
          <span>{l('Продолжить изучение', 'Оқуды жалғастыру')}</span>
          <strong>{l(a.continueTopic.titleRu, a.continueTopic.titleKz)}</strong>
        </Link>
      )}
      <div className="mastery-bars">
        {a.subjects.map((s) => (
          <Link key={s.id} to={`/subjects/${s.id}`}>
            <span>{l(s.titleRu, s.titleKz)}</span>
            <strong>{Math.round(s.mastery)}%</strong>
            <progress max={100} value={s.mastery} aria-label={l(s.titleRu, s.titleKz)} />
          </Link>
        ))}
      </div>
      <h3>{l('Темы, которым стоит уделить время', 'Көңіл бөлуге тұратын тақырыптар')}</h3>
      {[
        ...a.topics.filter((t) => t.attempts > 0 && t.mastery < 70),
        ...a.topics.filter((t) => t.attempts === 0),
      ]
        .slice(0, 3)
        .map((t) => (
          <Link className="activity-link" key={t.topicId} to={`/topics/${t.topicId}`}>
            {l(t.titleRu, t.titleKz)}
            <span>
              {t.attempts ? `${Math.round(t.mastery)}%` : l('Начать изучение', 'Оқуды бастау')}
            </span>
          </Link>
        ))}
    </section>
  );
}
export default function Learning({ errors = false }: { errors?: boolean }) {
  const l = useL(),
    r = useResource('/learning/me', learningSchema),
    action = useAction(),
    navigate = useNavigate();
  return (
    <>
      <PageHeading
        title={
          errors
            ? l('Работа над ошибками', 'Қателермен жұмыс')
            : l('Освоение тем', 'Тақырыптарды меңгеру')
        }
        body={
          errors
            ? l(
                'Каждый вопрос показан один раз. Верный ответ в новой завершённой попытке уберёт его из повторения.',
                'Әр сұрақ бір рет көрсетіледі. Жаңа аяқталған талпыныстағы дұрыс жауап оны қайталаудан алып тастайды.',
              )
            : l(
                '20% — отмеченная теория, 80% — точность последних трёх попыток. Если теории нет, используется только точность практики.',
                '20% — белгіленген теория, 80% — соңғы үш талпыныстың дәлдігі. Теория болмаса, тек жаттығу дәлдігі қолданылады.',
              )
        }
        back={{ to: '/', label: l('Главная', 'Басты бет') }}
      />
      <Feedback action={action} />
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : errors ? (
        r.data?.errorTopics.length ? (
          <div className="course-grid">
            {r.data.errorTopics.map((t) => (
              <section className="course-tile" key={t.topicId}>
                <h2>{l(t.titleRu, t.titleKz)}</h2>
                <p>
                  {t.count} {l('вопросов для повторения', 'қайталау сұрағы')}
                </p>
                <button
                  className="button"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(async () => {
                      const s = await request('/learning/errors/practice', startSchema, {
                        method: 'POST',
                        body: { topicId: t.topicId },
                      });
                      navigate(`/tests/${s.sessionId}`);
                    })
                  }
                >
                  {l('Повторить ошибки', 'Қателерді қайталау')}
                </button>
              </section>
            ))}
          </div>
        ) : (
          <Empty title={l('Вопросов на повторение нет', 'Қайталау сұрақтары жоқ')} />
        )
      ) : (
        <>
          <Link className="button secondary" to="/learning/errors">
            {l('Работа над ошибками', 'Қателермен жұмыс')} ({r.data?.errorCount})
          </Link>
          <div className="course-grid">
            {r.data?.topics.map((t) => (
              <section className="course-tile" key={t.topicId}>
                <p>{l(t.subjectRu, t.subjectKz)}</p>
                <h2>{l(t.titleRu, t.titleKz)}</h2>
                <div className="mastery-score">
                  <strong>{Math.round(t.mastery)}%</strong>
                  <progress max={100} value={t.mastery} aria-label={l('Освоение', 'Меңгеру')} />
                </div>
                <dl>
                  <div>
                    <dt>{l('Теория', 'Теория')}</dt>
                    <dd>
                      {t.theoryRead}/{t.theoryTotal}
                    </dd>
                  </div>
                  <div>
                    <dt>{l('Попыток', 'Талпыныстар')}</dt>
                    <dd>{t.attempts}</dd>
                  </div>
                  <div>
                    <dt>{l('Лучший результат', 'Ең жақсы нәтиже')}</dt>
                    <dd>{t.attempts ? `${t.bestScore}%` : '—'}</dd>
                  </div>
                  <div>
                    <dt>{l('Последняя попытка', 'Соңғы талпыныс')}</dt>
                    <dd>{t.attempts ? `${t.lastScore}%` : '—'}</dd>
                  </div>
                </dl>
                <Link className="text-link" to={`/topics/${t.topicId}`}>
                  {l('Продолжить изучение', 'Оқуды жалғастыру')}
                </Link>
              </section>
            ))}
          </div>
        </>
      )}
    </>
  );
}
