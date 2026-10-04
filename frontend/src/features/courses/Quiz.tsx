import { AnswerControls, AnswerText } from '../assessment/AnswerControls';
import { answerSchema, completeAnswer, type Answer } from '../assessment/model';
import { InlineText, RichText } from '../content/RichText';
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState } from '../../components';
import { useL, useAction, Feedback } from '../shared';
import { questionPayload } from '../content/model';
const attemptSchema = z.object({
  id: z.string(),
  quizId: z.string(),
  questions: z.array(questionPayload),
  score: z.number().nullable(),
  answers: z.array(z.union([z.string(), answerSchema])).nullable(),
});
export default function Quiz() {
  const { id, attemptId } = useParams();
  return attemptId ? <Attempt id={attemptId} /> : <Start id={id!} />;
}
function Start({ id }: { id: string }) {
  const l = useL(),
    action = useAction(),
    navigate = useNavigate();
  return (
    <>
      <PageHeading title={l('Проверка знаний', 'Білімді тексеру')} />
      <p>
        {l(
          'Выберите ответы на все вопросы. Разбор откроется после завершения.',
          'Барлық сұрақтарға жауап таңдаңыз. Талдау аяқтағаннан кейін ашылады.',
        )}
      </p>
      <button
        className="button"
        disabled={action.busy}
        onClick={() =>
          void action.run(async () => {
            const a = await request(`/quizzes/${id}/attempts`, attemptSchema, { method: 'POST' });
            navigate(`/quiz-attempts/${a.id}`);
          })
        }
      >
        {l('Начать тест урока', 'Сабақ тестін бастау')}
      </button>
      <Feedback action={action} />
    </>
  );
}
function Attempt({ id }: { id: string }) {
  const l = useL(),
    r = useResource(`/quiz-attempts/${id}`, attemptSchema),
    action = useAction(),
    [answers, setAnswers] = useState<Record<number, Answer>>({});
  if (r.loading) return <Loading />;
  if (r.error) return <ErrorState error={r.error} retry={r.reload} />;
  const a = r.data!,
    finished = a.score !== null;
  return (
    <>
      <PageHeading
        title={
          finished ? l('Результат теста', 'Тест нәтижесі') : l('Проверка знаний', 'Білімді тексеру')
        }
        back={{ to: '/courses', label: l('Курсы', 'Курстар') }}
      />
      {finished && <p className="quiz-score">{a.score}%</p>}
      <form
        className="editor-form"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            await request(`/quiz-attempts/${id}/finish`, attemptSchema, {
              method: 'POST',
              body: { answers: a.questions.map((_, i) => answers[i]) },
            });
            r.reload();
          });
        }}
      >
        {a.questions.map((q, i) => (
          <fieldset className="question-card" key={i}>
            <legend>
              {i + 1}. <InlineText text={l(q.titleRu, q.titleKz)} />
            </legend>
            <AnswerControls
              question={q}
              value={
                finished
                  ? (typeof a.answers?.[i] === 'string'
                      ? { selectedOptionId: a.answers[i] as string }
                      : (a.answers?.[i] as Answer)) || {}
                  : answers[i] || {}
              }
              onChange={(v) => setAnswers({ ...answers, [i]: v })}
              disabled={finished || action.busy}
            />
            {finished && (
              <>
                <p>
                  {l('Правильный ответ', 'Дұрыс жауап')}: <AnswerText question={q} correct />
                </p>
                <RichText text={l(q.explanationRu, q.explanationKz)} />
              </>
            )}
          </fieldset>
        ))}
        {!finished && (
          <button
            className="button"
            disabled={action.busy || !a.questions.every((q, i) => completeAnswer(q, answers[i]))}
          >
            {l('Завершить тест', 'Тестті аяқтау')}
          </button>
        )}
        <Feedback action={action} />
      </form>
    </>
  );
}
