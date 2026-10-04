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
