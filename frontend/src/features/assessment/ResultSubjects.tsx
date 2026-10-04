import { z } from 'zod';
import { Link } from 'react-router';
import { resultsSchema, subjectSchema } from '../../api';
import { useResource } from '../../hooks';
import { useL } from '../shared';
const subjects = z.array(subjectSchema);
export default function ResultSubjects({
  answers,
}: {
  answers: z.infer<typeof resultsSchema>['answers'];
}) {
  const l = useL(),
    catalog = useResource('/subjects', subjects),
    rows = new Map<string, { earned: number; max: number; count: number }>();
  for (const a of answers) {
    if (!a.subjectId) continue;
    const r = rows.get(a.subjectId) || { earned: 0, max: 0, count: 0 };
    r.earned += a.earnedPoints ?? (a.isCorrect ? 1 : 0);
    r.max += a.maxPoints ?? 1;
    r.count++;
    rows.set(a.subjectId, r);
  }
  if (rows.size < 2) return null;
  return (
    <section className="editor-section">
      <h2>{l('Баллы по предметам', 'Пәндер бойынша балдар')}</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>{l('Предмет', 'Пән')}</th>
              <th>{l('Вопросов', 'Сұрақтар')}</th>
              <th>{l('Баллы', 'Балдар')}</th>
            </tr>
          </thead>
          <tbody>
            {Array.from(rows).map(([id, r]) => {
              const s = catalog.data?.find((s) => s.id === id);
              return (
                <tr key={id}>
                  <th scope="row">
                    {s ? (
                      <Link to={`/subjects/${id}`}>{l(s.nameRu, s.nameKz)}</Link>
                    ) : (
                      l('Архивный предмет', 'Мұрағаттағы пән')
                    )}
                  </th>
                  <td>{r.count}</td>
                  <td>
                    {r.earned} / {r.max}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
