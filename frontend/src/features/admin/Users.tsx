import { useState } from 'react';
import { z } from 'zod';
import { request } from '../../api';
import { useResource } from '../../hooks';
import { PageHeading, Loading, ErrorState } from '../../components';
import { Field, Feedback, Pager, roleLabels, useAction, useL } from '../shared';
import { savedSchema } from '../content/model';
const account = z.object({
  id: z.string(),
  email: z.string(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  role: z.enum(['STUDENT', 'TEACHER', 'CONTENT_EDITOR', 'ADMIN']),
  active: z.boolean(),
  revision: z.number(),
});
const accounts = z.object({
  items: z.array(account),
  total: z.number(),
  page: z.number(),
  size: z.number(),
});
export default function Users() {
  const l = useL(),
    [q, setQ] = useState(''),
    [role, setRole] = useState(''),
    [page, setPage] = useState(0),
    r = useResource(`/admin/users?q=${encodeURIComponent(q)}&role=${role}&page=${page}`, accounts);
  return (
    <>
      <PageHeading
        title={l('Пользователи', 'Пайдаланушылар')}
        body={l(
          'Роли и доступ к платформе. Изменения записываются в журнал.',
          'Платформа рөлдері мен қолжетімділігі. Өзгерістер журналға жазылады.',
        )}
      />
      <div className="workspace-toolbar">
        <Field label={l('Поиск по имени или почте', 'Аты немесе поштасы бойынша іздеу')}>
          <input
            type="search"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </Field>
        <Field label={l('Роль', 'Рөл')}>
          <select
            value={role}
            onChange={(e) => {
              setRole(e.target.value);
              setPage(0);
            }}
          >
            <option value="">{l('Все', 'Барлығы')}</option>
            {Object.entries(roleLabels).map(([k, v]) => (
              <option key={k} value={k}>
                {l(...v)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      {r.loading ? (
        <Loading />
      ) : r.error ? (
        <ErrorState error={r.error} retry={r.reload} />
      ) : (
        <>
          <div className="account-list">
            {r.data?.items.map((u) => (
              <UserRow key={`${u.id}:${u.revision}`} user={u} reload={r.reload} />
            ))}
          </div>
          <Pager page={page} total={r.data?.total || 0} onChange={setPage} />
        </>
      )}
    </>
  );
}
function UserRow({ user, reload }: { user: z.infer<typeof account>; reload: () => void }) {
  const l = useL(),
    action = useAction(),
    [role, setRole] = useState(user.role),
    [active, setActive] = useState(user.active);
  return (
    <form
      className="account-row"
      onSubmit={(e) => {
        e.preventDefault();
        void action.run(async () => {
          await request(`/admin/users/${user.id}`, savedSchema, {
            method: 'PATCH',
            body: { role, active, revision: user.revision },
          });
          reload();
        });
      }}
    >
      <div>
        <strong>
          {user.firstName} {user.lastName}
        </strong>
        <small>{user.email}</small>
      </div>
      <Field label={`${l('Роль', 'Рөл')}: ${user.email}`}>
        <select value={role} onChange={(e) => setRole(e.target.value as typeof role)}>
          {Object.entries(roleLabels).map(([k, v]) => (
            <option key={k} value={k}>
              {l(...v)}
            </option>
          ))}
        </select>
      </Field>
      <label className="check-field">
        <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
        {l('Активен', 'Белсенді')}
      </label>
      <button
        className="button secondary"
        disabled={action.busy || (role === user.role && active === user.active)}
      >
        {l('Сохранить доступ', 'Қолжетімділікті сақтау')}
      </button>
      <Feedback action={action} />
    </form>
  );
}
