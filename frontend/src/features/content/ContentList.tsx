import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import BulkActions from './BulkActions';
import { useApp } from '../../state';
import { useResource } from '../../hooks';
import { Loading, ErrorState, Empty, PageHeading } from '../../components';
import { useL, Field, Pager } from '../shared';
import {
  contentPage,
  contentSchema,
  kinds,
  kindLabels,
  statusLabels,
  type Content,
  type Kind,
} from './model';
import { childKinds, ParentTrail } from './AuthoringGuide';
import './authoring.css';

export default function ContentList() {
  const [params] = useSearchParams();
  const parentId = params.get('parentId');
  return parentId ? <ScopedList key={parentId} id={parentId} /> : <List key="all" />;
}

function ScopedList({ id }: { id: string }) {
  const parent = useResource(`/cms/content/${id}`, contentSchema);
  if (parent.loading) return <Loading />;
  if (parent.error) return <ErrorState error={parent.error} retry={parent.reload} />;
  return parent.data ? <List parent={parent.data} /> : null;
}

function List({ parent }: { parent?: Content }) {
  const l = useL(),
    { user } = useApp(),
    [params, setParams] = useSearchParams();
  const kind = kinds.includes(params.get('kind') as Kind) ? params.get('kind') || '' : '';
  const status = params.get('status') || '',
    q = params.get('q') || '',
    missingTranslation = params.get('missingTranslation') === 'true';
  const page = Math.max(0, Number.parseInt(params.get('page') || '0') || 0);
  const selectionKey = params.toString();
  const [selection, setSelection] = useState({ key: '', ids: [] as string[] });
  const selected = selection.key === selectionKey ? selection.ids : [];
  const choose = (ids: string[]) => setSelection({ key: selectionKey, ids });
  function filter(key: string, value: string) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (value) next.set(key, value);
        else next.delete(key);
        if (key !== 'page') next.delete('page');
        return next;
      },
      { replace: key === 'q' },
    );
  }
  const resource = useResource(
    `/cms/content?kind=${kind}&status=${encodeURIComponent(status)}&q=${encodeURIComponent(q)}&page=${page}${parent ? `&parentId=${parent.id}` : ''}${missingTranslation ? '&missingTranslation=true' : ''}`,
    contentPage,
  );
  const allowed = parent
    ? childKinds[parent.kind] || []
    : user?.role === 'TEACHER'
      ? kinds.filter((k) => !['SUBJECT', 'TOPIC', 'THEORY', 'QUESTION', 'CONTEXT'].includes(k))
      : kinds;
  const creationKind = kind || (parent ? allowed[0] : '');
  const create = new URLSearchParams();
  if (creationKind) create.set('kind', creationKind);
  if (parent) create.set('parent', parent.id);
  return (
    <>
      <PageHeading
        title={
          parent ? l(parent.titleRu, parent.titleKz) : l('Учебные материалы', 'Оқу материалдары')
        }
        body={
          parent
            ? l(
                'Содержание этого раздела. Откройте материал для редактирования или продолжите дерево.',
                'Осы бөлімнің мазмұны. Материалды өңдеу үшін ашыңыз не құрылымды жалғастырыңыз.',
              )
            : l(
                'Начните с предмета или курса, затем добавьте темы, уроки, теорию и практику.',
                'Пәннен не курстан бастап, тақырып, сабақ, теория және жаттығу қосыңыз.',
              )
        }
        {...(parent
          ? {
              back: {
                to: `/workspace/content/${parent.id}`,
                label: l('Редактор раздела', 'Бөлім редакторы'),
              },
            }
          : {})}
      />
      {parent?.parentId && <ParentTrail id={parent.parentId} />}
      {parent && (
        <p>
          <Link to="/workspace/content">{l('Все материалы', 'Барлық материалдар')}</Link>
        </p>
      )}
      {!parent && (
        <div className="authoring-starts">
          {user?.role !== 'TEACHER' && (
            <Link className="button secondary" to="/workspace/content/new?kind=SUBJECT">
              + {l('Предмет ЕНТ', 'ҰБТ пәні')}
            </Link>
          )}
          <Link className="button secondary" to="/workspace/content/new?kind=COURSE">
            + {l('Дополнительный курс', 'Қосымша курс')}
          </Link>
          <button className="text-button" onClick={() => filter('status', 'REVIEW')}>
            {l('Очередь проверки', 'Тексеру кезегі')}
          </button>
          <p>
            {l(
              'Внутри предмета: темы → теория и вопросы. Внутри курса: модули → уроки → тесты и задания.',
              'Пән ішінде: тақырыптар → теория мен сұрақтар. Курс ішінде: модульдер → сабақтар → тесттер мен тапсырмалар.',
            )}
          </p>
        </div>
      )}
      <div className="workspace-toolbar">
        <Field label={l('Поиск материалов', 'Материалдарды іздеу')}>
          <input type="search" value={q} onChange={(e) => filter('q', e.target.value)} />
        </Field>
        <Field label={l('Тип материала', 'Материал түрі')}>
          <select value={kind} onChange={(e) => filter('kind', e.target.value)}>
            <option value="">{l('Все типы', 'Барлық түрлері')}</option>
            {allowed.map((k) => (
              <option key={k} value={k}>
                {l(...kindLabels[k])}
              </option>
            ))}
          </select>
        </Field>
        <Field label={l('Статус', 'Күйі')}>
          <select value={status} onChange={(e) => filter('status', e.target.value)}>
            <option value="">{l('Все статусы', 'Барлық күйлер')}</option>
            {Object.entries(statusLabels).map(([k, v]) => (
              <option key={k} value={k}>
                {l(...v)}
              </option>
            ))}
          </select>
        </Field>
        {(!parent || (allowed.length > 0 && parent.status !== 'ARCHIVED')) && (
          <Link className="button" to={`/workspace/content/new?${create}`}>
            + {l('Создать материал', 'Материал жасау')}
          </Link>
        )}
      </div>
      <label className="check-field">
        <input
          type="checkbox"
          checked={missingTranslation}
          onChange={(e) => filter('missingTranslation', e.target.checked ? 'true' : '')}
        />
        {l('Без перевода названия KZ', 'Атауы KZ тіліне аударылмаған')}
      </label>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : resource.data?.items.length ? (
        <>
          <p className="hint">
            {l('Найдено материалов', 'Табылған материалдар')}: {resource.data.total}.{' '}
            {l(
              'Статус перевода в таблице относится к названию; содержание проверяется в редакторе.',
              'Кестедегі аударма күйі атауға қатысты; мазмұн редакторда тексеріледі.',
            )}
          </p>
          {user?.role !== 'TEACHER' && (
            <BulkActions
              items={resource.data.items
                .filter((c) => selected.includes(c.id))
                .map((c) => ({ id: c.id, version: c.version }))}
              reload={() => {
                choose([]);
                resource.reload();
              }}
            />
          )}
          <div
            className="table-region"
            role="region"
            aria-label={l('Материалы', 'Материалдар')}
            tabIndex={0}
          >
            <table>
              <thead>
                <tr>
                  <th scope="col">
                    {user?.role !== 'TEACHER' && (
                      <input
                        type="checkbox"
                        aria-label={l('Выбрать страницу', 'Бетті таңдау')}
                        checked={resource.data.items.every((c) => selected.includes(c.id))}
                        onChange={(e) =>
                          choose(e.target.checked ? resource.data!.items.map((c) => c.id) : [])
                        }
                      />
                    )}{' '}
                    {l('Название', 'Атауы')}
                  </th>
                  <th scope="col">{l('Тип', 'Түрі')}</th>
                  <th scope="col">{l('Статус', 'Күйі')}</th>
                  <th scope="col">{l('Версия', 'Нұсқа')}</th>
                  <th scope="col">{l('Перевод названия', 'Атаудың аудармасы')}</th>
                </tr>
              </thead>
              <tbody>
                {resource.data.items.map((c) => (
                  <tr key={c.id}>
                    <td>
                      {user?.role !== 'TEACHER' && (
                        <input
                          type="checkbox"
                          aria-label={`${l('Выбрать', 'Таңдау')}: ${l(c.titleRu, c.titleKz)}`}
                          checked={selected.includes(c.id)}
                          onChange={(e) =>
                            choose(
                              e.target.checked
                                ? [...selected, c.id]
                                : selected.filter((id) => id !== c.id),
                            )
                          }
                        />
                      )}
                      <Link className="text-link" to={`/workspace/content/${c.id}`}>
                        {l(c.titleRu, c.titleKz)}
                      </Link>
                      {childKinds[c.kind] && (
                        <small>
                          <Link to={`/workspace/content?parentId=${c.id}`}>
                            {l('Открыть содержание', 'Мазмұнын ашу')}
                          </Link>
                        </small>
                      )}
                    </td>
                    <td>{l(...kindLabels[c.kind])}</td>
                    <td>
                      <span className={`status-badge status-${c.status.toLowerCase()}`}>
                        {l(...statusLabels[c.status])}
                      </span>
                    </td>
                    <td>{c.version}</td>
                    <td>{c.titleKz.trim() ? 'RU / KZ' : l('Нужен KZ', 'KZ қажет')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={page}
            total={resource.data.total}
            onChange={(value) => filter('page', String(value))}
          />
        </>
      ) : (
        <Empty
          title={l('Материалы не найдены', 'Материалдар табылмады')}
          body={l(
            'Измените фильтры или создайте черновик в этом разделе.',
            'Сүзгілерді өзгертіңіз не осы бөлімде жоба жасаңыз.',
          )}
        />
      )}
    </>
  );
}
