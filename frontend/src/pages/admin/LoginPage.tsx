import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useLogin, usePublicConfig } from '../../api/hooks/auth';
import { Turnstile } from '../../components/Turnstile';
import { Button } from '../../components/ui/Button';
import { ApiError } from '../../api/client';

export function LoginPage() {
  const login = useLogin();
  const config = usePublicConfig();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);

  const turnstileEnabled = Boolean(config.data?.turnstileEnabled && config.data.turnstileSiteKey);
  const blocked = turnstileEnabled && !turnstileToken;

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (blocked) return;
    login.mutate({ username, password, turnstileToken }, {
      onSuccess: (tokens) => {
        const requested = (location.state as { from?: string } | null)?.from;
        navigate(requested ?? (tokens.account.role === 'PLAYER' ? '/panel' : '/admin'), { replace: true });
      },
    });
  };

  const errorMsg = login.error instanceof ApiError
    ? login.error.message
    : login.isError ? 'Logowanie nie powiodło się' : null;

  return (
    <div className="grid min-h-screen place-items-center p-4">
      <div className="glass grid-tex w-full max-w-sm p-8">
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-lg bg-gradient-to-b from-gold-soft to-gold text-[#1a1205] shadow-glow-gold">
            <span className="font-display text-2xl font-bold">D</span>
          </div>
          <h1 className="font-display text-2xl">Logowanie</h1>
          <p className="mt-1 text-sm text-text-lo">Panel gracza i administracji · Driperska Liga</p>
        </div>
        <form onSubmit={onSubmit} className="space-y-4">
          <label className="block"><span className="kicker">Nick / login</span>
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username" className="form-control mt-1" />
          </label>
          <label className="block"><span className="kicker">Hasło</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" className="form-control mt-1" />
          </label>
          {turnstileEnabled && config.data?.turnstileSiteKey && (
            <Turnstile siteKey={config.data.turnstileSiteKey} onToken={setTurnstileToken} />
          )}
          {errorMsg && <p className="text-sm text-loss">{errorMsg}</p>}
          <Button type="submit" variant="gold" className="w-full" disabled={login.isPending || blocked}>
            {login.isPending ? 'Logowanie…' : 'Zaloguj się'}
          </Button>
        </form>
      </div>
    </div>
  );
}