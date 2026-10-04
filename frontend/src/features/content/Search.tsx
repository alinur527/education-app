import { useState } from 'react';
import { Link } from 'react-router';
import { z } from 'zod';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, Field } from '../shared';
import { kindLabels } from './model';
const schema = z.array(
  z.object({
    id: z.string(),
    kind: z.enum(['SUBJECT', 'TOPIC', 'COURSE', 'LESSON']),
    titleRu: z.string(),
    titleKz: z.string(),
  }),
);
export default function Search() {
  const l = useL(),
    [q, setQ] = useState(''),
    r = useResource(`/search?q=${encodeURIComponent(q)}`, schema);
  const roots = { SUBJECT: 'subjects', TOPIC: 'topics', COURSE: 'courses', LESSON: 'lessons' };
  return (
    <>
      <PageHeading title={l('Поиск по платформе', 'Платформа бойынша іздеу')} />
      <Field label={l('Предмет, тема, курс или урок', 'Пән, тақырып, курс немесе сабақ')}>
        <input type="search" maxLength={200} value={q} onChange={(e) => setQ(e.target.value)} />
      </Field>
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : r.data?.length ? (
        <div className="course-grid">
          {r.data.map((c) => (
            <Link className="course-tile" key={c.id} to={`/${roots[c.kind]}/${c.id}`}>
              <small>{l(...kindLabels[c.kind])}</small>
              <h2>{l(c.titleRu, c.titleKz)}</h2>
            </Link>
          ))}
        </div>
      ) : (
        <Empty
          title={
            q
              ? l('Ничего не найдено', 'Ештеңе табылмады')
              : l('Введите название материала', 'Материалдың атауын енгізіңіз')
          }
        />
      )}
    </>
  );
}
