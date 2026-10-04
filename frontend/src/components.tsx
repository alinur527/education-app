import { Component, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ArrowUpRight, BookOpen, CircleAlert, LoaderCircle } from 'lucide-react';
import * as Dialog from '@radix-ui/react-alert-dialog';
import { useApp, errorKey } from './state';
import { ru, kz } from './i18n';
import type { Subject, Statistics } from './api';

export function Logo() {
  return (
    <span className="logo">
      <span className="logo-mark">
        <BookOpen size={22} strokeWidth={2.1} />
      </span>
      <span>
        Education<span className="logo-dot">.</span>
      </span>
    </span>
  );
}
export function LanguageSwitch() {
  const { language, setLanguage, t } = useApp();
  return (
    <div className="language-switch" role="group" aria-label={t('language')}>
      <button
        type="button"
        lang="ru"
        aria-pressed={language === 'ru'}
        onClick={() => void setLanguage('ru')}
      >
        РУС
      </button>
      <button
        type="button"
        lang="kk"
        aria-pressed={language === 'kz'}
        onClick={() => void setLanguage('kz')}
      >
        ҚАЗ
      </button>
    </div>
  );
}
export function Loading() {
  const { t } = useApp();
  return (
    <div className="state-block" role="status">
      <LoaderCircle className="spinner" size={28} />
      <p>{t('loading')}</p>
    </div>
  );
}
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  const { t } = useApp();
  return (
    <div className="state-block error-state" role="alert">
      <CircleAlert size={30} />
      <h2>{t('errorTitle')}</h2>
      <p>{t(errorKey(error))}</p>
      {retry && (
        <button className="button secondary" onClick={retry}>
          {t('retry')}
        </button>
      )}
    </div>
  );
}
export function Empty({
  title,
  body,
  action,
}: {
  title: string;
  body?: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <BookOpen size={28} />
      <h3>{title}</h3>
      {body && <p>{body}</p>}
      {action}
    </div>
  );
}
export function PageHeading({
  title,
  body,
  back,
}: {
  title: string;
  body?: string;
  back?: { to: string; label: string };
}) {
  return (
    <div className="page-heading">
      {back && (
        <Link className="back-link" to={back.to}>
          ‹ {back.label}
        </Link>
      )}
      <h1 tabIndex={-1}>{title}</h1>
      {body && <p>{body}</p>}
    </div>
  );
}
const symbols = ['∑', '⌘', 'ƒ', '◈', 'λ', 'π'];
export function SubjectCards({ items }: { items: Subject[] }) {
  const { t, content } = useApp();
  return (
    <div className="subject-grid">
      {items.map((item, i) => (
        <Link className={`subject-card subject-${i % 3}`} to={`/subjects/${item.id}`} key={item.id}>
          <div className="subject-top">
            <span className="subject-symbol" aria-hidden="true">
              {symbols[i % symbols.length]}
            </span>
            <ArrowUpRight size={21} aria-hidden="true" />
          </div>
          <h3>{content(item.nameRu, item.nameKz)}</h3>
          <p>
            {t('questions')}: {item.questionCount}
          </p>
          <span className="subject-bottom">
            {t('theory')} <span aria-hidden="true">/</span> {t('practice')}
          </span>
        </Link>
      ))}
    </div>
  );
}
export function Metrics({ stats }: { stats: Statistics }) {
  const { t } = useApp();
  return (
    <div className="metrics">
      {(
        [
          ['testsTaken', String(stats.testsTaken)],
          ['average', stats.testsTaken ? `${Math.round(stats.averageScore)}%` : '—'],
          ['best', stats.testsTaken ? `${Math.round(stats.bestScore)}%` : '—'],
        ] as const
      ).map(([label, value]) => (
        <div className="metric" key={label}>
          <span>{t(label)}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  );
}
export function RecentAttempts({ stats }: { stats: Statistics }) {
  const { t, content, language } = useApp();
  if (!stats.recentAttempts.length)
    return (
      <Empty
        title={t('noAttempts')}
        body={t('noAttemptsBody')}
        action={
          <Link className="text-link" to="/subjects">
            {t('explore')}
          </Link>
        }
      />
    );
  return (
    <ul className="attempt-list">
      {stats.recentAttempts.map((a) => (
        <li key={a.sessionId}>
          <Link
            to={`/results/${a.sessionId}`}
            aria-label={`${t('seeResult')}: ${content(a.nameRu, a.nameKz)}, ${a.score}%`}
          >
            <span className="attempt-icon">
              <BookOpen size={19} />
            </span>
            <span className="attempt-name">
              <strong>{content(a.nameRu, a.nameKz)}</strong>
              <small>
                {new Intl.DateTimeFormat(language === 'ru' ? 'ru-RU' : 'kk-KZ', {
                  day: 'numeric',
                  month: 'short',
                }).format(new Date(a.completedAt))}
              </small>
            </span>
            <span className="score-pill">{Math.round(a.score)}%</span>
            <ArrowUpRight size={17} aria-hidden="true" />
          </Link>
        </li>
      ))}
    </ul>
  );
}
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  body,
  confirm,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  body: string;
  confirm: string;
  onConfirm: () => void;
}) {
  const { t } = useApp();
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay" />
        <Dialog.Content className="dialog-content">
          <Dialog.Title>{title}</Dialog.Title>
          <Dialog.Description>{body}</Dialog.Description>
          <div className="button-row">
            <Dialog.Cancel className="button secondary">{t('stay')}</Dialog.Cancel>
            <Dialog.Action className="button" onClick={onConfirm}>
              {confirm}
            </Dialog.Action>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
export function BookDrawing() {
  return (
    <svg className="book-drawing" viewBox="0 0 340 260" fill="none" aria-hidden="true">
      <circle cx="186" cy="135" r="99" fill="#DCE5FF" />
      <path
        d="M61 73c40-12 76-9 110 15 35-24 72-27 111-15v132c-40-12-77-8-111 16-34-24-71-28-110-16z"
        fill="white"
        stroke="#315AE8"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d="M171 88v133M84 104c23-4 42 0 63 12M84 127c23-4 42 0 63 12M84 150c23-4 42 0 48 4M195 114c20-11 42-15 64-10M195 138c20-11 42-15 64-10"
        stroke="#315AE8"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        d="m241 168 12 12 27-32"
        stroke="#21735B"
        strokeWidth="5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M98 48v-18M89 39h18M293 115v-14M286 108h14" stroke="#315AE8" strokeWidth="2" />
    </svg>
  );
}
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    const dict = localStorage.getItem('education.language') === 'kz' ? kz : ru;
    return this.state.failed ? (
      <main className="state-block">
        <h1>{dict.appError}</h1>
        <button className="button" onClick={() => window.location.reload()}>
          {dict.reload}
        </button>
      </main>
    ) : (
      this.props.children
    );
  }
}
