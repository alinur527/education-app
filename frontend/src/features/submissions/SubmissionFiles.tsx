import { useState } from 'react';
import { request, TOKEN_KEY, ApiError } from '../../api';
import { ConfirmDialog, ErrorState, Loading } from '../../components';
import { Feedback, Pager, useAction, useL } from '../shared';
import { savedSchema } from '../content/model';
import { useStudyResource } from '../study/useStudyResource';
import { submissionFileSchema, submissionHistorySchema, type SubmissionFile } from './model';
import './submissions.css';

export function SubmissionFeedback({ action }: { action: ReturnType<typeof useAction> }) {
  const l = useL();
  const messages: Record<string, [string, string]> = {
    FILE_NOT_CLEAN: [
      'Файл ещё не прошёл антивирусную проверку. Его нельзя отправить или скачать.',
      'Файл антивирустық тексеруден әлі өтпеді. Оны жіберуге немесе жүктеуге болмайды.',
    ],
    SUBMISSION_FILES_NOT_CLEAN: [
      'Вложения этой версии недоступны: нужна успешная проверка перед оценкой.',
      'Бұл нұсқаның тіркемелері қолжетімсіз: бағалау алдында сәтті тексеру керек.',
    ],
    REQUEST_KEY_REUSED: [
      'Этот запрос уже использован с другим содержимым. Обновите данные и повторите действие.',
      'Бұл сұрау басқа мазмұнмен пайдаланылған. Деректерді жаңартып, әрекетті қайталаңыз.',
    ],
    UPLOAD_QUOTA: [
      'Достигнут лимит хранилища или суточный лимит загрузок. Удалите ненужные неотправленные файлы либо повторите позже.',
      'Сақтау орнының немесе тәуліктік жүктеу шегіне жетті. Қажетсіз жіберілмеген файлдарды жойыңыз немесе кейін қайталаңыз.',
    ],
    STAGED_FILE_LIMIT: [
      'Сначала удалите ненужные неотправленные файлы (максимум 10).',
      'Алдымен қажетсіз жіберілмеген файлдарды жойыңыз (ең көбі 10).',
    ],
    FILE_INTEGRITY_ERROR: [
      'Файл не прошёл проверку целостности. Скачивание заблокировано.',
      'Файл тұтастық тексеруінен өтпеді. Жүктеу бұғатталды.',
    ],
    EMPTY_SUBMISSION: [
      'Добавьте текст или хотя бы один проверенный файл.',
      'Мәтін немесе кемінде бір тексерілген файл қосыңыз.',
    ],
  };
  const message = action.error instanceof ApiError ? messages[action.error.code] : undefined;
  return message ? (
    <p className="form-error" role="alert">
      {l(...message)}
    </p>
  ) : (
    <Feedback action={action} />
  );
}
export async function downloadSubmissionFile(file: SubmissionFile) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') || ''}/api/submission-files/${file.id}/download`,
    { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(30000) },
  );
  if (!response.ok) {
    if (response.status === 401 && token === sessionStorage.getItem(TOKEN_KEY)) {
      sessionStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event('session-expired'));
    }
    let code = `HTTP_${response.status}`;
    try {
      code = (await response.json()).code || code;
    } catch {
      /* Status remains available for non-JSON failures. */
    }
    throw new ApiError(response.status, code);
  }
  const url = URL.createObjectURL(await response.blob()),
    a = document.createElement('a');
  a.href = url;
  a.download = file.originalFileName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function SubmissionFiles({
  files,
  onChanged,
  select,
  onSelect,
  disabled = false,
  editable = false,
}: {
  files: SubmissionFile[];
  onChanged?: () => void;
  select?: string[];
  onSelect?: (id: string, checked: boolean) => void;
  disabled?: boolean;
  editable?: boolean;
}) {
  const l = useL(),
    action = useAction();
  const [deleting, setDeleting] = useState<SubmissionFile | null>(null);
  const labels: Record<SubmissionFile['scanStatus'], [string, string]> = {
    CLEAN: ['Проверен', 'Тексерілген'],
    PENDING: ['Ожидает проверки — отправка заблокирована', 'Тексеруді күтуде — жіберу бұғатталған'],
    UNSCANNED: [
      'Антивирус не настроен — отправка заблокирована',
      'Антивирус бапталмаған — жіберу бұғатталған',
    ],
    SCAN_FAILED: [
      'Ошибка проверки — отправка заблокирована',
      'Тексеру қатесі — жіберу бұғатталған',
    ],
    INFECTED: ['Обнаружена угроза — файл заблокирован', 'Қауіп анықталды — файл бұғатталған'],
  };
  return (
    <>
      <ul className="material-list submission-files">
        {files.map((file) => (
          <li key={file.id}>
            <span>
              {select && onSelect ? (
                <label className="submission-checkbox">
                  <input
                    type="checkbox"
                    checked={select.includes(file.id)}
                    disabled={
                      disabled ||
                      action.busy ||
                      file.scanStatus !== 'CLEAN' ||
                      (!select.includes(file.id) && select.length >= 5)
                    }
                    onChange={(e) => onSelect(file.id, e.target.checked)}
                  />
                  <strong>{file.originalFileName}</strong>
                </label>
              ) : (
                <strong>{file.originalFileName}</strong>
              )}
              <small>
                {Math.ceil(file.size / 1024)} {l('КБ', 'КБ')} · {l(...labels[file.scanStatus])}
                {file.bound && ` · ${l('Сохранён в истории ответа', 'Жауап тарихында сақталған')}`}
              </small>
            </span>
            <div className="button-row">
              <button
                type="button"
                className="button secondary small"
                disabled={disabled || action.busy || file.scanStatus !== 'CLEAN'}
                onClick={() => void action.run(() => downloadSubmissionFile(file))}
              >
                {l('Скачать', 'Жүктеу')}
              </button>
              {file.scanStatus !== 'CLEAN' && file.scanStatus !== 'INFECTED' && (
                <button
                  type="button"
                  className="button secondary small"
                  disabled={disabled || action.busy}
                  onClick={() =>
                    void action.run(async () => {
                      await request(
                        '/submission-files/' + file.id + '/scan',
                        submissionFileSchema,
                        { method: 'POST' },
                      );
                      onChanged?.();
                    })
                  }
                >
                  {l('Повторить проверку', 'Тексеруді қайталау')}
                </button>
              )}
              {editable && !file.bound && (
                <button
                  type="button"
                  className="button secondary small"
                  disabled={disabled || action.busy}
                  onClick={() => setDeleting(file)}
                >
                  {l('Удалить файл', 'Файлды жою')}
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
      <SubmissionFeedback action={action} />
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
        title={l('Удалить неотправленный файл?', 'Жіберілмеген файлды жою керек пе?')}
        body={l(
          'Файл ещё не относится к отправленному ответу.',
          'Файл әлі жіберілген жауапқа жатпайды.',
        )}
        confirm={l('Удалить', 'Жою')}
        onConfirm={() => {
          if (deleting)
            void action.run(async () => {
              await request('/submission-files/' + deleting.id, savedSchema, { method: 'DELETE' });
              onSelect?.(deleting.id, false);
              setDeleting(null);
              onChanged?.();
            });
        }}
      />
    </>
  );
}
export function SubmissionHistory({
  assignmentId,
  userId,
}: {
  assignmentId: string;
  userId?: string;
}) {
  const l = useL();
  const [open, setOpen] = useState(false);
  return (
    <details className="editor-section" onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary>{l('История ответов и оценок', 'Жауаптар мен бағалар тарихы')}</summary>
      {open && <HistoryBody assignmentId={assignmentId} userId={userId} />}
    </details>
  );
}
function HistoryBody({ assignmentId, userId }: { assignmentId: string; userId?: string }) {
  const l = useL(),
    [page, setPage] = useState(0);
  const history = useStudyResource(
    `/assignments/${assignmentId}/submission-history?page=${page}${userId ? '&userId=' + userId : ''}`,
    submissionHistorySchema,
  );
  return history.error ? (
    <ErrorState error={history.error} retry={history.reload} />
  ) : !history.data ? (
    <Loading />
  ) : (
    <>
      {history.data.items.length === 0 ? (
        <p>{l('Отправленных версий пока нет.', 'Жіберілген нұсқалар әзірге жоқ.')}</p>
      ) : (
        history.data.items.map((rev) => (
          <article key={rev.id} className="submission-panel">
            <h3>
              {l('Версия ответа', 'Жауап нұсқасы')} {rev.contentRevision} ·{' '}
              <time dateTime={rev.submittedAt}>{new Date(rev.submittedAt).toLocaleString()}</time>
            </h3>
            {rev.legacyImported && (
              <p className="hint">
                {l(
                  'Перенесена последняя доступная старая версия. Более ранние версии не сохранялись.',
                  'Соңғы қолжетімді ескі нұсқа көшірілді. Бұрынғы нұсқалар сақталмаған.',
                )}
              </p>
            )}
            <p className="plain-content">{rev.text}</p>
            <SubmissionFiles files={rev.files} onChanged={history.reload} />
            {rev.grades.map((grade, i) => (
              <div className="learning-callout" key={grade.gradedAt + ':' + i}>
                <strong>
                  {grade.score} / {grade.maxScore}
                </strong>{' '}
                · <time dateTime={grade.gradedAt}>{new Date(grade.gradedAt).toLocaleString()}</time>
                <p className="plain-content">{grade.feedback}</p>
              </div>
            ))}
          </article>
        ))
      )}
      {history.data.total > 10 && (
        <Pager page={page} size={10} total={history.data.total} onChange={setPage} />
      )}
    </>
  );
}
