import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';

export function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <div className="num text-6xl font-bold text-gold">404</div>
      <h1 className="text-2xl">Nie znaleziono strony</h1>
      <p className="text-sm text-text-lo">Ta ścieżka nie istnieje w Driperskiej Lidze.</p>
      <Link to="/">
        <Button variant="gold">Wróć na stronę główną</Button>
      </Link>
    </div>
  );
}
