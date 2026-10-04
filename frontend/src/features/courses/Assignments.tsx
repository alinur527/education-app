import { useParams, Link } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, useAction, Feedback, Field } from '../shared';
import { payloadSchema, savedSchema, materialsSchema } from '../content/model';
import { RenderedContent } from '../content/Blocks';
import { Materials } from '../content/Materials';
const listSchema = z.array(
  z.object({
    id: z.string(),
    titleRu: z.string(),
    titleKz: z.string(),
    dueAt: z.string().nullable(),
    maxScore: z.number(),
    submittedAt: z.string().nullable(),
    score: z.number().nullable(),
  }),
);
const detailSchema = z.object({
  id: z.string(),
  content: payloadSchema,
  submission: z
    .object({
      text: z.string(),
      submittedAt: z.string(),
      score: z.number().nullable(),
      feedback: z.string().nullable(),
    })
    .nullable(),
});
export default function Assignments() {
  const { id } = useParams();
  return id ? <Assignment id={id} /> : <AssignmentList />;
}
function AssignmentList() {
  const l = useL(),
    r = useResource('/assignments', listSchema);
  return (
    <>
      <PageHeading
        title={l('Мои задания', 'Менің тапсырмаларым')}
        back={{ to: '/courses', label: l('Курсы', 'Курстар') }}
      />
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : r.data?.length ? (
        <div className="course-grid">
          {r.data.map((a) => (
            <Link className="course-tile" key={a.id} to={`/assignments/${a.id}`}>
              <h2>{l(a.titleRu, a.titleKz)}</h2>
              <p>{a.dueAt ? new Date(a.dueAt).toLocaleString() : l('Без срока', 'Мерзімсіз')}</p>
              <strong>
                {a.score !== null
                  ? `${a.score} / ${a.maxScore}`
                  : a.submittedAt
                    ? l('Ответ отправлен', 'Жауап жіберілді')
                    : l('Ожидает выполнения', 'Орындалуы күтілуде')}
              </strong>
            </Link>
          ))}
        </div>
      ) : (
        <Empty
          title={l('Заданий пока нет', 'Тапсырмалар әзірге жоқ')}
          body={l(
            'Назначенные вашей группе задания появятся здесь.',
            'Тобыңызға берілген тапсырмалар осында пайда болады.',
          )}
        />
      )}
    </>
  );
}
function Assignment({ id }: { id: string }) {
  const l = useL(),
    action = useAction(),
    r = useResource(`/assignments/${id}`, detailSchema),
    files = useResource(`/content/${id}/materials`, materialsSchema);
  if (r.loading) return <Loading />;
  if (r.error) return <ErrorState error={r.error} retry={r.reload} />;
  const a = r.data!;
  return (
    <>
      <PageHeading
        title={l('Задание', 'Тапсырма')}
        back={{ to: '/assignments', label: l('Мои задания', 'Менің тапсырмаларым') }}
      />
      <div className="lesson-surface">
        <RenderedContent payload={a.content} materials={files.data || []} />
        {a.content.dueAt && (
          <p>
            {l('Срок сдачи', 'Тапсыру мерзімі')}: {new Date(a.content.dueAt).toLocaleString()}
          </p>
        )}
        <Materials contentId={id} />
        {a.submission?.score !== null && a.submission && (
          <aside className="learning-callout">
            <strong>
              {a.submission.score} / {a.content.maxScore || 100}
            </strong>
            <p>{a.submission.feedback}</p>
          </aside>
        )}
        <form
          className="editor-form"
          onSubmit={(e) => {
            e.preventDefault();
            const body = Object.fromEntries(new FormData(e.currentTarget));
            void action.run(async () => {
              await request(`/assignments/${id}/submit`, savedSchema, { method: 'POST', body });
              r.reload();
            });
          }}
        >
          <Field label={l('Ваш ответ', 'Сіздің жауабыңыз')}>
            <textarea
              name="text"
              required
              maxLength={20000}
              rows={8}
              defaultValue={a.submission?.text || ''}
            />
          </Field>
          {a.submission && (
            <p className="hint">
              {l(
                'Повторная отправка заменит ответ и сбросит прежнюю оценку.',
                'Қайта жіберу жауапты ауыстырып, алдыңғы бағаны өшіреді.',
              )}
            </p>
          )}
          <button className="button" disabled={action.busy}>
            {l('Отправить ответ', 'Жауап жіберу')}
          </button>
          <Feedback action={action} />
        </form>
      </div>
    </>
  );
}
