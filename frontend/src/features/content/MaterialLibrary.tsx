import { Link, useSearchParams } from 'react-router';
import { z } from 'zod';
import { useResource } from '../../hooks';
import { Empty, ErrorState, Loading, PageHeading } from '../../components';
import { Feedback, Field, Pager, useAction, useL } from '../shared';
import { kinds, kindLabels, materialSchema } from './model';
import { downloadMaterial } from './Materials';

const libraryPage = z.object({
  items: z.array(
    materialSchema.extend({
      contentTitleRu: z.string(),
      contentTitleKz: z.string(),
      contentKind: z.enum(kinds),
    }),
  ),
  page: z.number(),
  size: z.number(),
  total: z.number(),
});

export default function MaterialLibrary() {
  const l = useL(),
    action = useAction(),
    [params, setParams] = useSearchParams();
  const q = params.get('q') || '',
    page = Math.max(0, Number.parseInt(params.get('page') || '0') || 0);
  const resource = useResource(
    `/cms/materials?q=${encodeURIComponent(q)}&page=${page}`,
    libraryPage,
  );
  function search(value: string) {
    setParams(value ? { q: value } : {}, { replace: true });
  }
  function move(value: number) {
    setParams({ ...(q ? { q } : {}), page: String(value) });
  }
  return (
    <>
      <PageHeading
        title={l('Библиотека файлов', 'Файлдар кітапханасы')}
        body={l(
          'Найдите загруженный файл и откройте материал, к которому он прикреплён. Здесь показаны только файлы, доступные вашей роли.',
          'Жүктелген файлды тауып, тіркелген материалын ашыңыз. Мұнда тек рөліңізге қолжетімді файлдар көрсетіледі.',
        )}
      />
      <div className="workspace-toolbar">
        <Field label={l('Найти файл или учебный материал', 'Файл не оқу материалын табу')}>
          <input type="search" maxLength={200} value={q} onChange={(e) => search(e.target.value)} />
        </Field>
        <Link className="button secondary" to="/workspace/content">
          {l('Выбрать материал для загрузки', 'Жүктеуге арналған материалды таңдау')}
        </Link>
      </div>
      <p className="hint">
        {l(
          'Чтобы добавить файл, откройте нужную тему, теорию, курс, урок или задание. После сохранения черновика используйте блок «Материалы».',
          'Файл қосу үшін қажетті тақырыпты, теорияны, курсты, сабақты не тапсырманы ашыңыз. Жобаны сақтағаннан кейін «Материалдар» бөлімін пайдаланыңыз.',
        )}
      </p>
      <Feedback action={action} />
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : resource.data?.items.length ? (
        <>
          <div
            className="table-region"
            role="region"
            aria-label={l('Загруженные файлы', 'Жүктелген файлдар')}
            tabIndex={0}
          >
            <table>
              <caption>
                {l('Найдено файлов', 'Табылған файлдар')}: {resource.data.total}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{l('Файл', 'Файл')}</th>
                  <th scope="col">{l('Где используется', 'Қайда қолданылады')}</th>
                  <th scope="col">{l('Доступ ученикам', 'Оқушыға қолжетімділік')}</th>
                  <th scope="col">{l('Действие', 'Әрекет')}</th>
                </tr>
              </thead>
              <tbody>
                {resource.data.items.map((file) => (
                  <tr key={file.id}>
                    <td>
                      <strong>{l(file.titleRu, file.titleKz)}</strong>
                      <small>{file.originalFileName}</small>
                      <small>
                        {Math.ceil(file.size / 1024)} {l('КБ', 'КБ')} · {file.mimeType}
                      </small>
                      {(file.scanStatus === 'INFECTED' || file.scanStatus === 'SCAN_FAILED') && (
                        <p>
                          {l(
                            'Скачивание заблокировано проверкой файла.',
                            'Файл тексеруі жүктеуді бұғаттады.',
                          )}
                        </p>
                      )}
                      {(file.scanStatus === 'UNSCANNED' ||
                        file.scanStatus === 'UNSCANNED_LEGACY') && (
                        <small>{l('Без антивирусной проверки', 'Антивирустық тексерусіз')}</small>
                      )}
                    </td>
                    <td>
                      <Link className="text-link" to={`/workspace/content/${file.contentId}`}>
                        {l(file.contentTitleRu, file.contentTitleKz)}
                      </Link>
                      <small>{l(...kindLabels[file.contentKind])}</small>
                    </td>
                    <td>
                      {file.published
                        ? l(
                            'С опубликованным материалом, по его правам доступа',
                            'Жарияланған материалдың қолжетімділік құқықтарына сай',
                          )
                        : l('Черновик: ученикам недоступен', 'Жоба: оқушыларға қолжетімсіз')}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="button secondary"
                        aria-label={`${l('Скачать', 'Жүктеу')}: ${l(file.titleRu, file.titleKz)}`}
                        disabled={
                          action.busy ||
                          file.scanStatus === 'INFECTED' ||
                          file.scanStatus === 'SCAN_FAILED'
                        }
                        onClick={() => void action.run(() => downloadMaterial(file))}
                      >
                        {l('Скачать', 'Жүктеу')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={page}
            total={resource.data.total}
            size={resource.data.size}
            onChange={move}
          />
        </>
      ) : (
        <Empty
          title={l('Файлы не найдены', 'Файлдар табылмады')}
          body={l(
            'Измените поисковый запрос или загрузите файл через редактор учебного материала.',
            'Іздеу сөзін өзгертіңіз не оқу материалының редакторы арқылы файл жүктеңіз.',
          )}
        />
      )}
    </>
  );
}
