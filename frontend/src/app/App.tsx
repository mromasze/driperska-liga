import { useEffect } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AppProviders } from './providers';
import { AppRoutes } from './router';
import { ServerGate } from '../components/system/ServerGate';
import { sound } from '../lib/sound';

export function App() {
  useEffect(() => {
    sound.armOnFirstGesture();
  }, []);

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
