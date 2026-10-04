import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { request } from '../../api';
import { Empty, ErrorState, Loading, PageHeading } from '../../components';
import { useApp } from '../../state';
import { Feedback, Field, Pager, useAction, useL } from '../shared';
import {
  notificationsSchema,
  openedSchema,
  preferencesSchema,
  profileSchema,
  savedSchema,
  type Preferences,
} from './model';
import { useStudyResource } from './useStudyResource';
import './study.css';
const labels: Record<string, [string, string]> = {
  PLAN: ['Учебный план', 'Оқу жоспары'],
  DEADLINE: ['Срок задания', 'Тапсырма мерзімі'],
  GRADE: ['Оценка опубликована', 'Баға жарияланды'],
  MATERIAL: ['Материал группы', 'Топ материалы'],
};
export function NotificationsPage() {
  const l = useL(),
    { language } = useApp(),
    navigate = useNavigate(),
    action = useAction();
  const [unread, setUnread] = useState(false),
    [page, setPage] = useState(0);
  const notifications = useStudyResource(
    `/study/notifications?page=${page}&unread=${unread}`,
    notificationsSchema,
  );
  const prefs = useStudyResource('/study/notifications/preferences', preferencesSchema);
  const profile = useStudyResource('/study/profile', profileSchema);
  return (
    <div className="study-page">
      <PageHeading
        title={l('Уведомления', 'Хабарламалар')}
        body={l(
          'Напоминания об учёбе, сроках, оценках и новых материалах вашей группы.',
          'Оқу, мерзімдер, бағалар және тобыңыздың жаңа материалдары туралы еске салулар.',
        )}
      />
      <div className="study-nav">
        <Link to="/study">{l('Мой план', 'Менің жоспарым')}</Link>
      </div>
      {prefs.error ? (
        <ErrorState error={prefs.error} retry={prefs.reload} />
      ) : (
        prefs.data && (
          <PreferenceForm
            key={prefs.data.revision}
            preferences={prefs.data}
            zone={profile.data?.timeZone || 'Asia/Almaty'}
            reload={prefs.reload}
          />
        )
      )}
      <div className="study-list-heading">
        <h2>
          {l('Лента', 'Тізім')}{' '}
          <span className="study-muted">
            ({notifications.data?.unread ?? 0} {l('непрочитанных', 'оқылмаған')})
          </span>
        </h2>
        <label>
          <input
            type="checkbox"
            checked={unread}
            onChange={(e) => {
              setUnread(e.target.checked);
              setPage(0);
            }}
          />
          {l('Непрочитанные', 'Оқылмаған')}
        </label>
        <button className="button secondary small" onClick={notifications.reload}>
          {l('Обновить', 'Жаңарту')}
        </button>
      </div>
      <Feedback action={action} />
      {notifications.error ? (
        <ErrorState error={notifications.error} retry={notifications.reload} />
      ) : !notifications.data ? (
        <Loading />
      ) : notifications.data.items.length === 0 ? (
        <Empty
          title={l('Новых сообщений нет', 'Жаңа хабарламалар жоқ')}
          body={l(
            'Напоминания появятся здесь после фоновой проверки.',
            'Фондық тексеруден кейін еске салулар осы жерде пайда болады.',
          )}
        />
      ) : (
        <div className="study-notification-list">
          {notifications.data.items.map((item) => (
            <article
              key={item.id}
              className={'study-notification' + (item.read ? '' : ' study-unread')}
            >
              <div>
                <p className="study-task-meta">
                  {l(...(labels[item.kind] || ['Уведомление', 'Хабарлама']))} ·{' '}
                  <time dateTime={item.createdAt}>
                    {new Intl.DateTimeFormat(language === 'ru' ? 'ru-RU' : 'kk-KZ', {
                      timeZone: profile.data?.timeZone || 'Asia/Almaty',
                      dateStyle: 'short',
                      timeStyle: 'short',
                    }).format(new Date(item.createdAt))}
                  </time>
                </p>
                <h3>{l(item.titleRu, item.titleKz)}</h3>
                {!item.available && (
                  <p className="study-unavailable">
                    {l('Материал больше недоступен.', 'Материал енді қолжетімсіз.')}
                  </p>
                )}
              </div>
              <div className="study-actions">
                <button
                  className="button secondary small"
                  disabled={action.busy || !item.available}
                  onClick={() =>
                    void action.run(async () => {
                      const opened = await request(
                        '/study/notifications/' + item.id + '/open',
                        openedSchema,
                        { method: 'POST' },
                      );
                      navigate(opened.url);
                    })
                  }
                >
                  {l('Открыть', 'Ашу')}
                </button>
                <button
                  className="button secondary small"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(async () => {
                      await request('/study/notifications/' + item.id, savedSchema, {
                        method: 'PATCH',
                        body: { read: !item.read },
                      });
                      notifications.reload();
                    })
                  }
                >
                  {item.read ? l('Не прочитано', 'Оқылмады') : l('Прочитано', 'Оқылды')}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
      {notifications.data && notifications.data.total > 25 && (
        <Pager page={page} total={notifications.data.total} onChange={setPage} />
      )}
    </div>
  );
}
function PreferenceForm({
  preferences,
  zone,
  reload,
}: {
  preferences: Preferences;
  zone: string;
  reload: () => void;
}) {
  const l = useL(),
    action = useAction(),
    [draft, setDraft] = useState(preferences);
  const choices: [
    keyof Pick<
      Preferences,
      'enabled' | 'plans' | 'deadlines' | 'grades' | 'materials' | 'quietEnabled'
    >,
    string,
    string,
  ][] = [
    ['enabled', 'Включить уведомления', 'Хабарламаларды қосу'],
    ['plans', 'Учебный план', 'Оқу жоспары'],
    ['deadlines', 'Сроки заданий', 'Тапсырма мерзімдері'],
    ['grades', 'Опубликованные оценки', 'Жарияланған бағалар'],
    ['materials', 'Материалы моей группы', 'Тобымның материалдары'],
    ['quietEnabled', 'Тихие часы', 'Тыныш уақыт'],
  ];
  return (
    <details className="study-panel">
      <summary>{l('Настроить уведомления', 'Хабарламаларды баптау')}</summary>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            setDraft(
              await request('/study/notifications/preferences', preferencesSchema, {
                method: 'PUT',
                body: draft,
              }),
            );
            reload();
          });
        }}
      >
        <fieldset disabled={action.busy} className="study-form">
          <div className="study-choice-group">
            {choices.map(([key, ru, kz]) => (
              <label key={key}>
                <input
                  type="checkbox"
                  checked={draft[key]}
                  onChange={(e) => setDraft({ ...draft, [key]: e.target.checked })}
                />
                {l(ru, kz)}
              </label>
            ))}
          </div>
          <div className="study-form-row">
            <Field label={l('Тихие часы: начало', 'Тыныш уақыт: басталуы')}>
              <input
                type="time"
                required
                disabled={!draft.quietEnabled}
                value={draft.quietStart.slice(0, 5)}
                onChange={(e) => setDraft({ ...draft, quietStart: e.target.value })}
              />
            </Field>
            <Field label={l('Тихие часы: конец', 'Тыныш уақыт: аяқталуы')}>
              <input
                type="time"
                required
                disabled={!draft.quietEnabled}
                value={draft.quietEnd.slice(0, 5)}
                onChange={(e) => setDraft({ ...draft, quietEnd: e.target.value })}
              />
            </Field>
          </div>
          <p className="study-muted">
            {zone} ·{' '}
            {l(
              'В тихие часы новые напоминания откладываются. Одинаковое время начала и конца отключает тихий период. Уведомления остаются внутри приложения.',
              'Тыныш уақытта жаңа еске салулар кейінге қалдырылады. Басталу мен аяқталу бірдей болса, тыныш кезең өшеді. Хабарламалар қолданба ішінде қалады.',
            )}
          </p>
          <button className="button">{l('Сохранить настройки', 'Баптауларды сақтау')}</button>
        </fieldset>
        <Feedback action={action} />
      </form>
    </details>
  );
}
