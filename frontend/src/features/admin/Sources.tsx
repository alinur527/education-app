import { useState } from 'react';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState } from '../../components';
import { useAction, useL, Feedback, Field } from '../shared';
const source = z.object({
  sourceId: z.string(),
  revision: z.number(),
  metadata: z.record(z.string(), z.union([z.string(), z.number(), z.null()])),
});
const sources = z.array(source);
export default function Sources() {
  const l = useL(),
    action = useAction(),
    [q, setQ] = useState(''),
    [page, setPage] = useState(0),
    [id, setId] = useState(''),
    [url, setUrl] = useState(''),
    [name, setName] = useState('');
  const r = useResource(`/cms/sources?q=${encodeURIComponent(q)}&page=${page}`, sources);
  return (
    <>
      <PageHeading
        title={l('Источники и права использования', 'Дереккөздер және пайдалану құқықтары')}
        body={l(
          'Доступность файла в интернете не означает разрешение на перепубликацию. Неясные права — только внешняя ссылка.',
          'Файлдың интернетте қолжетімді болуы оны қайта жариялауға рұқсатты білдірмейді. Құқықтары белгісіз болса, тек сыртқы сілтеме беріледі.',
        )}
      />
      <Field label={l('Найти источник', 'Дереккөзді табу')}>
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
        <div className="source-grid">
          {r.data?.map((s) => (
            <article className="editor-section" key={s.sourceId}>
              <h2>{String(s.metadata.sourceName || s.metadata.subject || s.sourceId)}</h2>
              <p>
                {s.sourceId} · {String(s.metadata.sourceKind || '')}
              </p>
              <a href={String(s.metadata.url)} target="_blank" rel="noreferrer">
                {l('Открыть оригинал', 'Түпнұсқаны ашу')}
              </a>
              <dl>
                <dt>{l('Доступ', 'Қолжетімділік')}</dt>
                <dd>{String(s.metadata.accessStatus || 'NOT_CHECKED')}</dd>
                <dt>{l('Использование', 'Пайдалану')}</dt>
                <dd>{String(s.metadata.reuseMode || 'EXTERNAL_LINK_UNLESS_REUSE_CONFIRMED')}</dd>
                <dt>{l('Проверка источника', 'Дереккөзді тексеру')}</dt>
                <dd>{String(s.metadata.reviewStatus || 'NOT_REVIEWED')}</dd>
                <dt>{l('Получен', 'Алынды')}</dt>
                <dd>{String(s.metadata.retrievedAt || '—')}</dd>
              </dl>
              {s.metadata.sha256 && (
                <details>
                  <summary>SHA-256</summary>
                  <code className="hash-value">{String(s.metadata.sha256)}</code>
                </details>
              )}
            </article>
          ))}
        </div>
      )}
      <div className="pager">
        <button
          className="button secondary"
          disabled={page === 0}
          onClick={() => setPage(page - 1)}
        >
          {l('Назад', 'Артқа')}
        </button>
        <span>{page + 1}</span>
        <button
          className="button secondary"
          disabled={(r.data?.length || 0) < 50}
          onClick={() => setPage(page + 1)}
        >
          {l('Далее', 'Келесі')}
        </button>
      </div>
      <details className="editor-section">
        <summary>{l('Добавить ссылку на источник', 'Дереккөз сілтемесін қосу')}</summary>
        <form
          className="editor-form"
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(async () => {
              await request('/cms/sources', sources, {
                method: 'POST',
                body: [
                  {
                    sourceId: id,
                    metadata: {
                      url,
                      sourceName: name,
                      sourceKind: 'EXTERNAL_REFERENCE',
                      accessStatus: 'NOT_CHECKED',
                      reuseMode: 'EXTERNAL_LINK_UNLESS_REUSE_CONFIRMED',
                      reviewStatus: 'NOT_REVIEWED',
                    },
                  },
                ],
              });
              r.reload();
              setId('');
              setUrl('');
              setName('');
            });
          }}
        >
          <Field label={l('Постоянный ключ (латиница)', 'Тұрақты кілт (латынша)')}>
            <input
              required
              maxLength={100}
              pattern="[A-Za-z0-9_.:\-]+"
              value={id}
              onChange={(e) => setId(e.target.value)}
            />
          </Field>
          <Field label={l('Название', 'Атауы')}>
            <input
              required
              maxLength={300}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </Field>
          <Field label="URL">
            <input type="url" required value={url} onChange={(e) => setUrl(e.target.value)} />
          </Field>
          <button className="button" disabled={action.busy}>
            {l('Сохранить ссылку', 'Сілтемені сақтау')}
          </button>
          <Feedback action={action} />
        </form>
      </details>
    </>
  );
}
