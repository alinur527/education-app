import { useState } from 'react';
import { useNavigate, Link } from 'react-router';
import { z } from 'zod';
import { request, startSchema } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState } from '../../components';
import { Feedback, Field, useAction, useL } from '../shared';

const catalogSchema = z.object({
  subjects: z.array(
    z.object({ id: z.string(), titleRu: z.string(), titleKz: z.string(), available: z.number() }),
  ),
  topics: z
    .array(
      z.object({
        id: z.string(),
        subjectId: z.string(),
        titleRu: z.string(),
        titleKz: z.string(),
        difficulty: z.string(),
        available: z.number(),
      }),
    )
    .default([]),
  configuration: z.object({
    id: z.string(),
    checkedAt: z.string(),
    durationMinutes: z.number(),
    allowedProfilePairs: z.array(z.object({ subjects: z.array(z.string()) })),
    formatEvidence: z.object({ url: z.string() }),
  }),
});
const bankSchema = z.object({
  officialReady: z.boolean(),
  availableTotal: z.number(),
  shortenedMaximum: z.number(),
  subjects: z.array(
    z.object({
      titleRu: z.string(),
      titleKz: z.string(),
      required: z.number(),
      available: z.number(),
      missing: z.number(),
      blueprint: z.array(
        z.object({
          type: z.string(),
          context: z.boolean(),
          required: z.number(),
          available: z.number(),
          missing: z.number(),
        }),
      ),
    }),
  ),
});
export default function Practice() {
  const l = useL(),
    catalog = useResource('/practice', catalogSchema);
  const [mode, setMode] = useState('MIXED_PRACTICE'),
    [subjects, setSubjects] = useState<string[]>([]),
    [count, setCount] = useState(10),
    [minutes, setMinutes] = useState(20),
    [pair, setPair] = useState(0),
    [topicIds, setTopicIds] = useState<string[]>([]),
    [difficulty, setDifficulty] = useState('');
  const bank = useResource(`/practice/exam-bank?profilePair=${pair}`, bankSchema),
    action = useAction(),
    navigate = useNavigate();
  if (catalog.loading) return <Loading />;
  if (!catalog.data || catalog.error)
    return <ErrorState error={catalog.error} retry={catalog.reload} />;
  const data = catalog.data,
    exam = mode === 'SHORTENED_ENT',
    available = exam
      ? bank.data?.shortenedMaximum || 0
      : data.topics
          .filter(
            (t) =>
              subjects.includes(t.subjectId) &&
              (!topicIds.length || topicIds.includes(t.id)) &&
              (!difficulty || t.difficulty === difficulty),
          )
          .reduce((sum, t) => sum + t.available, 0);
  const topicOptions = Array.from(
    new Map(
      data.topics.filter((t) => subjects.includes(t.subjectId)).map((t) => [t.id, t]),
    ).values(),
  );
  return (
    <div className="practice-setup">
      <PageHeading
        title={l('Время практиковаться', 'Жаттығу уақыты')}
        body={l(
          'Соберите тренировку из опубликованных заданий. Результат пополнит ваш прогресс по каждой теме.',
          'Жарияланған тапсырмалардан жаттығу құрастырыңыз. Нәтиже әр тақырыптағы жетістігіңізге қосылады.',
        )}
      />
      <div className="tabs" role="group" aria-label={l('Режим', 'Режим')}>
        <button
          className={!exam ? 'active' : ''}
          aria-pressed={!exam}
          onClick={() => setMode('MIXED_PRACTICE')}
        >
          {l('Смешанная практика', 'Аралас жаттығу')}
        </button>
        <button
          className={exam ? 'active' : ''}
          aria-pressed={exam}
          onClick={() => setMode('SHORTENED_ENT')}
        >
          {l('Тренировка с таймером', 'Таймермен жаттығу')}
        </button>
      </div>
      <form
        className="panel practice-form"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            const result = await request('/practice/sessions', startSchema, {
              method: 'POST',
              body: {
                mode,
                subjectIds: subjects,
                topicIds: exam ? [] : topicIds,
                difficulty: exam ? '' : difficulty,
                count,
                minutes,
                profilePair: pair,
              },
            });
            navigate(`/tests/${result.sessionId}`);
          });
        }}
      >
        {exam ? (
          <>
            <Field label={l('Профильные предметы', 'Бейіндік пәндер')}>
              <select value={pair} onChange={(e) => setPair(Number(e.target.value))}>
                {data.configuration.allowedProfilePairs.map((p, i) => (
                  <option key={i} value={i}>
                    {p.subjects
                      .map((name) => {
                        const s = data.subjects.find((s) => s.titleRu === name);
                        return s ? l(s.titleRu, s.titleKz) : name;
                      })
                      .join(' + ')}
                  </option>
                ))}
              </select>
            </Field>
            <p className="notice">
              {l(
                'Сокращённая тренировка. Полный пробник ЕНТ недоступен: банк ещё не подтверждён по всем форматам, контекстам и уровням сложности.',
                'Қысқартылған жаттығу. Толық ҰБТ сынағы қолжетімсіз: қор барлық форматтар, контекстер мен күрделілік деңгейлері бойынша әлі расталмаған.',
              )}
            </p>
            <p>
              <a href={data.configuration.formatEvidence.url} target="_blank" rel="noreferrer">
                {l('Формат НЦТ', 'ҰТО форматы')}
              </a>{' '}
              · {l('Проверен', 'Тексерілді')} {data.configuration.checkedAt}
            </p>
            {bank.loading ? (
              <Loading />
            ) : bank.error ? (
              <ErrorState error={bank.error} retry={bank.reload} />
            ) : (
              <div className="table-scroll">
                <table>
                  <caption>{l('Доступность банка', 'Қордың қолжетімділігі')}</caption>
                  <thead>
                    <tr>
                      <th>{l('Предмет', 'Пән')}</th>
                      <th>{l('Нужно', 'Қажет')}</th>
                      <th>{l('Есть', 'Бар')}</th>
                      <th>{l('Не хватает', 'Жетіспейді')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bank.data?.subjects.map((s) => (
                      <tr key={s.titleRu}>
                        <th scope="row">
                          {l(s.titleRu, s.titleKz)}
                          {s.blueprint.length > 0 && (
                            <details>
                              <summary>{l('Форматы', 'Форматтар')}</summary>
                              {s.blueprint.map((b, i) => (
                                <p key={i}>
                                  {b.type === 'MATCHING'
                                    ? l('Сопоставление', 'Сәйкестендіру')
                                    : b.type === 'MULTIPLE_SELECT'
                                      ? l('Несколько ответов', 'Бірнеше жауап')
                                      : b.context
                                        ? l('Общий текст', 'Ортақ мәтін')
                                        : l('Один ответ', 'Бір жауап')}
                                  : {b.available}/{b.required}
                                </p>
                              ))}
                            </details>
                          )}
                        </th>
                        <td>{s.required}</td>
                        <td>{s.available}</td>
                        <td>{s.missing}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <Field label={l('Минуты', 'Минут')}>
              <input
                type="number"
                min={1}
                max={240}
                value={minutes}
                onChange={(e) => setMinutes(Number(e.target.value))}
              />
            </Field>
          </>
        ) : (
          <fieldset>
            <legend>{l('Выберите до пяти предметов', 'Бес пәнге дейін таңдаңыз')}</legend>
            <div className="subject-picks">
              {data.subjects.map((s) => (
                <label className="subject-pick" key={s.id}>
                  <input
                    type="checkbox"
                    checked={subjects.includes(s.id)}
                    disabled={
                      !subjects.includes(s.id) && (subjects.length >= 5 || s.available === 0)
                    }
                    onChange={(e) => {
                      setSubjects(
                        e.target.checked
                          ? [...subjects, s.id]
                          : subjects.filter((id) => id !== s.id),
                      );
                      setTopicIds([]);
                    }}
                  />
                  <span>
                    {l(s.titleRu, s.titleKz)}
                    <small>
                      {s.available} {l('заданий', 'тапсырма')}
                    </small>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        )}
        {!exam && (
          <>
            <Field label={l('Сложность', 'Күрделілік')}>
              <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
                <option value="">{l('Все уровни', 'Барлық деңгейлер')}</option>
                <option value="easy">{l('Базовая', 'Негізгі')}</option>
                <option value="medium">{l('Средняя', 'Орташа')}</option>
                <option value="hard">{l('Сложная', 'Күрделі')}</option>
              </select>
            </Field>
            {topicOptions.length > 0 && (
              <details className="editor-section">
                <summary>
                  {l('Выбрать отдельные темы', 'Жеке тақырыптарды таңдау')} ·{' '}
                  {topicIds.length || l('все', 'барлығы')}
                </summary>
                <p>
                  {l(
                    'Без отметок используются все доступные темы выбранных предметов.',
                    'Белгі болмаса, таңдалған пәндердің барлық қолжетімді тақырыптары қолданылады.',
                  )}
                </p>
                <div className="subject-picks">
                  {topicOptions.map((t) => (
                    <label key={t.id}>
                      <input
                        type="checkbox"
                        checked={topicIds.includes(t.id)}
                        onChange={(e) =>
                          setTopicIds(
                            e.target.checked
                              ? [...topicIds, t.id]
                              : topicIds.filter((id) => id !== t.id),
                          )
                        }
                      />
                      {l(t.titleRu, t.titleKz)}
                    </label>
                  ))}
                </div>
              </details>
            )}
          </>
        )}
        <Field label={l('Количество вопросов (до 50)', 'Сұрақ саны (50-ге дейін)')}>
          <input
            type="number"
            min={1}
            max={Math.min(50, available) || 1}
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
          />
        </Field>
        <p>
          {l('В выбранном банке доступно', 'Таңдалған қорда қолжетімді')}: {available}
        </p>
        <Feedback action={action} />
        <button
          className="button"
          disabled={
            action.busy ||
            count < 1 ||
            count > available ||
            count > 50 ||
            (!exam && subjects.length === 0) ||
            (exam && bank.loading)
          }
        >
          {l('Начать тренировку', 'Жаттығуды бастау')}
        </button>
      </form>
      <p>
        <Link to="/learning/errors">{l('Повторить свои ошибки', 'Қателерімді қайталау')} →</Link>
      </p>
    </div>
  );
}
