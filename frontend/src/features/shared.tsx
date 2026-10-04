import { useRef, useState, type ReactNode } from 'react';
import { useApp, errorKey } from '../state';
import { ApiError } from '../api';

export function useL() {
  const { content } = useApp();
  return (...labels: (string | null | undefined)[]) => content(labels[0], labels[1]);
}
export function useAction() {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [saved, setSaved] = useState(false);
  const lock = useRef(false);
  async function run(work: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await work();
      setSaved(true);
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return { busy, error, saved, run };
}
export function Feedback({ action }: { action: ReturnType<typeof useAction> }) {
  const { t } = useApp();
  const l = useL();
  if (action.error)
    return (
      <p className="form-error" role="alert">
        {action.error instanceof ApiError && action.error.status === 409
          ? l(
              'Данные изменились. Ваш текст сохранён в форме. Обновите страницу в другой вкладке и сравните версии перед повторным сохранением.',
              'Деректер өзгерді. Мәтініңіз пішінде сақталды. Басқа қойындыда бетті жаңартып, нұсқаларды салыстырыңыз.',
            )
          : action.error instanceof ApiError && action.error.status === 413
            ? l('Файл слишком большой. Максимум 20 МБ.', 'Файл тым үлкен. Ең көбі 20 МБ.')
            : t(errorKey(action.error))}
      </p>
    );
  return action.saved ? (
    <p className="success-message" role="status">
      {l('Сохранено', 'Сақталды')}
    </p>
  ) : null;
}
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}
export function Pager({
  page,
  total,
  size = 25,
  onChange,
}: {
  page: number;
  total: number;
  size?: number;
  onChange: (page: number) => void;
}) {
  const l = useL();
  return (
    <nav className="pager" aria-label={l('Страницы', 'Беттер')}>
      <button className="button secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        {l('Назад', 'Артқа')}
      </button>
      <span>
        {page + 1} / {Math.max(1, Math.ceil(total / size))}
      </span>
      <button
        className="button secondary"
        disabled={(page + 1) * size >= total}
        onClick={() => onChange(page + 1)}
      >
        {l('Далее', 'Келесі')}
      </button>
    </nav>
  );
}
export const roleLabels = {
  STUDENT: ['Ученик', 'Оқушы'],
  TEACHER: ['Учитель', 'Мұғалім'],
  CONTENT_EDITOR: ['Редактор', 'Редактор'],
  ADMIN: ['Администратор', 'Әкімші'],
} as const;
