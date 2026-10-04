import { useState, useEffect, useRef } from 'react';
import { useParams, Link, useBlocker } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState, Empty, ConfirmDialog } from '../../components';
import { useL, useAction, Field } from '../shared';
import { payloadSchema, savedSchema, materialsSchema } from '../content/model';
import { RenderedContent } from '../content/Blocks';
import { Materials } from '../content/Materials';
import { useStudyResource } from '../study/useStudyResource';
import {
  currentSubmissionSchema,
  submissionFilesSchema,
  submissionFileSchema,
  type CurrentSubmission,
} from '../submissions/model';
import {
  SubmissionFiles,
  SubmissionHistory,
  SubmissionFeedback,
} from '../submissions/SubmissionFiles';
const listSchema = z.array(
  z.object({
    id: z.string(),
    titleRu: z.string(),
    titleKz: z.string(),
    dueAt: z.string().nullable(),
    maxScore: z.number(),
    submittedAt: z.string().nullable(),
    score: z.number().nullable(),
  }),
);
const detailSchema = z.object({
  id: z.string(),
  content: payloadSchema,
  submission: currentSubmissionSchema.nullable(),
});
export default function Assignments() {
  const { id } = useParams();
  return id ? <Assignment id={id} /> : <AssignmentList />;
}
function AssignmentList() {
  const l = useL(),
    r = useResource('/assignments', listSchema);
  return (
    <>
      <PageHeading
        title={l('Мои задания', 'Менің тапсырмаларым')}
        back={{ to: '/courses', label: l('Курсы', 'Курстар') }}
      />
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : r.data?.length ? (
        <div className="course-grid">
          {r.data.map((a) => (
            <Link className="course-tile" key={a.id} to={`/assignments/${a.id}`}>
              <h2>{l(a.titleRu, a.titleKz)}</h2>
              <p>{a.dueAt ? new Date(a.dueAt).toLocaleString() : l('Без срока', 'Мерзімсіз')}</p>
              <strong>
                {a.score !== null
                  ? `${a.score} / ${a.maxScore}`
                  : a.submittedAt
                    ? l('Ответ отправлен', 'Жауап жіберілді')
                    : l('Ожидает выполнения', 'Орындалуы күтілуде')}
              </strong>
            </Link>
          ))}
        </div>
      ) : (
        <Empty
          title={l('Заданий пока нет', 'Тапсырмалар әзірге жоқ')}
          body={l(
            'Назначенные вашей группе задания появятся здесь.',
            'Тобыңызға берілген тапсырмалар осында пайда болады.',
          )}
        />
      )}
    </>
  );
}
function Assignment({ id }: { id: string }) {
  const l = useL(),
    r = useStudyResource(`/assignments/${id}`, detailSchema),
    files = useResource(`/content/${id}/materials`, materialsSchema);
  if (!r.data && r.loading) return <Loading />;
  if (r.error) return <ErrorState error={r.error} retry={r.reload} />;
  const a = r.data!;
  return (
    <>
      <PageHeading
        title={l('Задание', 'Тапсырма')}
        back={{ to: '/assignments', label: l('Мои задания', 'Менің тапсырмаларым') }}
      />
      <div className="lesson-surface">
        <RenderedContent payload={a.content} materials={files.data || []} />
        {a.content.dueAt && (
          <p>
            {l('Срок сдачи', 'Тапсыру мерзімі')}: {new Date(a.content.dueAt).toLocaleString()}
          </p>
        )}
        <Materials contentId={id} />
        {a.submission?.score !== null && a.submission && (
          <aside className="learning-callout">
            <strong>
              {a.submission.score} / {a.content.maxScore || 100}
            </strong>
            <p>{a.submission.feedback}</p>
          </aside>
        )}
        <AssignmentAnswer
          key={id}
          id={id}
          current={a.submission}
          refreshing={r.loading}
          onSaved={r.reload}
        />
        <SubmissionHistory key={a.submission?.revision || 0} assignmentId={id} />
      </div>
    </>
  );
}
function AssignmentAnswer({
  id,
  current,
  refreshing,
  onSaved,
}: {
  id: string;
  current: CurrentSubmission | null;
  refreshing: boolean;
  onSaved: () => void;
}) {
  const l = useL(),
    action = useAction(),
    upload = useAction();
  const fileInput = useRef<HTMLInputElement>(null);
  const files = useStudyResource('/assignments/' + id + '/files', submissionFilesSchema);
  const [text, setText] = useState(current?.text || ''),
    [selected, setSelected] = useState<string[]>(current?.files.map((f) => f.id) || []),
    [requestKey, setRequestKey] = useState(() => crypto.randomUUID()),
    [uploadKey, setUploadKey] = useState(() => crypto.randomUUID()),
    [file, setFile] = useState<File | null>(null),
    [dirty, setDirty] = useState(false);
  const blocker = useBlocker(dirty);
  useEffect(() => {
    if (!dirty) return;
    const stop = (e: BeforeUnloadEvent) => e.preventDefault();
    window.addEventListener('beforeunload', stop);
    return () => window.removeEventListener('beforeunload', stop);
  }, [dirty]);
  function select(id: string, checked: boolean) {
    setSelected((old) => (checked ? [...new Set([...old, id])] : old.filter((v) => v !== id)));
    setRequestKey(crypto.randomUUID());
    setDirty(true);
  }
  const known = files.data || current?.files || [];
  const unsafe = selected.some((id) => !known.some((f) => f.id === id && f.scanStatus === 'CLEAN'));
  return (
    <>
      <form
        className="editor-form"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            await request('/assignments/' + id + '/submit', savedSchema, {
              method: 'POST',
              body: { text, fileIds: selected, requestKey, revision: current?.revision || 0 },
            });
            setDirty(false);
            setRequestKey(crypto.randomUUID());
            onSaved();
            files.reload();
          });
        }}
      >
        <fieldset
          className="submission-fieldset"
          disabled={action.busy || upload.busy || refreshing}
        >
          <Field label={l('Ваш ответ', 'Сіздің жауабыңыз')}>
            <textarea
              rows={8}
              maxLength={20000}
              value={text}
              required={!selected.length}
              onChange={(e) => {
                setText(e.target.value);
                setRequestKey(crypto.randomUUID());
                setDirty(true);
              }}
            />
          </Field>
          <h3>{l('Файлы к ответу', 'Жауап файлдары')}</h3>
          <p className="hint">
            {l(
              'До 5 файлов, каждый до 20 МБ. Отметьте файлы, которые войдут в эту версию ответа. Скачать и отправить можно только после успешной антивирусной проверки.',
              '5 файлға дейін, әрқайсысы 20 МБ-ға дейін. Осы жауап нұсқасына кіретін файлдарды белгілеңіз. Сәтті антивирустық тексеруден кейін ғана жүктеп, жіберуге болады.',
            )}
          </p>
          {files.error ? (
            <ErrorState error={files.error} retry={files.reload} />
          ) : (
            <SubmissionFiles
              files={known}
              select={selected}
              onSelect={select}
              onChanged={files.reload}
              editable
              disabled={action.busy || upload.busy || refreshing}
            />
          )}
          <Field label={l('Выбрать файл к ответу', 'Жауап файлын таңдау')}>
            <input
              type="file"
              ref={fileInput}
              accept=".pdf,.docx,.pptx,.png,.jpg,.jpeg,.webp,.txt,.md"
              onChange={(e) => {
                setFile(e.target.files?.[0] || null);
                setUploadKey(crypto.randomUUID());
              }}
            />
          </Field>
          <button
            type="button"
            className="button secondary"
            disabled={!file || selected.length >= 5}
            onClick={() =>
              void upload.run(async () => {
                if (!file) return;
                const body = new FormData();
                body.set('file', file);
                body.set('requestKey', uploadKey);
                const uploaded = await request(
                  '/assignments/' + id + '/files',
                  submissionFileSchema,
                  { method: 'POST', body },
                );
                if (uploaded.scanStatus === 'CLEAN') select(uploaded.id, true);
                setFile(null);
                if (fileInput.current) fileInput.current.value = '';
                files.reload();
              })
            }
          >
            {upload.busy
              ? l('Загружаем и проверяем…', 'Жүктеліп, тексерілуде…')
              : l('Загрузить и проверить файл', 'Файлды жүктеп, тексеру')}
          </button>
          <SubmissionFeedback action={upload} />
          {current && (
            <p className="hint">
              {l(
                'Новая отправка создаст следующую версию. Прежний ответ, файлы и оценки останутся в истории; новая версия ожидает новой оценки.',
                'Қайта жіберу келесі нұсқаны жасайды. Бұрынғы жауап, файлдар мен бағалар тарихта сақталады; жаңа нұсқа жаңа бағалауды күтеді.',
              )}
            </p>
          )}
          <button className="button" disabled={unsafe || (!text.trim() && !selected.length)}>
            {l('Отправить ответ', 'Жауап жіберу')}
          </button>
        </fieldset>
        <SubmissionFeedback action={action} />
      </form>
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        onOpenChange={(open) => {
          if (!open && blocker.state === 'blocked') blocker.reset();
        }}
        title={l('Уйти без отправки?', 'Жібермей шығасыз ба?')}
        body={l(
          'Текст и выбор файлов в форме будут потеряны. Загруженные файлы останутся доступны в этом задании.',
          'Пішіндегі мәтін мен таңдалған файлдар жоғалады. Жүктелген файлдар осы тапсырмада қолжетімді қалады.',
        )}
        confirm={l('Уйти', 'Шығу')}
        onConfirm={() => {
          if (blocker.state === 'blocked') blocker.proceed();
        }}
      />
    </>
  );
}
