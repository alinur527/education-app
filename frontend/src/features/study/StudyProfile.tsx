import { useState } from 'react';
import { z } from 'zod';
import { request, subjectSchema } from '../../api';
import { Field, Feedback, useAction, useL } from '../shared';
import { profileSchema, type Profile } from './model';
import { useStudyResource } from './useStudyResource';
export function StudyProfile({ profile, onSaved }: { profile: Profile; onSaved: () => void }) {
  const l = useL(),
    action = useAction();
  const [draft, setDraft] = useState(profile);
  const subjects = useStudyResource('/subjects', z.array(subjectSchema));
  const days = [
    ['Пн', 'Дс'],
    ['Вт', 'Сс'],
    ['Ср', 'Ср'],
    ['Чт', 'Бс'],
    ['Пт', 'Жм'],
    ['Сб', 'Сб'],
    ['Вс', 'Жс'],
  ];
  return (
    <details className="study-panel" open={profile.revision === 0}>
      <summary>
        {l('Мой учебный профиль', 'Менің оқу профилім')} · {profile.minutesPerDay}{' '}
        {l('мин/день', 'мин/күн')}
      </summary>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            const saved = await request('/study/profile', profileSchema, {
              method: 'PUT',
              body: draft,
            });
            setDraft(saved);
            onSaved();
          });
        }}
      >
        <fieldset disabled={action.busy} className="study-form">
          <Field label={l('Моя цель', 'Менің мақсатым')}>
            <input
              maxLength={300}
              value={draft.goal}
              onChange={(e) => setDraft({ ...draft, goal: e.target.value })}
              placeholder={l('Например, подготовиться к ЕНТ', 'Мысалы, ҰБТ-ға дайындалу')}
            />
          </Field>
          <div className="study-form-row">
            <Field label={l('Целевая дата', 'Мақсатты күн')}>
              <input
                type="date"
                required
                value={draft.targetDate}
                onChange={(e) => setDraft({ ...draft, targetDate: e.target.value })}
              />
            </Field>
            <Field label={l('Минут в учебный день', 'Оқу күніндегі минуттар')}>
              <input
                type="number"
                min={10}
                max={360}
                required
                value={draft.minutesPerDay}
                onChange={(e) => setDraft({ ...draft, minutesPerDay: Number(e.target.value) })}
              />
            </Field>
            <Field label={l('Часовой пояс', 'Уақыт белдеуі')}>
              <input
                required
                maxLength={80}
                list="study-timezones"
                value={draft.timeZone}
                onChange={(e) => setDraft({ ...draft, timeZone: e.target.value })}
              />
              <datalist id="study-timezones">
                <option value="Asia/Almaty" />
                <option value="Asia/Qyzylorda" />
                <option value="UTC" />
              </datalist>
            </Field>
          </div>
          <fieldset className="study-choice-group">
            <legend>{l('Учебные дни', 'Оқу күндері')}</legend>
            {days.map((day, i) => (
              <label key={i}>
                <input
                  type="checkbox"
                  checked={draft.availableDays.includes(i + 1)}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      availableDays: e.target.checked
                        ? [...draft.availableDays, i + 1]
                        : draft.availableDays.filter((d) => d !== i + 1),
                    })
                  }
                />
                {l(...day)}
              </label>
            ))}
          </fieldset>
          <fieldset className="study-choice-group">
            <legend>{l('Предметы ЕНТ', 'ҰБТ пәндері')}</legend>
            {subjects.data?.map((s) => (
              <label key={s.id}>
                <input
                  type="checkbox"
                  checked={draft.selectedSubjects.includes(s.id)}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      selectedSubjects: e.target.checked
                        ? [...draft.selectedSubjects, s.id]
                        : draft.selectedSubjects.filter((id) => id !== s.id),
                    })
                  }
                />
                {l(s.nameRu, s.nameKz)}
              </label>
            ))}
            {subjects.error ? (
              <p role="alert">
                {l('Предметы не загрузились', 'Пәндер жүктелмеді')}{' '}
                <button type="button" onClick={subjects.reload}>
                  {l('Повторить', 'Қайталау')}
                </button>
              </p>
            ) : null}
          </fieldset>
          <p className="study-muted">
            {l(
              'Курсы, задания и ваши карточки также войдут в план. Пересчёт не меняет закреплённые, перенесённые и завершённые задачи.',
              'Курстар, тапсырмалар және жеке карточкалар да жоспарға кіреді. Қайта есептеу бекітілген, ауыстырылған және аяқталған тапсырмаларды өзгертпейді.',
            )}
          </p>
          <button className="button" disabled={!draft.availableDays.length}>
            {action.busy
              ? l('Сохраняем…', 'Сақталуда…')
              : l('Сохранить профиль', 'Профильді сақтау')}
          </button>
        </fieldset>
        <Feedback action={action} />
      </form>
    </details>
  );
}
