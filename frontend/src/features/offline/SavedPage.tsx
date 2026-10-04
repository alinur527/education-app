import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { LanguageSwitch, PageHeading, Loading } from '../../components';
import { ApiError } from '../../api';
import { useAction, useL, Feedback } from '../shared';
import { RichText, MathText, TextTable } from '../content/RichText';
import { listSaved, deleteSaved, saveTheory, type SavedTheory } from './library';
export default function SavedPage() {
  const l = useL(),
    action = useAction(),
    [items, setItems] = useState<SavedTheory[] | null>(null),
    [selected, setSelected] = useState<string | null>(null),
    [online, setOnline] = useState(navigator.onLine),
    [error, setError] = useState(false);
  async function load() {
    try {
      setItems(await listSaved());
      setError(false);
    } catch {
      setError(true);
    }
  }
  useEffect(() => {
    let live = true;
    void listSaved()
      .then((v) => {
        if (live) setItems(v);
      })
      .catch(() => {
        if (live) setError(true);
      });
    const change = () => setOnline(navigator.onLine);
    window.addEventListener('online', change);
    window.addEventListener('offline', change);
    return () => {
      live = false;
      window.removeEventListener('online', change);
      window.removeEventListener('offline', change);
    };
  }, []);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (selected) heading.current?.focus();
  }, [selected]);
  const item = items?.find((i) => i.id === selected);
  return (
    <main id="main" className="saved-library">
      <header className="button-row">
        <Link className="text-link" to="/">
          {l('На сайт', 'Сайтқа')}
        </Link>
        <LanguageSwitch />
      </header>
      <PageHeading
        title={l('Сохранённое на устройстве', 'Құрылғыда сақталған')}
        body={l(
          'Для спокойного чтения без сети. Практика, оценки и личные данные здесь не сохраняются.',
          'Желісіз оқу үшін. Жаттығулар, бағалар және жеке деректер мұнда сақталмайды.',
        )}
      />
      <p role="status">
        {online
          ? l('Вы в сети', 'Желіге қосылғансыз')
          : l('Офлайн: показана сохранённая версия', 'Офлайн: сақталған нұсқа көрсетілуде')}
      </p>
      {error ? (
        <div>
          <p role="alert">
            {l(
              'Хранилище недоступно. Проверьте настройки браузера.',
              'Сақтау орны қолжетімсіз. Браузер баптауларын тексеріңіз.',
            )}
          </p>
          <button className="button secondary" onClick={() => void load()}>
            {l('Повторить', 'Қайталау')}
          </button>
        </div>
      ) : items === null ? (
        <Loading />
      ) : !items.length ? (
        <p>
          {l(
            'В библиотеке пока пусто. Откройте публичную теорию и нажмите «Сохранить для чтения без интернета».',
            'Кітапхана әзірге бос. Жария теорияны ашып, «Интернетсіз оқу үшін сақтау» батырмасын басыңыз.',
          )}
        </p>
      ) : (
        <>
          <p>
            {l('Занято', 'Пайдаланылды')}:{' '}
            {(items.reduce((n, i) => n + i.size, 0) / 1024 / 1024).toFixed(2)} / 100 МБ
          </p>
          <ul className="saved-list">
            {items.map((i) => (
              <li key={i.id}>
                <button className="text-link" onClick={() => setSelected(i.id)}>
                  {l(i.document.content.titleRu, i.document.content.titleKz)}
                </button>
                <small>
                  {l('Версия', 'Нұсқа')} {i.document.version} ·{' '}
                  {new Date(i.savedAt).toLocaleString()} · {Math.ceil(i.size / 1024)} КБ
                </small>
                <div className="button-row">
                  <button
                    className="text-button"
                    disabled={!online || action.busy}
                    onClick={() =>
                      void action.run(async () => {
                        try {
                          await saveTheory(i.id);
                        } catch (e) {
                          if (e instanceof ApiError && e.status === 404) await deleteSaved(i.id);
                          throw e;
                        } finally {
                          await load();
                        }
                      })
                    }
                  >
                    {l('Обновить копию', 'Көшірмені жаңарту')}
                  </button>
                  <button
                    className="text-button"
                    disabled={action.busy}
                    onClick={() =>
                      void action.run(async () => {
                        await deleteSaved(i.id);
                        await load();
                      })
                    }
                  >
                    {l('Удалить', 'Жою')}
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <button
            className="button secondary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                for (const i of items) await deleteSaved(i.id);
                await load();
                setSelected(null);
              })
            }
          >
            {l('Очистить библиотеку', 'Кітапхананы тазалау')}
          </button>
        </>
      )}
      <Feedback action={action} />
      {item && (
        <article className="theory-content saved-reading">
          <h2 ref={heading} tabIndex={-1}>
            {l(item.document.content.titleRu, item.document.content.titleKz)}
          </h2>
          <RichText text={l(item.document.content.contentRu, item.document.content.contentKz)} />
          {item.document.content.blocks.map((b, i) => {
            const text = l(b.textRu, b.textKz);
            switch (b.type) {
              case 'HEADING':
                return <h3 key={i}>{text}</h3>;
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
              case 'IMAGE':
              case 'FILE':
                return null;
              case 'VIDEO':
                return (
                  <p key={i}>
                    {text} ·{' '}
                    {l('Внешняя ссылка доступна в сети', 'Сыртқы сілтеме желіде қолжетімді')}
                  </p>
                );
              default:
                return <RichText key={i} text={text} />;
            }
          })}
          {item.document.files.map((f) => {
            const saved = item.files.find((v) => v.id === f.id);
            return saved ? (
              <SavedFile
                key={f.id}
                blob={saved.blob}
                name={f.name}
                title={l(f.titleRu, f.titleKz)}
              />
            ) : null;
          })}
        </article>
      )}
    </main>
  );
}
function SavedFile({ blob, name, title }: { blob: Blob; name: string; title: string }) {
  const url = useMemo(() => URL.createObjectURL(blob), [blob]);
  useEffect(() => () => URL.revokeObjectURL(url), [url]);
  return (
    <figure>
      {blob.type.startsWith('image/') && <img src={url} alt={title} />}
      <figcaption>
        <a href={url} download={name}>
          {title} · {name}
        </a>
      </figcaption>
    </figure>
  );
}
