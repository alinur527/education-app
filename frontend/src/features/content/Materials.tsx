import { useState, useEffect, type DragEvent } from 'react';
import { request, TOKEN_KEY, ApiError } from '../../api';
import { useResource } from '../../hooks';
import { ErrorState, Loading } from '../../components';
import { useL, useAction, Feedback, Field } from '../shared';
import { materialsSchema, materialSchema, type Material } from './model';

export async function materialBlob(material: Material) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const res = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || ''}/api/materials/${material.id}/download`,
    { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(30000) },
  );
  if (!res.ok) {
    if (res.status === 401 && token === sessionStorage.getItem(TOKEN_KEY)) {
      sessionStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event('session-expired'));
    }
    throw new ApiError(res.status, `HTTP_${res.status}`);
  }
  return res.blob();
}
export async function downloadMaterial(material: Material) {
  const url = URL.createObjectURL(await materialBlob(material)),
    a = document.createElement('a');
  a.href = url;
  a.download = material.originalFileName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function MaterialImage({ material }: { material: Material }) {
  const l = useL(),
    [url, setUrl] = useState(''),
    [error, setError] = useState<unknown>(null);
  useEffect(() => {
    let live = true,
      objectUrl = '';
    materialBlob(material)
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        if (live) setUrl(objectUrl);
        else URL.revokeObjectURL(objectUrl);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [material]);
  return error ? (
    <ErrorState error={error} />
  ) : url ? (
    <figure className="material-image">
      <img src={url} alt={l(material.titleRu, material.titleKz)} />
      <figcaption>{l(material.titleRu, material.titleKz)}</figcaption>
    </figure>
  ) : (
    <Loading />
  );
}
export function MaterialList({ items }: { items: Material[] }) {
  const l = useL(),
    action = useAction();
  return (
    <>
      <ul className="material-list">
        {items.map((m) => (
          <li key={m.id}>
            <span>
              <strong>{l(m.titleRu, m.titleKz)}</strong>
              <small>
                {m.originalFileName} · {Math.ceil(m.size / 1024)} {l('КБ', 'КБ')}
                {!m.published && ` · ${l('Черновик', 'Жоба')}`}
              </small>
            </span>
            <button
              className="button secondary"
              disabled={action.busy}
              onClick={() => void action.run(() => downloadMaterial(m))}
            >
              {l('Скачать', 'Жүктеу')}
            </button>
          </li>
        ))}
      </ul>
      <Feedback action={action} />
    </>
  );
}
export function Materials({
  contentId,
  editable = false,
  onUploaded,
}: {
  contentId: string;
  editable?: boolean;
  onUploaded?: (m: Material) => void;
}) {
  const l = useL(),
    resource = useResource(`/content/${contentId}/materials`, materialsSchema),
    action = useAction();
  const [file, setFile] = useState<File | null>(null),
    [drag, setDrag] = useState(false);
  function drop(e: DragEvent) {
    e.preventDefault();
    setDrag(false);
    setFile(e.dataTransfer.files[0] || null);
  }
  if (!editable && resource.data?.length === 0) return null;
  return (
    <section className="editor-section">
      <h2>{l('Материалы', 'Материалдар')}</h2>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : (
        resource.data && <MaterialList items={resource.data} />
      )}
      {editable && (
        <form
          className="editor-form"
          onSubmit={(e) => {
            e.preventDefault();
            const form = new FormData(e.currentTarget);
            if (!file) return;
            form.set('file', file);
            form.set('contentId', contentId);
            void action.run(async () => {
              const m = await request('/cms/materials', materialSchema, {
                method: 'POST',
                body: form,
              });
              setFile(null);
              resource.reload();
              onUploaded?.(m);
            });
          }}
        >
          <div
            className={`drop-zone ${drag ? 'dragging' : ''}`}
            onDragOver={(e) => {
              e.preventDefault();
              setDrag(true);
            }}
            onDragLeave={() => setDrag(false)}
            onDrop={drop}
          >
            <p>
              {l(
                'Перетащите файл сюда или выберите на устройстве',
                'Файлды осында сүйреңіз немесе құрылғыдан таңдаңыз',
              )}
            </p>
            <label className="file-picker">
              {l('Выбрать файл', 'Файл таңдау')}
              <input
                type="file"
                accept=".pdf,.docx,.pptx,.png,.jpg,.jpeg,.webp,.txt,.md"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
              />
            </label>
            <small>PDF, DOCX, PPTX, PNG, JPG, WebP, TXT, MD · {l('до 20 МБ', '20 МБ дейін')}</small>
            {file && <strong>{file.name}</strong>}
          </div>
          <div className="form-pair">
            <Field label={l('Название файла RU', 'Файл атауы RU')}>
              <input name="titleRu" required maxLength={300} />
            </Field>
            <Field label={l('Название файла KZ', 'Файл атауы KZ')}>
              <input name="titleKz" maxLength={300} />
            </Field>
          </div>
          <button className="button secondary" disabled={action.busy || !file}>
            {l('Загрузить файл', 'Файлды жүктеу')}
          </button>
          <p className="hint">
            {l(
              'Новые файлы станут доступны ученикам после публикации материала.',
              'Жаңа файлдар материал жарияланғаннан кейін оқушыларға қолжетімді болады.',
            )}
          </p>
          <Feedback action={action} />
        </form>
      )}
    </section>
  );
}
