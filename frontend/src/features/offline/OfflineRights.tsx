import { useState } from 'react';
import { request } from '../../api';
import { Field, Feedback, useAction, useL } from '../shared';
import { savedSchema, type Material } from '../content/model';
export default function OfflineRights({ files }: { files: Material[] }) {
  const l = useL(),
    action = useAction(),
    [id, setId] = useState(''),
    [basis, setBasis] = useState(''),
    [allowed, setAllowed] = useState(false);
  if (!files.length) return null;
  return (
    <details className="editor-section">
      <summary>
        {l('Права на сохранение файлов офлайн', 'Файлдарды офлайн сақтауға құқықтар')}
      </summary>
      <p>
        {l(
          'Разрешайте только публичные материалы, которые можно распространять и сохранять. Уже сохранённую копию нельзя отозвать с устройства без сети.',
          'Тек таратуға және сақтауға болатын жария материалдарға рұқсат беріңіз. Желісіз құрылғыдағы сақталған көшірмені қайтарып алу мүмкін емес.',
        )}
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            await request(`/cms/materials/${id}/offline-rights`, savedSchema, {
              method: 'POST',
              body: { allowed, basis },
            });
          });
        }}
      >
        <Field label={l('Файл для изменения прав', 'Құқықтары өзгертілетін файл')}>
          <select
            required
            value={id}
            onChange={(e) => {
              setId(e.target.value);
              setAllowed(false);
              setBasis('');
            }}
          >
            <option value="">{l('Выберите файл', 'Файлды таңдаңыз')}</option>
            {files.map((f) => (
              <option key={f.id} value={f.id}>
                {l(f.titleRu, f.titleKz)}
              </option>
            ))}
          </select>
        </Field>
        <label>
          <input type="checkbox" checked={allowed} onChange={(e) => setAllowed(e.target.checked)} />
          {l(
            'Разрешить сохранение этого файла на устройстве',
            'Бұл файлды құрылғыда сақтауға рұқсат беру',
          )}
        </label>
        <Field label={l('Основание / лицензия', 'Негіздеме / лицензия')}>
          <textarea
            required={allowed}
            maxLength={1000}
            value={basis}
            onChange={(e) => setBasis(e.target.value)}
          />
        </Field>
        <button className="button secondary" disabled={!id || action.busy}>
          {l('Сохранить права', 'Құқықтарды сақтау')}
        </button>
        <Feedback action={action} />
      </form>
    </details>
  );
}
