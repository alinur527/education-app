import { useEffect, useState } from 'react';
import { useLocation, Link } from 'react-router';
import { useL } from '../shared';
type InstallEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: string }>;
};
export default function PwaNotice() {
  const l = useL(),
    location = useLocation(),
    [waiting, setWaiting] = useState<ServiceWorker | null>(null),
    [install, setInstall] = useState<InstallEvent | null>(null),
    [blocked, setBlocked] = useState(false),
    [offline, setOffline] = useState(!navigator.onLine),
    [installError, setInstallError] = useState(false);
  useEffect(() => {
    const ready = (e: Event) => setWaiting((e as CustomEvent<ServiceWorker>).detail),
      offer = (e: Event) => {
        e.preventDefault();
        setInstall(e as InstallEvent);
      },
      network = () => setOffline(!navigator.onLine);
    window.addEventListener('pwa-update', ready);
    window.addEventListener('beforeinstallprompt', offer);
    window.addEventListener('online', network);
    window.addEventListener('offline', network);
    const message = (e: MessageEvent) => {
      if (e.data === 'UPDATE_BLOCKED') setBlocked(true);
    };
    navigator.serviceWorker?.addEventListener('message', message);
    void navigator.serviceWorker?.getRegistration().then((r) => {
      if (r?.waiting) setWaiting(r.waiting);
    });
    return () => {
      window.removeEventListener('pwa-update', ready);
      window.removeEventListener('beforeinstallprompt', offer);
      window.removeEventListener('online', network);
      window.removeEventListener('offline', network);
      navigator.serviceWorker?.removeEventListener('message', message);
    };
  }, []);
  const safe = ['/', '/settings', '/saved'].includes(location.pathname);
  if (!waiting && !install && !offline && !installError) return null;
  return (
    <aside className="pwa-notice" aria-label={l('Приложение и офлайн', 'Қолданба және офлайн')}>
      {offline && (
        <p>
          {l('Сеть недоступна.', 'Желі қолжетімсіз.')}{' '}
          <Link to="/saved">{l('Сохранённые материалы', 'Сақталған материалдар')}</Link>
        </p>
      )}
      {install && (
        <button
          type="button"
          className="text-button"
          onClick={() => {
            setInstallError(false);
            void install
              .prompt()
              .then(() => install.userChoice)
              .then(() => setInstall(null))
              .catch(() => {
                setInstallError(true);
                setInstall(null);
              });
          }}
        >
          {l('Установить приложение', 'Қолданбаны орнату')}
        </button>
      )}
      {installError && (
        <p role="alert">
          {l(
            'Установить приложение не удалось. Сайт продолжает работать в браузере.',
            'Қолданбаны орнату мүмкін болмады. Сайт браузерде жұмысын жалғастырады.',
          )}
        </p>
      )}
      {waiting && (
        <>
          <p>
            {l(
              'Доступна новая версия. Закончите работу и сохраните формы.',
              'Жаңа нұсқа қолжетімді. Жұмысты аяқтап, пішіндерді сақтаңыз.',
            )}
          </p>
          <button
            className="text-button"
            disabled={!safe}
            onClick={() => waiting.postMessage({ type: 'APPLY_UPDATE' })}
          >
            {l('Обновить приложение', 'Қолданбаны жаңарту')}
          </button>
          {(!safe || blocked) && (
            <p>
              {l(
                'Для обновления закройте другие вкладки приложения и откройте главную страницу.',
                'Жаңарту үшін қолданбаның басқа қойындыларын жауып, басты бетті ашыңыз.',
              )}
            </p>
          )}
        </>
      )}
    </aside>
  );
}
