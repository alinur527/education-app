import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, useAction, Feedback, Field, Pager } from '../shared';
import { payloadSchema, savedSchema, publishedSchema, materialsSchema } from '../content/model';
import { RenderedContent } from '../content/Blocks';
import { Materials } from '../content/Materials';
const coursesSchema = z.object({
  items: z.array(
    z.object({
      id: z.string(),
      icon: z.string().nullable(),
      titleRu: z.string(),
      titleKz: z.string(),
      descriptionRu: z.string(),
      descriptionKz: z.string(),
      visibility: z.string(),
      selfEnroll: z.boolean(),
      enrollment: z.string().nullable(),
    }),
  ),
  total: z.number(),
  page: z.number(),
  size: z.number(),
});
const detailSchema = z.object({
  id: z.string(),
  content: payloadSchema,
  enrollment: z.string().nullable(),
  totalLessons: z.number(),
  completedLessons: z.number(),
  modules: z.array(
    z.object({
      id: z.string(),
      titleRu: z.string(),
      titleKz: z.string(),
      lessons: z.array(
        z.object({
          id: z.string(),
          titleRu: z.string(),
          titleKz: z.string(),
          completed: z.boolean(),
        }),
      ),
    }),
  ),
});
const activitiesSchema = z.array(
  z.object({ id: z.string(), kind: z.string(), titleRu: z.string(), titleKz: z.string() }),
);
export default function Courses() {
  const { id } = useParams();
  return id ? <CourseDetail id={id} /> : <CourseList />;
}
function CourseList() {
  const l = useL(),
    [q, setQ] = useState(''),
    [page, setPage] = useState(0),
    r = useResource(`/courses?q=${encodeURIComponent(q)}&page=${page}`, coursesSchema);
  return (
    <>
      <PageHeading
        title={l('Курсы', 'Курстар')}
        body={l(
          'Учитесь по шагам: урок, практика, обратная связь.',
          'Қадамдап үйреніңіз: сабақ, жаттығу, кері байланыс.',
        )}
      />
      <div className="workspace-toolbar">
        <Field label={l('Найти курс', 'Курс іздеу')}>
          <input
            type="search"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Link className="button secondary" to="/assignments">
          {l('Мои задания', 'Менің тапсырмаларым')}
        </Link>
      </div>
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : r.data?.items.length ? (
        <>
          <div className="course-grid">
            {r.data.items.map((c) => (
              <Link className="course-tile" key={c.id} to={`/courses/${c.id}`}>
                <div className="course-mark" aria-hidden="true">
                  {c.icon && c.icon !== 'book' ? c.icon : '▤'}
                </div>
                <h2>{l(c.titleRu, c.titleKz)}</h2>
                <p>{l(c.descriptionRu, c.descriptionKz)}</p>
                <span className="text-link">
                  {c.enrollment === 'COMPLETED'
                    ? l('Курс завершён', 'Курс аяқталды')
                    : c.enrollment === 'ACTIVE'
                      ? l('Продолжить обучение', 'Оқуды жалғастыру')
                      : l('Посмотреть программу', 'Бағдарламаны көру')}
                </span>
              </Link>
            ))}
          </div>
          <Pager page={page} total={r.data.total} onChange={setPage} />
        </>
      ) : (
        <Empty
          title={l('Курсы скоро появятся', 'Курстар жақында пайда болады')}
          body={l(
            'Учитель может зачислить вас на закрытый курс по вашей почте.',
            'Мұғалім сізді поштаңыз арқылы жабық курсқа тіркей алады.',
          )}
        />
      )}
    </>
  );
}
function CourseDetail({ id }: { id: string }) {
  const l = useL(),
    r = useResource(`/courses/${id}`, detailSchema),
    action = useAction();
  if (r.loading) return <Loading />;
  if (r.error) return <ErrorState error={r.error} retry={r.reload} />;
  const c = r.data!,
    member = ['ACTIVE', 'COMPLETED'].includes(c.enrollment || '');
  return (
    <>
      <PageHeading
        title={l(c.content.titleRu, c.content.titleKz)}
        body={l(c.content.descriptionRu, c.content.descriptionKz)}
        back={{ to: '/courses', label: l('Курсы', 'Курстар') }}
      />
      <section className="course-intro">
        <div>
          <h2>{l('Ваш путь по курсу', 'Курстағы жолыңыз')}</h2>
          <p>
            {c.completedLessons} / {c.totalLessons} {l('уроков завершено', 'сабақ аяқталды')}
          </p>
          <progress
            aria-label={l('Прогресс курса', 'Курс ілгерілеуі')}
            max={Math.max(c.totalLessons, 1)}
            value={c.completedLessons}
          />
        </div>
        {!member &&
          (c.content.selfEnroll && c.content.visibility === 'PUBLIC' ? (
            <button
              className="button"
              disabled={action.busy}
              onClick={() =>
                void action.run(async () => {
                  await request(`/courses/${id}/enroll`, savedSchema, { method: 'POST' });
                  r.reload();
                })
              }
            >
              {l('Записаться на курс', 'Курсқа тіркелу')}
            </button>
          ) : (
            <p>
              {l('Для доступа обратитесь к учителю.', 'Қолжетімділік үшін мұғалімге жүгініңіз.')}
            </p>
          ))}
      </section>
      <Feedback action={action} />
      <div className="module-list">
        {c.modules.map((m, i) => (
          <section className="module-panel" key={m.id}>
            <h2>
              <span>{i + 1}</span>
              {l(m.titleRu, m.titleKz)}
            </h2>
            <ol>
              {m.lessons.map((lesson) => (
                <li key={lesson.id}>
                  {member ? (
                    <Link to={`/lessons/${lesson.id}`}>
                      <span>{l(lesson.titleRu, lesson.titleKz)}</span>
                      <span>
                        {lesson.completed
                          ? l('Завершено', 'Аяқталды')
                          : l('Открыть урок', 'Сабақты ашу')}
                      </span>
                    </Link>
                  ) : (
                    <span>{l(lesson.titleRu, lesson.titleKz)}</span>
                  )}
                </li>
              ))}
            </ol>
          </section>
        ))}
      </div>
      <Materials contentId={id} />
    </>
  );
}
export function Lesson() {
  const { id } = useParams(),
    l = useL(),
    action = useAction(),
    r = useResource(`/content/${id}`, publishedSchema),
    activities = useResource(`/lessons/${id}/activities`, activitiesSchema),
    files = useResource(`/content/${id}/materials`, materialsSchema);
  return r.loading ? (
    <Loading />
  ) : r.error ? (
    <ErrorState error={r.error} retry={r.reload} />
  ) : r.data ? (
    <>
      <PageHeading
        title={l('Урок', 'Сабақ')}
        back={{ to: '/courses', label: l('Курсы', 'Курстар') }}
      />
      <div className="lesson-surface">
        <RenderedContent payload={r.data.content} materials={files.data || []} />
        <Materials contentId={id!} />
        <section className="editor-section">
          <h2>{l('Закрепите материал', 'Материалды бекітіңіз')}</h2>
          {activities.data?.map((a) => (
            <Link
              className="activity-link"
              key={a.id}
              to={a.kind === 'QUIZ' ? `/quizzes/${a.id}` : `/assignments/${a.id}`}
            >
              {l(a.titleRu, a.titleKz)}{' '}
              <span>{a.kind === 'QUIZ' ? l('Тест', 'Тест') : l('Задание', 'Тапсырма')}</span>
            </Link>
          ))}
          <button
            className="button"
            disabled={action.busy || action.saved}
            onClick={() =>
              void action.run(async () => {
                await request(`/lessons/${id}/complete`, savedSchema, { method: 'POST' });
              })
            }
          >
            {action.saved
              ? l('Урок завершён', 'Сабақ аяқталды')
              : l('Отметить урок завершённым', 'Сабақты аяқталды деп белгілеу')}
          </button>
          <Feedback action={action} />
        </section>
      </div>
    </>
  ) : null;
}
