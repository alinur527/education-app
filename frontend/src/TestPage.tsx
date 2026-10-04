import { AnswerControls } from './features/assessment/AnswerControls';
import { completeAnswer, type Answer } from './features/assessment/model';
import { InlineText, RichText } from './features/content/RichText';
import { useL } from './features/shared';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Navigate, useBlocker, useNavigate, useParams } from 'react-router';
import { ArrowLeft, ArrowRight, Check, LogOut } from 'lucide-react';
import type { z } from 'zod';
import { request, sessionSchema, questionSchema, receiptSchema, finishSchema } from './api';
import { useApp, errorKey } from './state';
import { useResource } from './hooks';
import { ConfirmDialog, ErrorState, Loading } from './components';

export function TestPage() {
  const { sessionId } = useParams();
  const resource = useResource(`/tests/${sessionId}`, sessionSchema);
  if (resource.loading) return <Loading />;
  if (resource.error || !resource.data)
    return <ErrorState error={resource.error} retry={resource.reload} />;
  if (resource.data.status === 'COMPLETED')
    return <Navigate to={`/results/${sessionId}`} replace />;
  return <TestRunner session={resource.data} key={sessionId} />;
}
function TestRunner({ session }: { session: z.infer<typeof sessionSchema> }) {
  const { t, user } = useApp();
  const navigate = useNavigate();
  const l = useL();
  const [now, setNow] = useState(Date.now);
  const remaining = session.deadlineAt
    ? Math.max(0, Math.ceil((Date.parse(session.deadlineAt) - now) / 1000))
    : null;
  const [index, setIndex] = useState(Math.min(session.answers.length, session.totalQuestions - 1));
  const [saved, setSaved] = useState(session.answers);
  const [drafts, setDrafts] = useState<Record<string, Answer>>({});
  const resource = useResource(`/tests/${session.sessionId}/questions/${index}`, questionSchema);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [confirmFinish, setConfirmFinish] = useState(false);
  const allowLeave = useRef(false);
  const lock = useRef(false);
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      Boolean(user) && !allowLeave.current && currentLocation.pathname !== nextLocation.pathname,
  );
  useEffect(() => {
    const before = (event: BeforeUnloadEvent) => {
      if (!allowLeave.current) {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', before);
    return () => window.removeEventListener('beforeunload', before);
  }, []);
  const finish = useCallback(async () => {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(null);
    try {
      await request(`/tests/${session.sessionId}/finish`, finishSchema, { method: 'POST' });
      allowLeave.current = true;
      navigate(`/results/${session.sessionId}`, { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }, [navigate, session.sessionId]);
  useEffect(() => {
    if (!session.deadlineAt) return;
    const deadline = Date.parse(session.deadlineAt);
    const id = setInterval(() => {
      const at = Date.now();
      setNow(at);
      if (at >= deadline) void finish();
    }, 1000);
    return () => clearInterval(id);
  }, [session.deadlineAt, finish]);
  async function save(answer: Answer, timeSpentSecs: number) {
    if (!resource.data || lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(null);
    try {
      const receipt = await request(`/tests/${session.sessionId}/answers`, receiptSchema, {
        method: 'POST',
        body: {
          questionId: resource.data.questionId,
          ...(answer.selectedOptionId ? { selectedOptionId: answer.selectedOptionId } : { answer }),
          timeSpentSecs,
        },
      });
      setSaved((items) =>
        items.some((a) => a.questionId === receipt.questionId) ? items : [...items, receipt],
      );
      if (index < session.totalQuestions - 1) setIndex((i) => i + 1);
      else {
        lock.current = false;
        await finish();
      }
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  const q = resource.data;
  const existing = saved.find((a) => a.questionId === q?.questionId);
  return (
    <div className="test-workspace">
      <div className="test-top">
        <span>
          {session.practiceMode === 'MOCK_ENT'
            ? l('Сокращённая тренировка', 'Қысқартылған жаттығу')
            : t('practice')}
        </span>
        <button className="text-button" onClick={() => navigate('/')} disabled={busy}>
          <LogOut size={17} />
          {t('exit')}
        </button>
      </div>
      {remaining !== null && (
        <p role="timer" aria-label={l('Осталось времени', 'Қалған уақыт')}>
          {l('Осталось', 'Қалды')}: {Math.floor(remaining / 60)}:
          {String(remaining % 60).padStart(2, '0')}
        </p>
      )}
      <div className="test-progress">
        <span>
          {t('question')} {index + 1} {t('of')} {session.totalQuestions}
        </span>
        <span>
          {t('savedAnswers')}: {saved.length}
        </span>
        <progress
          value={saved.length}
          max={session.totalQuestions}
          aria-label={t('testProgress')}
        />
      </div>
      {session.questionIds && (
        <details className="question-map">
          <summary>
            {l('Карта вопросов', 'Сұрақтар картасы')} · {saved.length}/{session.totalQuestions}
          </summary>
          <nav aria-label={l('Перейти к вопросу', 'Сұраққа өту')}>
            {session.questionIds.map((id, i) => (
              <button
                type="button"
                key={id}
                disabled={busy}
                aria-current={index === i ? 'step' : undefined}
                className={saved.some((a) => a.questionId === id) ? 'answered' : ''}
                aria-label={`${l('Вопрос', 'Сұрақ')} ${i + 1}, ${saved.some((a) => a.questionId === id) ? l('ответ сохранён', 'жауап сақталды') : l('без ответа', 'жауап жоқ')}`}
                onClick={() => setIndex(i)}
              >
                {i + 1}
              </button>
            ))}
          </nav>
        </details>
      )}
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : (
        q && (
          <QuestionForm
            key={q.questionId}
            question={q}
            saved={
              existing
                ? existing.answer || { selectedOptionId: existing.selectedOptionId || '' }
                : undefined
            }
            busy={busy}
            draft={drafts[q.questionId]}
            onDraft={(answer) => setDrafts((d) => ({ ...d, [q.questionId]: answer }))}
            onSave={save}
          />
        )
      )}
      {error !== null && (
        <p role="alert" className="form-error">
          {t(errorKey(error))}
        </p>
      )}
      <div className="test-navigation">
        <button
          className="button secondary"
          onClick={() => setIndex((i) => i - 1)}
          disabled={index === 0 || busy}
        >
          <ArrowLeft size={17} />
          {t('previous')}
        </button>
        {existing && index < session.totalQuestions - 1 && (
          <button className="button" disabled={busy} onClick={() => setIndex((i) => i + 1)}>
            {t('next')}
            <ArrowRight size={17} />
          </button>
        )}
      </div>
      <button
        className="text-button finish-early"
        disabled={busy}
        onClick={() =>
          saved.length < session.totalQuestions ? setConfirmFinish(true) : void finish()
        }
      >
        {t('finish')}
      </button>
      <ConfirmDialog
        open={confirmFinish}
        onOpenChange={setConfirmFinish}
        title={t('earlyFinish')}
        body={t('earlyFinishBody')}
        confirm={t('finishConfirm')}
        onConfirm={() => void finish()}
      />
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        onOpenChange={(open) => {
          if (!open && blocker.state === 'blocked') blocker.reset();
        }}
        title={t('exitTitle')}
        body={t('exitBody')}
        confirm={t('exit')}
        onConfirm={() => {
          if (blocker.state === 'blocked') blocker.proceed();
        }}
      />
    </div>
  );
}
function QuestionForm({
  question,
  saved,
  busy,
  onSave,
  draft,
  onDraft,
}: {
  question: z.infer<typeof questionSchema>;
  saved?: Answer;
  busy: boolean;
  onSave: (answer: Answer, time: number) => Promise<void>;
  draft?: Answer;
  onDraft: (answer: Answer) => void;
}) {
  const { t, content } = useApp();
  const l = useL();
  const selected = saved || draft || {};
  const setSelected = onDraft;
  const assessment = { ...question.assessment, options: question.options };
  const [start] = useState(() => Date.now());
  function submit(e: FormEvent) {
    e.preventDefault();
    if (completeAnswer(assessment, selected) && !busy && !saved)
      void onSave(selected, Math.min(86400, Math.floor((Date.now() - start) / 1000)));
  }
  return (
    <form className="question-card" onSubmit={submit}>
      <fieldset disabled={busy || Boolean(saved)}>
        <legend>
          <InlineText text={content(question.questionRu, question.questionKz)} />
        </legend>
        {question.context && (
          <div className="passage">
            <h3>{l(question.context.titleRu, question.context.titleKz)}</h3>
            <RichText text={l(question.context.contentRu, question.context.contentKz)} />
          </div>
        )}
        <p className="answer-hint">
          {assessment.questionType === 'MULTIPLE_SELECT'
            ? l('Выберите один или несколько ответов.', 'Бір немесе бірнеше жауап таңдаңыз.')
            : assessment.questionType === 'MATCHING'
              ? l('Установите два соответствия.', 'Екі сәйкестікті белгілеңіз.')
              : t('answerHint')}
        </p>
        <AnswerControls
          question={assessment}
          value={selected}
          onChange={setSelected}
          disabled={busy || Boolean(saved)}
        />
      </fieldset>
      {saved ? (
        <p className="saved-answer" role="status">
          <Check size={17} />
          {t('saved')}
        </p>
      ) : (
        <button
          className="button answer-submit"
          disabled={!completeAnswer(assessment, selected) || busy}
        >
          {t(
            busy
              ? 'saving'
              : question.index === question.totalQuestions - 1
                ? 'saveFinish'
                : 'saveNext',
          )}
          <ArrowRight size={18} />
        </button>
      )}
    </form>
  );
}
