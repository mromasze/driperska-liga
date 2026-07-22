import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center">
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
