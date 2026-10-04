import { Link } from 'react-router';
import { useAction, useL, Feedback } from '../shared';
import { saveTheory } from './library';
export default function SaveTheory({ id }: { id: string }) {
  const l = useL(),
    action = useAction();
  return (
    <div className="offline-save">
      <button
        type="button"
        className="button secondary"
        disabled={action.busy}
        onClick={() =>
          void action.run(async () => {
            await saveTheory(id);
          })
        }
      >
        {action.saved
          ? l('Сохранено на устройстве', 'Құрылғыда сақталды')
          : l('Сохранить для чтения без интернета', 'Интернетсіз оқу үшін сақтау')}
      </button>
      <p className="hint">
        {l(
          'Сохраняется публичная теория и файлы с разрешением на офлайн-копию. Библиотека доступна всем, кто пользуется этим устройством.',
          'Жария теория мен офлайн көшірмеге рұқсаты бар файлдар сақталады. Кітапхана осы құрылғыны пайдаланатындардың бәріне қолжетімді.',
        )}
      </p>
      <Link to="/saved">{l('Открыть сохранённое', 'Сақталғанды ашу')}</Link>
      <Feedback action={action} />
    </div>
  );
}
