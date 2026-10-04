import { useState } from 'react';
import { request } from '../../api';
import { useAction, useL, Field, Feedback } from '../shared';
import { savedSchema } from '../content/model';

export default function Enrollment({ courseId }: { courseId: string }) {
  const l = useL(),
    action = useAction(),
    [email, setEmail] = useState(''),
    [status, setStatus] = useState('ACTIVE');
  return (
    <section className="editor-section">
      <h2>{l('Зачисление ученика', 'Оқушыны тіркеу')}</h2>
      <p className="hint">
        {l(
          'Укажите почту зарегистрированного ученика. Участники группы зачисляются автоматически. Отмена закрывает доступ к курсу, в том числе через текущие группы.',
          'Тіркелген оқушының поштасын көрсетіңіз. Топ мүшелері автоматты түрде тіркеледі. Бас тарту курсқа, соның ішінде қазіргі топтар арқылы кіруді жабады.',
        )}
      </p>
      <form
        className="editor-form"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            await request(`/teacher/courses/${courseId}/enrollments`, savedSchema, {
              method: 'POST',
              body: { email, status },
            });
          });
        }}
      >
        <div className="form-pair">
          <Field label={l('Почта ученика', 'Оқушының поштасы')}>
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label={l('Доступ к курсу', 'Курсқа кіру')}>
            <select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="ACTIVE">{l('Зачислить', 'Тіркеу')}</option>
              <option value="CANCELLED">{l('Отменить зачисление', 'Тіркеуден шығару')}</option>
            </select>
          </Field>
        </div>
        <button className="button secondary" disabled={action.busy}>
          {l('Применить зачисление', 'Тіркеуді қолдану')}
        </button>
        <Feedback action={action} />
      </form>
    </section>
  );
}
