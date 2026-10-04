import { useId } from 'react';
import { useL } from '../shared';
import { InlineText } from '../content/RichText';
import type { Answer, Assessment } from './model';

export function AnswerControls({
  question,
  value,
  onChange,
  disabled = false,
}: {
  question: Assessment;
  value: Answer;
  onChange: (a: Answer) => void;
  disabled?: boolean;
}) {
  const l = useL(),
    name = useId();
  if (question.questionType === 'MATCHING')
    return (
      <div className="matching-options">
        {question.leftOptions?.map((left) => (
          <label key={left.id}>
            <span>
              <InlineText text={l(left.textRu, left.textKz)} />
            </span>
            <select
              disabled={disabled}
              required
              value={value.pairs?.find((p) => p.leftId === left.id)?.rightId || ''}
              onChange={(e) =>
                onChange({
                  pairs: [
                    ...(value.pairs || []).filter((p) => p.leftId !== left.id),
                    { leftId: left.id, rightId: e.target.value },
                  ],
                })
              }
            >
              <option value="">{l('Выберите соответствие', 'Сәйкестікті таңдаңыз')}</option>
              {question.options?.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.id}. {l(o.textRu, o.textKz)}
                </option>
              ))}
            </select>
          </label>
        ))}
      </div>
    );
  const multiple = question.questionType === 'MULTIPLE_SELECT';
  return (
    <div className="answer-options">
      {question.options?.map((option) => {
        const checked = multiple
          ? value.selectedOptionIds?.includes(option.id)
          : value.selectedOptionId === option.id;
        return (
          <label className={`answer-option ${checked ? 'selected' : ''}`} key={option.id}>
            <input
              type={multiple ? 'checkbox' : 'radio'}
              name={name}
              disabled={disabled}
              checked={!!checked}
              onChange={() =>
                onChange(
                  multiple
                    ? {
                        selectedOptionIds: checked
                          ? (value.selectedOptionIds || []).filter((id) => id !== option.id)
                          : [...(value.selectedOptionIds || []), option.id],
                      }
                    : { selectedOptionId: option.id },
                )
              }
            />
            <span className="option-letter" aria-hidden="true">
              {option.id}
            </span>
            <span>
              <InlineText text={l(option.textRu, option.textKz)} />
            </span>
          </label>
        );
      })}
    </div>
  );
}

export function AnswerText({
  question,
  answer,
  correct = false,
}: {
  question: Assessment;
  answer?: Answer | null;
  correct?: boolean;
}) {
  const l = useL();
  const text = (id: string | undefined) => {
    const o = question.options?.find((o) => o.id === id);
    return o ? l(o.textRu, o.textKz) : id || '';
  };
  if (question.questionType === 'MATCHING') {
    const pairs = correct ? question.correctPairs : answer?.pairs;
    return (
      <>
        {pairs
          ?.map((p) => {
            const left = question.leftOptions?.find((o) => o.id === p.leftId);
            return `${left ? l(left.textRu, left.textKz) : p.leftId}: ${text(p.rightId)}`;
          })
          .join('; ') || l('Нет ответа', 'Жауап жоқ')}
      </>
    );
  }
  const ids = correct
    ? question.questionType === 'MULTIPLE_SELECT'
      ? question.correctOptionIds
      : [question.correctOptionId || '']
    : answer?.selectedOptionIds || (answer?.selectedOptionId ? [answer.selectedOptionId] : []);
  return <InlineText text={ids?.map(text).join('; ') || l('Нет ответа', 'Жауап жоқ')} />;
}
