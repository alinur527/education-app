import { useState } from 'react';
import { Link } from 'react-router';
import { z } from 'zod';
import { useResource } from '../../hooks';
import { useL, Field } from '../shared';
import { Loading, ErrorState } from '../../components';
import { RichText } from './RichText';
import type { Payload } from './model';
const contexts = z.array(
  z.object({
    id: z.string(),
    version: z.number(),
    titleRu: z.string(),
    titleKz: z.string(),
    contentRu: z.string().nullable(),
    contentKz: z.string().nullable(),
  }),
);
export default function ContextPicker({
  topic,
  payload,
  onChange,
}: {
  topic: string;
  payload: Payload;
  onChange: (p: Payload) => void;
}) {
  const l = useL(),
    [q, setQ] = useState('');
  const r = useResource(`/cms/contexts?topicId=${topic}&q=${encodeURIComponent(q)}`, contexts),
    selected = r.data?.find((c) => c.id === payload.contextId);
  return (
    <details className="editor-section">
      <summary>{l('Общий текст для группы вопросов', 'Сұрақтар тобына ортақ мәтін')}</summary>
      <p>
        {l(
          'Сначала опубликуйте контекст этой темы. Вопрос закрепит выбранную версию при публикации.',
          'Алдымен осы тақырыптың контекстін жариялаңыз. Сұрақ жарияланған кезде таңдалған нұсқаны бекітеді.',
        )}
      </p>
      <Link to={`/workspace/content/new?kind=CONTEXT&parent=${topic}`}>
        {l('Создать общий текст', 'Ортақ мәтін жасау')}
      </Link>
      <Field label={l('Найти контекст', 'Контексті табу')}>
        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} />
      </Field>
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : (
        <Field label={l('Опубликованный контекст', 'Жарияланған контекст')}>
          <select
            value={payload.contextId || ''}
            onChange={(e) => {
              const v = r.data?.find((c) => c.id === e.target.value);
              onChange({ ...payload, contextId: v?.id, contextVersion: v?.version });
            }}
          >
            <option value="">{l('Без общего текста', 'Ортақ мәтінсіз')}</option>
            {payload.contextId && !selected && (
              <option value={payload.contextId}>
                {l('Закреплённый контекст', 'Бекітілген контекст')} · {payload.contextVersion}
              </option>
            )}
            {r.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {l(c.titleRu, c.titleKz)} · {c.version}
              </option>
            ))}
          </select>
        </Field>
      )}
      {selected && (
        <>
          <p>
            {l('Закреплённая версия', 'Бекітілген нұсқа')}:{' '}
            {payload.contextVersion || selected.version}
          </p>
          {payload.contextVersion && payload.contextVersion !== selected.version ? (
            <p className="hint">
              {l(
                'Ниже показана последняя опубликованная версия; закреплённая версия вопроса не меняется без вашего выбора.',
                'Төменде соңғы жарияланған нұсқа көрсетілген; сұрақтың бекітілген нұсқасы таңдауыңызсыз өзгермейді.',
              )}
            </p>
          ) : null}
          <RichText text={l(selected.contentRu, selected.contentKz)} />
          {payload.contextVersion !== selected.version && (
            <button
              type="button"
              className="text-button"
              onClick={() => onChange({ ...payload, contextVersion: selected.version })}
            >
              {l('Использовать последнюю версию', 'Соңғы нұсқаны пайдалану')}
            </button>
          )}
        </>
      )}
    </details>
  );
}
