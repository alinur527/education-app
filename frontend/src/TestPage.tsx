import { useEffect, useRef, useState, type FormEvent } from 'react';
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
  const [index, setIndex] = useState(Math.min(session.answers.length, session.totalQuestions - 1));
  const [saved, setSaved] = useState(session.answers);
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
  async function finish() {
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
  }
  async function save(selectedOptionId: string, timeSpentSecs: number) {
    if (!resource.data || lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(null);
    try {
      const receipt = await request(`/tests/${session.sessionId}/answers`, receiptSchema, {
        method: 'POST',
        body: { questionId: resource.data.questionId, selectedOptionId, timeSpentSecs },
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
        <span>{t('practice')}</span>
        <button className="text-button" onClick={() => navigate('/')} disabled={busy}>
          <LogOut size={17} />
          {t('exit')}
        </button>
      </div>
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
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : (
        q && (
          <QuestionForm
            key={q.questionId}
            question={q}
            saved={existing?.selectedOptionId}
            busy={busy}
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
}: {
  question: z.infer<typeof questionSchema>;
  saved?: string;
  busy: boolean;
  onSave: (id: string, time: number) => Promise<void>;
}) {
  const { t, content } = useApp();
  const [selected, setSelected] = useState(saved || '');
  const [start] = useState(() => Date.now());
  function submit(e: FormEvent) {
    e.preventDefault();
    if (selected && !busy && !saved)
      void onSave(selected, Math.min(86400, Math.floor((Date.now() - start) / 1000)));
  }
  return (
    <form className="question-card" onSubmit={submit}>
      <fieldset disabled={busy || Boolean(saved)}>
        <legend>{content(question.questionRu, question.questionKz)}</legend>
        <p className="answer-hint">{t('answerHint')}</p>
        <div className="answer-options">
          {question.options.map((option, i) => (
            <label
              className={`answer-option ${selected === option.id ? 'selected' : ''}`}
              key={option.id}
            >
              <input
                type="radio"
                name="answer"
                value={option.id}
                checked={selected === option.id}
                onChange={() => setSelected(option.id)}
              />
              <span className="option-letter" aria-hidden="true">
                {String.fromCharCode(65 + i)}
              </span>
              <span>{content(option.textRu, option.textKz)}</span>
              <span className="radio-indicator" aria-hidden="true">
                {selected === option.id && <Check size={13} />}
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      {saved ? (
        <p className="saved-answer" role="status">
          <Check size={17} />
          {t('saved')}
        </p>
      ) : (
        <button className="button answer-submit" disabled={!selected || busy}>
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
