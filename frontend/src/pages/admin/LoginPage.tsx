import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { useLogin } from '../../api/hooks/auth';
import { Button } from '../../components/ui/Button';
import { Card, CardBody, CardHeader, CardTitle } from '../../components/ui/Card';
import { ApiError } from '../../api/client';

const schema = z.object({
  username: z.string().min(1, 'Podaj login'),
  password: z.string().min(1, 'Podaj hasło'),
});

type LoginForm = z.infer<typeof schema>;

interface LocationState {
  from?: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useLogin();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit((values) => {
    login.mutate(values, {
      onSuccess: () => {
        const state = location.state as LocationState | null;
        navigate(state?.from ?? '/admin', { replace: true });
      },
    });
  });

  const errorMessage =
    login.error instanceof ApiError
      ? login.error.status === 401
        ? 'Błędny login lub hasło.'
        : (login.error.problem?.detail ?? login.error.message)
      : login.error
        ? 'Logowanie nie powiodło się. Spróbuj ponownie.'
        : null;

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-0 px-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Panel — logowanie</CardTitle>
        </CardHeader>
        <CardBody>
          <form onSubmit={onSubmit} className="space-y-4" noValidate>
            <div>
              <label htmlFor="username" className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
                Login
              </label>
              <input
                id="username"
                autoComplete="username"
                {...register('username')}
                className="w-full rounded-sm border border-line bg-bg-0 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--gold)]"
              />
              {errors.username && (
                <p className="mt-1 text-xs text-loss">{errors.username.message}</p>
              )}
            </div>

            <div>
              <label htmlFor="password" className="mb-1 block text-xs uppercase tracking-wide text-text-lo">
                Hasło
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                {...register('password')}
                className="w-full rounded-sm border border-line bg-bg-0 px-3 py-2 text-sm text-text-hi outline-none focus:border-[var(--gold)]"
              />
              {errors.password && (
                <p className="mt-1 text-xs text-loss">{errors.password.message}</p>
              )}
            </div>

            {errorMessage && <p className="text-sm text-loss">{errorMessage}</p>}

            <Button
              type="submit"
              variant="gold"
              className="w-full"
              disabled={isSubmitting || login.isPending}
            >
              {login.isPending ? 'Logowanie…' : 'Zaloguj'}
            </Button>
          </form>

          <Link to="/" className="mt-4 block text-center text-xs text-text-lo hover:text-text-hi">
            ← Powrót do strony publicznej
          </Link>
        </CardBody>
      </Card>
    </div>
  );
}
