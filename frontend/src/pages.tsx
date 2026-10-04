import ResultSubjects from './features/assessment/ResultSubjects';
import Today from './features/study/Today';
import WeeklySummary from './features/analytics/WeeklySummary';
import TopicCatalog from './features/content/TopicCatalog';
import { AnswerText } from './features/assessment/AnswerControls';
import { RichText, InlineText } from './features/content/RichText';
import { useState, useRef, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router';
import { ArrowRight, Check, ChevronRight, Clock3, Compass, RotateCcw } from 'lucide-react';
import { z } from 'zod';
import {
  ApiError,
  request,
  subjectSchema,
  topicSchema,
  theorySchema,
  statsSchema,
  resultsSchema,
  startSchema,
} from './api';
import { useApp, errorKey } from './state';
import { useResource } from './hooks';
import { LearningSummary } from './features/mastery/Learning';
import { TheoryExtras } from './features/content/TheoryExtras';
import { Materials } from './features/content/Materials';
import {
  Logo,
  LanguageSwitch,
  BookDrawing,
  Loading,
  ErrorState,
  Empty,
  PageHeading,
  SubjectCards,
  Metrics,
  RecentAttempts,
} from './components';

const subjectsSchema = z.array(subjectSchema);
const theoriesSchema = z.array(theorySchema);

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { t, user, authenticate, expired } = useApp();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const lock = useRef(false);
  const location = useLocation();
  const desired = (location.state as { from?: string } | null)?.from;
  const target = desired?.startsWith('/') && !desired.startsWith('//') ? desired : '/';
  if (user) return <Navigate to={target} replace />;
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (lock.current) return;
    const values = Object.fromEntries(new FormData(e.currentTarget)) as Record<string, string>;
    if (new TextEncoder().encode(values.password).length > 72) {
      setError(new ApiError(400, 'INVALID_REQUEST'));
      return;
    }
    lock.current = true;
    setBusy(true);
    setError(null);
    try {
      await authenticate(mode, values);
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  const errorMessage =
    error instanceof ApiError && error.status === 401
      ? 'invalidCredentials'
      : error instanceof ApiError && error.status === 409 && mode === 'register'
        ? 'emailExists'
        : errorKey(error);
  return (
    <main className="auth-page">
      <section className="auth-story">
        <Logo />
        <div className="auth-story-copy">
          <h1>{t('authTitle')}</h1>
          <p>{t('authBody')}</p>
          <BookDrawing />
          <ul>
            {(['authStep1', 'authStep2', 'authStep3'] as const).map((key) => (
              <li key={key}>
                <Check size={18} />
                {t(key)}
              </li>
            ))}
          </ul>
        </div>
        <span className="auth-caption">{t('prep')}</span>
      </section>
      <section className="auth-panel">
        <div className="auth-top">
          <span className="mobile-logo">
            <Logo />
          </span>
          <LanguageSwitch />
        </div>
        <div className="auth-form-wrap">
          <h2>{t(mode === 'login' ? 'loginTitle' : 'registerTitle')}</h2>
          <p>{t('authIntro')}</p>
          {expired && (
            <p className="notice" role="status">
              {t('expired')}
            </p>
          )}
          <form onSubmit={(e) => void submit(e)} className="auth-form">
            {mode === 'register' && (
              <div className="form-pair">
                <label>
                  {t('firstName')}
                  <input name="firstName" autoComplete="given-name" required maxLength={100} />
                </label>
                <label>
                  {t('lastName')}
                  <input name="lastName" autoComplete="family-name" required maxLength={100} />
                </label>
              </div>
            )}
            <label>
              {t('email')}
              <input
                name="email"
                type="email"
                autoComplete="email"
                required
                maxLength={255}
                placeholder="you@example.com"
              />
            </label>
            <label>
              {t('password')}
              <input
                name="password"
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                minLength={mode === 'register' ? 8 : 1}
                maxLength={72}
                required
                aria-describedby={mode === 'register' ? 'password-hint' : undefined}
              />
            </label>
            {mode === 'register' && <small id="password-hint">{t('passwordHint')}</small>}
            {error !== null && (
              <p className="form-error" role="alert">
                {t(errorMessage)}
              </p>
            )}
            <button className="button full" disabled={busy}>
              {t(busy ? 'loading' : mode === 'login' ? 'login' : 'register')}
              <ArrowRight size={18} />
            </button>
          </form>
          <p className="auth-alternate">
            {t(mode === 'login' ? 'noAccount' : 'haveAccount')}{' '}
            <Link to={mode === 'login' ? '/register' : '/login'} state={location.state}>
              {t(mode === 'login' ? 'register' : 'login')}
            </Link>
          </p>
        </div>
      </section>
    </main>
  );
}

export function Dashboard() {
  const { t, user, content } = useApp();
  const subjects = useResource('/subjects', subjectsSchema);
  const stats = useResource('/statistics/me', statsSchema);
  return (
    <>
      <div className="greeting">
        {t('welcome')} {user?.firstName} <span aria-hidden="true">✦</span>
      </div>
      <section className="study-hero">
        <div>
          <h1>{t('heroTitle')}</h1>
          <p>{t('heroBody')}</p>
          <Link className="button" to="/subjects">
            {t('explore')}
            <ArrowRight size={18} />
          </Link>
        </div>
        <BookDrawing />
      </section>
      <div className="dashboard-columns">
        <div>
          <Today />
          <WeeklySummary />
          <LearningSummary />
          <div className="section-heading">
            <h2>{t('yourSubjects')}</h2>
            <Link className="text-link" to="/subjects">
              {t('allSubjects')}
              <ChevronRight size={16} />
            </Link>
          </div>
          {subjects.loading ? (
            <Loading />
          ) : subjects.error ? (
            <ErrorState error={subjects.error} retry={subjects.reload} />
          ) : subjects.data?.length ? (
            <SubjectCards items={subjects.data.slice(0, 3)} />
          ) : (
            <Empty title={t('noSubjects')} body={t('noSubjectsBody')} />
          )}
          <section className="activity-section">
            <h2>{t('recent')}</h2>
            {stats.loading ? (
              <Loading />
            ) : stats.error ? (
              <ErrorState error={stats.error} retry={stats.reload} />
            ) : (
              stats.data && <RecentAttempts stats={stats.data} />
            )}
          </section>
        </div>
        <aside className="learning-panel">
          <span className="panel-icon">
            <Compass size={23} />
          </span>
          <h2>{t('learning')}</h2>
          {stats.data && (
            <>
              <Metrics stats={stats.data} />
              {stats.data.activeAttempts.length > 0 && (
                <div className="active-attempts">
                  <h3>{t('activeAttempts')}</h3>
                  {stats.data.activeAttempts.map((a) => (
                    <Link to={`/tests/${a.sessionId}`} key={a.sessionId}>
                      <strong>{content(a.nameRu, a.nameKz)}</strong>
                      <span>
                        {t('savedAnswers')}: {a.answeredQuestions}/{a.totalQuestions}
                      </span>
                      <span className="text-link">
                        {t('continue')}
                        <ArrowRight size={16} />
                      </span>
                    </Link>
                  ))}
                </div>
              )}
              <Link to="/statistics" className="text-link">
                {t('toStatistics')}
                <ArrowRight size={16} />
              </Link>
            </>
          )}
        </aside>
      </div>
    </>
  );
}
export function SubjectsPage() {
  const { t, content } = useApp();
  const [search, setSearch] = useState('');
  const resource = useResource('/subjects', subjectsSchema);
  return (
    <>
      <PageHeading title={t('subjects')} body={t('subjectIntro')} />
      <label className="catalog-search">
        {content('Найти предмет', 'Пәнді табу')}
        <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} />
      </label>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : resource.data?.length ? (
        <>
          {resource.data.some((s) => s.category) ? (
            <>
              {resource.data.some((s) => s.category === 'MANDATORY') && (
                <section className="subject-section">
                  <h2>{content('Обязательные предметы', 'Міндетті пәндер')}</h2>
                  <SubjectCards
                    items={resource.data.filter(
                      (s) =>
                        s.category === 'MANDATORY' &&
                        `${s.nameRu} ${s.nameKz}`.toLowerCase().includes(search.toLowerCase()),
                    )}
                  />
                </section>
              )}
              <section className="subject-section">
                <h2>{content('Профильные предметы', 'Бейіндік пәндер')}</h2>
                <SubjectCards
                  items={resource.data.filter(
                    (s) =>
                      s.category !== 'MANDATORY' &&
                      `${s.nameRu} ${s.nameKz}`.toLowerCase().includes(search.toLowerCase()),
                  )}
                />
              </section>
            </>
          ) : (
            <SubjectCards
              items={resource.data.filter((s) =>
                `${s.nameRu} ${s.nameKz}`.toLowerCase().includes(search.toLowerCase()),
              )}
            />
          )}
        </>
      ) : (
        <Empty title={t('noSubjects')} body={t('noSubjectsBody')} />
      )}
    </>
  );
}
export function TopicsPage() {
  return <TopicCatalog />;
}
export function TopicPage() {
  const { topicId } = useParams();
  const { t, content } = useApp();
  const topic = useResource(`/topics/${topicId}`, topicSchema);
  const theories = useResource(`/theories/topic/${topicId}`, theoriesSchema);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const lock = useRef(false);
  const navigate = useNavigate();
  async function start() {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(null);
    try {
      const s = await request('/tests/start', startSchema, { method: 'POST', body: { topicId } });
      navigate(`/tests/${s.sessionId}`);
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  if (topic.loading || theories.loading) return <Loading />;
  if (topic.error || theories.error || !topic.data)
    return (
      <ErrorState
        error={topic.error || theories.error}
        retry={() => {
          topic.reload();
          theories.reload();
        }}
      />
    );
  return (
    <>
      <PageHeading
        title={content(topic.data.titleRu, topic.data.titleKz)}
        body={content(topic.data.descriptionRu, topic.data.descriptionKz)}
        back={{ to: `/subjects/${topic.data.subjectId}`, label: t('backTopics') }}
      />
      <Link
        className="text-link"
        to={`/notes?kind=TOPIC&target=${topicId}&title=${encodeURIComponent(content(topic.data.titleRu, topic.data.titleKz))}`}
      >
        {content('Заметка / закладка к теме', 'Тақырыпқа жазба / бетбелгі')}
      </Link>
      <div className="reading-layout">
        <article className="theory-content">
          <Materials contentId={topicId!} />
          {theories.data?.length ? (
            theories.data.map((theory, i) => (
              <section id={`material-${i}`} key={theory.id}>
                <h2>{content(theory.titleRu, theory.titleKz)}</h2>
                <RichText text={content(theory.contentRu, theory.contentKz)} />
                <TheoryExtras id={theory.id} />
              </section>
            ))
          ) : (
            <Empty title={t('noTheory')} />
          )}
        </article>
        <aside className="reading-aside">
          {(theories.data?.length ?? 0) > 1 && (
            <nav aria-label={t('contents')} className="contents">
              <h3>{t('contents')}</h3>
              {theories.data?.map((theory, i) => (
                <a href={`#material-${i}`} key={theory.id}>
                  {content(theory.titleRu, theory.titleKz)}
                </a>
              ))}
            </nav>
          )}
          <div className="practice-panel">
            <span className="panel-icon">
              <RotateCcw size={23} />
            </span>
            <h2>{t('knowledgeCheck')}</h2>
            <p>{t(topic.data.questionCount ? 'testIntro' : 'noQuestionsBody')}</p>
            <strong>
              {t('questions')}: {topic.data.questionCount}
            </strong>
            {error !== null && (
              <p role="alert" className="form-error">
                {t(errorKey(error))}
              </p>
            )}
            {topic.data.questionCount > 0 ? (
              <button className="button full" disabled={busy} onClick={() => void start()}>
                {t(busy ? 'loading' : 'start')}
                <ArrowRight size={18} />
              </button>
            ) : (
              <p>{t('noQuestions')}</p>
            )}
          </div>
        </aside>
      </div>
    </>
  );
}
export function ResultsPage() {
  const { sessionId } = useParams();
  const { t, content } = useApp();
  const resource = useResource(`/tests/${sessionId}/results`, resultsSchema);
  const results = resource.data;
  if (resource.loading) return <Loading />;
  if (resource.error || !results)
    return <ErrorState error={resource.error} retry={resource.reload} />;
  return (
    <>
      <PageHeading
        title={t('results')}
        back={{
          to: results.topicId ? `/topics/${results.topicId}` : '/subjects',
          label: t('backTopic'),
        }}
      />
      <section className="result-banner">
        <div className="result-score">
          <strong>
            {Math.round(results.score)}
            <span>%</span>
          </strong>
          <span>{t('score')}</span>
        </div>
        <div className="result-copy">
          <h2>{t('resultTitle')}</h2>
          <p>{t('resultBody')}</p>
          <div className="result-facts">
            {results.maxPoints != null && (
              <span>
                {content('Баллы', 'Балдар')}:{' '}
                <b>
                  {results.earnedPoints ?? 0} / {results.maxPoints}
                </b>
              </span>
            )}
            <span>
              <Check size={18} />
              {t('correct')}: <b>{results.correctAnswers}</b>
            </span>
            <span>
              {t('incorrect')}: <b>{results.totalQuestions - results.correctAnswers}</b>
            </span>
            <span>
              <Clock3 size={18} />
              {Math.floor(results.timeTakenSecs / 60)} {t('minutes')} {results.timeTakenSecs % 60}{' '}
              {t('seconds')}
            </span>
          </div>
        </div>
      </section>
      <div className="button-row results-actions">
        {results.topicId && (
          <Link className="button" to={`/topics/${results.topicId}`}>
            {t('retryTest')}
            <RotateCcw size={17} />
          </Link>
        )}
        <Link className="button secondary" to="/statistics">
          {t('toStatistics')}
        </Link>
      </div>
      <ResultSubjects answers={results.answers} />
      <h2>{t('review')}</h2>
      <div className="review-list">
        {results.answers.map((answer, i) => (
          <article className="review-item" key={answer.questionId}>
            <div className="review-heading">
              <span className={`review-status ${answer.isCorrect ? 'correct' : 'incorrect'}`}>
                {answer.isCorrect
                  ? t('correct')
                  : answer.selectedOptionId || answer.answer
                    ? t('incorrect')
                    : t('unanswered')}
              </span>
              <small>
                {t('question')} {i + 1}
              </small>
            </div>
            <h3>
              <InlineText text={content(answer.questionRu, answer.questionKz)} />
            </h3>
            {answer.context && (
              <RichText text={content(answer.context.contentRu, answer.context.contentKz)} />
            )}
            {answer.maxPoints && (
              <p>
                {answer.earnedPoints ?? 0} / {answer.maxPoints} {t('score')}
              </p>
            )}
            <dl>
              <div>
                <dt>{t('yourAnswer')}</dt>
                <dd>
                  <AnswerText
                    question={{ ...answer.assessment, options: answer.options }}
                    answer={
                      answer.answer ||
                      (answer.selectedOptionId
                        ? { selectedOptionId: answer.selectedOptionId }
                        : null)
                    }
                  />
                </dd>
              </div>
              {!answer.isCorrect && (
                <div>
                  <dt>{t('rightAnswer')}</dt>
                  <dd>
                    <AnswerText
                      question={{
                        ...answer.assessment,
                        options: answer.options,
                        correctOptionId: answer.correctOptionId,
                      }}
                      correct
                    />
                  </dd>
                </div>
              )}
            </dl>
            {(answer.explanationRu || answer.explanationKz) && (
              <div className="explanation">
                <strong>{t('explanation')}</strong>
                <RichText text={content(answer.explanationRu, answer.explanationKz)} />
              </div>
            )}
          </article>
        ))}
      </div>
    </>
  );
}
export function SettingsPage() {
  const { t, user, logout } = useApp();
  return (
    <>
      <PageHeading title={t('settings')} body={t('preferences')} />
      <section className="settings-panel panel">
        <h2>{t('profile')}</h2>
        <dl>
          <div>
            <dt>{t('firstName')}</dt>
            <dd>
              {user?.firstName} {user?.lastName}
            </dd>
          </div>
          <div>
            <dt>{t('email')}</dt>
            <dd>{user?.email}</dd>
          </div>
        </dl>
        <hr />
        <h2>{t('language')}</h2>
        <p>{t('languageHelp')}</p>
        <LanguageSwitch />
        <hr />
        <button className="button secondary" onClick={logout}>
          {t('logout')}
        </button>
      </section>
    </>
  );
}
export function NotFound() {
  const { t } = useApp();
  return (
    <div className="not-found">
      <span>404</span>
      <h1>{t('notFound')}</h1>
      <p>{t('notFoundBody')}</p>
      <Link className="button" to="/">
        {t('toHome')}
      </Link>
    </div>
  );
}
