import { useEffect } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AppProviders } from './providers';
import { AppRoutes } from './router';
import { ServerGate } from '../components/system/ServerGate';
import { SplashGate } from '../components/system/SplashGate';
import { ConsentBanner } from '../components/consent/ConsentBanner';
import { sound } from '../lib/sound';

export function App() {
  useEffect(() => {
    sound.armOnFirstGesture();
  }, []);

  return (
    <AppProviders>
      <BrowserRouter>
        <SplashGate>
          <ServerGate>
            <AppRoutes />
          </ServerGate>
          <ConsentBanner />
        </SplashGate>
      </BrowserRouter>
    </AppProviders>
  );
}
