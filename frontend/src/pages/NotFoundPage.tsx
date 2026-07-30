import { Link } from 'react-router-dom';
import { LogoHex } from '../components/brand/Logo';
import { Button } from '../components/ui/Button';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center">
      <LogoHex size={80} className="opacity-45" />
      <div className="font-display text-7xl font-bold text-gradient-gold">404</div>
      <p className="max-w-sm text-text-lo">
        Ta strona zniknęła w mgłach Riftu. Wróć na stronę główną.
      </p>
      <Link to="/">
        <Button variant="gold">Strona główna</Button>
      </Link>
    </div>
  );
}
