import { Link } from 'react-router';
import { useResource } from '../../hooks';
import { Loading, ErrorState } from '../../components';
import { useL } from '../shared';
import {
  contentPage,
  contentSchema,
  kindLabels,
  statusLabels,
  type Content,
  type Kind,
  type Payload,
} from './model';

export const childKinds: Partial<Record<Kind, Kind[]>> = {
  SUBJECT: ['TOPIC'],
  TOPIC: ['THEORY', 'QUESTION', 'CONTEXT'],
  COURSE: ['MODULE'],
  MODULE: ['LESSON'],
  LESSON: ['QUIZ', 'ASSIGNMENT'],
};

export function ParentTrail({ id }: { id: string }) {
  const l = useL(),
    parent = useResource(`/cms/content/${id}`, contentSchema);
  if (parent.loading) return <p className="hint">{l('Загружаем раздел…', 'Бөлім жүктелуде…')}</p>;
  if (parent.error) return <ErrorState error={parent.error} retry={parent.reload} />;
  if (!parent.data) return null;
  return (
    <nav className="authoring-parent" aria-label={l('Раздел материала', 'Материал бөлімі')}>
      <Link to={`/workspace/content/${id}`}>
        {l(...kindLabels[parent.data.kind])}: {l(parent.data.titleRu, parent.data.titleKz)}
      </Link>
      <Link to={`/workspace/content?parentId=${id}`}>
        {l('Материалы этого раздела', 'Осы бөлімнің материалдары')}
      </Link>
    </nav>
  );
}

export function ContentChildren({ item }: { item: Content }) {
  const l = useL(),
    resource = useResource(`/cms/content?parentId=${item.id}&page=0&size=5`, contentPage);
  const children = childKinds[item.kind] || [];
  if (!children.length) return null;
  return (
    <section className="authoring-children" aria-labelledby="children-heading">
      <h2 id="children-heading">{l('Продолжить наполнение', 'Мазмұнды толықтыру')}</h2>
      <p>
        {item.kind === 'TOPIC'
          ? l(
              'Добавьте объяснение и вопросы. Теория и практика публикуются отдельно внутри этой темы.',
              'Түсіндірме мен сұрақтарды қосыңыз. Теория мен жаттығу осы тақырып ішінде бөлек жарияланады.',
            )
          : item.kind === 'LESSON'
            ? l(
                'Дополните урок тестом или заданием. Файлы прикрепляются ниже.',
                'Сабаққа тест не тапсырма қосыңыз. Файлдар төменде тіркеледі.',
              )
            : l(
                'Откройте существующий материал или добавьте следующий уровень. Родитель уже будет выбран.',
                'Бар материалды ашыңыз не келесі деңгейді қосыңыз. Жоғары тұрған бөлім алдын ала таңдалады.',
              )}
      </p>
      {item.status !== 'ARCHIVED' && (
        <div className="button-row">
          {children.map((kind) => (
            <Link
              className="button secondary"
              key={kind}
              to={`/workspace/content/new?kind=${kind}&parent=${item.id}`}
            >
              + {l(...kindLabels[kind])}
            </Link>
          ))}
        </div>
      )}
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.reload} />
      ) : resource.data?.items.length ? (
        <ul className="authoring-child-list">
          {resource.data.items.map((child) => (
            <li key={child.id}>
              <Link to={`/workspace/content/${child.id}`}>{l(child.titleRu, child.titleKz)}</Link>
              <span>
                {l(...kindLabels[child.kind])} · {l(...statusLabels[child.status])}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="hint">
          {l('В этом разделе пока нет материалов.', 'Бұл бөлімде әзірге материал жоқ.')}
        </p>
      )}
      {Boolean(resource.data?.total) && (
        <Link className="text-link" to={`/workspace/content?parentId=${item.id}`}>
          {l('Все материалы раздела', 'Бөлімнің барлық материалдары')} ({resource.data?.total})
        </Link>
      )}
      <p className="hint">
        {l(
          'Публикация раздела не публикует его вложенные материалы автоматически.',
          'Бөлімді жариялау ішіндегі материалдарды автоматты жарияламайды.',
        )}
      </p>
    </section>
  );
}

export function translationGaps(p: Payload, l: (...s: string[]) => string) {
  const gaps: string[] = [];
  function pair(
    ru: string | null | undefined,
    kz: string | null | undefined,
    label: string,
    required = false,
  ) {
    if (!ru?.trim() && (required || kz?.trim())) gaps.push(`${label} RU`);
    if (!kz?.trim() && (required || ru?.trim())) gaps.push(`${label} KZ`);
  }
  pair(p.titleRu, p.titleKz, l('Название', 'Атауы'), true);
  pair(p.descriptionRu, p.descriptionKz, l('Описание', 'Сипаттама'));
  pair(p.contentRu, p.contentKz, l('Основной текст', 'Негізгі мәтін'));
  const questions = p.questions || (p.options ? [p] : []);
  questions.forEach((q, i) => {
    const prefix = p.questions ? `${l('Вопрос', 'Сұрақ')} ${i + 1}: ` : '';
    if (p.questions) pair(q.titleRu, q.titleKz, `${prefix}${l('Текст', 'Мәтін')}`, true);
    pair(q.explanationRu, q.explanationKz, `${prefix}${l('Объяснение', 'Түсіндірме')}`, true);
    [...(q.options || []), ...(q.leftOptions || [])].forEach((o) =>
      pair(o.textRu, o.textKz, `${prefix}${l('Ответ', 'Жауап')} ${o.id}`, true),
    );
  });
  p.blocks.forEach((b, i) => pair(b.textRu, b.textKz, `${l('Блок', 'Блок')} ${i + 1}`));
  return gaps;
}
