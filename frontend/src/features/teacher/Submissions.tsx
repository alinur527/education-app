import { useState } from 'react';
import { useParams } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, useAction, Feedback, Field, Pager } from '../shared';
import { savedSchema } from '../content/model';
const item = z.array(
  z.object({
    userId: z.string(),
    firstName: z.string().nullable(),
    lastName: z.string().nullable(),
    text: z.string(),
    score: z.number().nullable(),
    feedback: z.string().nullable(),
    submittedAt: z.string(),
    revision: z.number(),
  }),
);
const schema = z.object({ items: item, page: z.number(), size: z.number(), total: z.number() });
export default function Submissions() {
  const { id } = useParams(),
    l = useL(),
    action = useAction(),
    [page, setPage] = useState(0),
    r = useResource(`/teacher/assignments/${id}/submissions?page=${page}`, schema);
  return (
    <>
      <PageHeading
        title={l('Ответы учеников', 'Оқушылардың жауаптары')}
        back={{ to: '/workspace/groups', label: l('Группы', 'Топтар') }}
      />
      <Feedback action={action} />
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : r.data?.items.length ? (
        r.data.items.map((s) => (
          <form
            className="submission-panel editor-form"
            key={`${s.userId}:${s.revision}`}
            onSubmit={(e) => {
              e.preventDefault();
              const values = Object.fromEntries(new FormData(e.currentTarget));
              void action.run(async () => {
                await request(
                  `/teacher/assignments/${id}/submissions/${s.userId}/grade`,
                  savedSchema,
                  {
                    method: 'POST',
                    body: { ...values, score: Number(values.score), revision: s.revision },
                  },
                );
                r.reload();
              });
            }}
          >
            <h2>
              {s.firstName} {s.lastName}
            </h2>
            <p className="plain-content">{s.text}</p>
            <div className="form-pair">
              <Field label={l('Балл', 'Балл')}>
                <input
                  type="number"
                  min={0}
                  max={10000}
                  name="score"
                  required
                  defaultValue={s.score ?? ''}
                />
              </Field>
              <Field label={l('Комментарий учителя', 'Мұғалімнің пікірі')}>
                <textarea name="feedback" defaultValue={s.feedback || ''} />
              </Field>
            </div>
            <button className="button secondary" disabled={action.busy}>
              {l('Сохранить оценку', 'Бағаны сақтау')}
            </button>
          </form>
        ))
      ) : (
        <Empty title={l('Ответов пока нет', 'Жауаптар әзірге жоқ')} />
      )}
      {r.data && <Pager page={page} total={r.data.total} onChange={setPage} />}
    </>
  );
}
