import { useState } from 'react';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { useL, useAction, Feedback, Field } from '../shared';
const reviews = z.array(
  z.object({
    id: z.string(),
    revision: z.number(),
    decision: z.string(),
    note: z.string(),
    reviewerName: z.string().nullable(),
    createdAt: z.string(),
    current: z.boolean(),
  }),
);
export default function EditorialPanel({
  id,
  version,
  dirty,
}: {
  id: string;
  version: number;
  dirty: boolean;
}) {
  const l = useL(),
    r = useResource(`/cms/content/${id}/editorial-review`, reviews),
    action = useAction();
  const [note, setNote] = useState(''),
    [checked, setChecked] = useState(false),
    [decision, setDecision] = useState('APPROVED');
  return (
    <details className="editor-section">
      <summary>{l('Предметная проверка редактором', 'Редактордың пәндік тексеруі')}</summary>
      <p>
        {l(
          'Отметки автоматической проверки и публикация не означают одобрение специалистом. Здесь редактор фиксирует собственную проверку конкретной версии.',
          'Автоматты тексеру белгілері мен жариялау маманның мақұлдауын білдірмейді. Мұнда редактор нақты нұсқаны өзі тексергенін тіркейді.',
        )}
      </p>
      {r.error && (
        <p role="alert">
          {l('Не удалось загрузить историю проверок.', 'Тексеру тарихын жүктеу мүмкін болмады.')}
        </p>
      )}
      {r.data?.map((v) => (
        <article key={v.id} className="review-entry">
          <strong>
            {v.decision === 'APPROVED'
              ? l('Одобрено', 'Мақұлданды')
              : l('Нужны правки', 'Түзету қажет')}{' '}
            · {v.reviewerName}
          </strong>
          <p>{v.note}</p>
          <small>
            {new Date(v.createdAt).toLocaleString()} ·{' '}
            {v.current
              ? l('Текущий текст', 'Ағымдағы мәтін')
              : l('Другая версия текста', 'Мәтіннің басқа нұсқасы')}
          </small>
        </article>
      ))}
      <Field label={l('Решение', 'Шешім')}>
        <select value={decision} onChange={(e) => setDecision(e.target.value)}>
          <option value="APPROVED">{l('Одобрить', 'Мақұлдау')}</option>
          <option value="CHANGES_REQUIRED">{l('Нужны правки', 'Түзету қажет')}</option>
        </select>
      </Field>
      <Field
        label={l(
          'Что проверено и какие замечания остались',
          'Не тексерілді және қандай ескертулер қалды',
        )}
      >
        <textarea maxLength={4000} value={note} onChange={(e) => setNote(e.target.value)} />
      </Field>
      <label className="check-field">
        <input type="checkbox" checked={checked} onChange={(e) => setChecked(e.target.checked)} />
        {l(
          'Я лично проверил содержание, ответы, объяснения и перевод этой версии.',
          'Мен осы нұсқаның мазмұнын, жауаптарын, түсіндірмелерін және аудармасын өзім тексердім.',
        )}
      </label>
      <button
        type="button"
        className="button secondary"
        disabled={dirty || !checked || !note.trim() || action.busy}
        onClick={() =>
          void action.run(async () => {
            await request(`/cms/content/${id}/editorial-review`, reviews, {
              method: 'POST',
              body: { version, decision, note, confirmedHumanReview: checked },
            });
            r.reload();
            setChecked(false);
          })
        }
      >
        {l('Зафиксировать проверку', 'Тексеруді тіркеу')}
      </button>
      <Feedback action={action} />
    </details>
  );
}
