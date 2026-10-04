import { RichText, MathText, TextTable } from './RichText';
import { Link } from 'react-router';
import { useL, Field } from '../shared';
import type { Block, Payload, Material } from './model';
import { MaterialList, MaterialImage } from './Materials';

export const blockLabels: Record<Block['type'], readonly [string, string]> = {
  TEXT: ['Текст', 'Мәтін'],
  HEADING: ['Заголовок', 'Тақырып'],
  IMAGE: ['Изображение', 'Сурет'],
  FILE: ['Файл', 'Файл'],
  VIDEO: ['Видео / ссылка', 'Бейне / сілтеме'],
  QUOTE: ['Цитата', 'Дәйексөз'],
  FORMULA: ['Формула', 'Формула'],
  CODE: ['Код', 'Код'],
  TABLE: ['Таблица', 'Кесте'],
  CALLOUT: ['Запомните', 'Есте сақтаңыз'],
  PRACTICE: ['Ссылка на практику', 'Жаттығуға сілтеме'],
};
export function BlockEditor({
  blocks,
  onChange,
  materials,
}: {
  blocks: Block[];
  onChange: (b: Block[]) => void;
  materials: Material[];
}) {
  const l = useL();
  function update(i: number, b: Block) {
    onChange(blocks.map((v, n) => (n === i ? b : v)));
  }
  return (
    <section className="editor-section">
      <h2>{l('Содержание', 'Мазмұны')}</h2>
      {blocks.map((b, i) => (
        <fieldset className="block-editor" key={i}>
          <legend>
            {i + 1}. {l(...blockLabels[b.type])}
          </legend>
          <div className="editor-toolbar">
            <button
              type="button"
              className="text-button"
              disabled={i === 0}
              onClick={() => {
                const copy = [...blocks];
                [copy[i - 1], copy[i]] = [copy[i], copy[i - 1]];
                onChange(copy);
              }}
            >
              {l('Выше', 'Жоғары')}
            </button>
            <button
              type="button"
              className="text-button"
              onClick={() => onChange(blocks.filter((_, n) => n !== i))}
            >
              {l('Удалить блок', 'Блокты жою')}
            </button>
          </div>
          {['FILE', 'IMAGE'].includes(b.type) ? (
            <Field label={l('Материал', 'Материал')}>
              <select
                required
                value={b.materialId || ''}
                onChange={(e) => update(i, { ...b, materialId: e.target.value })}
              >
                <option value="">
                  {l('Выберите загруженный файл', 'Жүктелген файлды таңдаңыз')}
                </option>
                {materials
                  .filter((m) => b.type !== 'IMAGE' || m.mimeType.startsWith('image/'))
                  .map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.titleRu}
                    </option>
                  ))}
              </select>
            </Field>
          ) : b.type === 'VIDEO' ? (
            <Field label={l('Ссылка на видео', 'Бейне сілтемесі')}>
              <input
                type="url"
                required
                value={b.url || ''}
                onChange={(e) => update(i, { ...b, url: e.target.value })}
              />
            </Field>
          ) : b.type === 'PRACTICE' ? (
            <Field label={l('ID темы для практики', 'Жаттығу тақырыбының ID')}>
              <input
                required
                value={b.topicId || ''}
                onChange={(e) => update(i, { ...b, topicId: e.target.value })}
              />
            </Field>
          ) : (
            <div className="form-pair">
              <Field label="RU">
                <textarea
                  maxLength={20000}
                  value={b.textRu || ''}
                  onChange={(e) => update(i, { ...b, textRu: e.target.value })}
                />
              </Field>
              <Field label="KZ">
                <textarea
                  maxLength={20000}
                  value={b.textKz || ''}
                  onChange={(e) => update(i, { ...b, textKz: e.target.value })}
                />
              </Field>
            </div>
          )}
        </fieldset>
      ))}
      <div className="block-add">
        {(Object.keys(blockLabels) as Block['type'][])
          .filter((k) => k !== 'PRACTICE')
          .map((type) => (
            <button
              key={type}
              type="button"
              className="button secondary"
              disabled={blocks.length >= 100}
              onClick={() => onChange([...blocks, { type, textRu: '', textKz: '' }])}
            >
              + {l(...blockLabels[type])}
            </button>
          ))}
      </div>
    </section>
  );
}
export function RenderedContent({
  payload,
  materials = [],
}: {
  payload: Payload;
  materials?: Material[];
}) {
  const l = useL();
  return (
    <article className="learning-content">
      {payload.sourceType === 'AI_GENERATED' && (
        <p className="hint">
          {l(
            'Авторский тренировочный материал создан с помощью ИИ; проверка специалистом отмечается отдельно.',
            'Авторлық жаттығу материалы ЖИ көмегімен жасалған; маман тексеруі бөлек белгіленеді.',
          )}
        </p>
      )}
      {(payload.titleRu || payload.titleKz) && <h2>{l(payload.titleRu, payload.titleKz)}</h2>}
      {l(payload.descriptionRu, payload.descriptionKz) && (
        <RichText text={l(payload.descriptionRu, payload.descriptionKz)} />
      )}
      {l(payload.contentRu, payload.contentKz) && (
        <RichText text={l(payload.contentRu, payload.contentKz)} />
      )}
      {payload.blocks.map((b, i) => {
        const text = l(b.textRu, b.textKz);
        switch (b.type) {
          case 'HEADING':
            return <h3 key={i}>{text}</h3>;
          case 'QUOTE':
            return <blockquote key={i}>{text}</blockquote>;
          case 'FORMULA':
            return <MathText key={i} text={text} display />;
          case 'CODE':
            return (
              <pre key={i}>
                <code>{text}</code>
              </pre>
            );
          case 'TABLE':
            return <TextTable key={i} text={text} />;
          case 'CALLOUT':
            return (
              <aside className="learning-callout" key={i}>
                {text}
              </aside>
            );
          case 'VIDEO':
            return /^https?:\/\//.test(b.url || '') ? (
              <a
                className="text-link"
                key={i}
                href={b.url}
                target="_blank"
                rel="noopener noreferrer"
              >
                {text || l('Открыть видео / ссылку', 'Бейне / сілтемені ашу')}
              </a>
            ) : null;
          case 'PRACTICE':
            return (
              <Link key={i} className="button secondary" to={`/topics/${b.topicId}`}>
                {l('Перейти к практике', 'Жаттығуға өту')}
              </Link>
            );
          case 'IMAGE': {
            const material = materials.find((m) => m.id === b.materialId);
            return material ? <MaterialImage key={i} material={material} /> : null;
          }
          case 'FILE':
            return <MaterialList key={i} items={materials.filter((m) => m.id === b.materialId)} />;
          default:
            return <RichText key={i} text={text} />;
        }
      })}
    </article>
  );
}
