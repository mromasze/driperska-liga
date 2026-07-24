import { BrowserRouter } from 'react-router-dom';
import { AppProviders } from './providers';
import { AppRoutes } from './router';
import { ServerGate } from '../components/system/ServerGate';

export function App() {
  return (
    <AppProviders>
      <BrowserRouter>
        <ServerGate>
          <AppRoutes />
        </ServerGate>
      </BrowserRouter>
    </AppProviders>
  );
}
