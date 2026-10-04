import { useEffect, useState } from 'react';
import { Link, useBlocker, useSearchParams } from 'react-router';
import { request } from '../../api';
import { ConfirmDialog, Empty, ErrorState, Loading, PageHeading } from '../../components';
import { Feedback, Field, Pager, useAction, useL } from '../shared';
import { noteSchema, notesSchema, savedSchema, searchSchema, type Note } from './model';
import { useStudyResource } from './useStudyResource';
import './study.css';
type Draft = Pick<
  Note,
  | 'id'
  | 'targetKind'
  | 'targetId'
  | 'title'
  | 'body'
  | 'bookmarked'
  | 'cardFront'
  | 'cardBack'
  | 'revision'
>;
function blank(kind: string, id: string, title: string): Draft {
  return {
    id: crypto.randomUUID(),
    targetKind: kind,
    targetId: id,
    title,
    body: '',
    bookmarked: false,
    cardFront: '',
    cardBack: '',
    revision: 0,
  };
}
export function NotesPage() {
  const l = useL(),
    [params] = useSearchParams();
  const [q, setQ] = useState(''),
    [search, setSearch] = useState(''),
    [bookmarked, setBookmarked] = useState(false),
    [due, setDue] = useState(params.get('review') === 'true'),
    [page, setPage] = useState(0),
    [draft, setDraft] = useState<Draft | null>(() => {
      const kind = params.get('kind'),
        id = params.get('target'),
        title = params.get('title');
      return (kind === 'TOPIC' || kind === 'LESSON') && id && /^[0-9a-f-]{36}$/i.test(id)
        ? blank(kind, id, (title || '').slice(0, 300))
        : null;
    }),
    [creating, setCreating] = useState(false);
  const notes = useStudyResource(
    `/study/notes?q=${encodeURIComponent(q)}&bookmarked=${bookmarked}&due=${due}&page=${page}`,
    notesSchema,
  );
  return (
    <div className="study-page">
      <PageHeading
        title={l('Заметки и карточки', 'Жазбалар мен карточкалар')}
        body={l(
          'Личные записи видны только вам. Карточки повторяют ваши формулы, даты и термины.',
          'Жеке жазбаларыңызды тек өзіңіз көресіз. Карточкалар өз формулаларыңызды, күндер мен терминдерді қайталауға арналған.',
        )}
      />
      <div className="study-nav">
        <Link to="/study">{l('Мой план', 'Менің жоспарым')}</Link>
      </div>
      {draft ? (
        <NoteEditor
          key={draft.id}
          initial={draft}
          onClose={() => setDraft(null)}
          onSaved={() => {
            notes.reload();
          }}
        />
      ) : (
        <>
          <div className="study-toolbar">
            <form
              className="study-inline-form"
              onSubmit={(e) => {
                e.preventDefault();
                setQ(search);
                setPage(0);
              }}
            >
              <Field label={l('Поиск в моих заметках', 'Жеке жазбалардан іздеу')}>
                <input
                  type="search"
                  value={search}
                  maxLength={200}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </Field>
              <button className="button secondary">{l('Найти', 'Іздеу')}</button>
            </form>
            <button
              className="button"
              aria-expanded={creating}
              onClick={() => setCreating((v) => !v)}
            >
              {l('Создать заметку', 'Жазба жасау')}
            </button>
          </div>
          <div className="study-filters">
            <label>
              <input
                type="checkbox"
                checked={bookmarked}
                onChange={(e) => {
                  setBookmarked(e.target.checked);
                  setPage(0);
                }}
              />
              {l('Только закладки', 'Тек бетбелгілер')}
            </label>
            <label>
              <input
                type="checkbox"
                checked={due}
                onChange={(e) => {
                  setDue(e.target.checked);
                  setPage(0);
                }}
              />
              {l('Карточки к повторению', 'Қайталауға арналған карточкалар')}
            </label>
          </div>
          {creating && (
            <ChooseMaterial
              onChoose={(kind, id, title) => {
                setDraft(blank(kind, id, title));
                setCreating(false);
              }}
            />
          )}
          {notes.error ? (
            <ErrorState error={notes.error} retry={notes.reload} />
          ) : !notes.data ? (
            <Loading />
          ) : notes.data.items.length === 0 ? (
            <Empty
              title={l('Здесь пока пусто', 'Әзірге бос')}
              body={l(
                'Создайте заметку к доступной теме или уроку. Для карточки добавьте вопрос и ответ.',
                'Қолжетімді тақырыпқа немесе сабаққа жазба жасаңыз. Карточка үшін сұрақ пен жауап қосыңыз.',
              )}
            />
          ) : (
            <div className="study-notes-list">
              {notes.data.items.map((note) => (
                <NoteItem
                  key={note.id + ':' + note.revision}
                  note={note}
                  onEdit={() => setDraft(note)}
                  reload={notes.reload}
                  due={due}
                />
              ))}
            </div>
          )}
          {notes.data && notes.data.total > 25 && (
            <Pager page={page} total={notes.data.total} onChange={setPage} />
          )}
        </>
      )}
    </div>
  );
}
function ChooseMaterial({
  onChoose,
}: {
  onChoose: (kind: string, id: string, title: string) => void;
}) {
  const l = useL(),
    action = useAction();
  const [q, setQ] = useState(''),
    [results, setResults] = useState<import('zod').infer<typeof searchSchema> | null>(null);
  return (
    <section className="study-panel">
      <h2>{l('К чему относится запись?', 'Жазба не туралы?')}</h2>
      <form
        className="study-inline-form"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            setResults(
              (await request('/search?q=' + encodeURIComponent(q), searchSchema)).filter(
                (r) => r.kind === 'TOPIC' || r.kind === 'LESSON',
              ),
            );
          });
        }}
      >
        <Field label={l('Название темы или урока', 'Тақырып немесе сабақ атауы')}>
          <input
            required
            minLength={2}
            maxLength={200}
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </Field>
        <button className="button secondary" disabled={action.busy}>
          {l('Найти материал', 'Материалды табу')}
        </button>
      </form>
      <Feedback action={action} />
      {results?.length === 0 ? (
        <p>
          {l(
            'Нет доступных тем и уроков по этому запросу.',
            'Бұл сұрау бойынша қолжетімді тақырыптар мен сабақтар жоқ.',
          )}
        </p>
      ) : (
        results?.map((r) => (
          <button
            className="study-search-result"
            key={r.id}
            onClick={() => onChoose(r.kind, r.id, l(r.titleRu, r.titleKz))}
          >
            {l(r.titleRu, r.titleKz)}
            <small>{r.kind === 'TOPIC' ? l('Тема', 'Тақырып') : l('Урок', 'Сабақ')} →</small>
          </button>
        ))
      )}
    </section>
  );
}
function NoteEditor({
  initial,
  onClose,
  onSaved,
}: {
  initial: Draft;
  onClose: () => void;
  onSaved: () => void;
}) {
  const l = useL(),
    action = useAction();
  const [draft, setDraft] = useState(initial),
    [dirty, setDirty] = useState(false),
    [closing, setClosing] = useState(false);
  const blocker = useBlocker(dirty);
  useEffect(() => {
    if (!dirty) return;
    const stop = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', stop);
    return () => window.removeEventListener('beforeunload', stop);
  }, [dirty]);
  function change(patch: Partial<Draft>) {
    setDraft({ ...draft, ...patch });
    setDirty(true);
  }
  return (
    <section className="study-panel">
      <h2>
        {initial.revision ? l('Моя запись', 'Менің жазбам') : l('Новая запись', 'Жаңа жазба')}
      </h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            const saved = await request('/study/notes/' + draft.id, noteSchema, {
              method: 'PUT',
              body: draft,
            });
            setDraft(saved);
            setDirty(false);
            onSaved();
          });
        }}
      >
        <fieldset className="study-form" disabled={action.busy}>
          <Field label={l('Заголовок заметки', 'Жазба тақырыбы')}>
            <input
              required
              maxLength={300}
              value={draft.title}
              onChange={(e) => change({ title: e.target.value })}
            />
          </Field>
          <Field label={l('Моя заметка', 'Менің жазбам')}>
            <textarea
              rows={7}
              maxLength={20000}
              value={draft.body}
              onChange={(e) => change({ body: e.target.value })}
            />
          </Field>
          <label className="study-checkbox">
            <input
              type="checkbox"
              checked={draft.bookmarked}
              onChange={(e) => change({ bookmarked: e.target.checked })}
            />
            {l('Добавить в закладки', 'Бетбелгілерге қосу')}
          </label>
          <div className="study-card-fields">
            <h3>{l('Карточка для повторения', 'Қайталау карточкасы')}</h3>
            <p>
              {l(
                'Необязательно. Заполните обе стороны своими словами; ответы не генерируются автоматически.',
                'Міндетті емес. Екі жағын да өз сөзіңізбен толтырыңыз; жауаптар автоматты түрде жасалмайды.',
              )}
            </p>
            <Field label={l('Вопрос / термин', 'Сұрақ / термин')}>
              <textarea
                rows={3}
                maxLength={4000}
                value={draft.cardFront}
                required={!!draft.cardBack.trim()}
                onChange={(e) => change({ cardFront: e.target.value })}
              />
            </Field>
            <Field label={l('Ответ / определение', 'Жауап / анықтама')}>
              <textarea
                rows={3}
                maxLength={4000}
                value={draft.cardBack}
                required={!!draft.cardFront.trim()}
                onChange={(e) => change({ cardBack: e.target.value })}
              />
            </Field>
          </div>
          <div className="study-actions">
            <button className="button">
              {action.busy
                ? l('Сохраняем…', 'Сақталуда…')
                : l('Сохранить запись', 'Жазбаны сақтау')}
            </button>
            <button
              type="button"
              className="button secondary"
              onClick={() => (dirty ? setClosing(true) : onClose())}
            >
              {l('К списку', 'Тізімге')}
            </button>
          </div>
        </fieldset>
        <Feedback action={action} />
      </form>
      <ConfirmDialog
        open={closing || blocker.state === 'blocked'}
        onOpenChange={(open) => {
          if (!open) {
            setClosing(false);
            if (blocker.state === 'blocked') blocker.reset();
          }
        }}
        title={l('Уйти без сохранения?', 'Сақтамай шығасыз ба?')}
        body={l('Текст в форме будет потерян.', 'Пішіндегі мәтін жоғалады.')}
        confirm={l('Уйти', 'Шығу')}
        onConfirm={() => {
          if (blocker.state === 'blocked') blocker.proceed();
          else onClose();
        }}
      />
    </section>
  );
}
function NoteItem({
  note,
  onEdit,
  reload,
  due,
}: {
  note: Note;
  onEdit: () => void;
  reload: () => void;
  due: boolean;
}) {
  const l = useL(),
    action = useAction();
  const [deleting, setDeleting] = useState(false),
    [revealed, setRevealed] = useState(false);
  return (
    <article className="study-note">
      <div className="study-list-heading">
        <h2>{note.title}</h2>
        {note.bookmarked && <span className="study-status">{l('Закладка', 'Бетбелгі')}</span>}
      </div>
      <p className="study-note-text">{note.body}</p>
      {note.available && note.url ? (
        <Link to={note.url}>{l('Открыть материал', 'Материалды ашу')} →</Link>
      ) : (
        <p className="study-unavailable">
          {l(
            'Материал недоступен. Ваш текст и карточка сохранены.',
            'Материал қолжетімсіз. Мәтініңіз бен карточкаңыз сақталды.',
          )}
        </p>
      )}
      {note.cardFront && (
        <section className="study-flashcard">
          <p className="study-task-meta">
            {l('Личная карточка', 'Жеке карточка')} · {l('Повторений', 'Қайталаулар')}:{' '}
            {note.reviewCount}
          </p>
          <h3>{note.cardFront}</h3>
          {revealed ? (
            <>
              <p className="study-note-text">{note.cardBack}</p>
              <p>{l('Насколько легко вспомнили?', 'Қаншалықты оңай еске түсірдіңіз?')}</p>
              <div className="study-actions">
                {(['AGAIN', 'GOOD', 'EASY'] as const).map((rating, i) => (
                  <button
                    className="button secondary small"
                    disabled={action.busy}
                    key={rating}
                    onClick={() =>
                      void action.run(async () => {
                        await request('/study/notes/' + note.id + '/review', noteSchema, {
                          method: 'POST',
                          body: { rating, revision: note.revision },
                        });
                        setRevealed(false);
                        reload();
                      })
                    }
                  >
                    {l(
                      ...[
                        ['Повторить завтра', 'Ертең қайталау'],
                        ['Вспомнил', 'Есіме түсті'],
                        ['Легко', 'Оңай'],
                      ][i],
                    )}
                  </button>
                ))}
              </div>
            </>
          ) : (
            <button className="button secondary" onClick={() => setRevealed(true)}>
              {l('Показать ответ', 'Жауапты көрсету')}
            </button>
          )}
          {!due && note.nextReviewAt && (
            <p className="study-muted">
              {l('Следующее повторение', 'Келесі қайталау')}:{' '}
              <time dateTime={note.nextReviewAt}>{note.nextReviewAt.slice(0, 10)}</time>
            </p>
          )}
        </section>
      )}
      <div className="study-actions">
        <button className="button secondary small" disabled={action.busy} onClick={onEdit}>
          {l('Редактировать', 'Өңдеу')}
        </button>
        <button
          className="button secondary small"
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              await request('/study/notes/' + note.id, noteSchema, {
                method: 'PUT',
                body: { ...note, bookmarked: !note.bookmarked },
              });
              reload();
            })
          }
        >
          {note.bookmarked ? l('Убрать закладку', 'Бетбелгіні жою') : l('В закладки', 'Бетбелгіге')}
        </button>
        <button
          className="button secondary small"
          disabled={action.busy}
          onClick={() => setDeleting(true)}
        >
          {l('Удалить', 'Жою')}
        </button>
      </div>
      <Feedback action={action} />
      <ConfirmDialog
        open={deleting}
        onOpenChange={setDeleting}
        title={l('Удалить личную запись?', 'Жеке жазбаны жою керек пе?')}
        body={l(
          'Заметка, закладка и карточка будут удалены. Учебный материал сохранится.',
          'Жазба, бетбелгі және карточка жойылады. Оқу материалы сақталады.',
        )}
        confirm={l('Удалить', 'Жою')}
        onConfirm={() =>
          void action.run(async () => {
            await request('/study/notes/' + note.id + '?revision=' + note.revision, savedSchema, {
              method: 'DELETE',
            });
            reload();
          })
        }
      />
    </article>
  );
}
