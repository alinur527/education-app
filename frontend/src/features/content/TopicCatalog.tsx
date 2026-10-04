import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { z } from 'zod';
import { BookOpen, ArrowRight } from 'lucide-react';
import { topicSchema, subjectSchema } from '../../api';
import { useResource } from '../../hooks';
import { useApp } from '../../state';
import { PageHeading, Loading, ErrorState, Empty } from '../../components';
import { useL, Field, Pager } from '../shared';
const topics = z.array(topicSchema),
  subjects = z.array(subjectSchema);
export default function TopicCatalog() {
  const { subjectId } = useParams();
  return <TopicCatalogBody key={subjectId} />;
}
function TopicCatalogBody() {
  const { subjectId } = useParams(),
    l = useL(),
    { t } = useApp();
  const r = useResource(`/topics/subject/${subjectId}`, topics),
    s = useResource('/subjects', subjects),
    subject = s.data?.find((v) => v.id === subjectId);
  const [view, setView] = useState('LEARNING'),
    [variant, setVariant] = useState(''),
    [q, setQ] = useState(''),
    [page, setPage] = useState(0);
  const visible = (r.data || []).filter(
    (v) =>
      (v.contentRole || 'LEARNING') === view &&
      (!variant || v.curriculum?.sourceId === variant) &&
      `${v.titleRu} ${v.titleKz} ${v.descriptionRu} ${v.descriptionKz}`
        .toLowerCase()
        .includes(q.toLowerCase()),
  );
  const variants = Array.from(
    new Map(
      (r.data || [])
        .filter((v) => v.contentRole === 'SYLLABUS')
        .map((v) => [v.curriculum?.sourceId, v]),
    ).values(),
  );
  return (
    <>
      <PageHeading
        title={subject ? l(subject.nameRu, subject.nameKz) : t('topics')}
        body={
          view === 'LEARNING'
            ? l(
                'Готовые уроки: прочитайте объяснение, проверьте знания и вернитесь к ошибкам.',
                'Дайын сабақтар: түсіндірмені оқып, біліміңізді тексеріп, қателерге оралыңыз.',
              )
            : l(
                'Полный перечень тем из спецификаций НЦТ. Наличие темы в программе не означает, что учебный материал уже готов.',
                'ҰТО спецификацияларындағы тақырыптардың толық тізімі. Бағдарламада тақырыптың болуы оқу материалы дайын екенін білдірмейді.',
              )
        }
        back={{ to: '/subjects', label: t('backSubjects') }}
      />
      <div className="tabs" role="group" aria-label={l('Содержание предмета', 'Пән мазмұны')}>
        {['LEARNING', 'SYLLABUS'].map((v) => (
          <button
            key={v}
            aria-pressed={view === v}
            className={view === v ? 'active' : ''}
            onClick={() => {
              setView(v);
              setPage(0);
              setVariant('');
            }}
          >
            {v === 'LEARNING' ? l('Уроки', 'Сабақтар') : l('Программа НЦТ', 'ҰТО бағдарламасы')}
          </button>
        ))}
      </div>
      <div className="workspace-toolbar">
        <Field label={l('Найти тему', 'Тақырыпты табу')}>
          <input
            type="search"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        {view === 'SYLLABUS' && (
          <Field
            label={l('Вариант программы и язык документа', 'Бағдарлама нұсқасы және құжат тілі')}
          >
            <select
              value={variant}
              onChange={(e) => {
                setVariant(e.target.value);
                setPage(0);
              }}
            >
              <option value="">{l('Все варианты', 'Барлық нұсқалар')}</option>
              {variants.map((v) => (
                <option key={v.curriculum?.sourceId} value={v.curriculum?.sourceId}>
                  {v.curriculum?.variant} · {v.documentLanguage === 'kk' ? 'KZ' : 'RU'}
                </option>
              ))}
            </select>
          </Field>
        )}
      </div>
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : visible.length ? (
        <>
          <p>
            {l('Тем', 'Тақырыптар')}: {visible.length}
          </p>
          <div className="topic-list">
            {visible.slice(page * 25, page * 25 + 25).map((topic, i) =>
              view === 'SYLLABUS' ? (
                <article className="syllabus-row" key={topic.id}>
                  <span className="syllabus-code">{topic.curriculum?.officialCode || '—'}</span>
                  <div>
                    <small>
                      {topic.curriculum?.variant} · {topic.documentLanguage === 'kk' ? 'KZ' : 'RU'}{' '}
                      · {l('страница', 'бет')} {topic.curriculum?.page}
                    </small>
                    <h2>
                      {l(
                        topic.descriptionRu || topic.titleRu,
                        topic.descriptionKz || topic.titleKz,
                      )}
                    </h2>
                    <p>{l(topic.curriculum?.sectionRu, topic.curriculum?.sectionKz)}</p>
                    {topic.curriculum?.extractionStatus?.startsWith('UNCERTAIN') && (
                      <p className="feedback error">
                        {l(
                          'Название обрезано в исходном PDF. Нужна редакторская проверка; продолжение не восстановлено догадкой.',
                          'Атау бастапқы PDF ішінде қиылған. Редактор тексеруі қажет; жалғасы болжаммен толықтырылмаған.',
                        )}
                      </p>
                    )}
                    {topic.sourceUrl && (
                      <a
                        className="text-link"
                        href={topic.sourceUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        {l('Официальная спецификация', 'Ресми спецификация')}
                      </a>
                    )}
                  </div>
                </article>
              ) : (
                <Link className="topic-row" to={`/topics/${topic.id}`} key={topic.id}>
                  <span className="topic-number" aria-hidden="true">
                    {String(page * 25 + i + 1).padStart(2, '0')}
                  </span>
                  <div className="topic-copy">
                    <h2>{l(topic.titleRu, topic.titleKz)}</h2>
                    <p>{l(topic.descriptionRu, topic.descriptionKz)}</p>
                    <div className="topic-meta">
                      <span>
                        <BookOpen size={15} /> {t('material')}: {topic.theoryCount}
                      </span>
                      <span>
                        {t('questions')}: {topic.questionCount}
                      </span>
                    </div>
                    {!topic.theoryCount && !topic.questionCount && (
                      <p>{l('Материал готовится', 'Материал дайындалуда')}</p>
                    )}
                  </div>
                  <span className="topic-action">
                    {t('read')}
                    <ArrowRight size={18} />
                  </span>
                </Link>
              ),
            )}
          </div>
          <Pager page={page} total={visible.length} onChange={setPage} />
        </>
      ) : (
        <Empty
          title={
            view === 'SYLLABUS'
              ? l('Программа ещё не загружена', 'Бағдарлама әлі жүктелмеген')
              : t('noTopics')
          }
        />
      )}
    </>
  );
}
