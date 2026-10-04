import SaveTheory from '../offline/SaveTheory';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { ErrorState } from '../../components';
import { useL, useAction, Feedback } from '../shared';
import { publishedSchema, materialsSchema, savedSchema } from './model';
import { RenderedContent } from './Blocks';
import { MaterialList } from './Materials';
export function TheoryExtras({ id }: { id: string }) {
  const l = useL(),
    action = useAction(),
    r = useResource(`/content/${id}`, publishedSchema),
    files = useResource(`/content/${id}/materials`, materialsSchema);
  return (
    <div className="theory-extras">
      {r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : (
        r.data &&
        r.data.content.blocks.length > 0 && (
          <RenderedContent
            payload={{ ...r.data.content, titleRu: '', titleKz: '', contentRu: '', contentKz: '' }}
            materials={files.data || []}
          />
        )
      )}
      {r.data?.content.sourceType === 'AI_GENERATED' && (
        <p className="hint">
          {l(
            'Авторский тренировочный материал создан с помощью ИИ; проверка специалистом отмечается отдельно.',
            'Авторлық жаттығу материалы ЖИ көмегімен жасалған; маман тексеруі бөлек белгіленеді.',
          )}
        </p>
      )}
      <MaterialList items={files.data || []} />
      {r.data?.content.offlineAllowed && <SaveTheory id={id} />}
      <button
        className="button secondary"
        disabled={action.busy || action.saved}
        onClick={() =>
          void action.run(async () => {
            await request(`/learning/theories/${id}/read`, savedSchema, { method: 'POST' });
          })
        }
      >
        {action.saved
          ? l('Теория отмечена прочитанной', 'Теория оқылды деп белгіленді')
          : l('Отметить прочитанным', 'Оқылды деп белгілеу')}
      </button>
      <Feedback action={action} />
    </div>
  );
}
