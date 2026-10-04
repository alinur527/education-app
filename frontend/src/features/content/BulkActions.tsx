import { useState } from 'react';
import { z } from 'zod';
import { request } from '../../api';
import { useAction, useL, Feedback } from '../shared';
const previewSchema = z.object({
  valid: z.boolean(),
  confirmation: z.string(),
  rows: z.array(
    z.object({
      id: z.string(),
      titleRu: z.string(),
      titleKz: z.string().nullable(),
      from: z.string(),
      to: z.string(),
      error: z.string().optional(),
    }),
  ),
  applied: z.boolean(),
});
export default function BulkActions({
  items,
  reload,
}: {
  items: { id: string; version: number }[];
  reload: () => void;
}) {
  const l = useL(),
    action = useAction(),
    [target, setTarget] = useState('REVIEW'),
    [preview, setPreview] = useState<z.infer<typeof previewSchema> | null>(null);
  const signature = JSON.stringify({ items, target }),
    [snapshot, setSnapshot] = useState('');
  return (
    <section className="bulk-actions">
      <p>
        {l('Выбрано', 'Таңдалды')}: {items.length}
      </p>
      <label>
        {l('Действие', 'Әрекет')}{' '}
        <select
          value={target}
          onChange={(e) => {
            setTarget(e.target.value);
            setPreview(null);
          }}
        >
          <option value="REVIEW">{l('Отправить на проверку', 'Тексеруге жіберу')}</option>
          <option value="PUBLISHED">{l('Опубликовать', 'Жариялау')}</option>
        </select>
      </label>
      <button
        type="button"
        className="button secondary"
        disabled={!items.length || action.busy}
        onClick={() =>
          void action.run(async () => {
            const result = await request('/cms/content-batches/preview', previewSchema, {
              method: 'POST',
              body: { items, target },
            });
            setPreview(result);
            setSnapshot(signature);
          })
        }
      >
        {l('Предпросмотр изменений', 'Өзгерістерді алдын ала қарау')}
      </button>
      {preview && snapshot === signature && (
        <div
          role="region"
          aria-label={l('Предпросмотр массовой операции', 'Жаппай әрекетті алдын ала қарау')}
        >
          <ul>
            {preview.rows.map((row) => (
              <li key={row.id}>
                {l(row.titleRu, row.titleKz)}: {row.from} → {row.to}
                {row.error && <strong> · {row.error}</strong>}
              </li>
            ))}
          </ul>
          <p>
            {l(
              'Изменения применятся одной транзакцией. Родительские материалы должны быть опубликованы раньше дочерних.',
              'Өзгерістер бір транзакциямен қолданылады. Негізгі материалдар еншілес материалдардан бұрын жариялануы керек.',
            )}
          </p>
          <button
            type="button"
            className="button"
            disabled={!preview.valid || action.busy}
            onClick={() =>
              void action.run(async () => {
                await request('/cms/content-batches/confirm', previewSchema, {
                  method: 'POST',
                  body: { items, target, confirmation: preview.confirmation },
                });
                setPreview(null);
                reload();
              })
            }
          >
            {l('Подтвердить изменения', 'Өзгерістерді растау')}
          </button>
        </div>
      )}
      <Feedback action={action} />
    </section>
  );
}
