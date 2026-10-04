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
      <MaterialList items={files.data || []} />
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
