import { useL, Field } from '../shared';
import { blankQuestion, type QuizQuestion } from './model';

export function QuestionEditor({
  value,
  onChange,
  titles = false,
}: {
  value: QuizQuestion;
  onChange: (q: QuizQuestion) => void;
  titles?: boolean;
}) {
  const l = useL();
  return (
    <fieldset className="question-editor">
      <legend>{l('Варианты ответа', 'Жауап нұсқалары')}</legend>
      <Field label={l('Тип вопроса', 'Сұрақ түрі')}>
        <select
          value={value.questionType || 'SINGLE_CHOICE'}
          onChange={(e) => {
            const questionType = e.target.value as QuizQuestion['questionType'];
            onChange({
              ...value,
              questionType,
              scoringPolicy: undefined,
              correctOptionId: questionType === 'SINGLE_CHOICE' ? '' : undefined,
              correctOptionIds: questionType === 'MULTIPLE_SELECT' ? [] : undefined,
              correctPairs: questionType === 'MATCHING' ? [] : undefined,
              leftOptions:
                questionType === 'MATCHING'
                  ? [
                      { id: 'L1', textRu: '', textKz: '' },
                      { id: 'L2', textRu: '', textKz: '' },
                    ]
                  : undefined,
            });
          }}
        >
          <option value="SINGLE_CHOICE">{l('Один ответ', 'Бір жауап')}</option>
          <option value="MULTIPLE_SELECT">{l('Несколько ответов', 'Бірнеше жауап')}</option>
          <option value="MATCHING">{l('Соответствие', 'Сәйкестік')}</option>
        </select>
      </Field>
      {titles && (
        <div className="form-pair">
          <Field label={l('Вопрос RU', 'Сұрақ RU')}>
            <textarea
              required
              value={value.titleRu}
              onChange={(e) => onChange({ ...value, titleRu: e.target.value })}
            />
          </Field>
          <Field label={l('Вопрос KZ', 'Сұрақ KZ')}>
            <textarea
              value={value.titleKz}
              onChange={(e) => onChange({ ...value, titleKz: e.target.value })}
            />
          </Field>
        </div>
      )}
      {value.options.map((o, i) => (
        <div className="option-edit" key={o.id}>
          <strong>{o.id}</strong>
          <Field label={`${l('Ответ', 'Жауап')} ${o.id} RU`}>
            <input
              required
              value={o.textRu}
              onChange={(e) =>
                onChange({
                  ...value,
                  options: value.options.map((v, n) =>
                    n === i ? { ...v, textRu: e.target.value } : v,
                  ),
                })
              }
            />
          </Field>
          <Field label={`${l('Ответ', 'Жауап')} ${o.id} KZ`}>
            <input
              value={o.textKz}
              onChange={(e) =>
                onChange({
                  ...value,
                  options: value.options.map((v, n) =>
                    n === i ? { ...v, textKz: e.target.value } : v,
                  ),
                })
              }
            />
          </Field>
          <button
            type="button"
            className="text-button"
            disabled={value.options.length <= 2}
            aria-label={`${l('Удалить ответ', 'Жауапты жою')} ${o.id}`}
            onClick={() =>
              onChange({
                ...value,
                options: value.options.filter((_, n) => n !== i),
                correctOptionId: value.correctOptionId === o.id ? '' : value.correctOptionId,
                correctOptionIds: value.correctOptionIds?.filter((id) => id !== o.id),
                correctPairs: value.correctPairs?.filter((pair) => pair.rightId !== o.id),
              })
            }
          >
            ×
          </button>
        </div>
      ))}
      <button
        type="button"
        className="button secondary"
        disabled={value.options.length >= 8}
        onClick={() => {
          const id = 'ABCDEFGH'.split('').find((v) => !value.options.some((o) => o.id === v))!;
          onChange({ ...value, options: [...value.options, { id, textRu: '', textKz: '' }] });
        }}
      >
        + {l('Вариант ответа', 'Жауап нұсқасы')}
      </button>
      {value.questionType === 'MULTIPLE_SELECT' ? (
        <fieldset>
          <legend>{l('Правильные ответы (1–3)', 'Дұрыс жауаптар (1–3)')}</legend>
          {value.options.map((o) => (
            <label className="check-field" key={o.id}>
              <input
                type="checkbox"
                checked={value.correctOptionIds?.includes(o.id) || false}
                onChange={(e) =>
                  onChange({
                    ...value,
                    correctOptionIds: e.target.checked
                      ? [...(value.correctOptionIds || []), o.id]
                      : (value.correctOptionIds || []).filter((id) => id !== o.id),
                  })
                }
              />
              {o.id}
            </label>
          ))}
        </fieldset>
      ) : value.questionType === 'MATCHING' ? (
        <fieldset>
          <legend>{l('Левые части и ключ', 'Сол жақ бөліктері және кілт')}</legend>
          {value.leftOptions?.map((o, i) => (
            <div className="form-pair" key={o.id}>
              <Field label={`${o.id} RU`}>
                <input
                  required
                  value={o.textRu}
                  onChange={(e) =>
                    onChange({
                      ...value,
                      leftOptions: value.leftOptions?.map((v, n) =>
                        n === i ? { ...v, textRu: e.target.value } : v,
                      ),
                    })
                  }
                />
              </Field>
              <Field label={`${o.id} KZ`}>
                <input
                  value={o.textKz || ''}
                  onChange={(e) =>
                    onChange({
                      ...value,
                      leftOptions: value.leftOptions?.map((v, n) =>
                        n === i ? { ...v, textKz: e.target.value } : v,
                      ),
                    })
                  }
                />
              </Field>
              <Field label={`${l('Соответствие', 'Сәйкестік')} ${o.id}`}>
                <select
                  required
                  value={value.correctPairs?.find((p) => p.leftId === o.id)?.rightId || ''}
                  onChange={(e) =>
                    onChange({
                      ...value,
                      correctPairs: [
                        ...(value.correctPairs || []).filter((p) => p.leftId !== o.id),
                        { leftId: o.id, rightId: e.target.value },
                      ],
                    })
                  }
                >
                  <option value="">{l('Выберите', 'Таңдаңыз')}</option>
                  {value.options.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.id}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
          ))}
        </fieldset>
      ) : (
        <Field label={l('Правильный ответ', 'Дұрыс жауап')}>
          <select
            required
            value={value.correctOptionId || ''}
            onChange={(e) => onChange({ ...value, correctOptionId: e.target.value })}
          >
            <option value="">{l('Выберите', 'Таңдаңыз')}</option>
            {value.options.map((o) => (
              <option key={o.id} value={o.id}>
                {o.id}
              </option>
            ))}
          </select>
        </Field>
      )}
      <div className="form-pair">
        <Field label={l('Объяснение RU', 'Түсіндірме RU')}>
          <textarea
            value={value.explanationRu || ''}
            onChange={(e) => onChange({ ...value, explanationRu: e.target.value })}
          />
        </Field>
        <Field label={l('Объяснение KZ', 'Түсіндірме KZ')}>
          <textarea
            value={value.explanationKz || ''}
            onChange={(e) => onChange({ ...value, explanationKz: e.target.value })}
          />
        </Field>
      </div>
    </fieldset>
  );
}
export function QuizEditor({
  questions,
  onChange,
}: {
  questions: QuizQuestion[];
  onChange: (q: QuizQuestion[]) => void;
}) {
  const l = useL();
  return (
    <section className="editor-section">
      <h2>{l('Вопросы теста', 'Тест сұрақтары')}</h2>
      {questions.map((q, i) => (
        <div key={i}>
          <h3>
            {l('Вопрос', 'Сұрақ')} {i + 1}
          </h3>
          <QuestionEditor
            titles
            value={q}
            onChange={(v) => onChange(questions.map((old, n) => (n === i ? v : old)))}
          />
          <button
            type="button"
            className="text-button"
            disabled={questions.length === 1}
            onClick={() => onChange(questions.filter((_, n) => n !== i))}
          >
            {l('Удалить вопрос', 'Сұрақты жою')}
          </button>
        </div>
      ))}
      <button
        type="button"
        className="button secondary"
        disabled={questions.length >= 100}
        onClick={() => onChange([...questions, blankQuestion()])}
      >
        + {l('Вопрос', 'Сұрақ')}
      </button>
    </section>
  );
}
