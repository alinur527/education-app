import { useEffect, useState, useRef } from 'react';
import { useNavigate, useParams, useSearchParams, useBlocker } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useApp } from '../../state';
import { useResource } from '../../hooks';
import { Loading, ErrorState, PageHeading, ConfirmDialog } from '../../components';
import { useL, useAction, Feedback, Field } from '../shared';
import {
  contentSchema,
  contentPage,
  kinds,
  kindLabels,
  statusLabels,
  blankQuestion,
  materialsSchema,
  type Content,
  type Kind,
  type Payload,
} from './model';
import { BlockEditor, RenderedContent } from './Blocks';
import { QuestionEditor, QuizEditor } from './QuestionEditor';
import { Materials } from './Materials';
import Enrollment from '../teacher/Enrollment';

const historySchema = z.array(
  z.object({
    actorId: z.string().nullable(),
    actorName: z.string(),
    operation: z.string(),
    revision: z.number().nullable(),
    createdAt: z.string(),
  }),
);
export default function ContentEditor() {
  const { id } = useParams();
  return id && id !== 'new' ? <Existing id={id} /> : <Editor />;
}
function Existing({ id }: { id: string }) {
  const r = useResource(`/cms/content/${id}`, contentSchema);
  return r.loading ? (
    <Loading />
  ) : r.error ? (
    <ErrorState error={r.error} retry={r.reload} />
  ) : r.data ? (
    <Editor key={`${id}:${r.data.version}`} initial={r.data} reload={r.reload} />
  ) : null;
}
function Editor({ initial, reload }: { initial?: Content; reload?: () => void }) {
  const l = useL(),
    { user } = useApp(),
    navigate = useNavigate(),
    [params] = useSearchParams(),
    action = useAction();
  const initialKind = kinds.includes(params.get('kind') as Kind)
    ? (params.get('kind') as Kind)
    : user?.role === 'TEACHER'
      ? 'COURSE'
      : 'SUBJECT';
  const [kind, setKind] = useState<Kind>(initial?.kind || initialKind),
    [parent, setParent] = useState(initial?.parentId || params.get('parent') || ''),
    [p, setP] = useState<Payload>(initial?.payload || fresh(initialKind)),
    [preview, setPreview] = useState(false),
    [dirty, setDirty] = useState(false),
    [parentSearch, setParentSearch] = useState('');
  const allowNavigation = useRef(false);
  const blocker = useBlocker(() => dirty && !allowNavigation.current);
  const parentKind = (
    {
      TOPIC: 'SUBJECT',
      THEORY: 'TOPIC',
      QUESTION: 'TOPIC',
      MODULE: 'COURSE',
      LESSON: 'MODULE',
      QUIZ: 'LESSON',
      ASSIGNMENT: 'LESSON',
    } as Partial<Record<Kind, Kind>>
  )[kind];
  const parents = useResource(
    `/cms/content?kind=${parentKind || 'COURSE'}&size=100&q=${encodeURIComponent(parentSearch)}`,
    contentPage,
  );
  const [attachments, setAttachments] = useState<z.infer<typeof materialsSchema>>([]);
  useEffect(() => {
    if (!initial) return;
    let live = true;
    request(`/content/${initial.id}/materials`, materialsSchema)
      .then((v) => {
        if (live) setAttachments(v);
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [initial]);
  useEffect(() => {
    if (!dirty) return;
    const before = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', before);
    return () => window.removeEventListener('beforeunload', before);
  }, [dirty]);
  function change(next: Payload) {
    setP(next);
    setDirty(true);
  }
  function update<K extends keyof Payload>(key: K, value: Payload[K]) {
    change({ ...p, [key]: value });
  }
  const allowed =
    user?.role === 'TEACHER'
      ? kinds.filter((k) => !['SUBJECT', 'TOPIC', 'THEORY', 'QUESTION'].includes(k))
      : kinds;
  async function save() {
    const body = {
      kind,
      parentId: parent || null,
      payload: p,
      ...(initial ? { version: initial.version } : {}),
    };
    const c = await request(
      initial ? `/cms/content/${initial.id}` : '/cms/content',
      contentSchema,
      { method: initial ? 'PUT' : 'POST', body },
    );
    setDirty(false);
    allowNavigation.current = true;
    if (initial) reload?.();
    else navigate(`/workspace/content/${c.id}`, { replace: true });
  }
  async function transition(status: string) {
    if (!initial) return;
    await request(`/cms/content/${initial.id}/transition`, contentSchema, {
      method: 'POST',
      body: { status, version: initial.version },
    });
    reload?.();
  }
  return (
    <>
      <PageHeading
        title={
          initial
            ? l('Редактор материала', 'Материал редакторы')
            : l('Новый материал', 'Жаңа материал')
        }
        back={{ to: '/workspace/content', label: l('Все материалы', 'Барлық материалдар') }}
      />
      <div className="editor-toolbar">
        {initial && (
          <span className={`status-badge status-${initial.status.toLowerCase()}`}>
            {l(...statusLabels[initial.status])} · {l('версия', 'нұсқа')} {initial.version}
          </span>
        )}
        <span>
          {dirty
            ? l('Есть несохранённые изменения', 'Сақталмаған өзгерістер бар')
            : initial
              ? l('Все изменения сохранены', 'Барлық өзгерістер сақталды')
              : l('Новый черновик', 'Жаңа жоба')}
        </span>
        <button className="button secondary" onClick={() => setPreview(!preview)}>
          {preview
            ? l('Вернуться к редактированию', 'Өңдеуге оралу')
            : l('Предпросмотр', 'Алдын ала қарау')}
        </button>
      </div>
      {preview ? (
        <div className="preview-panel">
          <p className="notice">
            {l(
              'Предпросмотр черновика. Ученики видят только опубликованную версию.',
              'Жобаны алдын ала қарау. Оқушылар тек жарияланған нұсқаны көреді.',
            )}
          </p>
          <RenderedContent payload={p} materials={attachments} />
          {p.options && (
            <ul>
              {p.options.map((o) => (
                <li key={o.id}>
                  {o.id}. {l(o.textRu, o.textKz)}
                </li>
              ))}
            </ul>
          )}
          {p.questions?.map((q, i) => (
            <section key={i} className="question-card">
              <h3>
                {i + 1}. {l(q.titleRu, q.titleKz)}
              </h3>
              <ul>
                {q.options.map((o) => (
                  <li key={o.id}>
                    {o.id}. {l(o.textRu, o.textKz)}
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      ) : (
        <form
          className="editor-form"
          aria-busy={action.busy}
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(save);
          }}
        >
          <fieldset
            className="editor-lock"
            disabled={action.busy || initial?.status === 'ARCHIVED'}
          >
            {!initial && (
              <div className="form-pair">
                <Field label={l('Тип материала', 'Материал түрі')}>
                  <select
                    value={kind}
                    onChange={(e) => {
                      const next = e.target.value as Kind;
                      setKind(next);
                      setP(fresh(next));
                      setParent('');
                    }}
                  >
                    {allowed.map((k) => (
                      <option key={k} value={k}>
                        {l(...kindLabels[k])}
                      </option>
                    ))}
                  </select>
                </Field>
                {parentKind && (
                  <div>
                    <Field label={l('Найти родительский материал', 'Негізгі материалды іздеу')}>
                      <input
                        type="search"
                        value={parentSearch}
                        onChange={(e) => setParentSearch(e.target.value)}
                      />
                    </Field>
                    <Field label={l(...kindLabels[parentKind])}>
                      <select required value={parent} onChange={(e) => setParent(e.target.value)}>
                        <option value="">{l('Выберите', 'Таңдаңыз')}</option>
                        {parents.data?.items.map((c) => (
                          <option value={c.id} key={c.id}>
                            {l(c.titleRu, c.titleKz)}
                          </option>
                        ))}
                      </select>
                    </Field>
                  </div>
                )}
              </div>
            )}
            <section className="editor-section">
              <div className="section-heading">
                <h2>{l('Название и перевод', 'Атауы және аудармасы')}</h2>
                <span className="hint">
                  {p.titleKz
                    ? 'RU / KZ'
                    : l('Перевод KZ не заполнен', 'KZ аудармасы толтырылмаған')}
                </span>
              </div>
              <div className="form-pair">
                <Field label={l('Название RU', 'Атауы RU')}>
                  <input
                    required
                    maxLength={kind === 'QUESTION' ? 10000 : kind === 'SUBJECT' ? 200 : 300}
                    value={p.titleRu}
                    onChange={(e) => update('titleRu', e.target.value)}
                  />
                </Field>
                <Field label={l('Название KZ', 'Атауы KZ')}>
                  <input
                    maxLength={kind === 'QUESTION' ? 10000 : kind === 'SUBJECT' ? 200 : 300}
                    value={p.titleKz}
                    onChange={(e) => update('titleKz', e.target.value)}
                  />
                </Field>
              </div>
              {['TOPIC', 'COURSE', 'ASSIGNMENT', 'LESSON'].includes(kind) && (
                <div className="form-pair">
                  <Field label={l('Описание RU', 'Сипаттама RU')}>
                    <textarea
                      value={p.descriptionRu || ''}
                      onChange={(e) => update('descriptionRu', e.target.value)}
                    />
                  </Field>
                  <Field label={l('Описание KZ', 'Сипаттама KZ')}>
                    <textarea
                      value={p.descriptionKz || ''}
                      onChange={(e) => update('descriptionKz', e.target.value)}
                    />
                  </Field>
                </div>
              )}
              {['TOPIC', 'THEORY', 'MODULE', 'LESSON'].includes(kind) && (
                <Field label={l('Порядок', 'Реті')}>
                  <input
                    type="number"
                    min={0}
                    max={10000}
                    value={p.sortOrder || 0}
                    onChange={(e) => update('sortOrder', Number(e.target.value))}
                  />
                </Field>
              )}
              {kind === 'COURSE' && (
                <div className="form-pair">
                  <Field label={l('Значок курса', 'Курс белгішесі')}>
                    <input
                      maxLength={8}
                      placeholder="▤"
                      value={p.icon || ''}
                      onChange={(e) => update('icon', e.target.value)}
                    />
                  </Field>
                  <Field label={l('Видимость курса', 'Курстың көрінуі')}>
                    <select
                      value={p.visibility || 'PRIVATE'}
                      onChange={(e) => update('visibility', e.target.value as 'PUBLIC' | 'PRIVATE')}
                    >
                      <option value="PRIVATE">
                        {l('Только зачисленным', 'Тек тіркелгендерге')}
                      </option>
                      <option value="PUBLIC">{l('В каталоге', 'Каталогта')}</option>
                    </select>
                  </Field>
                  <label className="check-field">
                    <input
                      type="checkbox"
                      checked={p.selfEnroll || false}
                      onChange={(e) => update('selfEnroll', e.target.checked)}
                    />
                    {l('Разрешить самостоятельную запись', 'Өздігінен тіркелуге рұқсат беру')}
                  </label>
                </div>
              )}
              {kind === 'ASSIGNMENT' && (
                <div className="form-pair">
                  <Field label={l('Срок сдачи', 'Тапсыру мерзімі')}>
                    <input
                      type="datetime-local"
                      value={p.dueAt ? localDate(p.dueAt) : ''}
                      onChange={(e) =>
                        update(
                          'dueAt',
                          e.target.value ? new Date(e.target.value).toISOString() : '',
                        )
                      }
                    />
                  </Field>
                  <Field label={l('Максимальный балл', 'Ең жоғары балл')}>
                    <input
                      type="number"
                      required
                      min={1}
                      max={10000}
                      value={p.maxScore || 100}
                      onChange={(e) => update('maxScore', Number(e.target.value))}
                    />
                  </Field>
                </div>
              )}
            </section>
            {kind === 'QUESTION' && (
              <QuestionEditor
                value={{
                  ...blankQuestion(),
                  ...p,
                  explanationRu: p.explanationRu || '',
                  explanationKz: p.explanationKz || '',
                  options: p.options || blankQuestion().options,
                }}
                onChange={(q) => change({ ...p, ...q })}
              />
            )}
            {kind === 'QUIZ' && (
              <QuizEditor
                questions={p.questions || [blankQuestion()]}
                onChange={(v) => update('questions', v)}
              />
            )}
            {['THEORY', 'LESSON', 'ASSIGNMENT'].includes(kind) && (
              <>
                {(p.contentRu || p.contentKz) && (
                  <div className="form-pair">
                    <Field label={l('Исходный текст RU', 'Бастапқы мәтін RU')}>
                      <textarea
                        value={p.contentRu || ''}
                        onChange={(e) => update('contentRu', e.target.value)}
                      />
                    </Field>
                    <Field label={l('Исходный текст KZ', 'Бастапқы мәтін KZ')}>
                      <textarea
                        value={p.contentKz || ''}
                        onChange={(e) => update('contentKz', e.target.value)}
                      />
                    </Field>
                  </div>
                )}
                <BlockEditor
                  blocks={p.blocks}
                  onChange={(v) => update('blocks', v)}
                  materials={attachments}
                />
              </>
            )}
            <details className="source-details">
              <summary>{l('Происхождение материала', 'Материалдың шығу тегі')}</summary>
              <div className="form-pair">
                <Field label={l('Источник', 'Дереккөз')}>
                  <select
                    value={p.sourceType || 'EDITOR_CREATED'}
                    onChange={(e) => update('sourceType', e.target.value as Payload['sourceType'])}
                  >
                    {[
                      ['EDITOR_CREATED', 'Создан редактором', 'Редактор жасаған'],
                      ['OFFICIAL_SAMPLE', 'Официальный пример', 'Ресми үлгі'],
                      ['AI_GENERATED', 'Создан ИИ', 'ЖИ жасаған'],
                      ['IMPORTED', 'Импортирован', 'Импортталған'],
                    ].map(([v, r, k]) => (
                      <option key={v} value={v}>
                        {l(r, k)}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label={l('Название источника', 'Дереккөз атауы')}>
                  <input
                    maxLength={300}
                    value={p.sourceName || ''}
                    onChange={(e) => update('sourceName', e.target.value)}
                  />
                </Field>
                <Field label={l('Ссылка на источник', 'Дереккөз сілтемесі')}>
                  <input
                    type="url"
                    value={p.sourceUrl || ''}
                    onChange={(e) => update('sourceUrl', e.target.value)}
                  />
                </Field>
                <Field label={l('Год', 'Жыл')}>
                  <input
                    type="number"
                    min={1900}
                    max={2200}
                    value={p.year || ''}
                    onChange={(e) => update('year', e.target.value ? Number(e.target.value) : null)}
                  />
                </Field>
              </div>
              <label className="check-field">
                <input
                  type="checkbox"
                  checked={p.verified || false}
                  onChange={(e) => update('verified', e.target.checked)}
                />
                {l('Содержание проверено редактором', 'Мазмұнды редактор тексерді')}
              </label>
            </details>
            <div className="editor-actions">
              <button className="button" disabled={action.busy || initial?.status === 'ARCHIVED'}>
                {l('Сохранить черновик', 'Жобаны сақтау')}
              </button>
              {initial && (
                <>
                  <button
                    type="button"
                    className="button secondary"
                    disabled={action.busy || dirty || initial.status !== 'DRAFT'}
                    onClick={() => void action.run(() => transition('REVIEW'))}
                  >
                    {l('На проверку', 'Тексеруге жіберу')}
                  </button>
                  <button
                    type="button"
                    className="button secondary"
                    disabled={action.busy || dirty || initial.status !== 'REVIEW'}
                    onClick={() => void action.run(() => transition('PUBLISHED'))}
                  >
                    {l('Опубликовать', 'Жариялау')}
                  </button>
                  {user?.role === 'ADMIN' && initial.status !== 'ARCHIVED' && (
                    <button
                      type="button"
                      className="text-button"
                      disabled={action.busy || dirty}
                      onClick={() => void action.run(() => transition('ARCHIVED'))}
                    >
                      {l('В архив', 'Мұрағатқа')}
                    </button>
                  )}
                </>
              )}
            </div>
            <Feedback action={action} />
          </fieldset>
        </form>
      )}
      {initial && ['TOPIC', 'THEORY', 'COURSE', 'LESSON', 'ASSIGNMENT'].includes(kind) && (
        <Materials
          contentId={initial.id}
          editable={initial.status !== 'ARCHIVED'}
          onUploaded={(m) => setAttachments((a) => [...a, m])}
        />
      )}
      {!initial && (
        <p className="hint">
          {l(
            'Сначала сохраните черновик, затем добавьте файлы.',
            'Алдымен жобаны сақтаңыз, содан кейін файлдарды қосыңыз.',
          )}
        </p>
      )}
      {initial?.status === 'ARCHIVED' && user?.role === 'ADMIN' && (
        <button
          className="button secondary"
          disabled={action.busy}
          onClick={() => void action.run(() => transition('DRAFT'))}
        >
          {l('Восстановить черновик', 'Жобаны қалпына келтіру')}
        </button>
      )}
      {initial && kind === 'COURSE' && (user?.role === 'ADMIN' || user?.role === 'TEACHER') && (
        <Enrollment courseId={initial.id} />
      )}
      {initial && <History id={initial.id} />}
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        onOpenChange={(open) => {
          if (!open && blocker.state === 'blocked') blocker.reset();
        }}
        title={l('Уйти без сохранения?', 'Сақтамай шығасыз ба?')}
        body={l('Изменения в форме будут потеряны.', 'Пішіндегі өзгерістер жоғалады.')}
        confirm={l('Уйти', 'Шығу')}
        onConfirm={() => {
          if (blocker.state === 'blocked') blocker.proceed();
        }}
      />
    </>
  );
}
function fresh(kind: Kind): Payload {
  return {
    titleRu: '',
    titleKz: '',
    blocks: [],
    sourceType: 'EDITOR_CREATED',
    ...(kind === 'QUESTION' ? { ...blankQuestion() } : {}),
    ...(kind === 'QUIZ' ? { questions: [blankQuestion()] } : {}),
    ...(kind === 'COURSE' ? { visibility: 'PRIVATE', selfEnroll: false } : {}),
  };
}
function localDate(value: string) {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
function History({ id }: { id: string }) {
  const l = useL(),
    r = useResource(`/cms/content/${id}/history`, historySchema);
  return (
    <details className="source-details">
      <summary>{l('История изменений', 'Өзгерістер тарихы')}</summary>
      {r.data?.map((e, i) => (
        <p key={i}>
          {new Date(e.createdAt).toLocaleString()} · {e.actorName || l('Система', 'Жүйе')} ·{' '}
          {e.operation in statusLabels
            ? l(...statusLabels[e.operation as keyof typeof statusLabels])
            : e.operation === 'CREATE_DRAFT'
              ? l('Создан черновик', 'Жоба жасалды')
              : l('Сохранён черновик', 'Жоба сақталды')}{' '}
          · {l('версия', 'нұсқа')} {e.revision}
        </p>
      ))}
    </details>
  );
}
