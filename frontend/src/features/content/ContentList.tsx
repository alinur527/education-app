import { useState } from 'react';
import { Link } from 'react-router';
import { useApp } from '../../state';
import { useResource } from '../../hooks';
import { Loading, ErrorState, Empty, PageHeading } from '../../components';
import { useL, Field, Pager } from '../shared';
import { contentPage, kinds, kindLabels, statusLabels } from './model';

export default function ContentList() {
  const l = useL(),
    { user } = useApp();
  const [kind, setKind] = useState(''),
    [status, setStatus] = useState(''),
    [q, setQ] = useState(''),
    [page, setPage] = useState(0);
  const resource = useResource(
    `/cms/content?kind=${kind}&status=${status}&q=${encodeURIComponent(q)}&page=${page}`,
    contentPage,
  );
  const allowed =
    user?.role === 'TEACHER'
      ? kinds.filter((k) => !['SUBJECT', 'TOPIC', 'THEORY', 'QUESTION'].includes(k))
      : kinds;
  return (
    <>
      <PageHeading
        title={l('Учебные материалы', 'Оқу материалдары')}
        body={l(
          'Создавайте содержание, проверяйте переводы и публикуйте готовые версии.',
          'Мазмұн жасаңыз, аудармаларды тексеріңіз және дайын нұсқаларды жариялаңыз.',
        )}
      />
      <div className="workspace-toolbar">
        <Field label={l('Поиск материалов', 'Материалдарды іздеу')}>
          <input
            type="search"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label={l('Тип материала', 'Материал түрі')}>
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value);
              setPage(0);
            }}
          >
            <option value="">{l('Все типы', 'Барлық түрлері')}</option>
            {allowed.map((k) => (
              <option key={k} value={k}>
                {l(...kindLabels[k])}
              </option>
            ))}
          </select>
        </Field>
        <Field label={l('Статус', 'Күйі')}>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">{l('Все статусы', 'Барлық күйлер')}</option>
            {Object.entries(statusLabels).map(([k, v]) => (
              <option key={k} value={k}>
                {l(...v)}
              </option>
            ))}
          </select>
        </Field>
        <Link className="button" to={`/workspace/content/new${kind ? `?kind=${kind}` : ''}`}>
          + {l('Создать материал', 'Материал жасау')}
        </Link>
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : resource.data?.items.length ? (
        <>
          <div
            className="table-region"
            role="region"
            aria-label={l('Материалы', 'Материалдар')}
            tabIndex={0}
          >
            <table>
              <thead>
                <tr>
                  <th>{l('Название', 'Атауы')}</th>
                  <th>{l('Тип', 'Түрі')}</th>
                  <th>{l('Статус', 'Күйі')}</th>
                  <th>{l('Версия', 'Нұсқа')}</th>
                  <th>{l('Перевод', 'Аударма')}</th>
                </tr>
              </thead>
              <tbody>
                {resource.data.items.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link className="text-link" to={`/workspace/content/${c.id}`}>
                        {l(c.titleRu, c.titleKz)}
                      </Link>
                    </td>
                    <td>{l(...kindLabels[c.kind])}</td>
                    <td>
                      <span className={`status-badge status-${c.status.toLowerCase()}`}>
                        {l(...statusLabels[c.status])}
                      </span>
                    </td>
                    <td>{c.version}</td>
                    <td>{c.titleKz ? 'RU / KZ' : l('Нужен KZ', 'KZ қажет')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager page={page} total={resource.data.total} onChange={setPage} />
        </>
      ) : (
        <Empty
          title={l('Материалов пока нет', 'Материалдар әлі жоқ')}
          body={l(
            'Создайте первый черновик или измените фильтры.',
            'Алғашқы жобаны жасаңыз немесе сүзгілерді өзгертіңіз.',
          )}
        />
      )}
    </>
  );
}
