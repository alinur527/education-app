import { useState } from 'react';
import { Link } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, ErrorState } from '../../components';
import { useL, useAction, Feedback, Pager } from '../shared';
import { kindLabels, type Kind } from '../content/model';
function previewRow(value: unknown): {
  key?: string;
  kind?: string;
  payload?: { titleRu?: string; titleKz?: string };
} {
  if (!value || typeof value !== 'object') return {};
  const row = value as Record<string, unknown>,
    p =
      row.payload && typeof row.payload === 'object'
        ? (row.payload as Record<string, unknown>)
        : {};
  return {
    key: typeof row.key === 'string' ? row.key : undefined,
    kind: typeof row.kind === 'string' ? row.kind : undefined,
    payload: {
      titleRu: typeof p.titleRu === 'string' ? p.titleRu : undefined,
      titleKz: typeof p.titleKz === 'string' ? p.titleKz : undefined,
    },
  };
}
const previewSchema = z.object({
  id: z.string(),
  fileName: z.string(),
  status: z.enum(['VALID', 'INVALID', 'IMPORTED']),
  rows: z.array(z.unknown()).transform((rows) => rows.map(previewRow)),
  errors: z.array(
    z.object({ row: z.number(), field: z.string(), code: z.string(), detail: z.string() }),
  ),
  result: z.record(z.string(), z.string()).nullable(),
});
const historySchema = z.object({
  items: z.array(
    z.object({ id: z.string(), fileName: z.string(), status: z.string(), createdAt: z.string() }),
  ),
  total: z.number(),
  page: z.number(),
  size: z.number(),
});
export default function Imports() {
  const l = useL(),
    action = useAction(),
    [preview, setPreview] = useState<z.infer<typeof previewSchema> | null>(null),
    [page, setPage] = useState(0),
    history = useResource(`/cms/imports?page=${page}`, historySchema);
  return (
    <>
      <PageHeading
        title={l('Массовый импорт', 'Жаппай импорт')}
        body={l(
          'Загрузите CSV или JSON, проверьте все строки и подтвердите создание черновиков. До публикации они не появятся у учеников.',
          'CSV немесе JSON жүктеп, барлық жолдарды тексеріп, жобаларды жасауды растаңыз. Жарияланғанша оқушыларға көрінбейді.',
        )}
      />
      <form
        className="editor-form"
        onSubmit={(e) => {
          e.preventDefault();
          const body = new FormData(e.currentTarget);
          void action.run(async () => {
            setPreview(await request('/cms/imports', previewSchema, { method: 'POST', body }));
            history.reload();
          });
        }}
      >
        <label className="field">
          <span>
            {l('Файл импорта (до 2 МБ, 500 строк)', 'Импорт файлы (2 МБ дейін, 500 жол)')}
          </span>
          <input type="file" name="file" accept=".csv,.json" required />
        </label>
        <button className="button" disabled={action.busy}>
          {l('Проверить и показать preview', 'Тексеру және алдын ала қарау')}
        </button>
      </form>
      <Feedback action={action} />
      {preview && (
        <section className="editor-section">
          <h2>{preview.fileName}</h2>
          <p role="status">
            {preview.status === 'IMPORTED'
              ? l('Импорт завершён. Созданы черновики.', 'Импорт аяқталды. Жобалар жасалды.')
              : preview.status === 'VALID'
                ? l(
                    'Все строки прошли проверку. Можно подтвердить импорт.',
                    'Барлық жолдар тексерілді. Импортты растауға болады.',
                  )
                : l(
                    'Найдены ошибки. Исправьте файл и загрузите заново.',
                    'Қателер табылды. Файлды түзетіп, қайта жүктеңіз.',
                  )}
          </p>
          {preview.errors.length > 0 && (
            <div role="alert">
              <ul className="import-errors">
                {preview.errors.map((e, i) => (
                  <li key={i}>
                    {l('Строка', 'Жол')} {e.row}: {e.detail}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div
            className="table-region"
            role="region"
            tabIndex={0}
            aria-label={l('Предпросмотр импорта', 'Импортты алдын ала қарау')}
          >
            <table>
              <thead>
                <tr>
                  <th>{l('Строка', 'Жол')}</th>
                  <th>{l('Тип', 'Түрі')}</th>
                  <th>RU</th>
                  <th>KZ</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((r, i) => (
                  <tr key={i}>
                    <td>{i + 1}</td>
                    <td>
                      {Object.hasOwn(kindLabels, r.kind || '')
                        ? l(...kindLabels[r.kind as Kind])
                        : r.kind}
                    </td>
                    <td>
                      {preview.result?.[r.key || ''] ? (
                        <Link
                          className="text-link"
                          to={`/workspace/content/${preview.result[r.key || '']}`}
                        >
                          {r.payload?.titleRu}
                        </Link>
                      ) : (
                        r.payload?.titleRu
                      )}
                    </td>
                    <td>{r.payload?.titleKz || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {preview.status === 'VALID' && (
            <button
              className="button"
              disabled={action.busy}
              onClick={() =>
                void action.run(async () => {
                  setPreview(
                    await request(`/cms/imports/${preview.id}/confirm`, previewSchema, {
                      method: 'POST',
                    }),
                  );
                  history.reload();
                })
              }
            >
              {l('Подтвердить импорт', 'Импортты растау')}
            </button>
          )}
        </section>
      )}
      <section className="editor-section">
        <h2>{l('История импорта', 'Импорт тарихы')}</h2>
        {history.error ? (
          <ErrorState error={history.error} retry={history.reload} />
        ) : (
          <ul className="material-list">
            {history.data?.items.map((h) => (
              <li key={h.id}>
                <span>
                  {h.fileName}
                  <small>{new Date(h.createdAt).toLocaleString()}</small>
                </span>
                <button
                  className="text-button"
                  onClick={() =>
                    void action.run(async () =>
                      setPreview(await request(`/cms/imports/${h.id}`, previewSchema)),
                    )
                  }
                >
                  {l('Открыть', 'Ашу')}
                </button>
              </li>
            ))}
          </ul>
        )}
        <Pager page={page} total={history.data?.total || 0} onChange={setPage} />
      </section>
    </>
  );
}
