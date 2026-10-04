import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { AppProvider, useApp } from '../state';
import { AuthPage, SubjectsPage } from '../pages';
import { LanguageSwitch } from '../components';
import { TOKEN_KEY, request, userSchema } from '../api';

const user = {
  id: 'a41cdaf2-8cd3-4b59-94e8-8a75fe45b7fd',
  email: 'learner@example.org',
  firstName: 'Айдана',
  lastName: 'Тест',
  language: 'ru',
  role: 'STUDENT',
};
function Greeting() {
  const { user } = useApp();
  return <h1>{user?.firstName}</h1>;
}
function AccountControls() {
  const { user, logout } = useApp();
  return (
    <>
      <h1>{user?.firstName || 'Signed out'}</h1>
      <LanguageSwitch />
      <button onClick={logout}>Logout</button>
    </>
  );
}
function auth(mode: 'login' | 'register') {
  render(
    <AppProvider>
      <MemoryRouter initialEntries={[`/${mode}`]}>
        <Routes>
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route path="/" element={<Greeting />} />
        </Routes>
      </MemoryRouter>
    </AppProvider>,
  );
}
describe('student entry flows', () => {
  it('registers with actual form fields and navigates to the account', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ token: 'test-session', user })));
    vi.stubGlobal('fetch', fetcher);
    auth('register');
    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Имя'), 'Айдана');
    await u.type(screen.getByLabelText('Фамилия'), 'Тест');
    await u.type(screen.getByLabelText('Электронная почта'), user.email);
    await u.type(screen.getByLabelText('Пароль'), 'safe-test-password');
    await u.click(screen.getByRole('button', { name: 'Создать аккаунт' }));
    expect(await screen.findByRole('heading', { name: 'Айдана' })).toBeInTheDocument();
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toMatchObject({
      email: user.email,
      language: 'ru',
      firstName: 'Айдана',
    });
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('test-session');
  });
  it('keeps invalid credentials recoverable and prevents token storage', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })));
    auth('login');
    const u = userEvent.setup();
    await u.type(screen.getByLabelText('Электронная почта'), user.email);
    await u.type(screen.getByLabelText('Пароль'), 'wrong-password');
    await u.click(screen.getByRole('button', { name: 'Войти' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Проверьте почту и пароль');
    expect(screen.getByRole('button', { name: 'Войти' })).toBeEnabled();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
  it('persists Kazakh choice and translates the complete form', async () => {
    auth('register');
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'ҚАЗ' }));
    expect(screen.getByLabelText('Құпиясөз')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Тіркелу' })).toBeInTheDocument();
    expect(localStorage.getItem('education.language')).toBe('kz');
    expect(document.documentElement.lang).toBe('kk');
  });
  it('restores the user after a page refresh', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'test-session');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(user))));
    render(
      <AppProvider>
        <Greeting />
      </AppProvider>,
    );
    expect(await screen.findByRole('heading', { name: 'Айдана' })).toBeInTheDocument();
  });
  it('clears an expired account and preserves the chosen language', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'expired');
    localStorage.setItem('education.language', 'kz');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })));
    render(
      <AppProvider>
        <LanguageSwitch />
      </AppProvider>,
    );
    await waitFor(() => expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull());
    expect(screen.getByRole('button', { name: 'ҚАЗ' })).toHaveAttribute('aria-pressed', 'true');
  });
  it('turns a malformed successful response into a retryable error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ unexpected: true }))),
    );
    render(
      <AppProvider>
        <MemoryRouter>
          <SubjectsPage />
        </MemoryRouter>
      </AppProvider>,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Не удалось прочитать ответ сервера',
    );
    expect(screen.getByRole('button', { name: 'Попробовать снова' })).toBeEnabled();
  });
  it('offers a helpful empty catalogue with no dead actions', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]')));
    render(
      <AppProvider>
        <MemoryRouter>
          <SubjectsPage />
        </MemoryRouter>
      </AppProvider>,
    );
    expect(await screen.findByText('Предметы скоро появятся')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Начать тест' })).not.toBeInTheDocument();
  });
  it('does not restore a logged-out account from a delayed language response', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'old-session');
    let release!: (response: Response) => void;
    const delayed = new Promise<Response>((resolve) => {
      release = resolve;
    });
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(user)))
      .mockReturnValueOnce(delayed);
    vi.stubGlobal('fetch', fetcher);
    render(
      <AppProvider>
        <AccountControls />
      </AppProvider>,
    );
    await screen.findByRole('heading', { name: 'Айдана' });
    const u = userEvent.setup();
    await u.click(screen.getByRole('button', { name: 'ҚАЗ' }));
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));
    await u.click(screen.getByRole('button', { name: 'Logout' }));
    release(new Response(JSON.stringify({ ...user, language: 'kz' })));
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Signed out' })).toBeInTheDocument(),
    );
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });
  it('does not let an old unauthorized response clear a new account token', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'old-session');
    let release!: (response: Response) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn().mockReturnValue(
        new Promise<Response>((resolve) => {
          release = resolve;
        }),
      ),
    );
    const pending = request('/auth/me', userSchema);
    sessionStorage.setItem(TOKEN_KEY, 'new-session');
    release(new Response('{}', { status: 401 }));
    await expect(pending).rejects.toThrow();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBe('new-session');
  });
});
