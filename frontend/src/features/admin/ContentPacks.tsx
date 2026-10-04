import { useState } from 'react';
import { Link } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState } from '../../components';
import { useAction, useL, Feedback } from '../shared';
import { contentSchema, payloadSchema } from '../content/model';
import { RenderedContent } from '../content/Blocks';
const batch = z.object({
  id: z.string(),
  status: z.string(),
  preview: z.array(
    z.object({
      row: z.number(),
      externalKey: z.string(),
      action: z.string().optional(),
      error: z.string().optional(),
    }),
  ),
  result: z
    .object({
      created: z.number(),
      updated: z.number(),
      unchanged: z.number(),
      conflicts: z.number(),
    })
    .nullable(),
});
const conflicts = z.array(
  z.object({ id: z.string(), contentId: z.string(), baseRevision: z.number(), status: z.string() }),
);
const candidate = z.object({
  id: z.string(),
  status: z.string(),
  baseRevision: z.number(),
  current: contentSchema,
  incoming: payloadSchema,
});
const history = z.array(
  z.object({
    id: z.string(),
    namespace: z.string(),
    packVersion: z.string(),
    batchKey: z.string(),
    status: z.string(),
    createdAt: z.string(),
  }),
);
export default function ContentPacks() {
  const l = useL(),
    action = useAction(),
    [preview, setPreview] = useState<z.infer<typeof batch> | null>(null),
    [page, setPage] = useState(0),
    [conflictPage, setConflictPage] = useState(0),
    [selected, setSelected] = useState<string | null>(null),
    [fileName, setFileName] = useState('');
  const h = useResource(`/cms/content-packs?page=${page}`, history),
    c = useResource(`/cms/content-packs/conflicts?page=${conflictPage}`, conflicts);
  function reload() {
    h.reload();
    c.reload();
  }
  return (
    <>
      <PageHeading
        title={l('Версионные пакеты', 'Нұсқаланған пакеттер')}
        body={l(
          'Постоянные ключи сохраняют UUID. Изменения преподавателя требуют отдельного решения.',
          'Тұрақты кілттер UUID сақтайды. Мұғалімнің өзгерістеріне бөлек шешім қажет.',
        )}
      />
      <Link to="/workspace/imports">
        {l('Обычный CSV / JSON импорт', 'Қалыпты CSV / JSON импорты')}
      </Link>
      <section className="editor-section">
        <label className="field">
          <span>{l('Пакет JSON, до 2 МБ', 'JSON пакет, 2 МБ дейін')}</span>
          <input
            type="file"
            accept=".json,application/json"
            disabled={action.busy}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              setFileName(file.name);
              setPreview(null);
              void action.run(async () => {
                if (file.size > 2 * 1024 * 1024) throw new Error('FILE_SIZE');
                const req: unknown = JSON.parse(await file.text());
                setPreview(
                  await request('/cms/content-packs', batch, { method: 'POST', body: req }),
                );
              });
            }}
          />
        </label>
        <p>{fileName}</p>
        <Feedback action={action} />
        {preview && (
          <>
            <p role="status">
              {l('Статус', 'Күйі')}: {preview.status}
            </p>
            <div
              className="table-region"
              tabIndex={0}
              role="region"
              aria-label={l('Строки пакета', 'Пакет жолдары')}
            >
              <table>
                <thead>
                  <tr>
                    <th>№</th>
                    <th>{l('Ключ', 'Кілт')}</th>
                    <th>{l('Действие / ошибка', 'Әрекет / қате')}</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.preview.map((r) => (
                    <tr key={r.row}>
                      <td>{r.row}</td>
                      <td>{r.externalKey}</td>
                      <td>{r.error || r.action}</td>
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
                      await request(`/cms/content-packs/${preview.id}/confirm`, batch, {
                        method: 'POST',
                      }),
                    );
                    reload();
                  })
                }
              >
                {l('Подтвердить импорт в черновики', 'Жобаларға импорттауды растау')}
              </button>
            )}
            {preview.result && (
              <p>
                {l(
                  'Создано / обновлено / без изменений / конфликтов',
                  'Жасалды / жаңартылды / өзгеріссіз / қайшылықтар',
                )}
                : {preview.result.created} / {preview.result.updated} / {preview.result.unchanged} /{' '}
                {preview.result.conflicts}
              </p>
            )}
          </>
        )}
      </section>
      <section className="editor-section">
        <h2>{l('Конфликты', 'Қайшылықтар')}</h2>
        {c.loading ? (
          <Loading />
        ) : c.error ? (
          <ErrorState error={c.error} retry={c.reload} />
        ) : c.data?.length ? (
          c.data.map((v) => (
            <p key={v.id}>
              <button className="text-button" onClick={() => setSelected(v.id)}>
                {l('Сравнить версии', 'Нұсқаларды салыстыру')} · {v.contentId.slice(0, 8)}
              </button>
            </p>
          ))
        ) : (
          <p>{l('Нет нерешённых конфликтов.', 'Шешілмеген қайшылықтар жоқ.')}</p>
        )}
        <PageButtons page={conflictPage} length={c.data?.length || 0} change={setConflictPage} />
        {selected && (
          <Conflict
            key={selected}
            id={selected}
            done={() => {
              setSelected(null);
              reload();
            }}
          />
        )}
      </section>
      <section className="editor-section">
        <h2>{l('История пакетов', 'Пакеттер тарихы')}</h2>
        {h.error ? (
          <ErrorState error={h.error} retry={h.reload} />
        ) : (
          h.data?.map((v) => (
            <p key={v.id}>
              <button
                className="text-button"
                onClick={() =>
                  void action.run(async () =>
                    setPreview(await request(`/cms/content-packs/${v.id}`, batch)),
                  )
                }
              >
                {v.namespace} / {v.packVersion} / {v.batchKey}
              </button>{' '}
              · {v.status}
            </p>
          ))
        )}
        <PageButtons page={page} length={h.data?.length || 0} change={setPage} />
      </section>
    </>
  );
}
function PageButtons({
  page,
  length,
  change,
}: {
  page: number;
  length: number;
  change: (p: number) => void;
}) {
  const l = useL();
  return (
    <div className="pager">
      <button className="button secondary" disabled={page === 0} onClick={() => change(page - 1)}>
        {l('Назад', 'Артқа')}
      </button>
      <span>{page + 1}</span>
      <button className="button secondary" disabled={length < 25} onClick={() => change(page + 1)}>
        {l('Далее', 'Келесі')}
      </button>
    </div>
  );
}
function Conflict({ id, done }: { id: string; done: () => void }) {
  const l = useL(),
    r = useResource(`/cms/content-packs/conflicts/${id}`, candidate),
    action = useAction();
  if (r.loading) return <Loading />;
  if (r.error || !r.data) return <ErrorState error={r.error} retry={r.reload} />;
  const c = r.data;
  return (
    <section className="conflict-preview">
      <div className="form-pair">
        <div>
          <h3>{l('Текущий черновик', 'Ағымдағы жоба')}</h3>
          <RenderedContent payload={c.current.payload} />
          <p>{c.current.payload.answerEvidence}</p>
        </div>
        <div>
          <h3>{l('Входящий пакет', 'Кіріс пакет')}</h3>
          <RenderedContent payload={c.incoming} />
          <p>{c.incoming.answerEvidence}</p>
        </div>
      </div>
      <p>
        <Link to={`/workspace/content/${c.current.id}`}>
          {l('Открыть редактор и ответы', 'Редактор мен жауаптарды ашу')}
        </Link>
      </p>
      <p>
        {l(
          'Входящая версия сохранится как черновик. Опубликованный текст изменится только после отдельной публикации.',
          'Кіріс нұсқасы жоба ретінде сақталады. Жарияланған мәтін тек бөлек жариялаудан кейін өзгереді.',
        )}
      </p>
      {['KEEP_LOCAL', 'USE_INCOMING_DRAFT'].map((decision) => (
        <button
          key={decision}
          className="button secondary"
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              await request(`/cms/content-packs/conflicts/${id}/resolve`, candidate, {
                method: 'POST',
                body: { decision, version: c.current.version },
              });
              done();
            })
          }
        >
          {decision === 'KEEP_LOCAL'
            ? l('Оставить текущий текст', 'Ағымдағы мәтінді қалдыру')
            : l('Принять входящий черновик', 'Кіріс жобасын қабылдау')}
        </button>
      ))}
      <Feedback action={action} />
    </section>
  );
}
