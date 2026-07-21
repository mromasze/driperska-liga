import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useLogin } from '../../api/hooks/auth';
import { Button } from '../../components/ui/Button';
import { ApiError } from '../../api/client';

export function LoginPage() {
  const login = useLogin();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    login.mutate({ username, password }, { onSuccess: () => navigate('/admin') });
  };

  const errorMsg =
    login.error instanceof ApiError ? login.error.message : login.isError ? 'Logowanie nie powiodło się' : null;

  return (
    <div className="grid min-h-screen place-items-center p-4">
      <div className="glass grid-tex w-full max-w-sm p-8">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-lg bg-gradient-to-b from-gold-soft to-gold text-[#1a1205] shadow-glow-gold">
            <span className="font-display text-2xl font-bold">D</span>
          </div>
          <h1 className="font-display text-2xl">Panel administracyjny</h1>
          <p className="mt-1 text-sm text-text-lo">Driperska Liga</p>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block">
            <span className="kicker">Login</span>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoFocus
              className="mt-1 h-10 w-full rounded-md border border-line bg-bg-1 px-3 text-text-hi focus:border-line-strong"
            />
          </label>
          <label className="block">
            <span className="kicker">Hasło</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 h-10 w-full rounded-md border border-line bg-bg-1 px-3 text-text-hi focus:border-line-strong"
            />
          </label>
          {errorMsg && <p className="text-sm text-loss">{errorMsg}</p>}
          <Button type="submit" variant="gold" className="w-full" disabled={login.isPending}>
            {login.isPending ? 'Logowanie…' : 'Zaloguj się'}
          </Button>
        </form>
      </div>
    </div>
  );
}
