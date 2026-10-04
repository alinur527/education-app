import { createRoot } from 'react-dom/client';
import '@fontsource-variable/golos-text';
import './styles.css';
import App from './App';
createRoot(document.getElementById('root')!).render(<App />);

// Updates only reload the one safe page which explicitly requested activation.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  let approvedPath: string | null = null;
  navigator.serviceWorker.addEventListener('message', (event) => {
    if (event.data === 'UPDATE_APPROVED') approvedPath = window.location.pathname;
  });
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (
      approvedPath === window.location.pathname &&
      ['/', '/settings', '/saved'].includes(approvedPath)
    )
      window.location.reload();
  });
  void navigator.serviceWorker
    .register('/sw.js', { updateViaCache: 'none' })
    .then((registration) => {
      const notify = () => {
        if (registration.waiting)
          window.dispatchEvent(new CustomEvent('pwa-update', { detail: registration.waiting }));
      };
      notify();
      registration.addEventListener('updatefound', () => {
        registration.installing?.addEventListener('statechange', notify);
      });
    })
    .catch(() => {
      /* The online app remains usable when storage or service workers are disabled. */
    });
}
