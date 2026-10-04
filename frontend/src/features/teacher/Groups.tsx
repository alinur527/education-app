import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, useAction, Feedback, Field, Pager } from '../shared';
import { contentPage, idSchema, savedSchema } from '../content/model';
const listSchema = z.object({
  items: z.array(
    z.object({
      id: z.string(),
      name: z.string(),
      courseId: z.string(),
      courseRu: z.string(),
      courseKz: z.string(),
      students: z.number(),
    }),
  ),
  page: z.number(),
  size: z.number(),
  total: z.number(),
});
const groupSchema = z.object({
  id: z.string(),
  name: z.string(),
  courseId: z.string(),
  students: z.array(
    z.object({
      id: z.string(),
      email: z.string(),
      firstName: z.string().nullable(),
      lastName: z.string().nullable(),
      enrollment: z.string().nullable(),
      completed: z.number(),
    }),
  ),
  availableAssignments: z.array(
    z.object({ id: z.string(), titleRu: z.string(), titleKz: z.string() }),
  ),
  totalLessons: z.number(),
  averageProgress: z.number(),
  assignments: z.array(
    z.object({
      id: z.string(),
      titleRu: z.string(),
      titleKz: z.string(),
      dueAt: z.string().nullable(),
      submitted: z.number(),
    }),
  ),
  weakTopics: z.array(
    z.object({
      id: z.string(),
      titleRu: z.string(),
      titleKz: z.string().nullable(),
      accuracy: z.number(),
    }),
  ),
});
export default function Groups() {
  const { id } = useParams();
  return id ? <GroupDetail id={id} /> : <GroupList />;
}
function GroupList() {
  const l = useL(),
    action = useAction(),
    navigate = useNavigate(),
    [q, setQ] = useState(''),
    [page, setPage] = useState(0),
    r = useResource(`/teacher/groups?q=${encodeURIComponent(q)}&page=${page}`, listSchema),
    courses = useResource('/cms/content?kind=COURSE&size=100', contentPage);
  return (
    <>
      <PageHeading title={l('Группы и ученики', 'Топтар мен оқушылар')} />
      <form
        className="workspace-toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          const values = Object.fromEntries(new FormData(e.currentTarget));
          void action.run(async () => {
            const group = await request('/teacher/groups', idSchema, {
              method: 'POST',
              body: values,
            });
            navigate(`/workspace/groups/${group.id}`);
          });
        }}
      >
        <Field label={l('Название группы', 'Топ атауы')}>
          <input name="name" required maxLength={200} />
        </Field>
        <Field label={l('Курс', 'Курс')}>
          <select name="courseId" required>
            <option value="">{l('Выберите курс', 'Курсты таңдаңыз')}</option>
            {courses.data?.items.map((c) => (
              <option key={c.id} value={c.id}>
                {l(c.titleRu, c.titleKz)}
              </option>
            ))}
          </select>
        </Field>
        <button className="button" disabled={action.busy}>
          {l('Создать группу', 'Топ құру')}
        </button>
      </form>
      <Feedback action={action} />
      <Field label={l('Поиск групп', 'Топтарды іздеу')}>
        <input
          type="search"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
        />
      </Field>
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : (
        <>
          <div className="course-grid">
            {r.data?.items.map((g) => (
              <Link className="course-tile" key={g.id} to={`/workspace/groups/${g.id}`}>
                <h2>{g.name}</h2>
                <p>{l(g.courseRu, g.courseKz)}</p>
                <strong>
                  {g.students} {l('учеников', 'оқушы')}
                </strong>
              </Link>
            ))}
          </div>
          <Pager page={page} total={r.data?.total || 0} onChange={setPage} />
        </>
      )}
    </>
  );
}
function GroupDetail({ id }: { id: string }) {
  const l = useL(),
    action = useAction(),
    r = useResource(`/teacher/groups/${id}`, groupSchema);
  if (r.loading) return <Loading />;
  if (r.error) return <ErrorState error={r.error} retry={r.reload} />;
  const g = r.data!;
  return (
    <>
      <PageHeading
        title={g.name}
        back={{ to: '/workspace/groups', label: l('Все группы', 'Барлық топтар') }}
      />
      <div className="progress-summary">
        <div>
          <strong>{g.students.length}</strong>
          <span>{l('Учеников', 'Оқушы')}</span>
        </div>
        <div>
          <strong>{Math.round(g.averageProgress)}%</strong>
          <span>{l('Средний прогресс курса', 'Курстағы орташа ілгерілеу')}</span>
        </div>
        <div>
          <strong>{g.totalLessons}</strong>
          <span>{l('Опубликованных уроков', 'Жарияланған сабақ')}</span>
        </div>
      </div>
      <form
        className="workspace-toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          const form = e.currentTarget,
            values = Object.fromEntries(new FormData(form));
          void action.run(async () => {
            await request(`/teacher/groups/${id}/members`, savedSchema, {
              method: 'POST',
              body: values,
            });
            form.reset();
            r.reload();
          });
        }}
      >
        <Field label={l('Почта зарегистрированного ученика', 'Тіркелген оқушының поштасы')}>
          <input type="email" name="email" required />
        </Field>
        <button className="button" disabled={action.busy}>
          {l('Добавить ученика', 'Оқушы қосу')}
        </button>
      </form>
      <Feedback action={action} />
      <div
        className="table-region"
        role="region"
        tabIndex={0}
        aria-label={l('Ученики группы', 'Топ оқушылары')}
      >
        <table>
          <thead>
            <tr>
              <th>{l('Ученик', 'Оқушы')}</th>
              <th>{l('Прогресс', 'Ілгерілеу')}</th>
              <th>{l('Действие', 'Әрекет')}</th>
            </tr>
          </thead>
          <tbody>
            {g.students.map((s) => (
              <tr key={s.id}>
                <td>
                  {s.firstName} {s.lastName}
                  <small>{s.email}</small>
                </td>
                <td>
                  {s.completed} / {g.totalLessons}
                </td>
                <td>
                  <button
                    className="text-button"
                    disabled={action.busy}
                    onClick={() =>
                      void action.run(async () => {
                        await request(`/teacher/groups/${id}/members/${s.id}`, savedSchema, {
                          method: 'DELETE',
                        });
                        r.reload();
                      })
                    }
                  >
                    {l('Убрать из группы', 'Топтан шығару')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <section className="editor-section">
        <h2>{l('Задания группы', 'Топ тапсырмалары')}</h2>
        <form
          className="workspace-toolbar"
          onSubmit={(e) => {
            e.preventDefault();
            const body = Object.fromEntries(new FormData(e.currentTarget));
            void action.run(async () => {
              await request(`/teacher/groups/${id}/assignments`, savedSchema, {
                method: 'POST',
                body,
              });
              r.reload();
            });
          }}
        >
          <Field label={l('Задание этого курса', 'Осы курстың тапсырмасы')}>
            <select name="assignmentId" required>
              <option value="">{l('Выберите задание', 'Тапсырманы таңдаңыз')}</option>
              {g.availableAssignments.map((a) => (
                <option value={a.id} key={a.id}>
                  {l(a.titleRu, a.titleKz)}
                </option>
              ))}
            </select>
          </Field>
          <button className="button secondary" disabled={action.busy}>
            {l('Назначить группе', 'Топқа тағайындау')}
          </button>
        </form>
        {g.assignments.map((a) => (
          <div className="assignment-row" key={a.id}>
            <Link className="text-link" to={`/workspace/assignments/${a.id}`}>
              {l(a.titleRu, a.titleKz)}
            </Link>
            <span>
              {a.submitted} / {g.students.length} {l('сдано', 'тапсырылды')}
            </span>
            <small>
              {a.dueAt ? new Date(a.dueAt).toLocaleString() : l('Без срока', 'Мерзімсіз')}
            </small>
          </div>
        ))}
      </section>
      <section className="editor-section">
        <h2>{l('Темы для повторения', 'Қайталау тақырыптары')}</h2>
        {g.weakTopics.length ? (
          g.weakTopics.map((t) => (
            <p key={t.id}>
              {l(t.titleRu, t.titleKz)} — {Math.round(t.accuracy)}%
            </p>
          ))
        ) : (
          <Empty
            title={l(
              'Пока нет слабых тем по результатам практики',
              'Жаттығу нәтижелері бойынша әлсіз тақырыптар әзірге жоқ',
            )}
          />
        )}
      </section>
    </>
  );
}
